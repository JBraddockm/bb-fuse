package com.jbraddockm.link.endpoint.files;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.transport.smb.DeviceShares;
import com.jbraddockm.link.transport.smb.SmbLocation;
import java.util.Map;

/**
 * The free space of the share a path is on.
 *
 * @param path the path that was measured
 * @param freeBytes the free space in bytes
 */
public record DiskFree(SmbLocation path, long freeBytes) implements CommandResult {

  @Override
  public Map<String, Object> toJson() {
    return Json.object("path", DeviceShares.devicePath(path), "freeBytes", freeBytes);
  }
}
