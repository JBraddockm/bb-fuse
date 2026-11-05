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
  @DisplayName("a supported OS offers the USB scanner")
  void aSupportedOsOffersUsb(String osName) {
    assertThat(PlatformScanners.forOs(osName)).singleElement().isInstanceOf(UsbScanner.class);
  }

  @ParameterizedTest(name = "\"{0}\"")
  @ValueSource(strings = {"Windows 11", "Windows Server 2022", ""})
  @DisplayName("an unsupported OS offers nothing native, leaving the caller to fall back")
  void anUnsupportedOsOffersNothing(String osName) {
    assertThat(PlatformScanners.forOs(osName)).isEmpty();
  }
}
