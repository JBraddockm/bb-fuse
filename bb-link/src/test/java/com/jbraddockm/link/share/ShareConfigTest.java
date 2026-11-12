package com.jbraddockm.link.share;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("The host file-share configuration")
class ShareConfigTest {

  @TempDir Path dir;

  @Test
  @DisplayName("defaults to read-write and requires a root")
  void defaults() {
    assertThat(ShareConfig.of(dir).readOnly()).isFalse();
    assertThatThrownBy(() -> new ShareConfig(null, false))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("round-trips through a properties file")
  void roundTrip() throws IOException {
    var config = new ShareConfig(dir.resolve("shared"), true);
    var file = dir.resolve("share.properties");

    config.save(file);

    assertThat(ShareConfig.load(file)).isEqualTo(config);
  }

  @Test
  @DisplayName("rejects a configuration file that names no root")
  void noRoot() throws IOException {
    var file = dir.resolve("empty.properties");
    java.nio.file.Files.writeString(file, "readOnly=true\n");

    assertThatThrownBy(() -> ShareConfig.load(file)).isInstanceOf(IllegalArgumentException.class);
  }
}
