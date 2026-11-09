package com.jbraddockm.dev.debug;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.dev.Output;
import com.jbraddockm.dev.SandboxFetcher;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DebugTool: the debug namespace's command line")
class DebugToolTest {

  private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

  // A fetcher that is never reached in these tests (connect fails first, or it is a usage error).
  private final SandboxFetcher.Factory fetchers = (_, _, _) -> (_, _) -> new byte[0];

  private int run(List<String> arguments, boolean json) {
    return new DebugTool(fetchers)
        .run(arguments, new Output(new PrintStream(bytes, true, StandardCharsets.UTF_8), json));
  }

  private String out() {
    return bytes.toString(StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("no command prints usage and exits 2")
  void noCommand() {
    assertThat(run(List.of(), false)).isEqualTo(2);
    assertThat(out()).contains("Usage: bb-dev debug");
  }

  @Test
  @DisplayName("an unknown command is rejected")
  void unknownCommand() {
    assertThat(run(List.of("frobnicate"), true)).isEqualTo(2);
    assertThat(out()).contains("\"ok\":false").contains("unknown command");
  }

  @Nested
  @DisplayName("debug run")
  class Run {

    @Test
    @DisplayName("without a host and command it reports usage")
    void usage() {
      assertThat(run(List.of("run", "192.168.1.118"), false)).isEqualTo(2);
      assertThat(out()).contains("Usage: bb-dev debug run");
    }

    @Test
    @DisplayName("a refused connection surfaces the run error")
    void connectionError() {
      assertThat(run(List.of("run", "127.0.0.1", "/bin/true", "--port", "1"), false)).isEqualTo(1);
      assertThat(out()).contains("bb-dev debug run:");
    }
  }

  @Nested
  @DisplayName("debug core")
  class Core {

    @Test
    @DisplayName("without a host and app it reports usage")
    void usage() {
      assertThat(run(List.of("core", "192.168.1.118"), false)).isEqualTo(2);
      assertThat(out()).contains("Usage: bb-dev debug core");
    }
  }

  @Nested
  @DisplayName("debug logs")
  class Logs {

    @Test
    @DisplayName("without a host it reports usage")
    void usage() {
      assertThat(run(List.of("logs"), false)).isEqualTo(2);
      assertThat(out()).contains("Usage: bb-dev debug logs");
    }

    @Test
    @DisplayName("a refused connection surfaces the logs error")
    void connectionError() {
      assertThat(run(List.of("logs", "127.0.0.1", "--port", "1"), false)).isEqualTo(1);
      assertThat(out()).contains("bb-dev debug logs:");
    }
  }
}
