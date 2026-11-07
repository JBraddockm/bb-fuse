package com.jbraddockm.link.endpoint.files;

import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.link.transport.smb.FileStore;
import com.jbraddockm.link.transport.smb.SmbLocation;
import com.jbraddockm.link.transport.smb.SmbTransferException;

/**
 * Deletes a single file on the device's share. Directories are refused — removing a tree is a
 * client's concern, not a primitive.
 *
 * @param path the file to delete
 */
public record DeleteDeviceFile(SmbLocation path) implements FileCommand<FileChange> {

  @Override
  public CommandOutcome<FileChange> run(FileStore store) {
    try {
      store.delete(path);
      return CommandOutcome.succeeded(new FileChange("deleted", path, null));
    } catch (SmbTransferException failure) {
      return CommandOutcome.failed(failure.toServiceError());
    }
  }
}
