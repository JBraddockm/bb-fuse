package com.jbraddockm.dev;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.json.Json;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Output: a tool's one result, as text or JSON")
class OutputTest {

  private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

  private Output output(boolean json) {
    return new Output(new PrintStream(bytes, true, StandardCharsets.UTF_8), json);
  }

  private String printed() {
    return bytes.toString(StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("writes the plain text and returns the exit code in text mode")
  void emitsText() {
    var status = output(false).emit(3, "done", Json.object("ok", true));

    assertThat(status).isEqualTo(3);
    assertThat(printed()).contains("done").doesNotContain("ok");
  }

  @Test
  @DisplayName("writes the JSON object in JSON mode")
  void emitsJson() {
    output(true).emit(0, "done", Json.object("ok", true));

    assertThat(printed()).contains("\"ok\"").doesNotContain("done");
  }

  @Test
  @DisplayName("prints a progress note only in text mode")
  void noteOnlyInTextMode() {
    output(true).note("working");
    assertThat(printed()).isEmpty();

    output(false).note("working");
    assertThat(printed()).contains("working");
  }
}
