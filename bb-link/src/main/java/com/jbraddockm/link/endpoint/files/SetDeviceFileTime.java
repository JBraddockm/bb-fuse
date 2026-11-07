package com.jbraddockm.link.endpoint.files;

import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.link.transport.smb.FileStore;
import com.jbraddockm.link.transport.smb.SmbLocation;
import com.jbraddockm.link.transport.smb.SmbTransferException;
import java.time.Instant;

/**
 * Sets a file's last-modified time, then returns its updated metadata.
 *
 * @param path the file
 * @param time the new last-modified time
 */
public record SetDeviceFileTime(SmbLocation path, Instant time)
    implements FileCommand<DeviceFileInfo> {

  @Override
  public CommandOutcome<DeviceFileInfo> run(FileStore store) {
    try {
      store.setModifiedTime(path, time);
      return CommandOutcome.succeeded(new DeviceFileInfo(path, store.stat(path)));
    } catch (SmbTransferException failure) {
      return CommandOutcome.failed(failure.toServiceError());
    }
  }
}
