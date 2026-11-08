package com.jbraddockm.dev.deploy;

import com.jbraddockm.core.json.Json;
import java.util.List;
import java.util.Map;

/**
 * Whether an application is running, parsed from the {@code result::true}/{@code result::false}
 * line of an {@code Is Running} command. The device answers {@code result::false} for an
 * application that is not running or not installed.
 */
record AppRunState(boolean running) {

  /**
   * Parses the response lines of an {@code Is Running} command.
   *
   * @param lines the device's reply lines
   * @return the run state; not running unless the device reported {@code result::true}
   */
  static AppRunState parse(List<String> lines) {
    return new AppRunState(lines.contains("result::true"));
  }

  /**
   * This result as a JSON-friendly object.
   *
   * @return the run state as a JSON object
   */
  Map<String, Object> json() {
    return Json.object("running", running);
  }
}
