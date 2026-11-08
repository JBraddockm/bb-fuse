package com.jbraddockm.dev.connect;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EncryptionChallenge: the device's RSA challenge header")
class EncryptionChallengeTest {

  @Test
  @DisplayName("reads the container, signature length and type from the little-endian header")
  void parsesTheHeader() {
    var sourceName = new byte[] {'d', 'e', 'v'};
    var container = new byte[] {1, 2, 3, 4, 5};
    var payload =
        ByteBuffer.allocate(24 + sourceName.length + container.length)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort((short) 0) // entire length
            .putShort((short) 0) // version
            .putShort((short) sourceName.length)
            .putShort((short) 0) // session key length
            .putShort((short) 0) // session key type
            .putShort((short) container.length)
            .putShort((short) 0) // container primitive
            .putShort((short) 0) // container type
            .putShort((short) 128) // expected signature length
            .putShort((short) 1) // expected signature type
            .putInt(0) // container key version
            .put(sourceName)
            .put(container)
            .array();

    var challenge = EncryptionChallenge.parse(payload);

    assertThat(challenge.container()).isEqualTo(container);
    assertThat(challenge.expectedSignatureLength()).isEqualTo(128);
    assertThat(challenge.expectedSignatureType()).isEqualTo(1);
  }
}
