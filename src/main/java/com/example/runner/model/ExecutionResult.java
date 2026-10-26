package com.example.runner.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExecutionResult {
  private String stdout;
  private String stderr;
  private String output;
  private boolean success;
}
