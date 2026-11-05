package com.jbraddockm.core.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.discovery.DiscoveryClient.Body;
import com.jbraddockm.core.testing.Resources;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link UsbScanner}: identifying the device one address below a link-local host. */
@DisplayName("UsbScanner: finding a device over the USB link")
class UsbScannerTest {

  private static DiscoveryClient serving(String deviceAddress, String fixture) {
    return new DiscoveryClient(
        uri ->
            uri.getHost().equals(deviceAddress)
                ? new Body(200, Resources.text(DiscoveryClientTest.class, fixture))
                : new Body(404, ""));
  }

  private static List<DiscoveredDevice> scan(UsbScanner scanner) {
    var found = new ArrayList<DiscoveredDevice>();
    scanner.scan(found::add);
    return found;
  }

  @Nested
  @DisplayName("the device address for a host address")
  class DeviceAddress {

    @ParameterizedTest(name = "host {0} is device {1}")
    @CsvSource({
      "169.254.0.2, 169.254.0.1",
      "169.254.1.0, 169.254.0.255",
      "169.254.255.255, 169.254.255.254"
    })
    @DisplayName("is the host's address less one, within the link-local block")
    void isOneBelowTheHost(String host, String device) {
      assertThat(UsbScanner.deviceAddressFor(host)).contains(device);
    }

    @Test
    @DisplayName("is nothing when one below would leave the link-local block")
    void leavesTheBlock() {
      assertThat(UsbScanner.deviceAddressFor("169.254.0.0")).isEmpty();
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"192.168.1.5", "10.0.0.2", "::1"})
    @DisplayName("is nothing for an address that is not link-local IPv4")
    void notLinkLocalIpv4(String host) {
      assertThat(UsbScanner.deviceAddressFor(host)).isEmpty();
    }
  }

  @Nested
  @DisplayName("scanning")
  class Scanning {

    @Test
    @DisplayName("identifies the device one below a link-local host, over discovery.cgi")
    void identifiesTheDevice() {
      var scanner =
          new UsbScanner(
              () -> List.of("169.254.0.2"), serving("169.254.0.1", "discovery--real.xml"));

      assertThat(scan(scanner))
          .singleElement()
          .satisfies(
              device -> {
                assertThat(device.pin()).isEqualTo("2C1E7583");
                assertThat(device.name()).isEqualTo("STR100-1");
                assertThat(device.connection()).isEqualTo(ConnectionKind.USB);
                assertThat(device.address()).isEqualTo("169.254.0.1");
              });
    }

    @Test
    @DisplayName("finds nothing when the candidate does not answer as a device")
    void noDeviceAnswers() {
      var scanner =
          new UsbScanner(() -> List.of("169.254.0.2"), serving("10.9.9.9", "discovery--real.xml"));

      assertThat(scan(scanner)).isEmpty();
    }

    @Test
    @DisplayName("finds nothing when there is no link-local host interface")
    void noLinkLocalHost() {
      var scanner = new UsbScanner(List::of, serving("169.254.0.1", "discovery--real.xml"));

      assertThat(scan(scanner)).isEmpty();
    }

    @Test
    @DisplayName("finds nothing when the interfaces cannot be read")
    void interfacesCannotBeRead() {
      var scanner =
          new UsbScanner(
              () -> {
                throw new SocketException("no interfaces");
              },
              serving("169.254.0.1", "discovery--real.xml"));

      assertThat(scan(scanner)).isEmpty();
    }
  }
}
