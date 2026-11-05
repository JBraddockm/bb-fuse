package com.jbraddockm.link.endpoint.discovery;

import com.jbraddockm.core.discovery.CompositeScanner;
import com.jbraddockm.core.discovery.DeviceScanner;
import com.jbraddockm.core.discovery.PlatformScanners;
import java.util.ArrayList;

/** The device scanners bb-link provides to the composition root. */
public final class Scanners {

  private Scanners() {}

  /**
   * The scanner the daemon runs: bb-core's native scanners for this host (the USB link-local scan,
   * which identifies a device by its PIN) together with the legacy library-backed scanner. They run
   * at once; a device the native scan already identified is not listed a second time from the
   * legacy hit at the same address (see {@code DeviceDiscovery}). The legacy scanner is what still
   * finds Wi-Fi devices until a native Wi-Fi scanner lands.
   *
   * @return the combined scanner
   */
  public static DeviceScanner all() {
    var scanners = new ArrayList<>(PlatformScanners.forThisOs());
    scanners.add(new LibraryDeviceScanner());
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
