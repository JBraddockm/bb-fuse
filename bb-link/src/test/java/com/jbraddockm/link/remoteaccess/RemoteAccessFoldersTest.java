package com.jbraddockm.link.remoteaccess;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link RemoteAccessFolders}: choosing and creating the folder remote access serves. */
@DisplayName("RemoteAccessFolders: the folder remote access serves")
class RemoteAccessFoldersTest {

  @Nested
  @DisplayName("resolving the folder")
  class Resolving {

    @Test
    @DisplayName("uses the global default when the device names none, creating it")
    void usesTheDefault(@TempDir Path base) throws IOException {
      var defaultRoot = base.resolve("BB-Fuse");
      var folders = new RemoteAccessFolders(defaultRoot);

      var config = folders.resolve(null, false);

      assertThat(config.root()).isEqualTo(defaultRoot);
      assertThat(config.readOnly()).isFalse();
      assertThat(defaultRoot).isDirectory();
    }

    @Test
    @DisplayName("uses the device's own folder when set, creating it and leaving the default alone")
    void usesThePerDeviceFolder(@TempDir Path base) throws IOException {
      var defaultRoot = base.resolve("BB-Fuse");
      var custom = base.resolve("custom/shared");
      var folders = new RemoteAccessFolders(defaultRoot);

      var config = folders.resolve(custom, true);

      assertThat(config.root()).isEqualTo(custom);
      assertThat(config.readOnly()).isTrue();
      assertThat(custom).isDirectory();
      assertThat(defaultRoot).doesNotExist();
    }

    @Test
    @DisplayName("is happy when the chosen folder already exists")
    void existingFolderIsFine(@TempDir Path base) throws IOException {
      var existing = Files.createDirectories(base.resolve("already-here"));
      var folders = new RemoteAccessFolders(base.resolve("BB-Fuse"));

      assertThat(folders.resolve(existing, false).root()).isEqualTo(existing);
    }
  }

  @Test
  @DisplayName("the production default is ~/Documents/BB-Fuse")
  void productionDefault() {
    assertThat(RemoteAccessFolders.withHomeDefault().defaultRoot())
        .isEqualTo(Path.of(System.getProperty("user.home"), "Documents", "BB-Fuse"));
  }
}
