package com.jbraddockm.dev.connect;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("The SecureTarget frame codec")
class SecureTargetTest {

  @Test
  @DisplayName("encodes a big-endian header of total length, version and code")
  void encodesHeader() {
    byte[] payload = {7, 8, 9};
    byte[] encoded = new SecureTarget.Frame(SecureTarget.START_SERVICES, payload).encode();

    var buffer = ByteBuffer.wrap(encoded);
    assertThat(buffer.getShort() & 0xFFFF).isEqualTo(6 + payload.length);
    assertThat(buffer.getShort() & 0xFFFF).isEqualTo(SecureTarget.VERSION);
    assertThat(buffer.getShort() & 0xFFFF).isEqualTo(SecureTarget.START_SERVICES);
    byte[] rest = new byte[buffer.remaining()];
    buffer.get(rest);
    assertThat(rest).containsExactly(payload);
  }

  @Test
  @DisplayName("reads back a written frame's code and payload")
  void writeThenRead() throws Exception {
    var written = new ByteArrayOutputStream();
    byte[] payload = {1, 2, 3, 4, 5};
    SecureTarget.write(written, SecureTarget.CHALLENGE_REQUEST, payload);

    var frame = SecureTarget.read(new ByteArrayInputStream(written.toByteArray()));
    assertThat(frame.code()).isEqualTo(SecureTarget.CHALLENGE_REQUEST);
    assertThat(frame.payload()).containsExactly(payload);
  }

  @Test
  @DisplayName("fails cleanly when the stream ends before a full header")
  void truncatedStream() {
    var truncated = new ByteArrayInputStream(new byte[] {0, 6, 0});
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> SecureTarget.read(truncated))
        .isInstanceOf(java.io.IOException.class);
  }
}
