package com.jbraddockm.link.endpoint.pairing.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.jbraddockm.link.testing.Resources;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link StoredDevice}: its JSON form in {@code device.json}. */
@DisplayName("StoredDevice: the device.json file of a device")
class StoredDeviceTest {

  private static final StoredIdentity IDENTITY =
      new StoredIdentity("BBCGIAUTH [2A3B4C5D] workstation", Instant.parse("2026-10-18T10:00:00Z"));
  private static final StoredPairing PAIRING =
      new StoredPairing("01AB", "PC issuer 1", Instant.parse("2026-10-28T00:00:00Z"));
  private static final StoredDevice PINNED = new StoredDevice(null, null, "169.254.5.1", null);
  private static final StoredDevice NO_PIN = new StoredDevice(null, null, null, null);

  private static String resource(String name) {
    return Resources.text(StoredDeviceTest.class, name);
  }

  @Nested
  @DisplayName("toJsonText")
  class Writing {

    @Test
    @DisplayName("writes the pinned host compactly, with a newline")
    void writesThePinnedHost() {
      assertThat(PINNED.toJsonText()).isEqualTo(resource("stored-device--pinned.json"));
    }

    @Test
    @DisplayName("writes an empty object when the record holds nothing")
    void writesAnEmptyObject() {
      assertThat(NO_PIN.toJsonText()).isEqualTo(resource("stored-device--empty.json"));
    }
  }

  @Nested
  @DisplayName("fromJsonText")
  class Reading {

    @Test
    @DisplayName("reads back a pinned host that toJsonText wrote")
    void readsThePinnedHost() {
      assertThat(StoredDevice.fromJsonText(resource("stored-device--pinned.json")))
          .isEqualTo(PINNED);
    }

    @Test
    @DisplayName("round-trips the identity, pairing and pinned host together")
    void roundTripsEveryPart() {
      var full = new StoredDevice(IDENTITY, PAIRING, "169.254.5.1", null);

      assertThat(StoredDevice.fromJsonText(full.toJsonText())).isEqualTo(full);
    }

    @Test
    @DisplayName("reads an empty object as no parts")
    void readsAnEmptyObject() {
      var device = StoredDevice.fromJsonText(resource("stored-device--empty.json"));

      assertThat(device.identity()).isNull();
      assertThat(device.pairing()).isNull();
      assertThat(device.smbHost()).isNull();
      assertThat(device.remoteAccess()).isNull();
    }

    @Test
    @DisplayName("round-trips the remote-access setting alongside the other parts")
    void roundTripsRemoteAccess() {
      var full =
          new StoredDevice(
              IDENTITY,
              PAIRING,
              "169.254.5.1",
              new StoredRemoteAccess(true, Path.of("/Users/me/Documents/BB-Fuse")));

      assertThat(StoredDevice.fromJsonText(full.toJsonText())).isEqualTo(full);
    }

    @Test
    @DisplayName("reads the remote-access section from a file, keeping the other sections")
    void readsRemoteAccessFromAFile() {
      assertThat(StoredDevice.fromJsonText(resource("stored-device--remote-access.json")))
          .isEqualTo(
              new StoredDevice(
                  null,
                  null,
                  "169.254.5.1",
                  new StoredRemoteAccess(true, Path.of("/Users/me/Documents/BB-Fuse"))));
    }

    @Test
    @DisplayName("reads a malformed remote-access section as none, keeping the other sections")
    void readsMalformedRemoteAccessAsNone() {
      assertThat(StoredDevice.fromJsonText(resource("stored-device--remote-access-malformed.json")))
          .isEqualTo(PINNED);
    }

    @Test
    @DisplayName("ignores unknown fields a later version may add")
    void ignoresUnknownFields() {
      assertThat(StoredDevice.fromJsonText(resource("stored-device--extra-fields.json")))
          .isEqualTo(PINNED);
    }

    @ParameterizedTest(name = "{1}")
    @CsvSource({
      "stored-device--not-json.txt, not JSON",
      "stored-device--array.json, a JSON array",
      "stored-device--invalid-host.json, a non-169.254 host",
      "stored-device--k-reserved.json, a reserved octet (k=255)",
      "stored-device--host-not-text.json, a non-text host"
    })
    @DisplayName("reads anything unreadable as no pin, never throwing")
    void readsAnythingUnreadableAsNoPin(String name, String why) {
      assertThat(StoredDevice.fromJsonText(resource(name)).smbHost())
          .describedAs("%s should read as no pin", why)
          .isNull();
    }
  }

  @Nested
  @DisplayName("validation")
  class Validation {

    @ParameterizedTest(name = "\"{0}\" is a valid pinned host")
    @ValueSource(strings = {"169.254.1.1", "169.254.5.1", "169.254.254.1"})
    @DisplayName("accepts a 169.254.<k>.1 host with k in 1..254")
    void accepts(String host) {
      assertThat(new StoredDevice(null, null, host, null).smbHost()).isEqualTo(host);
    }

    @ParameterizedTest(name = "\"{0}\" is rejected")
    @ValueSource(
        strings = {"169.254.0.1", "169.254.255.1", "169.254.5.2", "10.0.0.1", "169.254.5", "x"})
    @DisplayName("rejects anything that is not a 169.254.<k>.1 host with k in 1..254")
    void rejects(String host) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new StoredDevice(null, null, host, null));
    }

    @Test
    @DisplayName("allows null — a device with no pinned host")
    void allowsNull() {
      assertThat(new StoredDevice(null, null, null, null).smbHost()).isNull();
    }
  }
}
