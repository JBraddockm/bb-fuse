package com.jbraddockm.link.endpoint.pairing.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link StoredPairing}: the {@code pairing} section of {@code device.json}. */
@DisplayName("StoredPairing: the pairing section of device.json")
class StoredPairingTest {

  private static final StoredPairing PAIRING =
      new StoredPairing("01AB", "PC issuer 1", Instant.parse("2026-10-18T10:00:00Z"));

  @Test
  @DisplayName("a pairing has an identifier, a name and a date")
  void aPairingHasThreeParts() {
    assertThatNullPointerException()
        .isThrownBy(() -> new StoredPairing(null, "x", Instant.EPOCH))
        .withMessage("deviceId");
    assertThatNullPointerException()
        .isThrownBy(() -> new StoredPairing("01AB", null, Instant.EPOCH))
        .withMessage("commonName");
    assertThatNullPointerException()
        .isThrownBy(() -> new StoredPairing("01AB", "x", null))
        .withMessage("createdAt");
  }

  @Nested
  @DisplayName("toJson and fromJson")
  class Json {

    @Test
    @DisplayName("round-trips the identifier, the name and the date")
    void roundTrips() {
      assertThat(StoredPairing.fromJson(PAIRING.toJson())).contains(PAIRING);
    }

    @Test
    @DisplayName("reads a section that still carries an older extra field, ignoring it")
    void ignoresExtraFields() {
      var section = new LinkedHashMap<>(PAIRING.toJson());
      section.put("deviceName", "ms-MacBook-Pro");

      assertThat(StoredPairing.fromJson(section)).contains(PAIRING);
    }

    @Test
    @DisplayName("reads a missing or malformed section as no pairing")
    void readsMalformedAsEmpty() {
      assertThat(StoredPairing.fromJson(null)).isEmpty();
      assertThat(StoredPairing.fromJson("not an object")).isEmpty();
      assertThat(
              StoredPairing.fromJson(
                  Map.of("commonName", "x", "createdAt", "2026-10-18T10:00:00Z")))
          .isEmpty();
      assertThat(
              StoredPairing.fromJson(
                  Map.of("deviceId", 5, "commonName", "x", "createdAt", "2026-10-18T10:00:00Z")))
          .isEmpty();
      assertThat(
              StoredPairing.fromJson(
                  Map.of("deviceId", "01AB", "commonName", "x", "createdAt", "not-a-date")))
          .isEmpty();
    }
  }
}
