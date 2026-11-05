package com.jbraddockm.core.discovery;

import com.jbraddockm.core.json.Json;
import java.util.Map;

/**
 * A device found by a scan — only what a client needs to pick and connect to it, not the device's
 * full characteristics (those are read at connect).
 *
 * <p>Two sources feed it, and a device may be seen by both at one address, so they merge (see
 * {@link DeviceDiscovery}): {@code discovery.cgi}, reachable over USB (and the tunnel), gives the
 * {@link #pin}, {@link #guid} and {@link #model}; the device's mDNS advert gives its {@link #name},
 * {@link #ula} and {@link #hwid}. A field a source did not supply is {@code null} — a Wi-Fi device
 * has no PIN (the device does not serve {@code discovery.cgi} over the plain Wi-Fi LAN; the PIN is
 * read at connect, over the tunnel), and a device whose mDNS advert was not seen has no
 * name/ula/hwid.
 *
 * @param pin the device PIN as 8-digit uppercase hexadecimal, or {@code null}
 * @param guid the base64 device GUID, or {@code null}
 * @param model the model name (for example {@code STR100-1}), from {@code discovery.cgi}, or {@code
 *     null}
 * @param name the device's network name (for example {@code BLACKBERRY-3247}), from its mDNS advert
 *     or the legacy detector, or {@code null}
 * @param version the operating-system version, or {@code null} if unknown
 * @param connection how the device was reached
 * @param address the address it was reached at
 * @param ula the tunnel address (IPv6 ULA) the device currently advertises over mDNS — it rotates,
 *     so it is the current address for a connect, not a stable key — or {@code null}
 * @param hwid the device's hardware id (for example {@code 0x87002c0a}), from its mDNS advert, or
 *     {@code null}
 */
public record DiscoveredDevice(
    String pin,
    String guid,
    String model,
    String name,
    String version,
    ConnectionKind connection,
    String address,
    String ula,
    String hwid) {

  /**
   * A device identified over {@code discovery.cgi}: its PIN, GUID and model.
   *
   * @param identity what {@code discovery.cgi} reported
   * @param connection how it was reached
   * @param address where it was reached
   * @return the device, without the mDNS fields (name/ula/hwid) a scan may later merge in
   */
  public static DiscoveredDevice identified(
      DeviceCharacteristics identity, ConnectionKind connection, String address) {
    return new DiscoveredDevice(
        identity.pin(),
        identity.guid(),
        identity.modelName(),
        null,
        identity.platformVersion(),
        connection,
        address,
        null,
        null);
  }

  /**
   * A device as its mDNS advert describes it: its network name, OS, tunnel ULA and hardware id, but
   * no PIN (the device does not serve {@code discovery.cgi} over the plain Wi-Fi LAN).
   *
   * @param name the device's network name
   * @param version the OS version, or {@code null}
   * @param connection how it was reached (USB when the advert was seen on the USB link, else Wi-Fi)
   * @param address the address it was reached at
   * @param ula the device's tunnel ULA, or {@code null}
   * @param hwid the device's hardware id, or {@code null}
   * @return the device, with no PIN, GUID or model
   */
  public static DiscoveredDevice fromMdns(
      String name,
      String version,
      ConnectionKind connection,
      String address,
      String ula,
      String hwid) {
    return new DiscoveredDevice(null, null, null, name, version, connection, address, ula, hwid);
  }

  /**
   * A device found without an identity (the legacy detector): just the host name it reported.
   *
   * @param name the host name the detector reported
   * @param version the OS version, or {@code null}
   * @param connection how it was reached
   * @param address where it was reached
   * @return the device, with only its name, version and address
   */
  public static DiscoveredDevice anonymous(
      String name, String version, ConnectionKind connection, String address) {
    return new DiscoveredDevice(null, null, null, name, version, connection, address, null, null);
  }

  /**
   * Returns the wire representation.
   *
   * @return an insertion-ordered map; an unknown field is {@code null}
   */
  public Map<String, Object> toJson() {
    return Json.object(
        "pin", pin,
        "guid", guid,
        "model", model,
        "name", name,
        "version", version,
        "connection", connection.wireName(),
        "address", address,
        "ula", ula,
        "hwid", hwid);
  }
}
