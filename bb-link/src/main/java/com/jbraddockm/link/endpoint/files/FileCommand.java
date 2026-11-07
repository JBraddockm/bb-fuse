package com.jbraddockm.link.endpoint.files;

import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.link.transport.smb.FileStore;

/**
 * One file-access operation, run against a {@link FileStore}. Keeping the store a parameter lets
 * the operation be exercised against a substitute in tests, while {@link FileAccess} supplies the
 * real store from the session.
 *
 * @param <R> the result type
 */
public interface FileCommand<R extends CommandResult> {

  /**
   * Runs the operation.
   *
   * @param store the file store to act on
   * @return a successful outcome with the result, or a failed outcome carrying the error
   */
  CommandOutcome<R> run(FileStore store);
}
