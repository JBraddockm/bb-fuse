package com.jbraddockm.link.endpoint.reset;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.json.Json;
import java.util.Locale;
import java.util.Map;

/**
 * The outcome of a reset the device accepted: the mode that was requested. The device answers
 * before it goes down, so this result means "accepted and about to happen", not "already down".
 *
 * @param mode the kind of reset that was requested
 */
public record ResetResult(ResetMode mode) implements CommandResult {

  @Override
  public Map<String, Object> toJson() {
    return Json.object("action", mode.name().toLowerCase(Locale.ROOT));
  }
}
