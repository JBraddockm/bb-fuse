package com.jbraddockm.dev.deploy;

import com.jbraddockm.core.json.Json;
import java.util.List;
import java.util.Map;

/**
 * The outcome of a {@code Launch} command: the process id the device reports for the started
 * application, from its {@code result::<pid>} line.
 */
record LaunchResult(long pid) {

  private static final String PREFIX = "result::";

  /**
   * Parses the response lines of a {@code Launch} command.
   *
   * @param lines the device's reply lines
   * @return the launch outcome; pid {@code 0} when the device reported no numeric id
   */
  static LaunchResult parse(List<String> lines) {
    return lines.stream()
        .filter(line -> line.startsWith(PREFIX))
        .map(line -> line.substring(PREFIX.length()))
        .filter(value -> !value.isEmpty() && value.chars().allMatch(Character::isDigit))
        .map(value -> new LaunchResult(Long.parseLong(value)))
        .findFirst()
        .orElse(new LaunchResult(0));
  }

  /**
   * This result as a JSON-friendly object.
   *
   * @return the launch outcome as a JSON object
   */
  Map<String, Object> json() {
    return Json.object("pid", pid);
  }
}
