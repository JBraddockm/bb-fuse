package com.jbraddockm.dev;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
  @DisplayName("generates a key at the default location when none is given, then tries to connect")
  void generatesDefaultKeyWhenNoneGiven(@TempDir Path rim) {
    var previous = System.getProperty("bb.dev.rim.dir");
    System.setProperty("bb.dev.rim.dir", rim.toString());
    try {
      // No -sshPublicKey: the key is generated at the default path, then the bogus host fails.
      assertThat(dispatch("connect", "device.invalid")).isEqualTo(1);
      assertThat(rim.resolve("bbt_id_rsa.pub")).exists();
      assertThat(rim.resolve("bbt_id_rsa")).exists();
    } finally {
      if (previous == null) {
        System.clearProperty("bb.dev.rim.dir");
      } else {
        System.setProperty("bb.dev.rim.dir", previous);
      }
    }
  }

  @Test
  @DisplayName("fails cleanly when the SSH public key file cannot be read")
  void reportsAnUnreadableKey() {
    assertThat(dispatch("connect", "device.invalid", "-sshPublicKey", "/no/such/key.pub"))
        .isEqualTo(1);
    assertThat(output()).contains("cannot read the SSH key");
  }
}
