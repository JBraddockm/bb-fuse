package com.jbraddockm.link.endpoint.discovery;

import com.jbraddockm.core.discovery.CompositeScanner;
import com.jbraddockm.core.discovery.DevicePinResolver;
import com.jbraddockm.core.discovery.DeviceScanner;
import com.jbraddockm.core.discovery.PlatformScanners;
import java.util.ArrayList;

/** The device scanners bb-link provides to the composition root. */
public final class Scanners {

  private Scanners() {}

  /**
   * The scanner the daemon runs: bb-core's native scanners for this host — the USB link-local scan
   * (which identifies a device by its PIN) and the Wi-Fi mDNS scan.
   *
   * @return the combined scanner
   */
  public static DeviceScanner all() {
    return all(DevicePinResolver.NONE);
  }

  /**
   * As {@link #all()}, but the Wi-Fi mDNS scan recovers a paired device's PIN from its advert
   * through {@code pins} (see {@link DevicePinResolver}) — so a Wi-Fi sighting of a paired device
   * carries its PIN, the same identity its USB sighting shows, with no tunnel.
   *
   * @param pins recovers a paired device's PIN from its mDNS advert, locally
   * @return the combined scanner
   */
  public static DeviceScanner all(DevicePinResolver pins) {
    return new CompositeScanner(new ArrayList<>(PlatformScanners.forThisOs(pins)));
  }
}
