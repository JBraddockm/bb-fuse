package com.jbraddockm.core.discovery;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Finds a device over the USB link. A paired-or-not BlackBerry 10 device attached by USB raises a
 * host network interface on the IPv4 link-local block ({@code 169.254.0.0/16}); the device sits one
 * address below the host's (host {@code …0.2} ⇒ device {@code …0.1}). This reads the host's
 * link-local addresses, works out each device candidate, and identifies it over {@link
 * DiscoveryClient}. An address that does not answer as a device is skipped.
 *
 * <p>The mechanism is portable JDK — {@link NetworkInterface} — so it serves every desktop OS the
 * same way. Enumerating the interfaces and the identity read are seams, so a test drives the scan
 * without a USB device.
 */
public final class UsbScanner implements DeviceScanner {

  private static final int LINK_LOCAL_PREFIX = 0xA9FE0000; // 169.254.0.0
  private static final int SIXTEEN_BIT_MASK = 0xFFFF0000;

  private final HostAddresses hosts;
  private final DiscoveryClient client;

  /** Creates a scanner that reads the real network interfaces and talks to a real device. */
  public UsbScanner() {
    this(UsbScanner::systemLinkLocalHosts, new DiscoveryClient());
  }

  /**
   * Creates a scanner over the given seams.
   *
   * @param hosts supplies the host's link-local addresses
   * @param client identifies a candidate address
   */
  UsbScanner(HostAddresses hosts, DiscoveryClient client) {
    this.hosts = hosts;
    this.client = client;
  }

  @Override
  public void scan(Consumer<DiscoveredDevice> found) {
    List<String> hostAddresses;
    try {
      hostAddresses = hosts.linkLocal();
    } catch (SocketException _) {
      return; // the interfaces could not be read, so there is nothing to scan
    }
    for (var host : hostAddresses) {
      deviceAddressFor(host)
          .flatMap(address -> client.read(address).map(identity -> identified(identity, address)))
          .ifPresent(found);
    }
  }

  private static DiscoveredDevice identified(DeviceCharacteristics identity, String address) {
    return DiscoveredDevice.identified(identity, ConnectionKind.USB, address);
  }

  /**
   * The device address for a host's link-local address: the host's, less one. It is empty when the
   * result would leave the {@code 169.254.0.0/16} block, so a host at {@code 169.254.0.0} yields no
   * candidate.
   *
   * @param hostAddress the host interface's IPv4 link-local address
   * @return the device candidate address, or empty when there is none
   */
  static Optional<String> deviceAddressFor(String hostAddress) {
    int host;
    try {
      host = toInt(((Inet4Address) InetAddress.getByName(hostAddress)).getAddress());
    } catch (UnknownHostException | ClassCastException _) {
      return Optional.empty();
    }
    int device = host - 1;
    if ((device & SIXTEEN_BIT_MASK) != LINK_LOCAL_PREFIX) {
      return Optional.empty();
    }
    return Optional.of(toAddress(device));
  }

  private static List<String> systemLinkLocalHosts() throws SocketException {
    var found = new ArrayList<String>();
    var interfaces = NetworkInterface.getNetworkInterfaces();
    while (interfaces.hasMoreElements()) {
      var networkInterface = interfaces.nextElement();
      if (!networkInterface.isUp() || networkInterface.isLoopback()) {
        continue;
      }
      for (var binding : networkInterface.getInterfaceAddresses()) {
        if (binding.getAddress() instanceof Inet4Address ip
            && (toInt(ip.getAddress()) & SIXTEEN_BIT_MASK) == LINK_LOCAL_PREFIX) {
          found.add(ip.getHostAddress());
        }
      }
    }
    return found;
  }

  private static int toInt(byte[] octets) {
    return ((octets[0] & 0xFF) << 24)
        | ((octets[1] & 0xFF) << 16)
        | ((octets[2] & 0xFF) << 8)
        | (octets[3] & 0xFF);
  }

  private static String toAddress(int value) {
    return (value >>> 24)
        + "."
        + ((value >>> 16) & 0xFF)
        + "."
        + ((value >>> 8) & 0xFF)
        + "."
        + (value & 0xFF);
  }

  /** Supplies the host's IPv4 link-local addresses; a seam, so a test needs no USB device. */
  @FunctionalInterface
  interface HostAddresses {

    /**
     * The host's IPv4 link-local addresses.
     *
     * @return the addresses, one per matching interface binding
     * @throws SocketException if the interfaces could not be read
     */
    List<String> linkLocal() throws SocketException;
  }
}
