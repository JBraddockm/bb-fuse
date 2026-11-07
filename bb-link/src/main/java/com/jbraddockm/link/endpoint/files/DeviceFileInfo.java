package com.jbraddockm.link.endpoint.files;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.link.transport.smb.DeviceShares;
import com.jbraddockm.link.transport.smb.SmbEntry;
import com.jbraddockm.link.transport.smb.SmbLocation;
import java.util.Map;

/**
 * The metadata of a single file or directory on the device's share.
 *
 * @param path the file or directory that was read
 * @param entry its metadata
 */
public record DeviceFileInfo(SmbLocation path, SmbEntry entry) implements CommandResult {

  @Override
  public Map<String, Object> toJson() {
    return FileEntries.toJson(DeviceShares.devicePath(path), entry);
  }
}
