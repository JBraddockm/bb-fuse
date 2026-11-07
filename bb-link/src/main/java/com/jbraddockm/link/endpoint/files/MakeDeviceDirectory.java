package com.jbraddockm.link.endpoint.files;

import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.link.transport.smb.FileStore;
import com.jbraddockm.link.transport.smb.SmbLocation;
import com.jbraddockm.link.transport.smb.SmbTransferException;

/**
 * Creates a directory (and any missing parents) on the device's share.
 *
 * @param path the directory to create
 */
public record MakeDeviceDirectory(SmbLocation path) implements FileCommand<FileChange> {

  @Override
  public CommandOutcome<FileChange> run(FileStore store) {
    try {
      store.makeDirectory(path);
      return CommandOutcome.succeeded(new FileChange("created", path, null));
    } catch (SmbTransferException failure) {
      return CommandOutcome.failed(failure.toServiceError());
    }
  }
}
