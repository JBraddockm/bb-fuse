package com.jbraddockm.link.endpoint.files;

import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.link.transport.smb.FileStore;
import com.jbraddockm.link.transport.smb.SmbLocation;
import com.jbraddockm.link.transport.smb.SmbTransferException;

/**
 * Lists the entries of a directory on the device's share.
 *
 * @param directory the directory to list
 */
public record ListDeviceFiles(SmbLocation directory) implements FileCommand<DeviceFileListing> {

  @Override
  public CommandOutcome<DeviceFileListing> run(FileStore store) {
    try {
      return CommandOutcome.succeeded(new DeviceFileListing(directory, store.list(directory)));
    } catch (SmbTransferException failure) {
      return CommandOutcome.failed(failure.toServiceError());
    }
  }
}
