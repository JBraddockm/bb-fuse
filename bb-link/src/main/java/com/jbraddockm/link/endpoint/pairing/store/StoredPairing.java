package com.jbraddockm.link.endpoint.pairing.store;

import com.jbraddockm.core.json.Json;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The {@code pairing} section of {@code device.json}: what is kept of a pairing besides the two
 * authorities and the key.
 *
 * @param deviceId the identifier the computer gave itself
 * @param commonName the common name of the computer's authority
 * @param createdAt when the pairing was made
 */
public record StoredPairing(String deviceId, String commonName, Instant createdAt) {

  /**
   * Checks the components.
   *
   * @throws NullPointerException if a component is {@code null}
   */
  public StoredPairing {
    Objects.requireNonNull(deviceId, "deviceId");
    Objects.requireNonNull(commonName, "commonName");
    Objects.requireNonNull(createdAt, "createdAt");
  }

  /** This pairing as the {@code pairing} object of {@code device.json}. */
  Map<String, Object> toJson() {
    return Json.object(
        "deviceId", deviceId, "commonName", commonName, "createdAt", createdAt.toString());
  }

  /**
   * The pairing read from a {@code device.json} section, or empty when the value is absent or not a
   * well-formed pairing.
   *
   * @param value the {@code pairing} field of the parsed record
   * @return the pairing, or empty
   */
  static Optional<StoredPairing> fromJson(Object value) {
    if (value instanceof Map<?, ?> fields
        && fields.get("deviceId") instanceof String deviceId
        && fields.get("commonName") instanceof String commonName
        && fields.get("createdAt") instanceof String createdAt) {
      try {
        return Optional.of(new StoredPairing(deviceId, commonName, Instant.parse(createdAt)));
      } catch (DateTimeParseException malformed) {
        return Optional.empty();
      }
    }
    return Optional.empty();
  }
}
