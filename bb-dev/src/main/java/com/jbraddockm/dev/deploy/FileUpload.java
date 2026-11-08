package com.jbraddockm.dev.deploy;

import com.jbraddockm.core.json.Json;
import java.util.List;
import java.util.Map;

/**
 * The outcome of a {@code Put File} command: the number of bytes the device accepted, from its
 * {@code Info: File size: N} line. A failed upload raises a {@link DeviceReportException} instead
 * of reaching this result.
 */
record FileUpload(long size) {

  private static final String MARKER = "Info: File size: ";

  /**
   * Parses the response lines of a {@code Put File} command.
   *
   * @param lines the device's reply lines
   * @return the upload outcome; size {@code 0} when the device reported none
   */
  static FileUpload parse(List<String> lines) {
    return lines.stream()
        .filter(line -> line.startsWith(MARKER))
        .map(line -> new FileUpload(Long.parseLong(line.substring(MARKER.length()).trim())))
        .findFirst()
        .orElse(new FileUpload(0));
  }

  /**
   * This result as a JSON-friendly object.
   *
   * @return the upload as a JSON object
   */
  Map<String, Object> json() {
    return Json.object("size", size);
  }
}
