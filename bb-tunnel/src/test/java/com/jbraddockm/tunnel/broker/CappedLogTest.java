package com.jbraddockm.tunnel.broker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link CappedLog}: a self-clearing daemon log that keeps only a small recent window.
 */
@DisplayName("CappedLog: a self-clearing daemon log")
class CappedLogTest {

  @Test
  @DisplayName("keeps at most the last MAX_LINES lines, clearing once it reaches the cap")
  void staysBounded(@TempDir Path dir) throws IOException {
    var file = dir.resolve("bb-tunneld.log");
    var log = CappedLog.to(file);

    var writes = CappedLog.MAX_LINES * 3; // well past the cap, so it clears itself several times
    for (var i = 0; i < writes; i++) {
      log.accept("up 192.168.1.118 line " + i);
    }

    assertThat(Files.readAllLines(file)).hasSizeLessThanOrEqualTo(CappedLog.MAX_LINES);
    assertThat(Files.readString(file)).contains("line " + (writes - 1)).contains("bb-tunneld: ");
  }

  @Test
  @DisplayName("writes a prefixed line to the file")
  void writesLine(@TempDir Path dir) throws IOException {
    var file = dir.resolve("bb-tunneld.log");

    CappedLog.to(file).accept("listening on /tmp/bb-tunneld.sock");

    assertThat(Files.readString(file)).isEqualTo("bb-tunneld: listening on /tmp/bb-tunneld.sock\n");
  }
}
