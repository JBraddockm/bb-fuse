package com.jbraddockm.core.discovery;

import com.jbraddockm.core.mdns.MdnsService;
import java.util.List;
import java.util.function.Consumer;

/**
 * Finds a device over Wi-Fi. A BlackBerry 10 device with Wi-Fi access enabled advertises a bp2p
 * service over mDNS ({@code _bp2p._tcp}); its {@code Friendly} instance carries the device's
 * network name and OS, and the device's hostname resolves to its LAN address. This reports each
 * such device.
 *
 * <p>A Wi-Fi device is <b>not</b> identified by its PIN: the device does not serve {@code
 * discovery.cgi} over the plain Wi-Fi LAN (only over USB and the tunnel), so the PIN and GUID are
 * learned later, at connect, over the tunnel. What Wi-Fi discovery gives — fast and without a
 * subnet sweep — is the device's presence, name, OS and address. The browse is a seam, so a test
 * needs no mDNS.
 */
public final class WifiScanner implements DeviceScanner {

  private final Browse browse;

  /** Creates a scanner that browses the shared mDNS facility. */
  public WifiScanner() {
    this(new MdnsBrowse(MdnsService.shared()));
  }

  /**
   * Creates a scanner over the given browse seam.
   *
   * @param browse supplies the advertised devices
   */
  WifiScanner(Browse browse) {
    this.browse = browse;
  }

  @Override
  public void scan(Consumer<DiscoveredDevice> found) {
    for (var advert : browse.resolve()) {
      found.accept(
          DiscoveredDevice.overWifi(
              advert.name(), advert.version(), advert.address(), advert.ula(), advert.hwid()));
    }
  }

  /**
   * A device as mDNS advertised it.
   *
   * @param name the device's network name (the bp2p {@code fname})
   * @param version the OS version (the bp2p {@code os}), or {@code null} if absent
   * @param address the device's LAN IPv4 address
   * @param ula the device's tunnel ULA (IPv6), or {@code null} if absent
   * @param hwid the device's hardware id (the bp2p {@code hwid}), or {@code null} if absent
   */
  record Advert(String name, String version, String address, String ula, String hwid) {}

  /** Browses mDNS for bp2p devices; a seam, so a test needs no mDNS. */
  @FunctionalInterface
  interface Browse {

    /**
     * Browses for the advertised devices.
     *
     * @return one advert per device found, resolved to its LAN address
     */
    List<Advert> resolve();
  }
}
