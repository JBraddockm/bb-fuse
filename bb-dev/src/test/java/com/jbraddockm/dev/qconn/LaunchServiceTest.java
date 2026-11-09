package com.jbraddockm.dev.qconn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LaunchService: the qconn launcher-service protocol")
class LaunchServiceTest {

  private final ByteArrayOutputStream sent = new ByteArrayOutputStream();

  private LaunchService over(String replies) {
    return new LaunchService(
        new QconnSocket(
            new ByteArrayInputStream(replies.getBytes(StandardCharsets.US_ASCII)), sent));
  }

  private String sent() {
    return sent.toString(StandardCharsets.US_ASCII);
  }

  @Nested
  @DisplayName("configuration before launch")
  class Configuration {

    @Test
    @DisplayName("setenv quotes a value and sends the bare form when empty")
    void setenv() throws IOException {
      var service = over("ok\nok\n");

      service.setEnvironment("LD_LIBRARY_PATH", "/usr/lib");
      service.setEnvironment("QT_NO_GLIB", "");

      assertThat(sent())
          .contains("setenv LD_LIBRARY_PATH=\"/usr/lib\"\r\n")
          .contains("setenv QT_NO_GLIB=\r\n");
    }

    @Test
    @DisplayName("chdir sets the working directory")
    void chdir() throws IOException {
      over("ok\n").setWorkingDirectory("/accounts/1000/appdata/com.example");

      assertThat(sent()).isEqualTo("chdir /accounts/1000/appdata/com.example\r\n");
    }

    @Test
    @DisplayName("disablePseudoTerminal selects the inline-console mode")
    void nopty() throws IOException {
      over("ok\n").disablePseudoTerminal();

      assertThat(sent()).isEqualTo("set nopty\r\n");
    }

    @Test
    @DisplayName("a blank environment name or working directory is rejected before any I/O")
    void rejectsBlank() {
      assertThatThrownBy(() -> over("").setEnvironment(" ", "x"))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> over("").setWorkingDirectory(""))
          .isInstanceOf(IllegalArgumentException.class);
      assertThat(sent()).isEmpty();
    }
  }

  @Nested
  @DisplayName("starting a process")
  class Starting {

    @Test
    @DisplayName("start sends the command with quoted arguments and returns the pid")
    void start() throws IOException {
      var process = over("ok 4231\n").start("/tmp/hello", List.of("-v", "a b"));

      assertThat(process.pid()).isEqualTo(4231);
      assertThat(process.isHeld()).isFalse();
      assertThat(sent()).isEqualTo("start /tmp/hello \"/tmp/hello\" \"-v\" \"a b\"\r\n");
    }

    @Test
    @DisplayName("start with no arguments sends only the command")
    void startNoArgs() throws IOException {
      var process = over("ok 9\n").start("/bin/pidin", List.of());

      assertThat(process.pid()).isEqualTo(9);
      assertThat(sent()).isEqualTo("start /bin/pidin \"/bin/pidin\"\r\n");
    }

    @Test
    @DisplayName("SPAWN_HOLD uses start/flags with the hex flag and marks the process held")
    void startHeld() throws IOException {
      var process = over("ok 77\n").start("/tmp/app", List.of(), LaunchService.SPAWN_HOLD);

      assertThat(process.isHeld()).isTrue();
      assertThat(sent()).isEqualTo("start/flags 8000 /tmp/app \"/tmp/app\"\r\n");
    }

    @Test
    @DisplayName("an embedded quote or newline in an argument is escaped")
    void escapesArguments() throws IOException {
      over("ok 1\n").start("/tmp/echo", List.of("he said \"hi\"\nbye"));

      assertThat(sent())
          .isEqualTo("start /tmp/echo \"/tmp/echo\" \"he said \\\"hi\\\"\\nbye\"\r\n");
    }

    @Test
    @DisplayName("an error reply becomes an IOException carrying the device message")
    void errorReply() {
      assertThatThrownBy(() -> over("error no such file\n").start("/nope", List.of()))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("no such file");
    }

    @Test
    @DisplayName("the no-pty device error is reported with the remedy")
    void noPtyError() {
      assertThatThrownBy(
              () -> over("error: Inappropriate I/O control operation\n").start("/x", List.of()))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("pty");
    }
  }

  @Nested
  @DisplayName("controlling a running process")
  class Controlling {

    @Test
    @DisplayName("console and stdin are the launcher connection's own streams")
    void consoleStreams() throws IOException {
      var process = over("ok 5\nhello from the device").start("/tmp/hi", List.of());

      assertThat(new String(process.console().readAllBytes(), StandardCharsets.US_ASCII))
          .isEqualTo("hello from the device");
    }

    @Test
    @DisplayName("resume sends continue for a held process")
    void resume() throws IOException {
      var process = over("ok 5\nok\n").start("/tmp/app", List.of(), LaunchService.SPAWN_HOLD);

      process.resume();

      assertThat(sent()).contains("continue\r\n");
    }

    @Test
    @DisplayName("exitCode decodes the POSIX wait status: the high byte of a normal exit")
    void exitCode() throws IOException {
      // 1792 == 7 << 8 — a normal exit with code 7.
      var process = over("ok 5\ne:1792\n").start("/tmp/app", List.of());

      assertThat(process.exitCode()).isEqualTo(7);
      assertThat(sent()).contains("getexitcode 5\r\n");
    }

    @Test
    @DisplayName("a process killed by a signal decodes to 128 + the signal number")
    void exitCodeSignal() throws IOException {
      // Low seven bits carry the terminating signal; 9 == SIGKILL.
      var process = over("ok 5\ne:9\n").start("/tmp/app", List.of());

      assertThat(process.exitCode()).isEqualTo(137);
    }

    @Test
    @DisplayName("exitCode raises the device error reply")
    void exitCodeError() throws IOException {
      var process = over("ok 5\nerror unknown pid\n").start("/tmp/app", List.of());

      assertThatThrownBy(process::exitCode)
          .isInstanceOf(IOException.class)
          .hasMessageContaining("unknown pid");
    }
  }

  @Test
  @DisplayName("on selects the launcher service")
  void selectsService() throws IOException {
    try (var agent = new QconnSocket(new ByteArrayInputStream("ok\n".getBytes()), sent)) {
      LaunchService.on(agent);

      assertThat(agent.service()).isEqualTo("launcher");
      assertThat(sent()).isEqualTo("service launcher\r\n");
    }
  }
}
