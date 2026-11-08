package com.jbraddockm.dev;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the {@code connect} command line's argument handling, up to the point it needs a device.
 */
@DisplayName("The bb-dev connect command line")
class ConnectCliTest {

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
  @DisplayName("fails with a usage error when no host is given")
  void needsAHost() {
    assertThat(dispatch("connect", "-sshPublicKey", "key.pub")).isEqualTo(2);
    assertThat(output()).contains("host");
  }

  @Test
  @DisplayName("fails with a usage error when no SSH public key is given")
  void needsAKey() {
    assertThat(dispatch("connect", "device.invalid")).isEqualTo(2);
    assertThat(output()).contains("sshPublicKey");
  }

  @Test
  @DisplayName("fails cleanly when the SSH public key file cannot be read")
  void reportsAnUnreadableKey() {
    assertThat(dispatch("connect", "device.invalid", "-sshPublicKey", "/no/such/key.pub"))
        .isEqualTo(1);
    assertThat(output()).contains("cannot read the SSH key");
  }
}
