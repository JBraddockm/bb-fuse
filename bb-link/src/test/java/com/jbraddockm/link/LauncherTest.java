package com.jbraddockm.link;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.link.Launcher.Invocation;
import com.jbraddockm.link.testing.Resources;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link Launcher#interpret(String[])}: the command lines that start the daemon or a
 * replay, and those that print usage.
 */
@DisplayName("Launcher: what the command line asks for")
class LauncherTest {

  private static String usage(String resource) {
    return Resources.text(LauncherTest.class, resource).strip();
  }

  @Nested
  @DisplayName("the daemon")
  class Daemon {

    @Test
    @DisplayName("with no more is the daemon with no trace")
    void withNoTrace() {
      assertThat(Launcher.interpret(new String[] {"-daemon"}))
          .isEqualTo(new Invocation.Daemon(Optional.empty()));
    }

    @Test
    @DisplayName("with -trace and a folder is the daemon that records the exchanges in that folder")
    void withATrace() {
      assertThat(Launcher.interpret(new String[] {"-daemon", "-trace", "traces/today"}))
          .isEqualTo(new Invocation.Daemon(Optional.of(Path.of("traces/today"))));
    }

    @Test
    @DisplayName(
        "with -trace and no folder, with something else, or with more, is not understood, and says"
            + " how the daemon is used")
    void notUnderstood() {
      var expected = new Invocation.Usage(usage("launcher--daemon-usage.txt"));

      assertThat(Launcher.interpret(new String[] {"-daemon", "-trace"})).isEqualTo(expected);
      assertThat(Launcher.interpret(new String[] {"-daemon", "-other"})).isEqualTo(expected);
      assertThat(Launcher.interpret(new String[] {"-daemon", "x", "y"})).isEqualTo(expected);
      assertThat(Launcher.interpret(new String[] {"-daemon", "-trace", "folder", "extra"}))
          .isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("the replay")
  class Replay {

    @Test
    @DisplayName("-parse and a command is the replay of that command")
    void aCommand() {
      assertThat(Launcher.interpret(new String[] {"-parse", "getPin"}))
          .isEqualTo(new Invocation.Replay("getPin"));
    }

    @Test
    @DisplayName("-parse with no command, or with more than one, says how the tool is used")
    void notUnderstood() {
      var expected = new Invocation.Usage(usage("launcher--usage.txt"));

      assertThat(Launcher.interpret(new String[] {"-parse"})).isEqualTo(expected);
      assertThat(Launcher.interpret(new String[] {"-parse", "getPin", "extra"}))
          .isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("the file share")
  class Share {

    @Test
    @DisplayName("a directory and a device host is the share, with the tunnel options folded in")
    void aShare() {
      var invocation =
          Launcher.interpret(
              new String[] {
                "-share",
                "/srv/shared",
                "--device-host",
                "192.168.1.118",
                "--broker-socket",
                "/tmp/bb-tunneld.sock",
                "--tunnel-cert",
                "devices/2C1E7583/tunnel_cert.pem",
                "--tunnel-key",
                "devices/2C1E7583/tunnel_key.pem",
                "--peer-id",
                "2C1E7583",
                "--read-only"
              });

      assertThat(invocation)
          .isEqualTo(
              new Invocation.Share(
                  Path.of("/srv/shared"),
                  "192.168.1.118",
                  Optional.of(Path.of("/tmp/bb-tunneld.sock")),
                  Optional.of(Path.of("devices/2C1E7583/tunnel_cert.pem")),
                  Optional.of(Path.of("devices/2C1E7583/tunnel_key.pem")),
                  Optional.of("2C1E7583"),
                  true));
    }

    @Test
    @DisplayName("just a directory and a device host defaults the rest")
    void minimal() {
      assertThat(
              Launcher.interpret(
                  new String[] {"-share", "/srv/shared", "--device-host", "192.168.1.118"}))
          .isEqualTo(
              new Invocation.Share(
                  Path.of("/srv/shared"),
                  "192.168.1.118",
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  false));
    }

    @Test
    @DisplayName(
        "no directory, no device host, a dangling value, an unknown option, or a second directory"
            + " says how the share is used")
    void notUnderstood() {
      var expected = new Invocation.Usage(usage("launcher--share-usage.txt"));

      assertThat(Launcher.interpret(new String[] {"-share", "--device-host", "h"}))
          .isEqualTo(expected);
      assertThat(Launcher.interpret(new String[] {"-share", "/srv/shared"})).isEqualTo(expected);
      assertThat(Launcher.interpret(new String[] {"-share", "/srv/shared", "--device-host"}))
          .isEqualTo(expected);
      assertThat(
              Launcher.interpret(
                  new String[] {"-share", "/srv/shared", "--device-host", "h", "--bogus"}))
          .isEqualTo(expected);
      assertThat(Launcher.interpret(new String[] {"-share", "/a", "/b", "--device-host", "h"}))
          .isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("anything else")
  class Anything {

    @Test
    @DisplayName("no arguments says how the tool is used")
    void noArguments() {
      assertThat(Launcher.interpret(new String[] {}))
          .isEqualTo(new Invocation.Usage(usage("launcher--usage.txt")));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(
        strings = {"daemon", "--daemon", "-DAEMON", "-Daemon", "parse", "-Parse", "-help", ""})
    @DisplayName("a first argument that is not exactly -daemon or -parse says how the tool is used")
    void aFirstArgumentThatIsNeither(String first) {
      assertThat(Launcher.interpret(new String[] {first}))
          .isEqualTo(new Invocation.Usage(usage("launcher--usage.txt")));
      assertThat(Launcher.interpret(new String[] {first, "x"}))
          .isEqualTo(new Invocation.Usage(usage("launcher--usage.txt")));
    }
  }
}
