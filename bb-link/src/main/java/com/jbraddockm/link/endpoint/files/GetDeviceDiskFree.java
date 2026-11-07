package com.jbraddockm.link.endpoint.files;

import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.link.transport.smb.FileStore;
import com.jbraddockm.link.transport.smb.SmbLocation;
import com.jbraddockm.link.transport.smb.SmbTransferException;

/**
 * Reads the free space of the share a path is on.
 *
 * @param path any path on the share to measure
 */
public record GetDeviceDiskFree(SmbLocation path) implements FileCommand<DiskFree> {

  @Override
  public CommandOutcome<DiskFree> run(FileStore store) {
    try {
      return CommandOutcome.succeeded(new DiskFree(path, store.freeSpace(path)));
    } catch (SmbTransferException failure) {
      return CommandOutcome.failed(failure.toServiceError());
    }
  }
}
