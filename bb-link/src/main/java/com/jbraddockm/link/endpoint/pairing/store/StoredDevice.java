package com.jbraddockm.link.endpoint.pairing.store;

import com.jbraddockm.core.json.Json;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * What {@code device.json} in a device folder records. For now just the pinned SMB host — the inner
 * address the user chose to keep stable for this device, proposed to the tunnel broker on connect.
 * A later iteration folds the pairing and identity records into this one file.
 *
 * @param smbHost the pinned device SMB host ({@code 169.254.<k>.1}, {@code k} in 1..254), or {@code
 *     null} when the device has no pinned address
 */
public record StoredDevice(String smbHost) {

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

  /**
   * The text of the file.
   *
   * @return JSON — {@code {"smbHost":"..."}}, or {@code {}} when nothing is pinned
   */
  public String toJsonText() {
    return Json.write(smbHost == null ? Json.object() : Json.object("smbHost", smbHost)) + "\n";
  }

  /**
   * Reads the text of the file, tolerantly. Anything unreadable — unparseable JSON, a missing or
   * malformed {@code smbHost} — becomes a record with no pinned host, so a hand-edited or stale
   * {@code device.json} can never break a connect. Unknown fields are ignored, so the record that a
   * later version writes still reads here.
   *
   * @param text the file's JSON
   * @return the record; its {@link #smbHost()} is {@code null} unless a valid one was present
   */
  public static StoredDevice fromJsonText(String text) {
    try {
      if (Json.parse(text) instanceof Map<?, ?> fields
          && fields.get("smbHost") instanceof String host
          && isSmbHost(host)) {
        return new StoredDevice(host);
      }
    } catch (Json.JsonException ignored) {
      // fall through to the no-pin record
    }
    return new StoredDevice(null);
  }
}
