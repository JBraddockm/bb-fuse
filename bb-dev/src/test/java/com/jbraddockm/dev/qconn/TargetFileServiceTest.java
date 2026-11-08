package com.jbraddockm.dev.qconn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TargetFileService: the qconn file-service write path")
class TargetFileServiceTest {

  private final ByteArrayOutputStream sent = new ByteArrayOutputStream();

  private TargetFileService over(String replies) {
    return new TargetFileService(
        new QconnSocket(
            new ByteArrayInputStream(replies.getBytes(StandardCharsets.US_ASCII)), sent));
  }

  private String sent() {
    return sent.toString(StandardCharsets.US_ASCII);
  }

  @Test
  @DisplayName("open sends the quoted path with hex flags and mode, and returns the descriptor")
  void open() throws IOException {
    var fd = over("o:5:81fd:0:\"/x\"\n").open("/x", 0x302, 0x1ff);

    assertThat(fd).isEqualTo(5);
    assertThat(sent()).isEqualTo("o:\"/x\":302:1ff\r\n");
  }

  @Test
  @DisplayName("write sends the fd and length then the bytes, and returns the count written")
  void write() throws IOException {
    var written =
        over("o:5:c\n").write(5, "hello qconn\n".getBytes(StandardCharsets.US_ASCII), 0, 12);

    assertThat(written).isEqualTo(12);
    assertThat(sent()).isEqualTo("w:5:c\r\nhello qconn\n");
  }

  @Test
  @DisplayName("an e: reply becomes an IOException carrying the device message")
  void errorReply() {
    assertThatThrownBy(() -> over("e:No such file or directory\n").open("/x", 1, 0))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("No such file or directory");
  }

  @Test
  @DisplayName("writeFile opens create+truncate, writes the content, and closes")
  void writeFile() throws IOException {
    over("o:3:81fd:0:\"/x\"\no:3:c\no\n")
        .writeFile("/x", "hello qconn\n".getBytes(StandardCharsets.US_ASCII));

    assertThat(sent())
        .contains("o:\"/x\":302:1ff\r\n")
        .contains("w:3:c\r\nhello qconn\n")
        .contains("c:3\r\n");
  }
}
