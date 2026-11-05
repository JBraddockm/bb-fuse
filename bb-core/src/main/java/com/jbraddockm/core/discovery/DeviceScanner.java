package com.jbraddockm.core.discovery;

import java.util.function.Consumer;

/** Finds devices. A seam, so tests can run without a network. */
@FunctionalInterface
public interface DeviceScanner {

  /**
   * Scans and reports each device as soon as it is found.
   *
   * @param found receives devices; may be called from several threads
   */
  void scan(Consumer<DiscoveredDevice> found);
}
