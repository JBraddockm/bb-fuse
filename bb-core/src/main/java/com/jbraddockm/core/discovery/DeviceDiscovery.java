package com.jbraddockm.core.discovery;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

/** Runs a scan, gathers its devices, and folds duplicate sightings of a device into one entry. */
public final class DeviceDiscovery {

  private DeviceDiscovery() {}

  /**
   * Scans for devices.
   *
   * <p>A scanner may report from several threads at once (a {@link CompositeScanner} fans out), so
   * {@code onFound} is called one device at a time, in the order found. The returned list folds
   * duplicate sightings of a device into one entry.
   *
   * @param scanner how to scan
   * @param onFound told about each device the moment it is found, before duplicates are folded
   * @return the devices found, deduplicated, identified ones first
   */
  public static DeviceList discover(DeviceScanner scanner, Consumer<DiscoveredDevice> onFound) {
    var found = new ArrayList<DiscoveredDevice>();
    var lock = new Object();
    scanner.scan(
        device -> {
          synchronized (lock) {
            found.add(device);
            onFound.accept(device);
          }
        });
    synchronized (lock) {
      return new DeviceList(deduplicate(found));
    }
  }

  /**
   * Folds duplicates of one device into a single entry. An identified device (one with a GUID) is
   * kept once per GUID — the first time it was found. An unidentified device (the legacy detector
   * finds devices without a GUID) is dropped when its address is already an identified device's
   * address, since that is the same device the identity step already named; otherwise it is kept.
   * Identified devices come first, each group in the order it was found.
   */
  private static List<DiscoveredDevice> deduplicate(List<DiscoveredDevice> found) {
    var deduplicated = new ArrayList<DiscoveredDevice>();
    var guids = new HashSet<String>();
    var identifiedAddresses = new HashSet<String>();
    for (var device : found) {
      if (device.guid() != null && guids.add(device.guid())) {
        deduplicated.add(device);
        identifiedAddresses.add(device.address());
      }
    }
    for (var device : found) {
      if (device.guid() == null && !identifiedAddresses.contains(device.address())) {
        deduplicated.add(device);
      }
    }
    return deduplicated;
  }
}
