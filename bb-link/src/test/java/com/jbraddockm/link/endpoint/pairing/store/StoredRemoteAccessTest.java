package com.jbraddockm.link.endpoint.pairing.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link StoredRemoteAccess}: the {@code remoteAccess} section of {@code device.json}.
 */
@DisplayName("StoredRemoteAccess: the remoteAccess section of device.json")
class StoredRemoteAccessTest {

  private static final Path FOLDER = Path.of("/Users/me/Documents/BB-Fuse");

  @Nested
  @DisplayName("the default (disabled, no folder)")
  class Default {

    @Test
    @DisplayName("is recognised, so it need never be written")
    void isRecognised() {
      assertThat(new StoredRemoteAccess(false, null).isDefault()).isTrue();
      assertThat(new StoredRemoteAccess(true, null).isDefault()).isFalse();
      assertThat(new StoredRemoteAccess(false, FOLDER).isDefault()).isFalse();
    }

    @Test
    @DisplayName("reads back as empty, even written out, so an absent section is the default")
    void readsBackAsEmpty() {
      assertThat(StoredRemoteAccess.fromJson(new StoredRemoteAccess(false, null).toJson()))
          .isEmpty();
    }
  }

  @Nested
  @DisplayName("toJson and fromJson")
  class Json {

    @Test
    @DisplayName("round-trips the enabled flag on its own")
    void roundTripsEnabledOnly() {
      var enabled = new StoredRemoteAccess(true, null);

      assertThat(StoredRemoteAccess.fromJson(enabled.toJson())).contains(enabled);
    }

    @Test
    @DisplayName("round-trips the enabled flag and the folder together")
    void roundTripsEnabledAndFolder() {
      var full = new StoredRemoteAccess(true, FOLDER);

      assertThat(StoredRemoteAccess.fromJson(full.toJson())).contains(full);
    }

    @Test
    @DisplayName("round-trips a folder chosen while still disabled")
    void roundTripsFolderWhileDisabled() {
      var disabledWithFolder = new StoredRemoteAccess(false, FOLDER);

      assertThat(StoredRemoteAccess.fromJson(disabledWithFolder.toJson()))
          .contains(disabledWithFolder);
    }

    @Test
    @DisplayName("reads a missing or non-object value as none, never throwing")
    void readsMalformedAsEmpty() {
      assertThat(StoredRemoteAccess.fromJson(null)).isEmpty();
      assertThat(StoredRemoteAccess.fromJson("not an object")).isEmpty();
      assertThat(StoredRemoteAccess.fromJson(Map.of())).isEmpty();
    }

    @Test
    @DisplayName("reads a non-boolean enabled as disabled and a non-string folder as none")
    void toleratesWrongFieldTypes() {
      assertThat(StoredRemoteAccess.fromJson(Map.of("enabled", "yes"))).isEmpty();
      assertThat(StoredRemoteAccess.fromJson(Map.of("enabled", true, "folder", 5)))
          .contains(new StoredRemoteAccess(true, null));
      assertThat(StoredRemoteAccess.fromJson(Map.of("folder", FOLDER.toString())))
          .contains(new StoredRemoteAccess(false, FOLDER));
    }
  }
}
