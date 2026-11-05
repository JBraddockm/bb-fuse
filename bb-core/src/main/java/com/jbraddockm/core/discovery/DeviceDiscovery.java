package com.jbraddockm.core.discovery;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
   * kept once per GUID — the first time it was found — and its address is taken. An unidentified
   * device (one found without a GUID, over Wi-Fi mDNS or by the legacy detector) is folded by
   * address, and dropped when its address is already an identified device's. So the mDNS and legacy
   * sightings of one Wi-Fi device fold into one entry; where they differ, the one carrying an OS
   * version (mDNS) is kept over the one without (the detector). Identified devices come first.
   */
  private static List<DiscoveredDevice> deduplicate(List<DiscoveredDevice> found) {
    var identified = new ArrayList<DiscoveredDevice>();
    var guids = new HashSet<String>();
    var takenAddresses = new HashSet<String>();
    for (var device : found) {
      if (device.guid() != null && guids.add(device.guid())) {
        identified.add(device);
        takenAddresses.add(device.address());
      }
    }
    var anonymousByAddress = new LinkedHashMap<String, DiscoveredDevice>();
    for (var device : found) {
      if (device.guid() == null && !takenAddresses.contains(device.address())) {
        anonymousByAddress.merge(device.address(), device, DeviceDiscovery::richer);
      }
    }
    var deduplicated = new ArrayList<>(identified);
    deduplicated.addAll(anonymousByAddress.values());
    return deduplicated;
  }

  /**
   * Keeps the device that says more: the one with a ULA (an mDNS advert) wins over one without,
   * then the one with an OS version, else the one found first.
   */
  private static DiscoveredDevice richer(DiscoveredDevice kept, DiscoveredDevice candidate) {
    if (kept.ula() != null || candidate.ula() != null) {
      return candidate.ula() != null ? candidate : kept;
    }
    return kept.version() != null || candidate.version() == null ? kept : candidate;
  }
}
