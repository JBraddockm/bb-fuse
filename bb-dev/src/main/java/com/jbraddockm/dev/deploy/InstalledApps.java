package com.jbraddockm.dev.deploy;

import com.jbraddockm.core.json.Json;
import java.util.ArrayList;
import java.util.List;

/**
 * The applications installed on the device, parsed from the {@code appInstaller.cgi} {@code List}
 * response. Each data line is {@code name::id,version,,flag,size,source}; the lines before the
 * {@code @applications} marker and any {@code Info:}/{@code result::} lines are not applications.
 */
record InstalledApps(List<App> apps) {

  InstalledApps {
    apps = List.copyOf(apps);
  }

  /**
   * One installed application.
   *
   * @param name the package name (the key before {@code ::})
   * @param id the package id
   * @param version the version
   * @param size the installed size in bytes, or {@code 0} when the device gave none
   * @param source where it was installed from (for example {@code websl} or {@code apk}), or empty
   */
  record App(String name, String id, String version, long size, String source) {}

  /**
   * Parses the response lines of a {@code List} command.
   *
   * @param lines the device's reply lines
   * @return the installed applications, in the order the device listed them
   */
  static InstalledApps parse(List<String> lines) {
    var apps = new ArrayList<App>();
    for (var line : lines) {
      if (line.startsWith("Info:") || line.startsWith("@") || line.startsWith("result::")) {
        continue;
      }
      var separator = line.indexOf("::");
      if (separator < 0) {
        continue;
      }
      var name = line.substring(0, separator);
      var fields = line.substring(separator + 2).split(",", -1);
      apps.add(
          new App(
              name,
              field(fields, 0),
              field(fields, 1),
              size(field(fields, 4)),
              source(field(fields, 5))));
    }
    return new InstalledApps(apps);
  }

  private static String field(String[] fields, int index) {
    return index < fields.length ? fields[index] : "";
  }

  private static long size(String value) {
    try {
      return value.isBlank() ? 0 : Long.parseLong(value.trim());
    } catch (NumberFormatException notANumber) {
      return 0;
    }
  }

  private static String source(String value) {
    return value.startsWith("source::") ? value.substring("source::".length()) : value;
  }

  /**
   * This result as a JSON-friendly structure: one object per application.
   *
   * @return the applications as a list of JSON objects
   */
  List<Object> json() {
    return apps.stream()
        .map(
            app ->
                (Object)
                    Json.object(
                        "name", app.name(),
                        "id", app.id(),
                        "version", app.version(),
                        "size", app.size(),
                        "source", app.source()))
        .toList();
  }
}
