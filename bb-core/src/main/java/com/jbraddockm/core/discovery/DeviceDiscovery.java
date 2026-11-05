package com.jbraddockm.core.discovery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;

/** Runs a scan, gathers its devices, and folds the sightings of a device into one entry. */
public final class DeviceDiscovery {

  private DeviceDiscovery() {}

  /**
   * Scans for devices.
   *
   * <p>A scanner may report from several threads at once (a {@link CompositeScanner} fans out), so
   * {@code onFound} is called one device at a time, in the order found. The returned list folds the
   * sightings of a device into one entry per address.
   *
   * @param scanner how to scan
   * @param onFound told about each device the moment it is found, before the sightings are folded
   * @return the devices found, one per address, merged
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
   * Folds the sightings of a device into one entry per address. One scan can see a device two ways
   * at one address — the USB scan identifies it over {@code discovery.cgi} (PIN, GUID, model) while
   * mDNS gives its name, ULA and hardware id — so the two are merged into one complete entry. A
   * device reachable at two addresses (the USB link and Wi-Fi) stays two entries, one per
   * transport.
   */
  private static List<DiscoveredDevice> deduplicate(List<DiscoveredDevice> found) {
    var byAddress = new LinkedHashMap<String, DiscoveredDevice>();
    for (var device : found) {
      byAddress.merge(device.address(), device, DeviceDiscovery::merge);
    }
    return List.copyOf(byAddress.values());
  }

  /**
   * Merges two sightings of a device at one address into one, taking each field from whichever
   * sighting supplied it. The fields are complementary — {@code discovery.cgi} gives the PIN, GUID
   * and model; mDNS gives the name, ULA and hardware id — so a non-null value wins, keeping the
   * first when both have one.
   */
  private static DiscoveredDevice merge(DiscoveredDevice a, DiscoveredDevice b) {
    return new DiscoveredDevice(
        a.pin() != null ? a.pin() : b.pin(),
        a.guid() != null ? a.guid() : b.guid(),
        a.model() != null ? a.model() : b.model(),
        mergedName(a, b),
        a.version() != null ? a.version() : b.version(),
        a.connection(),
        a.address(),
        a.ula() != null ? a.ula() : b.ula(),
        a.hwid() != null ? a.hwid() : b.hwid());
  }

  /**
   * The network name, from the mDNS sighting (the one with a hardware id) when there is one — its
   * {@code fname} is the device's own name — rather than the host name the legacy detector reports.
   */
  private static String mergedName(DiscoveredDevice a, DiscoveredDevice b) {
    if (a.hwid() != null) {
      return a.name();
    }
    if (b.hwid() != null) {
      return b.name();
    }
    return a.name() != null ? a.name() : b.name();
  }
}
