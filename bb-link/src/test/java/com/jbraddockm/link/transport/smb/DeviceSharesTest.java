package com.jbraddockm.link.transport.smb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.jbraddockm.core.error.InvalidArgumentException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link DeviceShares}: mapping a device-absolute path to a share location and back. */
@DisplayName("DeviceShares: device path to share location")
class DeviceSharesTest {

  @Nested
  @DisplayName("locating a device path")
  class Locating {

    @Test
    @DisplayName("maps internal storage to the media share")
    void internalStorage() {
      var location = DeviceShares.locate("/accounts/1000/shared/documents/report.txt");

      assertThat(location.share()).isEqualTo("media");
      assertThat(location.path()).isEqualTo("documents/report.txt");
    }

    @Test
    @DisplayName("maps the removable card to the media-sdcard share")
    void removableCard() {
      var location = DeviceShares.locate("/accounts/1000/removable/sdcard/camera/photo.jpg");

      assertThat(location.share()).isEqualTo("media-sdcard");
      assertThat(location.path()).isEqualTo("camera/photo.jpg");
    }

    @Test
    @DisplayName("maps a share base itself to the share root")
    void shareRoot() {
      var location = DeviceShares.locate("/accounts/1000/shared");

      assertThat(location.share()).isEqualTo("media");
      assertThat(location.path()).isEmpty();
    }

    @Test
    @DisplayName("ignores a trailing separator")
    void trailingSeparator() {
      assertThat(DeviceShares.locate("/accounts/1000/shared/documents/").path())
          .isEqualTo("documents");
    }

    @Test
    @DisplayName("matches only on a path boundary, not a shared prefix")
    void matchesOnBoundary() {
      assertThatExceptionOfType(InvalidArgumentException.class)
          .isThrownBy(() -> DeviceShares.locate("/accounts/1000/sharedpublic/x"));
    }

    @Test
    @DisplayName("rejects a path that is on no known share")
    void unknownPath() {
      assertThatExceptionOfType(InvalidArgumentException.class)
          .isThrownBy(() -> DeviceShares.locate("/var/log/messages"));
    }
  }

  @Nested
  @DisplayName("reversing to a device path")
  class Reversing {

    @Test
    @DisplayName("prefixes the share base to the sub-path")
    void prefixesBase() {
      var path = DeviceShares.devicePath(new SmbLocation("media", "documents/report.txt"));

      assertThat(path).isEqualTo("/accounts/1000/shared/documents/report.txt");
    }

    @Test
    @DisplayName("returns the base itself for the share root")
    void shareRoot() {
      assertThat(DeviceShares.devicePath(SmbLocation.root("media-sdcard")))
          .isEqualTo("/accounts/1000/removable/sdcard");
    }

    @Test
    @DisplayName("round-trips a located path")
    void roundTrips() {
      var original = "/accounts/1000/shared/music/track.mp3";

      assertThat(DeviceShares.devicePath(DeviceShares.locate(original))).isEqualTo(original);
    }

    @Test
    @DisplayName("rejects an unknown share")
    void unknownShare() {
      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(() -> DeviceShares.devicePath(new SmbLocation("backups", "x")));
    }
  }
}
