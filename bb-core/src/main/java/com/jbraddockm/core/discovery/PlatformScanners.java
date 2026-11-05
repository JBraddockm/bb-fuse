package com.jbraddockm.core.discovery;

import java.util.List;
import java.util.Locale;

/**
 * The native scanners bb-core runs on the host it is on. The USB link-local scan is portable JDK
 * (see {@link UsbScanner}), so it is offered on macOS and Linux; another OS is left to the caller's
 * own scanner. A composition root combines these with any caller-supplied scanner in a {@link
 * CompositeScanner}, and a further transport is added here alone.
 */
public final class PlatformScanners {

  private PlatformScanners() {}

  /**
   * The native scanners for the host this is running on.
   *
   * @return the scanners, which may be empty on an unsupported OS
   */
  public static List<DeviceScanner> forThisOs() {
    return forOs(System.getProperty("os.name", ""));
  }

  /**
   * The native scanners for a named OS.
   *
   * @param osName the value of the {@code os.name} system property
   * @return the scanners for that OS, empty when none is supported there
   */
  static List<DeviceScanner> forOs(String osName) {
    var os = osName.toLowerCase(Locale.ROOT);
    boolean supported = os.contains("mac") || os.contains("darwin") || os.contains("linux");
    return supported ? List.of(new UsbScanner()) : List.of();
  }
}
