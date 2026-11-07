package com.jbraddockm.link.transport.smb;

import com.jbraddockm.core.error.InvalidArgumentException;
import java.util.Objects;

/**
 * Translates between a device-absolute path and an {@link SmbLocation} on one of the device's
 * shares. A client names files by their device-absolute path — the same path {@code metadata.cgi}
 * reports as {@code filepath} — so that enumerating and transferring compose; this class maps that
 * path to the share and sub-path the SMB transport addresses, and back.
 *
 * <p>Two shares are known: the internal storage ({@code media} → {@code /accounts/1000/shared}) and
 * a removable card ({@code media-sdcard} → {@code /accounts/1000/removable/sdcard}).
 */
public final class DeviceShares {

  private enum Share {
    INTERNAL("media", "/accounts/1000/shared"),
    SD_CARD("media-sdcard", "/accounts/1000/removable/sdcard");

    private final String share;
    private final String base;

    Share(String share, String base) {
      this.share = share;
      this.base = base;
    }
  }

  private DeviceShares() {}

  /**
   * Resolves a device-absolute path to its location on a share.
   *
   * @param devicePath a path such as {@code /accounts/1000/shared/documents/report.txt}
   * @return the share and sub-path the transport addresses
   * @throws InvalidArgumentException if the path is on no known share
   */
  public static SmbLocation locate(String devicePath) {
    Objects.requireNonNull(devicePath, "devicePath");
    var path = trimTrailingSlashes(devicePath);
    for (var share : Share.values()) {
      if (path.equals(share.base)) {
        return SmbLocation.root(share.share);
      }
      if (path.startsWith(share.base + "/")) {
        return new SmbLocation(share.share, path.substring(share.base.length() + 1));
      }
    }
    throw new InvalidArgumentException("devicePath", devicePath + " is not on a device file share");
  }

  /**
   * Returns the device-absolute path of a location.
   *
   * @param location a location on a known share
   * @return the device-absolute path, for example {@code /accounts/1000/shared/documents}
   * @throws IllegalArgumentException if the location's share is unknown
   */
  public static String devicePath(SmbLocation location) {
    for (var share : Share.values()) {
      if (share.share.equals(location.share())) {
        return location.path().isEmpty() ? share.base : share.base + "/" + location.path();
      }
    }
    throw new IllegalArgumentException("unknown share: " + location.share());
  }

  private static String trimTrailingSlashes(String path) {
    var end = path.length();
    while (end > 1 && path.charAt(end - 1) == '/') {
      end--;
    }
    return path.substring(0, end);
  }
}
