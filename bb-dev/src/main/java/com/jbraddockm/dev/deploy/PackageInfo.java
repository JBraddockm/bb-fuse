package com.jbraddockm.dev.deploy;

import com.jbraddockm.core.json.Json;
import java.util.List;
import java.util.Map;

/**
 * The application identity the device confirms, from its {@code actual_dname}/{@code actual_id}/
 * {@code actual_version} lines: the full directory name, the package id and the version. {@code
 * Verify}, {@code Install} and {@code Uninstall} all return these fields.
 */
record PackageInfo(String name, String id, String version) {

  /**
   * Parses the identity lines a {@code Verify}, {@code Install} or {@code Uninstall} command
   * returns.
   *
   * @param lines the device's reply lines
   * @return the confirmed package identity, each field {@code null} when the device did not report
   *     it
   */
  static PackageInfo parse(List<String> lines) {
    return new PackageInfo(
        value(lines, "actual_dname"), value(lines, "actual_id"), value(lines, "actual_version"));
  }

  private static String value(List<String> lines, String key) {
    var prefix = key + "::";
    return lines.stream()
        .filter(line -> line.startsWith(prefix))
        .map(line -> line.substring(prefix.length()))
        .findFirst()
        .orElse(null);
  }

  /**
   * This result as a JSON-friendly object.
   *
   * @return the identity as a JSON object
   */
  Map<String, Object> json() {
    return Json.object("name", name, "id", id, "version", version);
  }
}
