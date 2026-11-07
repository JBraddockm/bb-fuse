package com.jbraddockm.link.endpoint.files;

import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.transport.smb.SmbEntry;
import java.util.Map;

/** Renders a {@link SmbEntry} as the JSON a client sees, keyed by its device-absolute path. */
final class FileEntries {

  private FileEntries() {}

  /**
   * Describes one entry. A directory omits its size.
   *
   * @param devicePath the entry's device-absolute path
   * @param entry the entry's metadata
   * @return an insertion-ordered map of name, path, type, size (files only) and last-modified time
   */
  static Map<String, Object> toJson(String devicePath, SmbEntry entry) {
    return Json.object(
        "name",
        entry.name(),
        "path",
        devicePath,
        "type",
        entry.directory() ? "directory" : "file",
        "size",
        entry.directory() ? null : entry.size(),
        "modifiedAt",
        entry.modifiedAt().toString());
  }
}
