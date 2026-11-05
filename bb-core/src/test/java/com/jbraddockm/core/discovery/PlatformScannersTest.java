package com.jbraddockm.core.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link PlatformScanners}: which native scanners a host offers. */
@DisplayName("PlatformScanners: the native scanners for a host")
class PlatformScannersTest {

  @ParameterizedTest(name = "\"{0}\"")
  @ValueSource(strings = {"Mac OS X", "Darwin", "Linux", "GNU/Linux"})
  @DisplayName("macOS and Linux offer the USB scanner and the Wi-Fi scanner")
  void macAndLinuxOfferUsbAndWifi(String osName) {
    assertThat(PlatformScanners.forOs(osName))
        .hasExactlyElementsOfTypes(UsbScanner.class, WifiScanner.class);
  }

  @ParameterizedTest(name = "\"{0}\"")
  @ValueSource(strings = {"Windows 11", "Windows Server 2022", ""})
  @DisplayName("another OS offers the Wi-Fi scanner, which is portable, but not USB")
  void anotherOsOffersWifiOnly(String osName) {
    assertThat(PlatformScanners.forOs(osName)).singleElement().isInstanceOf(WifiScanner.class);
  }
}
