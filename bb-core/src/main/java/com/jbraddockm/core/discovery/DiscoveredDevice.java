package com.jbraddockm.core.discovery;

import com.jbraddockm.core.json.Json;
import java.util.Map;

/**
 * A device found by a scan.
 *
 * <p>A device the scanner could identify over {@code discovery.cgi} carries its {@link #pin} and
 * {@link #guid}; a device found over Wi-Fi mDNS or by the legacy detector has neither, so both may
 * be {@code null}. The {@code guid} is the stable key a scan dedups on. A Wi-Fi device also carries
 * what its mDNS advert gives: its {@link #ula} — the tunnel address it currently advertises, for an
 * immediate connect — and its {@link #hwid}.
 *
 * @param pin the device PIN as 8-digit uppercase hexadecimal, or {@code null} when the scanner
 *     could not identify the device
 * @param guid the base64 device GUID, or {@code null} when unidentified
 * @param name a name to show: the model name when identified, otherwise the network or host name
 * @param version the operating-system version, or {@code null} if unknown
 * @param connection how the device was reached
 * @param address the address it was reached at
 * @param ula the tunnel address (IPv6 ULA) the device currently advertises over mDNS — it rotates,
 *     so it is the current address for a connect, not a stable key — or {@code null} when not
 *     advertised
 * @param hwid the device's hardware id (for example {@code 0x87002c0a}), from its mDNS advert, or
 *     {@code null}
 */
public record DiscoveredDevice(
    String pin,
    String guid,
    String name,
    String version,
    ConnectionKind connection,
    String address,
    String ula,
    String hwid) {

  /**
   * A device identified over {@code discovery.cgi}, named by its model.
   *
   * @param identity what {@code discovery.cgi} reported
   * @param connection how it was reached
   * @param address where it was reached
   * @return the device
   */
  public static DiscoveredDevice identified(
      DeviceCharacteristics identity, ConnectionKind connection, String address) {
    return new DiscoveredDevice(
        identity.pin(),
        identity.guid(),
        identity.modelName(),
        identity.platformVersion(),
        connection,
        address,
        null,
        null);
  }

  /**
   * A device advertising over Wi-Fi mDNS: its network name, OS, tunnel ULA and hardware id, but no
   * PIN (the device does not serve {@code discovery.cgi} over the plain Wi-Fi LAN).
   *
   * @param name the device's network name
   * @param version the OS version, or {@code null}
   * @param address the device's LAN IPv4 address
   * @param ula the device's tunnel ULA, or {@code null}
   * @param hwid the device's hardware id, or {@code null}
   * @return the device, with no PIN or GUID
   */
  public static DiscoveredDevice overWifi(
      String name, String version, String address, String ula, String hwid) {
    return new DiscoveredDevice(null, null, name, version, ConnectionKind.WIFI, address, ula, hwid);
  }

  /**
   * A device found without an identity (the legacy detector).
   *
   * @param name the host name the detector reported
   * @param version the OS version, or {@code null}
   * @param connection how it was reached
   * @param address where it was reached
   * @return the device, with no PIN, GUID, ULA or hardware id
   */
  public static DiscoveredDevice anonymous(
      String name, String version, ConnectionKind connection, String address) {
    return new DiscoveredDevice(null, null, name, version, connection, address, null, null);
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
        "name", name,
        "version", version,
        "connection", connection.wireName(),
        "address", address,
        "ula", ula,
        "hwid", hwid);
  }
}
