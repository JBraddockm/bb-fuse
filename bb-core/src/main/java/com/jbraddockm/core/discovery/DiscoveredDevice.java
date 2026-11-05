package com.jbraddockm.core.discovery;

import com.jbraddockm.core.json.Json;
import java.util.Map;

/**
 * A device found by a scan.
 *
 * <p>A device the scanner could identify over {@code discovery.cgi} carries its {@link #pin} and
 * {@link #guid}; the legacy detector finds a device without them, so both may be {@code null}. The
 * {@code guid} is the stable key a scan dedups on.
 *
 * @param pin the device PIN as 8-digit uppercase hexadecimal, or {@code null} when the scanner
 *     could not identify the device
 * @param guid the base64 device GUID, or {@code null} when unidentified
 * @param name a name to show: the model name when identified, otherwise the host name the detector
 *     reported
 * @param version the operating-system version, or {@code null} if unknown
 * @param connection how the device was reached
 * @param address the address it was reached at
 */
public record DiscoveredDevice(
    String pin,
    String guid,
    String name,
    String version,
    ConnectionKind connection,
    String address) {

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
        address);
  }

  /**
   * A device found without an identity (the legacy detector).
   *
   * @param name the host name the detector reported
   * @param version the OS version, or {@code null}
   * @param connection how it was reached
   * @param address where it was reached
   * @return the device, with no PIN or GUID
   */
  public static DiscoveredDevice anonymous(
      String name, String version, ConnectionKind connection, String address) {
    return new DiscoveredDevice(null, null, name, version, connection, address);
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
        "address", address);
  }
}
