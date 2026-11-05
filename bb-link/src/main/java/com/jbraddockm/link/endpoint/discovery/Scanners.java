package com.jbraddockm.link.endpoint.discovery;

import com.jbraddockm.core.discovery.DeviceScanner;

/** The device scanners bb-link provides to the composition root. */
public final class Scanners {

  private Scanners() {}

  /**
   * The legacy library-backed scanner. It shares the detector's list of known addresses (a file in
   * the user's home directory) with bb-deploy, so a device found by either tool is found faster by
   * the other.
   *
   * @return the scanner
   */
  public static DeviceScanner library() {
    return new LibraryDeviceScanner();
  }
}
