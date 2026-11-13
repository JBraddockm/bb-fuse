package com.jbraddockm.link.endpoint.pairing.store;

import com.jbraddockm.core.json.Json;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * The {@code remoteAccess} section of {@code device.json}: whether host remote access is allowed
 * for this device, and which host folder it serves. This is the persisted <em>intent</em> — the
 * "allow remote access" toggle and the chosen folder — never the live running state, which stays in
 * memory.
 *
 * <p>The default (disabled, no folder) is written as no section at all, so an absent section reads
 * back as the default and a {@code device.json} that never had one is left untouched.
 *
 * @param enabled whether remote access starts automatically when this device connects
 * @param folder the host folder to serve, or {@code null} to use the global default
 */
public record StoredRemoteAccess(boolean enabled, Path folder) {

  /** Whether this is the default — disabled and no folder — so it need not be written. */
  boolean isDefault() {
    return !enabled && folder == null;
  }

  /** This section as the {@code remoteAccess} object of {@code device.json}. */
  Map<String, Object> toJson() {
    return folder == null
        ? Json.object("enabled", enabled)
        : Json.object("enabled", enabled, "folder", folder.toString());
  }

  /**
   * The remote-access section read from a {@code device.json} value, tolerantly: a missing or
   * non-object value, or the default (disabled, no folder), reads as empty, so a hand-edited or
   * stale record never carries a spurious section.
   *
   * @param value the {@code remoteAccess} field of the parsed record
   * @return the section, or empty when absent, malformed, or default
   */
  static Optional<StoredRemoteAccess> fromJson(Object value) {
    if (value instanceof Map<?, ?> fields) {
      var enabled = fields.get("enabled") instanceof Boolean flag && flag;
      var folder =
          fields.get("folder") instanceof String path && !path.isBlank()
              ? Path.of(path.strip())
              : null;
      var parsed = new StoredRemoteAccess(enabled, folder);
      return parsed.isDefault() ? Optional.empty() : Optional.of(parsed);
    }
    return Optional.empty();
  }
}
