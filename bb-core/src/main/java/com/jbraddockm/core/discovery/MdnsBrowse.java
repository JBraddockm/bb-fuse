package com.jbraddockm.core.discovery;

import com.jbraddockm.core.mdns.MdnsService;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import javax.jmdns.ServiceInfo;

/**
 * Browses the shared mDNS facility for bp2p devices and resolves each to its LAN IPv4 address.
 *
 * <p>A device advertises several bp2p instances; the <b>Friendly</b> one carries the device's
 * network name and OS in its TXT ({@code fname}, {@code os}). The bp2p SRV target is the device's
 * tunnel ULA (no dependable IPv4), so the LAN address comes from the device's own hostname — {@code
 * <fname>.local}, whose A record is its Wi-Fi IPv4. A USB-attached device's hostname resolves to
 * its link-local address, which the USB scan already covers, so a link-local result is skipped.
 * mDNS is a listen-for-a-while source, so the browse resolves for a short window and returns what
 * it found.
 */
final class MdnsBrowse implements WifiScanner.Browse {

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
  public List<WifiScanner.Advert> resolve() {
    var byAddress = new LinkedHashMap<String, WifiScanner.Advert>();
    try {
      for (var instance : mdns.list(SERVICE_TYPE, windowMs)) {
        var name = instance.getPropertyString(FRIENDLY_NAME);
        if (name == null || name.isBlank()) {
          continue;
        }
        var version = instance.getPropertyString(OS_VERSION);
        var ula = ulaOf(instance);
        var hwid = instance.getPropertyString(HARDWARE_ID);
        lanAddressOf(name)
            .ifPresent(
                address ->
                    byAddress.putIfAbsent(
                        address, new WifiScanner.Advert(name, version, address, ula, hwid)));
      }
    } catch (IOException _) {
      // mDNS could not start, so there are no Wi-Fi devices to report this scan.
    }
    return List.copyOf(byAddress.values());
  }

  private static Optional<String> lanAddressOf(String friendlyName) {
    try {
      for (var address : InetAddress.getAllByName(friendlyName + ".local")) {
        if (address instanceof Inet4Address ipv4 && !ipv4.isLinkLocalAddress()) {
          return Optional.of(ipv4.getHostAddress());
        }
      }
    } catch (UnknownHostException _) {
      // The device's hostname did not resolve; another scanner may still find it.
    }
    return Optional.empty();
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
