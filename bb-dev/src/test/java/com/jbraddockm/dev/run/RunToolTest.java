package com.jbraddockm.dev.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.dev.Output;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RunTool: the run namespace's command line")
class RunToolTest {

  private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

  private int run(List<String> arguments, boolean json) {
    return new RunTool()
        .run(arguments, new Output(new PrintStream(bytes, true, StandardCharsets.UTF_8), json));
  }

  private String out() {
    return bytes.toString(StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("without a host and command it reports usage and exits 2")
  void usageWhenIncomplete() {
    assertThat(run(List.of("192.168.1.82"), false)).isEqualTo(2);
    assertThat(out()).contains("Usage: bb-dev run");
  }

  @Test
  @DisplayName("with no arguments the JSON form reports the error object")
  void usageJson() {
    assertThat(run(List.of(), true)).isEqualTo(2);
    assertThat(out()).contains("\"ok\":false").contains("required");
  }

  @Test
  @DisplayName("a refused connection surfaces the run error, not a usage error")
  void connectionError() {
    // Nothing listens on port 1 of localhost, so connect is refused immediately.
    var status = run(List.of("127.0.0.1", "/bin/true", "--port", "1"), false);

    assertThat(status).isEqualTo(1);
    assertThat(out()).contains("bb-dev run:");
  }
}
