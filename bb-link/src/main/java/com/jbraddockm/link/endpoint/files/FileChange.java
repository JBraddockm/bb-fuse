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

  /**
   * A file was deleted.
   *
   * @param path the file that was deleted
   * @return the change
   */
  public static FileChange deleted(SmbLocation path) {
    return new FileChange("deleted", path, null);
  }

  /**
   * A directory was created.
   *
   * @param path the directory that was created
   * @return the change
   */
  public static FileChange created(SmbLocation path) {
    return new FileChange("created", path, null);
  }

  /**
   * An empty directory was removed.
   *
   * @param path the directory that was removed
   * @return the change
   */
  public static FileChange removed(SmbLocation path) {
    return new FileChange("removed", path, null);
  }

  /**
   * A file or directory was renamed or moved.
   *
   * @param from the original path
   * @param to the new path
   * @return the change
   */
  public static FileChange moved(SmbLocation from, SmbLocation to) {
    return new FileChange("moved", from, to);
  }

  /**
   * A file was copied, server-side, to another path.
   *
   * @param from the source file
   * @param to the new file
   * @return the change
   */
  public static FileChange copied(SmbLocation from, SmbLocation to) {
    return new FileChange("copied", from, to);
  }

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
