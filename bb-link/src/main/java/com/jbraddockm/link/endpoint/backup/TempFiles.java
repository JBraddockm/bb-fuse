package com.jbraddockm.link.endpoint.backup;

import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.core.command.LogLevel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** The temporary files a backup or restore stages, and their best-effort clean-up. */
final class TempFiles {

  private TempFiles() {}

  /**
   * Removes the staged files and then their directory, logging at {@code DEBUG} on failure. Never
   * throws: a clean-up failure must not mask the command's own outcome.
   *
   * @param files the staged files to remove
   * @param directory the directory that held them
   * @param listener the listener a failed removal is logged to
   * @param what the word for the operation, for the log message ({@code backup} / {@code restore})
   */
  static void deleteQuietly(
      List<Path> files, Path directory, CommandListener listener, String what) {
    for (var file : files) {
      try {
        Files.deleteIfExists(file);
      } catch (IOException failure) {
        listener.onLog(
            LogLevel.DEBUG,
            "Could not remove a temporary " + what + " file: " + failure.getMessage());
      }
    }
    try {
      Files.deleteIfExists(directory);
    } catch (IOException failure) {
      listener.onLog(
          LogLevel.DEBUG,
          "Could not remove the temporary " + what + " folder: " + failure.getMessage());
    }
  }
}
