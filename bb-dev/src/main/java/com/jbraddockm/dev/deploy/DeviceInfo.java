package com.jbraddockm.dev.deploy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The device's properties, parsed from the {@code appInstaller.cgi} {@code List Device Info}
 * response into a flat map. The device groups properties under {@code @section} markers and tags
 * some values with a type ({@code key:b:value} for a boolean, {@code key:n:value} for a number);
 * the markers and the type tags are dropped and every value is kept as text. A later section that
 * repeats a key overwrites the earlier value.
 */
record DeviceInfo(Map<String, String> properties) {

  DeviceInfo {
    properties = Map.copyOf(properties);
  }

  /**
   * Parses the response lines of a {@code List Device Info} command.
   *
   * @param lines the device's reply lines
   * @return the device properties
   */
  static DeviceInfo parse(List<String> lines) {
    var map = new LinkedHashMap<String, String>();
    for (var raw : lines) {
      var line = raw.startsWith("[n]") ? raw.substring(3) : raw;
      if (line.isBlank() || line.startsWith("Info:") || line.startsWith("@")) {
        continue;
      }
      var untyped = line.indexOf("::");
      if (untyped >= 0) {
        map.put(line.substring(0, untyped), line.substring(untyped + 2).strip());
        continue;
      }
      var colon = line.indexOf(':');
      if (colon < 0) {
        continue;
      }
      var rest = line.substring(colon + 1);
      var value = rest.startsWith("b:") || rest.startsWith("n:") ? rest.substring(2) : rest.strip();
      map.put(line.substring(0, colon), value);
    }
    return new DeviceInfo(map);
  }

  /**
   * The value of a property.
   *
   * @param key the property name
   * @return the value, or {@code null} when the device did not report it
   */
  String get(String key) {
    return properties.get(key);
  }

  /**
   * This result as a JSON-friendly structure: the property map.
   *
   * @return the properties
   */
  Map<String, String> json() {
    return properties;
  }
}
