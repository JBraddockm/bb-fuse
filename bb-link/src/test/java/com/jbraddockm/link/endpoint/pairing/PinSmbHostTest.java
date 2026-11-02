package com.jbraddockm.link.endpoint.pairing;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.link.endpoint.pairing.store.DeviceDirectory;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link PinSmbHost}: pinning and clearing a device's SMB host, with no device call. */
@DisplayName("PinSmbHost: pinning a device's SMB host on this computer")
class PinSmbHostTest {

  @Nested
  @DisplayName("pin")
  class Pinning {

    @Test
    @DisplayName("writes the address to device.json and reports it")
    void writesTheAddress(@TempDir Path devices) throws Exception {
      var outcome = PinSmbHost.pin(devices, "2C1E7583", "169.254.5.1");

      assertThat(outcome)
          .isInstanceOfSatisfying(
              CommandOutcome.Succeeded.class,
              succeeded -> {
                var result = (PinResult) succeeded.result();
                assertThat(result.pin()).isEqualTo("2C1E7583");
                assertThat(result.smbHost()).isEqualTo("169.254.5.1");
                assertThat(result.toJson())
                    .containsEntry("smbHost", "169.254.5.1")
                    .containsEntry("pinned", Boolean.TRUE);
              });
      assertThat(DeviceDirectory.of(devices, "2C1E7583").pinnedSmbHost()).contains("169.254.5.1");
    }

    @Test
    @DisplayName("replaces an earlier pin")
    void replacesAnEarlierPin(@TempDir Path devices) throws Exception {
      PinSmbHost.pin(devices, "2C1E7583", "169.254.5.1");

      PinSmbHost.pin(devices, "2C1E7583", "169.254.9.1");

      assertThat(DeviceDirectory.of(devices, "2C1E7583").pinnedSmbHost()).contains("169.254.9.1");
    }

    @Test
    @DisplayName("refuses an address that is not a 169.254.<k>.1 host, writing nothing")
    void refusesABadAddress(@TempDir Path devices) throws Exception {
      var outcome = PinSmbHost.pin(devices, "2C1E7583", "10.0.0.1");

      assertThat(outcome)
          .isInstanceOfSatisfying(
              CommandOutcome.Failed.class,
              failed ->
                  assertThat(failed.error().code()).isEqualTo(CoreErrorCode.INVALID_ARGUMENT));
      assertThat(DeviceDirectory.of(devices, "2C1E7583").pinnedSmbHost()).isEmpty();
    }

    @Test
    @DisplayName("refuses a PIN that is not a safe folder name")
    void refusesABadPin(@TempDir Path devices) {
      var outcome = PinSmbHost.pin(devices, "../etc", "169.254.5.1");

      assertThat(outcome)
          .isInstanceOfSatisfying(
              CommandOutcome.Failed.class,
              failed ->
                  assertThat(failed.error().code()).isEqualTo(CoreErrorCode.INVALID_ARGUMENT));
    }
  }

  @Nested
  @DisplayName("unpin")
  class Clearing {

    @Test
    @DisplayName("clears the pin and reports no address")
    void clearsThePin(@TempDir Path devices) throws Exception {
      PinSmbHost.pin(devices, "2C1E7583", "169.254.5.1");

      var outcome = PinSmbHost.unpin(devices, "2C1E7583");

      assertThat(outcome)
          .isInstanceOfSatisfying(
              CommandOutcome.Succeeded.class,
              succeeded -> {
                var result = (PinResult) succeeded.result();
                assertThat(result.smbHost()).isNull();
                assertThat(result.toJson()).containsEntry("pinned", Boolean.FALSE);
              });
      assertThat(DeviceDirectory.of(devices, "2C1E7583").pinnedSmbHost()).isEmpty();
    }

    @Test
    @DisplayName("is a no-op when nothing is pinned")
    void isANoOpWhenNothingPinned(@TempDir Path devices) {
      var outcome = PinSmbHost.unpin(devices, "2C1E7583");

      assertThat(outcome).isInstanceOf(CommandOutcome.Succeeded.class);
    }

    @Test
    @DisplayName("refuses a PIN that is not a safe folder name")
    void refusesABadPin(@TempDir Path devices) {
      var outcome = PinSmbHost.unpin(devices, "../etc");

      assertThat(outcome)
          .isInstanceOfSatisfying(
              CommandOutcome.Failed.class,
              failed ->
                  assertThat(failed.error().code()).isEqualTo(CoreErrorCode.INVALID_ARGUMENT));
    }
  }
}
