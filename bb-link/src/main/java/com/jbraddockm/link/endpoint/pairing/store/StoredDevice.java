package com.jbraddockm.link.endpoint.pairing.store;

import com.jbraddockm.core.json.Json;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * What a device folder's {@code device.json} holds: the login {@code identity}, the {@code
 * pairing}, the pinned SMB host, and the {@code remoteAccess} setting — the single record for a
 * device. Each part is optional, so a device may have, say, a pairing but no pinned address yet.
 *
 * @param identity the login certificate's identity, or {@code null} when none is enrolled
 * @param pairing the pairing this computer made with the device, or {@code null} when not paired
 * @param smbHost the pinned device SMB host ({@code 169.254.<k>.1}, {@code k} in 1..254), or {@code
 *     null} when no address is pinned
 * @param remoteAccess the host remote-access setting (the "allow remote access" toggle and folder),
 *     or {@code null} when it is the default (disabled, no folder)
 */
public record StoredDevice(
    StoredIdentity identity,
    StoredPairing pairing,
    String smbHost,
    StoredRemoteAccess remoteAccess) {

  private static final Pattern SMB_HOST = Pattern.compile("169\\.254\\.(\\d{1,3})\\.1");

  /**
   * Checks the pinned host.
   *
   * @throws IllegalArgumentException if {@code smbHost} is non-null and not a {@code 169.254.<k>.1}
   *     address with {@code k} in 1..254
   */
  public StoredDevice {
    if (smbHost != null && !isSmbHost(smbHost)) {
      throw new IllegalArgumentException("not a 169.254.<k>.1 SMB host: " + smbHost);
    }
  }

  /** Whether {@code host} is a device SMB host: {@code 169.254.<k>.1} with {@code k} in 1..254. */
  static boolean isSmbHost(String host) {
    var matcher = SMB_HOST.matcher(host);
    if (!matcher.matches()) {
      return false;
    }
    int k = Integer.parseInt(matcher.group(1));
    return k >= 1 && k <= 254;
  }

  /** Whether the record holds nothing — so its file can be removed rather than left empty. */
  boolean isEmpty() {
    return identity == null && pairing == null && smbHost == null && remoteAccess == null;
  }

  /**
   * The text of the file: the present parts only, so an absent part leaves no key.
   *
   * @return JSON, with a trailing newline
   */
  public String toJsonText() {
    Map<String, Object> fields = new LinkedHashMap<>();
    if (identity != null) {
      fields.put("identity", identity.toJson());
    }
    if (pairing != null) {
      fields.put("pairing", pairing.toJson());
    }
    if (smbHost != null) {
      fields.put("smbHost", smbHost);
    }
    if (remoteAccess != null) {
      fields.put("remoteAccess", remoteAccess.toJson());
    }
    return Json.write(fields) + "\n";
  }

  /**
   * Reads the text of the file, tolerantly: anything unreadable — unparseable JSON, or a malformed
   * part — reads as that part being absent, so a hand-edited or stale {@code device.json} can never
   * break a connect. Unknown fields are ignored.
   *
   * @param text the file's JSON
   * @return the record; each part is {@code null} unless a valid one was present
   */
  public static StoredDevice fromJsonText(String text) {
    try {
      if (Json.parse(text) instanceof Map<?, ?> fields) {
        var identity = StoredIdentity.fromJson(fields.get("identity")).orElse(null);
        var pairing = StoredPairing.fromJson(fields.get("pairing")).orElse(null);
        var smbHost = fields.get("smbHost") instanceof String host && isSmbHost(host) ? host : null;
        var remoteAccess = StoredRemoteAccess.fromJson(fields.get("remoteAccess")).orElse(null);
        return new StoredDevice(identity, pairing, smbHost, remoteAccess);
      }
    } catch (Json.JsonException ignored) {
      // fall through to the empty record
    }
    return new StoredDevice(null, null, null, null);
  }
}
