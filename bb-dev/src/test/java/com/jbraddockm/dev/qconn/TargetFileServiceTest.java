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
  @DisplayName(
      "write sends the fd, offset and length then the bytes, and returns the count written")
  void write() throws IOException {
    var written =
        over("o:c:c\n").write(5, 0, "hello qconn\n".getBytes(StandardCharsets.US_ASCII), 0, 12);

    assertThat(written).isEqualTo(12);
    assertThat(sent()).isEqualTo("w:5:0:c:1\r\nhello qconn\n");
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
    over("o:3:81fd:0:\"/x\"\no:c:c\no\n")
        .writeFile("/x", "hello qconn\n".getBytes(StandardCharsets.US_ASCII));

    assertThat(sent())
        .contains("o:\"/x\":302:1ff\r\n")
        .contains("w:3:0:c:1\r\nhello qconn\n")
        .contains("c:3\r\n");
  }

  @Test
  @DisplayName("read sends the fd, hex offset and length, then copies the returned bytes")
  void read() throws IOException {
    var buffer = new byte[16];
    var count = over("o:5:5:a\nhello").read(3, 0, buffer, 0, 16);

    assertThat(count).isEqualTo(5);
    assertThat(new String(buffer, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("hello");
    assertThat(sent()).isEqualTo("r:3:0:10:1\r\n");
  }

  @Test
  @DisplayName("readFile opens read-only, streams blocks until EOF, and closes")
  void readFile() throws IOException {
    var content = over("o:7:81b4:5:\"/m\"\no:5:5:5\nhelloo:0:0:5\no\n").readFile("/m");

    assertThat(new String(content, StandardCharsets.US_ASCII)).isEqualTo("hello");
    assertThat(sent())
        .contains("o:\"/m\":0:1ff\r\n")
        .contains("r:7:0:10000:1\r\n")
        .contains("r:7:5:10000:1\r\n")
        .contains("c:7\r\n");
  }

  @Test
  @DisplayName("stat opens a transient handle, parses the metadata reply, and closes")
  void stat() throws IOException {
    var metadata =
        over("""
        o:9:81b4:14:"/m"
        o:1:14:2:0:0:0:5f000000:5f000000:5f000000:81b4:1:1000:1:1000:1
        o
        """)
            .stat("/m");

    assertThat(metadata.size()).isEqualTo(0x14);
    assertThat(metadata.isRegularFile()).isTrue();
    assertThat(metadata.isDirectory()).isFalse();
    assertThat(metadata.permissions()).isEqualTo(0x1b4);
    assertThat(metadata.modifiedMillis()).isEqualTo(0x5f000000L * 1000);
    assertThat(sent()).contains("oc:\"/m\":0\r\n").contains("s:9\r\n").contains("c:9\r\n");
  }

  @Test
  @DisplayName("a directory stat reply reports a directory")
  void statDirectory() throws IOException {
    var metadata =
        over("""
        o:9:41ff:0:"/d"
        o:2:0:2:0:0:0:5f000000:5f000000:5f000000:41ff:2:1000:0:1000:0
        o
        """)
            .stat("/d");

    assertThat(metadata.isDirectory()).isTrue();
    assertThat(metadata.isRegularFile()).isFalse();
    assertThat(metadata.permissions()).isEqualTo(0x1ff);
  }

  @Test
  @DisplayName("stat of a missing path raises the device error")
  void statMissing() {
    assertThatThrownBy(() -> over("e:No such file or directory\n").stat("/nope"))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("No such file or directory");
  }

  @Test
  @DisplayName("mkdir opens with the create flag and directory mode, then closes")
  void mkdir() throws IOException {
    over("o:9:41ff:0:\"/d\"\no\n").mkdir("/d");

    assertThat(sent()).isEqualTo("o:\"/d\":100:41ff\r\nc:9\r\n");
  }

  @Test
  @DisplayName("mkdirs creates each component in turn")
  void mkdirs() {
    over("o:5:41ff:0:\"/a\"\no\no:6:41ff:0:\"/a/b\"\no\n").mkdirs("/a/b");

    assertThat(sent()).contains("o:\"/a\":100:41ff\r\n").contains("o:\"/a/b\":100:41ff\r\n");
  }

  @Test
  @DisplayName("mkdirs steps over a component that already exists")
  void mkdirsExisting() {
    over("e:File exists\no:6:41ff:0:\"/a/b\"\no\n").mkdirs("/a/b");

    assertThat(sent()).contains("o:\"/a\":100:41ff\r\n").contains("o:\"/a/b\":100:41ff\r\n");
  }
}
