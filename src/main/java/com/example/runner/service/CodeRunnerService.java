package com.example.runner.service;

import com.example.runner.model.ExecutionResult;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.EnvironmentAccess;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CodeRunnerService {

  private static final long JAVA_TIMEOUT_SECONDS = 5;
  private final Object javaExecutionLock = new Object();

  public ExecutionResult executeCode(String languageId, String code) {
    if (languageId == null || languageId.isBlank()) {
      return ExecutionResult.builder()
          .stdout("")
          .stderr("Language ID must not be blank")
          .output("Language ID must not be blank")
          .success(false)
          .build();
    }

    String lang = languageId.trim().toLowerCase();
    return switch (lang) {
      case "python", "py" -> executeGraalVm("python", code);
      case "js", "javascript" -> executeGraalVm("js", code);
      case "java" -> executeJava(code);
      default ->
          ExecutionResult.builder()
              .stdout("")
              .stderr("Unsupported language: " + languageId)
              .output("Unsupported language: " + languageId)
              .success(false)
              .build();
    };
  }

  private ExecutionResult executeGraalVm(String lang, String code) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();

    try (Context context =
        Context.newBuilder(lang)
            .logHandler(OutputStream.nullOutputStream())
            .option("engine.WarnInterpreterOnly", "false")
            .allowEnvironmentAccess(EnvironmentAccess.NONE)
            .allowNativeAccess(false)
            .allowIO(false)
            .out(out)
            .err(err)
            .build()) {

      context.eval(lang, code != null ? code : "");
      String stdout = out.toString(StandardCharsets.UTF_8);
      String stderr = err.toString(StandardCharsets.UTF_8);

      String combinedOutput = stdout;
      if (!stderr.isBlank()) {
        combinedOutput = combinedOutput.isEmpty() ? stderr : combinedOutput + "\n" + stderr;
      }

      return ExecutionResult.builder()
          .stdout(stdout)
          .stderr(stderr)
          .output(combinedOutput)
          .success(stderr.isBlank())
          .build();

    } catch (Exception e) {
      String stdout = out.toString(StandardCharsets.UTF_8);
      String stderr = err.toString(StandardCharsets.UTF_8);
      String errorMsg = e.getMessage() != null ? e.getMessage() : e.toString();
      if (stderr.isBlank()) {
        stderr = errorMsg;
      }
      return ExecutionResult.builder()
          .stdout(stdout)
          .stderr(stderr)
          .output(stdout.isEmpty() ? stderr : stdout + "\n" + stderr)
          .success(false)
          .build();
    }
  }

  private ExecutionResult executeJava(String code) {
    if (code == null || code.isBlank()) {
      return ExecutionResult.builder()
          .stdout("")
          .stderr("Code must not be blank")
          .output("Code must not be blank")
          .success(false)
          .build();
    }

    String className = extractClassName(code);
    String fullClassName = className;
    String packageName = extractPackageName(code);
    if (packageName != null && !packageName.isBlank()) {
      fullClassName = packageName + "." + className;
    }

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      return ExecutionResult.builder()
          .stdout("")
          .stderr("JavaCompiler is not available in current runtime environment")
          .output("JavaCompiler is not available in current runtime environment")
          .success(false)
          .build();
    }

    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    StandardJavaFileManager standardFileManager =
        compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);

    Map<String, JavaClassAsBytes> compiledClasses = new ConcurrentHashMap<>();
    JavaFileManager fileManager =
        new ForwardingJavaFileManager<>(standardFileManager) {
          @Override
          public JavaFileObject getJavaFileForOutput(
              Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
            JavaClassAsBytes bytes = new JavaClassAsBytes(className, kind);
            compiledClasses.put(className, bytes);
            return bytes;
          }
        };

    JavaFileObject sourceFile = new JavaSourceFromString(className, code);
    JavaCompiler.CompilationTask task =
        compiler.getTask(
            null, fileManager, diagnostics, null, null, Collections.singletonList(sourceFile));

    boolean compileSuccess = task.call();
    if (!compileSuccess) {
      StringBuilder errorReport = new StringBuilder();
      for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
        errorReport
            .append("Line ")
            .append(d.getLineNumber())
            .append(": ")
            .append(d.getMessage(Locale.getDefault()))
            .append("\n");
      }
      return ExecutionResult.builder()
          .stdout("")
          .stderr(errorReport.toString().trim())
          .output(errorReport.toString().trim())
          .success(false)
          .build();
    }

    ClassLoader customClassLoader =
        new ClassLoader(getClass().getClassLoader()) {
          @Override
          protected Class<?> findClass(String name) throws ClassNotFoundException {
            JavaClassAsBytes bytes = compiledClasses.get(name);
            if (bytes != null) {
              byte[] byteData = bytes.getBytes();
              return defineClass(name, byteData, 0, byteData.length);
            }
            return super.findClass(name);
          }
        };

    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    synchronized (javaExecutionLock) {
      ExecutorService executor = Executors.newSingleThreadExecutor();
      PrintStream originalOut = System.out;
      PrintStream originalErr = System.err;

      try {
        String finalFullClassName = fullClassName;
        Future<Void> future =
            executor.submit(
                () -> {
                  PrintStream psOut = new PrintStream(stdout, true, StandardCharsets.UTF_8);
                  PrintStream psErr = new PrintStream(stderr, true, StandardCharsets.UTF_8);
                  System.setOut(psOut);
                  System.setErr(psErr);

                  Class<?> clazz = customClassLoader.loadClass(finalFullClassName);
                  Method mainMethod = clazz.getMethod("main", String[].class);
                  mainMethod.invoke(null, (Object) new String[] {});
                  return null;
                });

        future.get(JAVA_TIMEOUT_SECONDS, TimeUnit.SECONDS);

      } catch (TimeoutException e) {
        stderr.writeBytes(
            ("Execution timed out after " + JAVA_TIMEOUT_SECONDS + " seconds\n")
                .getBytes(StandardCharsets.UTF_8));
      } catch (Exception e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        stderr.writeBytes(
            ("Execution error: " + cause.getMessage() + "\n").getBytes(StandardCharsets.UTF_8));
      } finally {
        System.setOut(originalOut);
        System.setErr(originalErr);
        executor.shutdownNow();
      }
    }

    String outStr = stdout.toString(StandardCharsets.UTF_8);
    String errStr = stderr.toString(StandardCharsets.UTF_8);
    String combined = outStr;
    if (!errStr.isBlank()) {
      combined = combined.isEmpty() ? errStr : combined + "\n" + errStr;
    }

    return ExecutionResult.builder()
        .stdout(outStr)
        .stderr(errStr)
        .output(combined)
        .success(errStr.isBlank())
        .build();
  }

  private String extractClassName(String code) {
    Pattern pattern = Pattern.compile("(?:public\\s+)?(?:final\\s+)?class\\s+([A-Za-z0-9_$]+)");
    Matcher matcher = pattern.matcher(code);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return "Main";
  }

  private String extractPackageName(String code) {
    Pattern pattern = Pattern.compile("^\\s*package\\s+([a-zA-Z0-9_.]+)\\s*;", Pattern.MULTILINE);
    Matcher matcher = pattern.matcher(code);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return null;
  }

  static class JavaSourceFromString extends SimpleJavaFileObject {
    private final String code;

    JavaSourceFromString(String name, String code) {
      super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
      this.code = code;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
      return code;
    }
  }

  static class JavaClassAsBytes extends SimpleJavaFileObject {
    private final ByteArrayOutputStream bos = new ByteArrayOutputStream();

    JavaClassAsBytes(String name, Kind kind) {
      super(URI.create("string:///" + name.replace('.', '/') + kind.extension), kind);
    }

    byte[] getBytes() {
      return bos.toByteArray();
    }

    @Override
    public OutputStream openOutputStream() {
      return bos;
    }
  }
}
