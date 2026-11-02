package com.jbraddockm.link.endpoint.pairing.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.jbraddockm.link.testing.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link DeviceDirectory}: the {@code device.json} record of a device, holding its pinned
 * SMB host.
 */
@DisplayName("DeviceDirectory: the device.json record of a device")
class DeviceDirectoryTest {

  @TempDir private Path devices;
  private DeviceDirectory directory;

  @BeforeEach
  void prepareTheDirectory() {
    directory = DeviceDirectory.of(devices, "2A3B4C5D");
  }

  @Nested
  @DisplayName("of")
  class Naming {

    @Test
    @DisplayName("is device.json in the folder of the PIN in the folder of the devices")
    void isTheDeviceFile() {
      assertThat(directory.directory()).isEqualTo(devices.resolve("2A3B4C5D"));
      assertThat(directory.deviceFile()).isEqualTo(devices.resolve("2A3B4C5D/device.json"));
    }

    @ParameterizedTest(name = "\"{0}\" is a PIN")
    @ValueSource(strings = {"2A3B4C5D", "a", "0123456789ABCDEF"})
    @DisplayName("takes 1 to 16 letters and digits as a PIN")
    void takesLettersAndDigits(String pin) {
      assertThat(DeviceDirectory.of(devices, pin).directory()).isEqualTo(devices.resolve(pin));
    }

    @ParameterizedTest(name = "\"{0}\" is not a PIN")
    @ValueSource(strings = {"", "../etc", "has space", "0123456789ABCDEFG", "a/b"})
    @DisplayName("refuses anything that is not a safe folder name")
    void refusesUnsafeNames(String pin) {
      assertThatIllegalArgumentException().isThrownBy(() -> DeviceDirectory.of(devices, pin));
    }
  }

  @Nested
  @DisplayName("with no record yet")
  class Absent {

    @Test
    @DisplayName("reads as empty, with no pinned host")
    void readsAsEmpty() throws IOException {
      assertThat(directory.read()).isEmpty();
      assertThat(directory.pinnedSmbHost()).isEmpty();
    }

    @Test
    @DisplayName("clearing is a no-op")
    void clearingIsANoOp() throws IOException {
      directory.clearSmbHost();

      assertThat(directory.deviceFile()).doesNotExist();
    }
  }

  @Nested
  @DisplayName("pinning an SMB host")
  class Pinning {

    @Test
    @DisplayName("writes device.json and reads the host back")
    void writesAndReadsBack() throws IOException {
      directory.pinSmbHost("169.254.5.1");

      assertThat(directory.deviceFile()).exists();
      assertThat(directory.pinnedSmbHost()).contains("169.254.5.1");
      assertThat(Files.readString(directory.deviceFile(), StandardCharsets.UTF_8))
          .isEqualTo(Resources.text(DeviceDirectoryTest.class, "stored-device--pinned.json"));
    }

    @Test
    @DisplayName("creates the device folder if it is not there yet")
    void createsTheFolder() throws IOException {
      assertThat(directory.directory()).doesNotExist();

      directory.pinSmbHost("169.254.5.1");

      assertThat(directory.pinnedSmbHost()).contains("169.254.5.1");
    }

    @Test
    @DisplayName("replaces an earlier pin")
    void replacesAnEarlierPin() throws IOException {
      directory.pinSmbHost("169.254.5.1");
      directory.pinSmbHost("169.254.9.1");

      assertThat(directory.pinnedSmbHost()).contains("169.254.9.1");
    }

    @Test
    @DisplayName("refuses an address that is not a 169.254.<k>.1 host and writes nothing")
    void refusesABadAddress() {
      assertThatIllegalArgumentException().isThrownBy(() -> directory.pinSmbHost("10.0.0.1"));

      assertThat(directory.deviceFile()).doesNotExist();
    }
  }

  @Nested
  @DisplayName("clearing a pin")
  class Clearing {

    @Test
    @DisplayName("removes the record so there is no pinned host")
    void removesTheRecord() throws IOException {
      directory.pinSmbHost("169.254.5.1");

      directory.clearSmbHost();

      assertThat(directory.deviceFile()).doesNotExist();
      assertThat(directory.pinnedSmbHost()).isEmpty();
    }
  }

  @Nested
  @DisplayName("a hand-edited or stale record")
  class Tolerant {

    @Test
    @DisplayName("reads as no pin rather than breaking a connect")
    void readsAsNoPin() throws IOException {
      Files.createDirectories(directory.directory());
      Files.writeString(
          directory.deviceFile(),
          Resources.text(DeviceDirectoryTest.class, "stored-device--not-json.txt"),
          StandardCharsets.UTF_8);

      assertThat(directory.read()).isPresent();
      assertThat(directory.pinnedSmbHost()).isEmpty();
    }
  }
}
