package com.jbraddockm.link.endpoint.files;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.transport.smb.DeviceShares;
import com.jbraddockm.link.transport.smb.SmbLocation;
import java.util.Map;

/**
 * The outcome of a change to the device's files: a delete, a directory creation or a move.
 *
 * @param action what was done, for example {@code deleted}, {@code created} or {@code moved}
 * @param path the path that was acted on
 * @param target the path it was moved to, or {@code null} for an action with no second path
 */
public record FileChange(String action, SmbLocation path, SmbLocation target)
    implements CommandResult {

  @Override
  public Map<String, Object> toJson() {
    return Json.object(
        "action",
        action,
        "path",
        DeviceShares.devicePath(path),
        "target",
        target == null ? null : DeviceShares.devicePath(target));
  }
}
