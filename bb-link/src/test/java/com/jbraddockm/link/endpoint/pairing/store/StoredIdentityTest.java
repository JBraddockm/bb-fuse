package com.jbraddockm.link.endpoint.pairing.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link StoredIdentity}: the {@code identity} section of {@code device.json}. */
@DisplayName("StoredIdentity: the identity section of device.json")
class StoredIdentityTest {

  private static final StoredIdentity IDENTITY =
      new StoredIdentity("BBCGIAUTH [2A3B4C5D] workstation", Instant.parse("2026-10-18T10:00:00Z"));

  @Test
  @DisplayName("an identity has a name and a date")
  void anIdentityHasANameAndADate() {
    assertThatNullPointerException()
        .isThrownBy(() -> new StoredIdentity(null, Instant.EPOCH))
        .withMessage("commonName");
    assertThatNullPointerException()
        .isThrownBy(() -> new StoredIdentity("x", null))
        .withMessage("createdAt");
  }

  @Nested
  @DisplayName("toJson and fromJson")
  class Json {

    @Test
    @DisplayName("round-trips the common name and the date")
    void roundTrips() {
      assertThat(StoredIdentity.fromJson(IDENTITY.toJson())).contains(IDENTITY);
    }

    @Test
    @DisplayName("reads a section that still carries an older extra field, ignoring it")
    void ignoresExtraFields() {
      var section = new LinkedHashMap<>(IDENTITY.toJson());
      section.put("deviceId", "01AB");

      assertThat(StoredIdentity.fromJson(section)).contains(IDENTITY);
    }

    @Test
    @DisplayName("reads a missing or malformed section as no identity")
    void readsMalformedAsEmpty() {
      assertThat(StoredIdentity.fromJson(null)).isEmpty();
      assertThat(StoredIdentity.fromJson("not an object")).isEmpty();
      assertThat(StoredIdentity.fromJson(Map.of("createdAt", "2026-10-18T10:00:00Z"))).isEmpty();
      assertThat(
              StoredIdentity.fromJson(Map.of("commonName", 5, "createdAt", "2026-10-18T10:00:00Z")))
          .isEmpty();
      assertThat(StoredIdentity.fromJson(Map.of("commonName", "x", "createdAt", "not-a-date")))
          .isEmpty();
    }
  }
}
