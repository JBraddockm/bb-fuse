package com.jbraddockm.link.endpoint.metadata;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.json.Json;
import java.nio.file.Path;
import java.util.Map;

/**
 * A media thumbnail streamed from the device to a file. The image bytes live on disk, not here.
 *
 * @param httpStatus the HTTP status code the device answered with
 * @param contentType the {@code Content-Type} of the image, or {@code null} if the device gave none
 * @param file the file the image was written to
 * @param bytes the number of image bytes written
 */
public record DeviceThumbnail(int httpStatus, String contentType, Path file, long bytes)
    implements CommandResult {

  @Override
  public Map<String, Object> toJson() {
    return Json.object(
        "httpStatus",
        httpStatus,
        "contentType",
        contentType,
        "file",
        file.toString(),
        "bytes",
        bytes);
  }
}
