package com.jbraddockm.link.endpoint.files;

import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.link.transport.cgi.ConnectionPath;
import com.jbraddockm.link.transport.session.DeviceSession;
import com.jbraddockm.link.transport.smb.JcifsFileStore;

/** Runs a {@link FileCommand} against the session's file share. */
public final class FileAccess {

  private FileAccess() {}

  /**
   * Builds the file store from the session's current credentials and runs the command, after
   * checking the share can be reached. The share is served over USB and the Wi-Fi tunnel but not a
   * plain Wi-Fi LAN address, so a command on a LAN session is refused up front.
   *
   * @param session the open session
   * @param command the operation to run
   * @param <R> the result type
   * @return the command's outcome, or a failure when the share is unreachable or the login handed
   *     out no credentials
   */
  public static <R extends CommandResult> CommandOutcome<R> run(
      DeviceSession session, FileCommand<R> command) {
    if (session.connectionPath() == ConnectionPath.LAN) {
      return CommandOutcome.failed(
          new ServiceError(
              CoreErrorCode.COMMAND_FAILED,
              "The device file share is not reachable over a Wi-Fi LAN address; connect over USB or"
                  + " the tunnel"));
    }
    var credentials = session.smbCredentials().orElse(null);
    if (credentials == null) {
      return CommandOutcome.failed(
          new ServiceError(
              CoreErrorCode.COMMAND_FAILED, "The device did not hand out file share credentials"));
    }
    return command.run(new JcifsFileStore(credentials));
  }
}
