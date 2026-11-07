package com.jbraddockm.link.endpoint.files;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.transport.smb.DeviceShares;
import com.jbraddockm.link.transport.smb.SmbLocation;
import java.nio.file.Path;
import java.util.Map;

/**
 * The result of copying a file between the host and the device.
 *
 * @param device the file on the device's share
 * @param local the file on the host
 * @param bytes the number of bytes copied
 */
public record FileTransfer(SmbLocation device, Path local, long bytes) implements CommandResult {

  @Override
  public Map<String, Object> toJson() {
    return Json.object(
        "device", DeviceShares.devicePath(device), "local", local.toString(), "bytes", bytes);
  }
}
