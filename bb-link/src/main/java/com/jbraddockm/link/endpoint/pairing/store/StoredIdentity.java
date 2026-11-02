package com.jbraddockm.link.endpoint.pairing.store;

import com.jbraddockm.core.json.Json;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The {@code identity} section of {@code device.json}: the common name of the login certificate and
 * when it was issued.
 *
 * @param commonName the common name the certificate request carried
 * @param createdAt when the identity was made
 */
public record StoredIdentity(String commonName, Instant createdAt) {

  /**
   * Checks the components.
   *
   * @throws NullPointerException if a component is {@code null}
   */
  public StoredIdentity {
    Objects.requireNonNull(commonName, "commonName");
    Objects.requireNonNull(createdAt, "createdAt");
  }

  /** This identity as the {@code identity} object of {@code device.json}. */
  Map<String, Object> toJson() {
    return Json.object("commonName", commonName, "createdAt", createdAt.toString());
  }

  /**
   * The identity read from a {@code device.json} section, or empty when the value is absent or not
   * a well-formed identity.
   *
   * @param value the {@code identity} field of the parsed record
   * @return the identity, or empty
   */
  static Optional<StoredIdentity> fromJson(Object value) {
    if (value instanceof Map<?, ?> fields
        && fields.get("commonName") instanceof String commonName
        && fields.get("createdAt") instanceof String createdAt) {
      try {
        return Optional.of(new StoredIdentity(commonName, Instant.parse(createdAt)));
      } catch (DateTimeParseException malformed) {
        return Optional.empty();
      }
    }
    return Optional.empty();
  }
}
