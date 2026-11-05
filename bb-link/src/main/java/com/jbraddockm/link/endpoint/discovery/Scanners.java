package com.jbraddockm.link.endpoint.discovery;

import com.jbraddockm.core.discovery.CompositeScanner;
import com.jbraddockm.core.discovery.DeviceScanner;
import com.jbraddockm.core.discovery.PlatformScanners;
import java.util.ArrayList;

/** The device scanners bb-link provides to the composition root. */
public final class Scanners {

  private Scanners() {}

  /**
   * The scanner the daemon runs: bb-core's native scanners for this host — the USB link-local scan
   * (which identifies a device by its PIN) and the Wi-Fi mDNS scan. On a host without a native USB
   * scanner (see {@link PlatformScanners#hasNativeUsb()}) the legacy library-backed scanner is
   * added as the USB fallback; on macOS and Linux the native scanners suffice, so it is left out.
   * When both run, a device the native scan already identified is not listed a second time from the
   * legacy hit at the same address (see {@code DeviceDiscovery}).
   *
   * @return the combined scanner
   */
  public static DeviceScanner all() {
    var scanners = new ArrayList<>(PlatformScanners.forThisOs());
    if (!PlatformScanners.hasNativeUsb()) {
      scanners.add(new LibraryDeviceScanner());
    }
    return new CompositeScanner(scanners);
  }

  /**
   * The legacy library-backed scanner alone. It shares the detector's list of known addresses (a
   * file in the user's home directory) with bb-deploy, so a device found by either tool is found
   * faster by the other.
   *
   * @return the scanner
   */
  public static DeviceScanner library() {
    return new LibraryDeviceScanner();
  }
}
