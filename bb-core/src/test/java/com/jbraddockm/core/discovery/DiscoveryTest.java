package com.jbraddockm.core.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.json.Json;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for device discovery: {@link ConnectionKind}, {@link DiscoveredDevice}, {@link DeviceList}
 * and {@link DeviceDiscovery}.
 */
@DisplayName("Discovery: finding devices")
class DiscoveryTest {

  private static final DiscoveredDevice WIFI =
      DiscoveredDevice.anonymous("blackberry-0000.lan", null, ConnectionKind.WIFI, "192.168.1.82");
  private static final DiscoveredDevice USB =
      DiscoveredDevice.anonymous(
          "BlackBerry 10 10.3.3.2205", "10.3.3.2205", ConnectionKind.USB, "169.254.0.1");

  private static final String MODEL = "STR100-1";
  private static final String VERSION = "10.3.3.3216";

  private static DeviceCharacteristics identity(String pin, String guid) {
    return new DeviceCharacteristics(
        pin,
        guid,
        MODEL,
        "BlackBerry 10",
        VERSION,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  @Nested
  @DisplayName("ConnectionKind")
  class Kind {

    @ParameterizedTest(name = "{0} is \"{1}\"")
    @CsvSource({"WIFI, WiFi", "USB, USB", "SIMULATOR, Simulator"})
    @DisplayName(
        "each kind has the spelling that the protocol uses, which is also the word of the library,"
            + " and is found by it")
    void eachKindHasItsSpelling(ConnectionKind kind, String spelling) {
      assertThat(kind.wireName()).isEqualTo(spelling);
      assertThat(ConnectionKind.fromDetectorText(spelling)).isEqualTo(kind);
    }

    @ParameterizedTest(name = "\"{0}\" is not a kind")
    @ValueSource(strings = {"", "wifi", "WIFI", "Wi-Fi", "usb", "Bluetooth", " USB"})
    @DisplayName("a word of the library that is not exactly one of them is refused, saying which")
    void aWordThatIsNotAKind(String text) {
      assertThatThrownBy(() -> ConnectionKind.fromDetectorText(text))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("unknown connection type: " + text);
    }

    @ParameterizedTest
    @NullSource
    @DisplayName("no word at all is refused as well")
    void noWord(String text) {
      assertThatThrownBy(() -> ConnectionKind.fromDetectorText(text))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("unknown connection type: null");
    }
  }

  @Nested
  @DisplayName("DiscoveredDevice and DeviceList")
  class Results {

    @Test
    @DisplayName(
        "a device is said by its PIN, its GUID, its name, its version, the spelling of its"
            + " connection and its address")
    void aDeviceAsJson() {
      assertThat(USB.toJson())
          .containsOnlyKeys("pin", "guid", "name", "version", "connection", "address")
          .containsEntry("connection", "USB")
          .containsEntry("version", "10.3.3.2205")
          .containsEntry("address", "169.254.0.1");
      assertThat(List.copyOf(USB.toJson().keySet()))
          .containsExactly("pin", "guid", "name", "version", "connection", "address");
    }

    @Test
    @DisplayName("an identified device takes its PIN, GUID, model name and platform version")
    void anIdentifiedDevice() {
      var device =
          DiscoveredDevice.identified(
              identity("2A3B4C5D", "abc="), ConnectionKind.USB, "169.254.0.1");

      assertThat(device.pin()).isEqualTo("2A3B4C5D");
      assertThat(device.guid()).isEqualTo("abc=");
      assertThat(device.name()).isEqualTo(MODEL);
      assertThat(device.version()).isEqualTo(VERSION);
      assertThat(device.connection()).isEqualTo(ConnectionKind.USB);
      assertThat(device.address()).isEqualTo("169.254.0.1");
    }

    @Test
    @DisplayName("an unidentified device is said without a PIN or a GUID, and without a version")
    void anUnidentifiedDeviceOnTheWire() {
      assertThat(Json.write(WIFI.toJson()))
          .doesNotContain("pin")
          .doesNotContain("guid")
          .doesNotContain("version")
          .contains("\"connection\":\"WiFi\"");
    }

    @Test
    @DisplayName(
        "a list is said as the devices, in order, and keeps its own copy of what it was given")
    void aListAsJson() {
      var given = new ArrayList<>(List.of(WIFI, USB));

      var list = new DeviceList(given);
      given.clear();

      assertThat(list.devices()).containsExactly(WIFI, USB);
      assertThat(list.toJson())
          .containsOnlyKeys("devices")
          .containsEntry("devices", List.of(WIFI.toJson(), USB.toJson()));
      assertThatThrownBy(() -> list.devices().add(WIFI))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  @DisplayName("DeviceDiscovery")
  class Discovering {

    @Test
    @DisplayName(
        "tells each device as it is found, in the order that it was found, and gives them all as"
            + " the result")
    void tellsEachDeviceAndGivesAll() {
      DeviceScanner scanner = List.of(WIFI, USB)::forEach;
      var told = new ArrayList<DiscoveredDevice>();

      var list = DeviceDiscovery.discover(scanner, told::add);

      assertThat(told).containsExactly(WIFI, USB);
      assertThat(list.devices()).containsExactly(WIFI, USB);
    }

    @Test
    @DisplayName(
        "folds a legacy hit at an identified device's address, and keeps a legacy device of its"
            + " own")
    void foldsALegacyHitAtAnIdentifiedAddress() {
      var identified =
          DiscoveredDevice.identified(
              identity("2C1E7583", "G1"), ConnectionKind.USB, "169.254.0.1");
      var legacySame =
          DiscoveredDevice.anonymous("blackberry-7583", null, ConnectionKind.USB, "169.254.0.1");
      var legacyOther =
          DiscoveredDevice.anonymous("blackberry-3247", null, ConnectionKind.WIFI, "192.168.1.82");
      DeviceScanner scanner = List.of(legacyOther, identified, legacySame)::forEach;

      var list = DeviceDiscovery.discover(scanner, _ -> {});

      assertThat(list.devices()).containsExactly(identified, legacyOther);
    }

    @Test
    @DisplayName("keeps a device found twice under one GUID just once, the first it was found")
    void foldsOneGuidFoundTwice() {
      var usb =
          DiscoveredDevice.identified(
              identity("2C1E7583", "G1"), ConnectionKind.USB, "169.254.0.1");
      var wifi =
          DiscoveredDevice.identified(
              identity("2C1E7583", "G1"), ConnectionKind.WIFI, "192.168.1.82");
      DeviceScanner scanner = List.of(usb, wifi)::forEach;

      var list = DeviceDiscovery.discover(scanner, _ -> {});

      assertThat(list.devices()).containsExactly(usb);
    }

    @Test
    @DisplayName("finds nothing when the scanner finds nothing: an empty list, and nobody told")
    void findsNothing() {
      var told = new ArrayList<DiscoveredDevice>();

      var list = DeviceDiscovery.discover(_ -> {}, told::add);

      assertThat(list.devices()).isEmpty();
      assertThat(told).isEmpty();
    }

    @Test
    @DisplayName("takes devices that are found from many threads at once, without losing one")
    void takesDevicesFromManyThreads() {
      var names = IntStream.range(0, 400).mapToObj(index -> "device-" + index).toList();
      DeviceScanner scanner =
          found -> {
            try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
              names.forEach(
                  name ->
                      pool.submit(
                          () ->
                              found.accept(
                                  DiscoveredDevice.anonymous(
                                      name, null, ConnectionKind.WIFI, "10.0.0.1"))));
              pool.shutdown();
              assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException _) {
              Thread.currentThread().interrupt();
            }
          };
      var told = Collections.synchronizedList(new ArrayList<DiscoveredDevice>());

      var list = DeviceDiscovery.discover(scanner, told::add);

      assertThat(list.devices())
          .extracting(DiscoveredDevice::name)
          .containsExactlyInAnyOrderElementsOf(names);
      assertThat(told).hasSize(400);
    }
  }
}
