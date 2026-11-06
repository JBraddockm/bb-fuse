package com.jbraddockm.core.discovery;

import com.jbraddockm.core.mdns.MdnsService;
import java.util.List;
import java.util.function.Consumer;

/**
 * Finds devices over mDNS. A BlackBerry 10 device with Wi-Fi access on advertises a bp2p service —
 * over Wi-Fi, and over the USB link when attached; its {@code Friendly} instance carries the
 * device's network name, OS and hardware id, and the advert resolves to the device's address(es).
 * This reports a sighting per address, so a device reachable on both the USB link and Wi-Fi is
 * reported on each.
 *
 * <p>A sighting carries no PIN — the device does not serve {@code discovery.cgi} over the plain
 * Wi-Fi LAN, so the PIN is read at connect, over the tunnel. Over the USB link the sighting merges
 * with the USB scan's identified entry at the same address (see {@link DeviceDiscovery}), so a USB
 * device still gets its PIN and model alongside the name/ula/hwid from mDNS. The browse is a seam,
 * so a test needs no mDNS.
 */
public final class MdnsScanner implements DeviceScanner {

  private final Browse browse;
  private final DevicePinResolver pins;

  /** Creates a scanner that browses the shared mDNS facility and resolves no PIN. */
  public MdnsScanner() {
    this(new MdnsBrowse(MdnsService.shared()), DevicePinResolver.NONE);
  }

  /**
   * Creates a scanner that browses the shared mDNS facility and resolves PINs with {@code pins}.
   */
  public MdnsScanner(DevicePinResolver pins) {
    this(new MdnsBrowse(MdnsService.shared()), pins);
  }

  /**
   * Creates a scanner over the given browse seam that resolves no PIN.
   *
   * @param browse supplies the advertised devices
   */
  MdnsScanner(Browse browse) {
    this(browse, DevicePinResolver.NONE);
  }

  /**
   * Creates a scanner over the given browse seam and PIN resolver.
   *
   * @param browse supplies the advertised devices
   * @param pins recovers a paired device's PIN from its advert, locally (see {@link
   *     DevicePinResolver})
   */
  MdnsScanner(Browse browse, DevicePinResolver pins) {
    this.browse = browse;
    this.pins = pins;
  }

  @Override
  public void scan(Consumer<DiscoveredDevice> found) {
    for (var advert : browse.resolve()) {
      var pin = pins.resolve(advert.instanceName()).orElse(null);
      found.accept(
          DiscoveredDevice.fromMdns(
              pin,
              advert.name(),
              advert.version(),
              advert.connection(),
              advert.address(),
              advert.ula(),
              advert.hwid()));
    }
  }

  /**
   * A device at one address, as mDNS advertised it.
   *
   * @param name the device's network name (the bp2p {@code fname})
   * @param version the OS version (the bp2p {@code os}), or {@code null}
   * @param connection how it is reached at this address: USB on the link-local link, else Wi-Fi
   * @param address the address
   * @param ula the device's tunnel ULA (IPv6), or {@code null}
   * @param hwid the device's hardware id (the bp2p {@code hwid}), or {@code null}
   * @param instanceName the bp2p {@code Friendly} service instance name as advertised, from which a
   *     {@link DevicePinResolver} can recover the PIN, or {@code null}
   */
  record Advert(
      String name,
      String version,
      ConnectionKind connection,
      String address,
      String ula,
      String hwid,
      String instanceName) {}

  /** Browses mDNS for bp2p devices; a seam, so a test needs no mDNS. */
  @FunctionalInterface
  interface Browse {

    /**
     * Browses for the advertised devices.
     *
     * @return one advert per device address found
     */
    List<Advert> resolve();
  }
}
