package com.jbraddockm.core.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.discovery.WifiScanner.Advert;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link WifiScanner}: reporting the devices mDNS advertised, by name, OS and address.
 */
@DisplayName("WifiScanner: finding a device over Wi-Fi")
class WifiScannerTest {

  private static List<DiscoveredDevice> scan(Advert... adverts) {
    var found = new ArrayList<DiscoveredDevice>();
    new WifiScanner(() -> List.of(adverts)).scan(found::add);
    return found;
  }

  @Test
  @DisplayName("reports an advertised device by its name, OS, address, tunnel ULA and hardware id")
  void reportsAnAdvertisedDevice() {
    assertThat(
            scan(
                new Advert(
                    "BLACKBERRY-3247", "10.3.3.3216", "192.168.1.82", "fd0f:db1::1", "0x87002c0a")))
        .singleElement()
        .satisfies(
            device -> {
              assertThat(device.name()).isEqualTo("BLACKBERRY-3247");
              assertThat(device.version()).isEqualTo("10.3.3.3216");
              assertThat(device.connection()).isEqualTo(ConnectionKind.WIFI);
              assertThat(device.address()).isEqualTo("192.168.1.82");
              assertThat(device.ula()).isEqualTo("fd0f:db1::1");
              assertThat(device.hwid()).isEqualTo("0x87002c0a");
              assertThat(device.pin()).isNull();
              assertThat(device.guid()).isNull();
            });
  }

  @Test
  @DisplayName("reports each advertised device")
  void reportsEach() {
    assertThat(
            scan(
                new Advert(
                    "BLACKBERRY-3247", "10.3.3.3216", "192.168.1.82", "fd0f:db1::1", "0x87002c0a"),
                new Advert(
                    "BLACKBERRY-7583",
                    "10.3.3.3216",
                    "192.168.1.118",
                    "fd15:c6dc::1",
                    "0x07002e0a")))
        .extracting(DiscoveredDevice::address)
        .containsExactly("192.168.1.82", "192.168.1.118");
  }

  @Test
  @DisplayName("finds nothing when nothing advertised")
  void nothingAdvertised() {
    assertThat(scan()).isEmpty();
  }
}
