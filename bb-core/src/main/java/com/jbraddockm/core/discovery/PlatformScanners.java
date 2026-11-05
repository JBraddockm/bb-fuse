package com.jbraddockm.core.discovery;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The native scanners bb-core runs on the host it is on. The Wi-Fi mDNS scan ({@link WifiScanner})
 * is portable JDK, so it is offered on every OS; the USB link-local scan ({@link UsbScanner}) is
 * offered on macOS and Linux, where the OS exposes the device's network interface without a driver.
 * A composition root combines these with any caller-supplied scanner in a {@link CompositeScanner},
 * and a further transport is added here alone.
 */
public final class PlatformScanners {

  private PlatformScanners() {}

  /**
   * The native scanners for the host this is running on.
   *
   * @return the scanners for this OS
   */
  public static List<DeviceScanner> forThisOs() {
    return forOs(System.getProperty("os.name", ""));
  }

  /**
   * The native scanners for a named OS.
   *
   * @param osName the value of the {@code os.name} system property
   * @return the scanners for that OS
   */
  static List<DeviceScanner> forOs(String osName) {
    var os = osName.toLowerCase(Locale.ROOT);
    var scanners = new ArrayList<DeviceScanner>();
    if (os.contains("mac") || os.contains("darwin") || os.contains("linux")) {
      scanners.add(new UsbScanner());
    }
    scanners.add(new WifiScanner());
    return List.copyOf(scanners);
  }
}
