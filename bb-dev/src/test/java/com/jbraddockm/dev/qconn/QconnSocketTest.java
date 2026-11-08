package com.jbraddockm.dev.qconn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("QconnSocket: the qconn control protocol")
class QconnSocketTest {

  private static final int IAC = 255;
  private static final int WILL = 251;

  private static QconnSocket over(byte[] input) {
    return new QconnSocket(new ByteArrayInputStream(input), new ByteArrayOutputStream());
  }

  private static byte[] bytes(String text) {
    return text.getBytes(StandardCharsets.US_ASCII);
  }

  @Test
  @DisplayName("reads a response line up to the newline")
  void readsALine() throws IOException {
    try (var agent = over(bytes("QCONN\r\n"))) {
      assertThat(agent.receiveResponse()).isEqualTo("QCONN");
    }
  }

  @Test
  @DisplayName("skips Telnet option sequences while reading a line")
  void skipsTelnetOptions() throws IOException {
    var input = new byte[] {'A', 'B', (byte) IAC, (byte) WILL, 1, 'C', '\n'};

    try (var agent = over(input)) {
      assertThat(agent.receiveResponse()).isEqualTo("ABC");
    }
  }

  @Test
  @DisplayName("parses the endianness from the info properties")
  void readsEndianness() throws IOException {
    try (var agent = over(bytes("ENDIAN=l NUM_SRVCS=7\n"))) {
      agent.readInfo();

      assertThat(agent.isLittleEndian()).isTrue();
    }
  }

  @Test
  @DisplayName("lists the services from the broker's reply, after the colon")
  void listsServices() throws IOException {
    try (var agent = over(bytes("OK: file launch sinfo\n"))) {
      assertThat(agent.services()).containsExactly("file", "launch", "sinfo");
    }
  }

  @Test
  @DisplayName("raises the broker's error for a refused service list")
  void refusedServiceList() throws IOException {
    try (var agent = over(bytes("error unknown\n"))) {
      assertThatThrownBy(agent::services)
          .isInstanceOf(IOException.class)
          .hasMessageContaining("refused");
    }
  }
}
