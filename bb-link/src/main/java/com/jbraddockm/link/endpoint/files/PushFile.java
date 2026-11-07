package com.jbraddockm.link.endpoint.files;

import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.link.transport.smb.FileStore;
import com.jbraddockm.link.transport.smb.SmbLocation;
import com.jbraddockm.link.transport.smb.SmbTransferException;
import java.nio.file.Path;

/**
 * Copies a host file onto the device's share ("Sync to Device").
 *
 * @param source the local file to send
 * @param destination the file on the share to write
 * @param resume continue a partial upload instead of starting over
 */
public record PushFile(Path source, SmbLocation destination, boolean resume)
    implements FileCommand<FileTransfer> {

  @Override
  public CommandOutcome<FileTransfer> run(FileStore store) {
    try {
      var bytes = store.push(source, destination, _ -> {}, resume);
      return CommandOutcome.succeeded(new FileTransfer(destination, source, bytes));
    } catch (SmbTransferException failure) {
      return CommandOutcome.failed(failure.toServiceError());
    }
  }
}
