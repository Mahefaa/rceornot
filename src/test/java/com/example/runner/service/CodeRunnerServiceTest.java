package com.example.runner.service;

import static org.junit.jupiter.api.Assertions.*;

import com.example.runner.model.ExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CodeRunnerServiceTest {

  private CodeRunnerService service;

  @BeforeEach
  void setUp() {
    service = new CodeRunnerService();
  }

  @Test
  void testPythonExecution() {
    ExecutionResult result = service.executeCode("python", "print('Python execution successful')");
    assertTrue(result.isSuccess());
    assertEquals("Python execution successful", result.getStdout().trim());
  }

  @Test
  void testJsExecution() {
    ExecutionResult result = service.executeCode("js", "console.log('JS execution successful');");
    assertTrue(result.isSuccess());
    assertEquals("JS execution successful", result.getStdout().trim());
  }

  @Test
  void testJavaExecution() {
    String javaCode =
        """
        public class Solution {
            public static void main(String[] args) {
                System.out.println("Java execution successful");
            }
        }
        """;
    ExecutionResult result = service.executeCode("java", javaCode);
    assertTrue(result.isSuccess(), "Java execution failed: " + result.getStderr());
    assertEquals("Java execution successful", result.getStdout().trim());
  }

  @Test
  void testJavaTimeout() {
    String infiniteLoop =
        """
        public class Infinite {
            public static void main(String[] args) {
                while (true) {}
            }
        }
        """;
    ExecutionResult result = service.executeCode("java", infiniteLoop);
    assertFalse(result.isSuccess());
    assertTrue(result.getStderr().contains("timed out"));
  }

  @Test
  void testUnsupportedLanguage() {
    ExecutionResult result = service.executeCode("brainfuck", "+++");
    assertFalse(result.isSuccess());
    assertTrue(result.getStderr().contains("Unsupported language"));
  }
}
