package com.jbraddockm.link.endpoint.files;

import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.link.transport.smb.FileStore;
import com.jbraddockm.link.transport.smb.SmbLocation;
import com.jbraddockm.link.transport.smb.SmbTransferException;

/**
 * Copies a file to another path on the device, server-side (the bytes stay on the device).
 *
 * @param from the existing file
 * @param to the new file
 */
public record CopyDeviceFile(SmbLocation from, SmbLocation to) implements FileCommand<FileChange> {

  @Override
  public CommandOutcome<FileChange> run(FileStore store) {
    try {
      store.copy(from, to);
      return CommandOutcome.succeeded(FileChange.copied(from, to));
    } catch (SmbTransferException failure) {
      return CommandOutcome.failed(failure.toServiceError());
    }
  }
}
