package com.example.runner.endpoint.web;

import com.example.runner.model.ExecutionResult;
import com.example.runner.service.CodeRunnerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Slf4j
@Controller
@RequiredArgsConstructor
public class EditorController {

  private final CodeRunnerService codeRunnerService;

  @GetMapping("/")
  public String index(Model model) {
    if (!model.containsAttribute("language")) {
      model.addAttribute("language", "python");
    }
    if (!model.containsAttribute("code")) {
      model.addAttribute("code", "print('Hello, World!')");
    }
    if (!model.containsAttribute("output")) {
      model.addAttribute("output", "");
    }
    return "editor";
  }

  @PostMapping(value = "/", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  public String runCodeForm(
      @RequestParam("language") String language, @RequestParam("code") String code, Model model) {
    ExecutionResult result = codeRunnerService.executeCode(language, code);
    model.addAttribute("language", language);
    model.addAttribute("code", code);
    model.addAttribute("result", result);
    model.addAttribute("output", result.getOutput());
    model.addAttribute("stdout", result.getStdout());
    model.addAttribute("stderr", result.getStderr());
    return "editor";
  }

  @PostMapping(
      value = "/execute",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public ExecutionResult executeJson(@RequestBody ExecuteRequest request) {
    String lang = request != null ? request.getEffectiveLanguage() : "python";
    String code = request != null ? request.getCode() : "";
    return codeRunnerService.executeCode(lang, code);
  }

  @PostMapping(
      value = "/execute",
      consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public ExecutionResult executeForm(
      @RequestParam("language") String language, @RequestParam("code") String code) {
    return codeRunnerService.executeCode(language, code);
  }
}
