package com.jbraddockm.link.endpoint.files;

import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.link.transport.smb.FileStore;
import com.jbraddockm.link.transport.smb.SmbLocation;
import com.jbraddockm.link.transport.smb.SmbTransferException;
import java.nio.file.Path;

/**
 * Copies a file from the device's share to the host ("Sync to Computer").
 *
 * @param source the file on the share to read
 * @param destination the local file to write
 * @param resume continue a partial download instead of starting over
 */
public record PullFile(SmbLocation source, Path destination, boolean resume)
    implements FileCommand<FileTransfer> {

  @Override
  public CommandOutcome<FileTransfer> run(FileStore store) {
    try {
      var bytes = store.pull(source, destination, _ -> {}, resume);
      return CommandOutcome.succeeded(new FileTransfer(source, destination, bytes));
    } catch (SmbTransferException failure) {
      return CommandOutcome.failed(failure.toServiceError());
    }
  }
}
