package com.jbraddockm.core.discovery;

import com.jbraddockm.core.mdns.MdnsService;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.List;
import javax.jmdns.ServiceInfo;

/**
 * Browses the shared mDNS facility for bp2p devices and resolves each to its address(es).
 *
 * <p>A device advertises several bp2p instances; the <b>Friendly</b> one carries its network name,
 * OS and hardware id in the TXT ({@code fname}, {@code os}, {@code hwid}). The bp2p SRV target is
 * the device's tunnel ULA (IPv6, taken from the service info), so the usable address comes from the
 * device's own hostname — {@code <fname>.local} — which resolves to each IPv4 the device is
 * reachable at: its Wi-Fi LAN address, and its USB link-local address when attached. Each is
 * reported as a sighting, classified by the address — a link-local ({@code 169.254.x}) one as USB,
 * the rest as Wi-Fi. mDNS is a listen-for-a-while source, so the browse resolves for a short window
 * and returns what it found.
 */
final class MdnsBrowse implements MdnsScanner.Browse {

  private static final String SERVICE_TYPE = "_bp2p._tcp.local.";
  private static final String FRIENDLY_NAME = "fname";
  private static final String OS_VERSION = "os";
  private static final String HARDWARE_ID = "hwid";
  private static final long WINDOW_MS = 2500;

  private final MdnsService mdns;
  private final long windowMs;

  MdnsBrowse(MdnsService mdns) {
    this(mdns, WINDOW_MS);
  }

  MdnsBrowse(MdnsService mdns, long windowMs) {
    this.mdns = mdns;
    this.windowMs = windowMs;
  }

  @Override
  public List<MdnsScanner.Advert> resolve() {
    var byAddress = new LinkedHashMap<String, MdnsScanner.Advert>();
    try {
      for (var instance : mdns.list(SERVICE_TYPE, windowMs)) {
        var name = instance.getPropertyString(FRIENDLY_NAME);
        if (name == null || name.isBlank()) {
          continue;
        }
        var version = instance.getPropertyString(OS_VERSION);
        var ula = ulaOf(instance);
        var hwid = instance.getPropertyString(HARDWARE_ID);
        // The Friendly instance name (Friendly_<seed>_<deviceHash>) folds the device's PIN, so a
        // DevicePinResolver can recover it locally from this alone.
        var instanceName = instance.getName();
        for (var ipv4 : addressesOf(name)) {
          var connection = ipv4.isLinkLocalAddress() ? ConnectionKind.USB : ConnectionKind.WIFI;
          var address = ipv4.getHostAddress();
          byAddress.putIfAbsent(
              address,
              new MdnsScanner.Advert(name, version, connection, address, ula, hwid, instanceName));
        }
      }
    } catch (IOException _) {
      // mDNS could not start, so there are no devices to report this scan.
    }
    return List.copyOf(byAddress.values());
  }

  private static List<Inet4Address> addressesOf(String friendlyName) {
    var addresses = new java.util.ArrayList<Inet4Address>();
    try {
      for (var address : InetAddress.getAllByName(friendlyName + ".local")) {
        if (address instanceof Inet4Address ipv4) {
          addresses.add(ipv4);
        }
      }
    } catch (UnknownHostException _) {
      // The device's hostname did not resolve; another scanner may still find it.
    }
    return addresses;
  }

  /**
   * The device's tunnel ULA from its bp2p advert: the unique-local IPv6 address ({@code fc00::/7})
   * the SRV target resolves to, or {@code null} when it did not advertise one.
   */
  private static String ulaOf(ServiceInfo instance) {
    for (var address : instance.getInet6Addresses()) {
      byte[] octets = address.getAddress();
      if ((octets[0] & 0xFE) == 0xFC) {
        return address.getHostAddress();
      }
    }
    return null;
  }
}
