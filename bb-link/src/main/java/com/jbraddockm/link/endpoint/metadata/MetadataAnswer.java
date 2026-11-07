package com.jbraddockm.link.endpoint.metadata;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.transport.cgi.ResponseBody;
import java.util.Map;

/**
 * What {@code metadata.cgi} answered for a media query.
 *
 * <p>The {@code rows} are exactly what the device sent inside the {@code Metadata} envelope — the
 * CDATA JSON parsed into a tree, with no field renamed or dropped. The per-type row shape is the
 * client's to interpret; bb-link only strips the XML envelope and parses the JSON. If the content
 * is not the JSON it is expected to be, it is kept as text and {@code bodyFormat} says so.
 *
 * @param type the media type that was queried
 * @param source the storage area that was queried
 * @param body the device's rows, in the best form they could be read
 */
public record MetadataAnswer(MediaType type, MediaSource source, ResponseBody body)
    implements CommandResult {

  @Override
  public Map<String, Object> toJson() {
    return Json.object(
        "type",
        type.wireName(),
        "source",
        source.wireName(),
        "bodyFormat",
        body.format().wireName(),
        "rows",
        body.value());
  }
}
