package com.jbraddockm.link.endpoint.files;

import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.link.transport.smb.FileStore;
import com.jbraddockm.link.transport.smb.SmbLocation;
import com.jbraddockm.link.transport.smb.SmbTransferException;

/**
 * Renames or moves a file or directory on the device's share.
 *
 * @param from the existing path
 * @param to the new path
 */
public record MoveDeviceFile(SmbLocation from, SmbLocation to) implements FileCommand<FileChange> {

  @Override
  public CommandOutcome<FileChange> run(FileStore store) {
    try {
      store.move(from, to);
      return CommandOutcome.succeeded(new FileChange("moved", from, to));
    } catch (SmbTransferException failure) {
      return CommandOutcome.failed(failure.toServiceError());
    }
  }
}
