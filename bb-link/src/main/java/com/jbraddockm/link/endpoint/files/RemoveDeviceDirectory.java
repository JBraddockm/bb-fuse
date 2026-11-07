package com.jbraddockm.link.endpoint.files;

import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.link.transport.smb.FileStore;
import com.jbraddockm.link.transport.smb.SmbLocation;
import com.jbraddockm.link.transport.smb.SmbTransferException;

/**
 * Removes an empty directory on the device's share. A non-empty directory is refused ({@code
 * DIRECTORY_NOT_EMPTY}); removing a tree is a client's concern, not a primitive. The directory
 * counterpart of {@link DeleteDeviceFile}, which removes files only.
 *
 * @param path the directory to remove
 */
public record RemoveDeviceDirectory(SmbLocation path) implements FileCommand<FileChange> {

  @Override
  public CommandOutcome<FileChange> run(FileStore store) {
    try {
      store.removeDirectory(path);
      return CommandOutcome.succeeded(FileChange.removed(path));
    } catch (SmbTransferException failure) {
      return CommandOutcome.failed(failure.toServiceError());
    }
  }
}
