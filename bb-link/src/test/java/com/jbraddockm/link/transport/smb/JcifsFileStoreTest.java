package com.jbraddockm.link.transport.smb;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for the host-side path arithmetic of {@link JcifsFileStore}. */
@DisplayName("JcifsFileStore: host-side path arithmetic")
class JcifsFileStoreTest {

  @Test
  @DisplayName("the parent of a nested path drops the last segment")
  void parentOfNested() {
    assertThat(JcifsFileStore.parentOf(new SmbLocation("media", "documents/sub/file.txt")))
        .isEqualTo(new SmbLocation("media", "documents/sub"));
  }

  @Test
  @DisplayName("the parent of a top-level name is the share root")
  void parentOfTopLevel() {
    assertThat(JcifsFileStore.parentOf(new SmbLocation("media", "file.txt")))
        .isEqualTo(SmbLocation.root("media"));
  }

  @Test
  @DisplayName("the parent of the share root is the share root")
  void parentOfRoot() {
    assertThat(JcifsFileStore.parentOf(SmbLocation.root("media")))
        .isEqualTo(SmbLocation.root("media"));
  }
}
