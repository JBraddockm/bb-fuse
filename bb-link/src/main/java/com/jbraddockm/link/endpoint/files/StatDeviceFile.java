package com.jbraddockm.link.endpoint.files;

import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.link.transport.smb.FileStore;
import com.jbraddockm.link.transport.smb.SmbLocation;
import com.jbraddockm.link.transport.smb.SmbTransferException;

/**
 * Reads the metadata of a single file or directory on the device's share.
 *
 * @param path the file or directory to read
 */
public record StatDeviceFile(SmbLocation path) implements FileCommand<DeviceFileInfo> {

  @Override
  public CommandOutcome<DeviceFileInfo> run(FileStore store) {
    try {
      return CommandOutcome.succeeded(new DeviceFileInfo(path, store.stat(path)));
    } catch (SmbTransferException failure) {
      return CommandOutcome.failed(failure.toServiceError());
    }
  }
}
