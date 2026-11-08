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
  private static final int SB = 250;
  private static final int SE = 240;

  private final ByteArrayOutputStream sent = new ByteArrayOutputStream();

  private QconnSocket over(byte[] input) {
    return new QconnSocket(new ByteArrayInputStream(input), sent);
  }

  private String sent() {
    return sent.toString(StandardCharsets.US_ASCII);
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

  @Test
  @DisplayName("switches to a named service when the agent answers ok")
  void switchesToAService() throws IOException {
    try (var agent = over(bytes("ok\n"))) {
      agent.setService("file");

      assertThat(agent.service()).isEqualTo("file");
      assertThat(sent()).isEqualTo("service file\r\n");
    }
  }

  @Test
  @DisplayName("raises an error when a command is not answered with ok")
  void raisesWhenACommandIsRefused() throws IOException {
    try (var agent = over(bytes("no\n"))) {
      assertThatThrownBy(() -> agent.setService("file"))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("failed");
    }
  }

  @Test
  @DisplayName("skips a Telnet sub-negotiation up to IAC SE while reading a line")
  void skipsASubNegotiation() throws IOException {
    var input = new byte[] {'A', (byte) IAC, (byte) SB, 1, 2, (byte) IAC, (byte) SE, 'B', '\n'};

    try (var agent = over(input)) {
      assertThat(agent.receiveResponse()).isEqualTo("AB");
    }
  }
}
