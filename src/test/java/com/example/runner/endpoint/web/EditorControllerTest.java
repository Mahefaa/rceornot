package com.example.runner.endpoint.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.runner.conf.FacadeIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class EditorControllerTest extends FacadeIT {

  @Autowired private MockMvc mockMvc;

  @Test
  void shouldReturnEditorHtmlOnGetRoot() throws Exception {
    mockMvc
        .perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(content().string(containsString("Multi-Language Code Runner")))
        .andExpect(content().string(containsString("id=\"language\"")))
        .andExpect(content().string(containsString("id=\"code\"")))
        .andExpect(content().string(containsString("id=\"output\"")));
  }

  @Test
  void shouldExecutePythonViaJsonEndpoint() throws Exception {
    String payload =
        """
        {
          "language": "python",
          "code": "print('Python execution successful')"
        }
        """;

    mockMvc
        .perform(post("/execute").contentType(MediaType.APPLICATION_JSON).content(payload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stdout").value(containsString("Python execution successful")))
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void shouldExecuteJsViaJsonEndpoint() throws Exception {
    String payload =
        """
        {
          "language": "javascript",
          "code": "console.log('JS execution successful');"
        }
        """;

    mockMvc
        .perform(post("/execute").contentType(MediaType.APPLICATION_JSON).content(payload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stdout").value(containsString("JS execution successful")))
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void shouldExecuteJavaViaJsonEndpoint() throws Exception {
    String payload =
        """
{
  "language": "java",
  "code": "public class Solution { public static void main(String[] args) { System.out.println(\\\"Java execution successful\\\"); } }"
}
""";

    mockMvc
        .perform(post("/execute").contentType(MediaType.APPLICATION_JSON).content(payload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stdout").value(containsString("Java execution successful")))
        .andExpect(jsonPath("$.success").value(true));
  }
}
