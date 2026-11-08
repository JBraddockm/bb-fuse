package com.jbraddockm.dev;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests the {@code deploy} command line's exit codes, up to the point it needs a device. */
@DisplayName("The bb-dev deploy command line")
class DeployCliTest {

  private final ByteArrayOutputStream out = new ByteArrayOutputStream();
  private final ByteArrayOutputStream err = new ByteArrayOutputStream();

  private int dispatch(String... arguments) {
    return Launcher.dispatch(
        arguments,
        new PrintStream(out, true, StandardCharsets.UTF_8),
        new PrintStream(err, true, StandardCharsets.UTF_8));
  }

  private String output() {
    return out.toString(StandardCharsets.UTF_8) + err.toString(StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("prints usage and exits 2 when no command is given")
  void noCommand() {
    assertThat(dispatch("deploy")).isEqualTo(2);
    assertThat(output()).contains("Usage: bb-dev deploy");
  }

  @Test
  @DisplayName("prints the deploy help and exits 0")
  void help() {
    assertThat(dispatch("deploy", "help")).isZero();
    assertThat(output()).contains("install and manage applications");
  }

  @Test
  @DisplayName("reports a usage error and exits 2 when the device is missing")
  void missingDevice() {
    assertThat(dispatch("deploy", "install", "app.bar")).isEqualTo(2);
    assertThat(output()).contains("a device is required");
  }
}
