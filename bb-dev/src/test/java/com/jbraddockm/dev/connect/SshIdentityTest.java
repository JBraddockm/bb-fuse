package com.jbraddockm.dev.connect;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("The connect SSH identity")
class SshIdentityTest {

  @Nested
  @DisplayName("when the public key already exists")
  class Existing {

    @Test
    @DisplayName("returns its bytes and does not generate a new pair")
    void returnsExistingUntouched(@TempDir Path dir) throws Exception {
      var pub = dir.resolve("bbt_id_rsa.pub");
      Files.writeString(pub, "ssh-rsa AAAAalreadyhere user@host\n");

      var resolved = SshIdentity.resolve(pub, true);

      assertThat(resolved.generated()).isFalse();
      assertThat(new String(resolved.publicKey(), US_ASCII)).contains("AAAAalreadyhere");
      assertThat(dir.resolve("bbt_id_rsa")).doesNotExist();
    }
  }

  @Nested
  @DisplayName("when the public key is missing and generation is allowed")
  class Generated {

    @Test
    @DisplayName("writes an ssh-rsa public key and a PEM private key beside it")
    void writesThePair(@TempDir Path dir) throws Exception {
      var pub = dir.resolve("bbt_id_rsa.pub");

      var resolved = SshIdentity.resolve(pub, true);

      assertThat(resolved.generated()).isTrue();
      assertThat(resolved.publicKeyPath()).isEqualTo(pub);
      assertThat(resolved.privateKeyPath()).isEqualTo(dir.resolve("bbt_id_rsa"));
      assertThat(new String(resolved.publicKey(), US_ASCII)).startsWith("ssh-rsa ");
      assertThat(Files.readString(pub)).startsWith("ssh-rsa ");
      assertThat(Files.readString(dir.resolve("bbt_id_rsa"))).contains("BEGIN PRIVATE KEY");
    }

    @Test
    @DisplayName("gives the private key owner-only permissions")
    void privateKeyIsOwnerOnly(@TempDir Path dir) throws Exception {
      var pub = dir.resolve("bbt_id_rsa.pub");

      SshIdentity.resolve(pub, true);

      assertThat(Files.getPosixFilePermissions(dir.resolve("bbt_id_rsa")))
          .containsExactlyInAnyOrder(
              PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
    }

    @Test
    @DisplayName("produces a public key whose blob is a well-formed ssh-rsa key")
    void publicKeyBlobIsWellFormed(@TempDir Path dir) throws Exception {
      var pub = dir.resolve("bbt_id_rsa.pub");

      var resolved = SshIdentity.resolve(pub, true);

      // The second field of the line is base64; its first length-prefixed string is the key type.
      var fields = new String(resolved.publicKey(), US_ASCII).trim().split(" ");
      var blob = ByteBuffer.wrap(Base64.getDecoder().decode(fields[1]));
      var type = new byte[blob.getInt()];
      blob.get(type);
      assertThat(new String(type, US_ASCII)).isEqualTo("ssh-rsa");
    }

    @Test
    @DisplayName("gives the key a user@host comment (the device's qconn rejects bare comments)")
    void commentIsUserAtHost(@TempDir Path dir) throws Exception {
      var pub = dir.resolve("bbt_id_rsa.pub");

      var resolved = SshIdentity.resolve(pub, true);

      var fields = new String(resolved.publicKey(), US_ASCII).trim().split(" ");
      assertThat(fields).hasSize(3);
      assertThat(fields[2]).contains("@");
    }

    @Test
    @DisplayName("creates the key directory when it does not exist")
    void createsMissingDirectory(@TempDir Path dir) throws Exception {
      var pub = dir.resolve("nested").resolve("bbt_id_rsa.pub");

      SshIdentity.resolve(pub, true);

      assertThat(pub).exists();
    }
  }

  @Nested
  @DisplayName("when the public key is missing and generation is not allowed")
  class MissingNoGeneration {

    @Test
    @DisplayName("fails rather than inventing a key at the explicit path")
    void failsWithoutGenerating(@TempDir Path dir) {
      var pub = dir.resolve("absent.pub");

      assertThatThrownBy(() -> SshIdentity.resolve(pub, false))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("no such SSH public key");
      assertThat(pub).doesNotExist();
    }
  }
}
