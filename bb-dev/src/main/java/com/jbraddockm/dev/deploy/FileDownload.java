package com.jbraddockm.dev.deploy;

import com.jbraddockm.core.json.Json;
import java.util.List;
import java.util.Map;

/**
 * A file downloaded from an application's sandbox by a {@code Get File} command. The device
 * announces the byte count with an {@code Info: Sending File: N} line and then streams the content,
 * which the engine reads as text lines. A binary file must be saved straight to a path instead;
 * this result is for the text case.
 */
record FileDownload(long size, String content) {

  private static final String MARKER = "Info: Sending File: ";

  /**
   * Parses the response lines of a {@code Get File} command.
   *
   * @param lines the device's reply lines
   * @return the downloaded file; size {@code 0} and empty content when the device sent no file
   */
  static FileDownload parse(List<String> lines) {
    for (var i = 0; i < lines.size(); i++) {
      if (lines.get(i).startsWith(MARKER)) {
        var size = Long.parseLong(lines.get(i).substring(MARKER.length()).trim());
        return new FileDownload(size, String.join("\n", lines.subList(i + 1, lines.size())));
      }
    }
    return new FileDownload(0, "");
  }

  /**
   * This result as a JSON-friendly object.
   *
   * @return the download as a JSON object
   */
  Map<String, Object> json() {
    return Json.object("size", size, "content", content);
  }
}
