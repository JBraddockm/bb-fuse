package com.jbraddockm.core.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.discovery.MdnsScanner.Advert;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link MdnsScanner}: reporting a device per address from its mDNS advert. */
@DisplayName("MdnsScanner: finding a device over mDNS")
class MdnsScannerTest {

  private static final String FRIENDLY = "Friendly_33C9D3A93B9DD79F_9B2770A11C414366";

  private static List<DiscoveredDevice> scan(Advert... adverts) {
    return scan(DevicePinResolver.NONE, adverts);
  }

  private static List<DiscoveredDevice> scan(DevicePinResolver pins, Advert... adverts) {
    var found = new ArrayList<DiscoveredDevice>();
    new MdnsScanner(() -> List.of(adverts), pins).scan(found::add);
    return found;
  }

  @Test
  @DisplayName("reports a device by its name, OS, tunnel ULA and hardware id, but no PIN or model")
  void reportsAnAdvertisedDevice() {
    assertThat(
            scan(
                new Advert(
                    "BLACKBERRY-3247",
                    "10.3.3.3216",
                    ConnectionKind.WIFI,
                    "192.168.1.82",
                    "fd0f:db1::1",
                    "0x87002c0a",
                    FRIENDLY)))
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
              assertThat(device.model()).isNull();
            });
  }

  @Test
  @DisplayName("takes the connection from the advert: a USB-link sighting is reported as USB")
  void takesTheConnectionFromTheAdvert() {
    assertThat(
            scan(
                new Advert(
                    "BLACKBERRY-7583",
                    "10.3.3.3216",
                    ConnectionKind.USB,
                    "169.254.0.1",
                    "fd15:c6dc::1",
                    "0x07002e0a",
                    FRIENDLY)))
        .singleElement()
        .satisfies(device -> assertThat(device.connection()).isEqualTo(ConnectionKind.USB));
  }

  @Test
  @DisplayName("reports a device on both transports as one sighting per address")
  void reportsEachAddress() {
    assertThat(
            scan(
                new Advert(
                    "BLACKBERRY-7583",
                    "v",
                    ConnectionKind.USB,
                    "169.254.0.1",
                    "fd15::1",
                    "0x07",
                    FRIENDLY),
                new Advert(
                    "BLACKBERRY-7583",
                    "v",
                    ConnectionKind.WIFI,
                    "192.168.1.118",
                    "fd15::1",
                    "0x07",
                    FRIENDLY)))
        .extracting(DiscoveredDevice::address)
        .containsExactly("169.254.0.1", "192.168.1.118");
  }

  @Test
  @DisplayName("finds nothing when nothing advertised")
  void nothingAdvertised() {
    assertThat(scan()).isEmpty();
  }

  @Nested
  @DisplayName("stamping the PIN a resolver recovers from the advert")
  class PinResolution {

    @Test
    @DisplayName("carries the PIN when the resolver matches the advert's instance name")
    void stampsResolvedPin() {
      DevicePinResolver resolver =
          instanceName ->
              FRIENDLY.equals(instanceName) ? Optional.of("2C1E7583") : Optional.empty();

      assertThat(
              scan(
                  resolver,
                  new Advert(
                      "BLACKBERRY-7583",
                      "10.3.3.3216",
                      ConnectionKind.WIFI,
                      "192.168.1.118",
                      "fd15::1",
                      "0x07002e0a",
                      FRIENDLY)))
          .singleElement()
          .satisfies(device -> assertThat(device.pin()).isEqualTo("2C1E7583"));
    }

    @Test
    @DisplayName("leaves the PIN null when the resolver matches nothing")
    void leavesPinNullWithoutAMatch() {
      assertThat(
              scan(
                  _ -> Optional.empty(),
                  new Advert(
                      "BLACKBERRY-7583",
                      "10.3.3.3216",
                      ConnectionKind.WIFI,
                      "192.168.1.118",
                      "fd15::1",
                      "0x07002e0a",
                      FRIENDLY)))
          .singleElement()
          .satisfies(device -> assertThat(device.pin()).isNull());
    }
  }
}
