package com.jbraddockm.link.endpoint.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.discovery.ConnectionKind;
import com.jbraddockm.core.discovery.DiscoveredDevice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link LibraryDeviceScanner}: what the library says becomes what the daemon says. */
@DisplayName("LibraryDeviceScanner: the legacy library-backed scanner")
class LibraryDeviceScannerTest {

  @Test
  @DisplayName("the library factory gives the library-backed scanner")
  void theFactoryGivesTheLibraryScanner() {
    assertThat(Scanners.library()).isInstanceOf(LibraryDeviceScanner.class);
  }

  @Nested
  @DisplayName("what the library says becomes what the daemon says")
  class Converting {

    @Test
    @DisplayName(
        "a device is the host name and the address that the library gave, with the kind that its"
            + " word names")
    void aDevice() {
      assertThat(
              LibraryDeviceScanner.convert(
                  "blackberry-0000.lan", "192.168.1.82", "WiFi", "10.3.3.2205"))
          .isEqualTo(
              new DiscoveredDevice(
                  "blackberry-0000.lan", "192.168.1.82", ConnectionKind.WIFI, "10.3.3.2205"));
    }

    @ParameterizedTest(name = "version \"{0}\"")
    @CsvSource(value = {"'  10.3.3.2205  ', 10.3.3.2205", "10.3.3.2205, 10.3.3.2205"})
    @DisplayName("a version is kept without the blanks around it")
    void aVersionIsStripped(String given, String expected) {
      assertThat(LibraryDeviceScanner.convert("h", "a", "USB", given).version())
          .isEqualTo(expected);
    }

    @ParameterizedTest(name = "version \"{0}\"")
    @ValueSource(strings = {"", "   ", "\t"})
    @DisplayName("a version that is empty or blank is no version")
    void aBlankVersion(String given) {
      assertThat(LibraryDeviceScanner.convert("h", "a", "USB", given).version()).isNull();
    }

    @Test
    @DisplayName("a version that the library did not give is no version")
    void noVersion() {
      assertThat(LibraryDeviceScanner.convert("h", "a", "USB", null).version()).isNull();
    }

    @Test
    @DisplayName("a kind of connection that is not known is refused: the daemon does not guess")
    void anUnknownKind() {
      assertThatThrownBy(() -> LibraryDeviceScanner.convert("h", "a", "Bluetooth", null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("unknown connection type: Bluetooth");
    }
  }
}
