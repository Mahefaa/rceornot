package com.example.runner.endpoint.web;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExecuteRequest {
  @JsonAlias({"languageId", "lang"})
  private String language;

  private String code;

  public String getEffectiveLanguage() {
    return (language != null && !language.isBlank()) ? language : "python";
  }
}
