package com.jbraddockm.link.endpoint.support;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.json.Json;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * The diagnostic-log archive that the DumpLog conversation streamed from the device: the file it
 * was written to and the number of bytes written. The archive is the device's {@code
 * cso-logs-report.zip} (a {@code bbswltLog} bundle), stored verbatim.
 *
 * @param file the file the archive was written to
 * @param bytes the number of bytes written
 */
public record DeviceLogArchive(Path file, long bytes) implements CommandResult {

  /** Validates the result. */
  public DeviceLogArchive {
    Objects.requireNonNull(file, "file");
    if (bytes < 0) {
      throw new IllegalArgumentException("a log archive cannot have a negative size");
    }
  }

  @Override
  public Map<String, Object> toJson() {
    return Json.object("file", file.toString(), "bytes", bytes);
  }
}
