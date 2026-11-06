package com.jbraddockm.tunnel.broker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Consumer;

/**
 * The daemon's log to a file, kept tiny so it does not persist: it appends {@code bb-tunneld: }
 * -prefixed lines and, once it has written {@link #MAX_LINES} lines, clears the file and starts
 * over — so the log only ever holds a small, recent window. It also starts empty on each run. A
 * file that cannot be written falls back to standard error (for example a non-root run).
 */
final class CappedLog implements Consumer<String> {

  /** The log is cleared once it reaches this many lines. */
  static final int MAX_LINES = 100;

  private final Path file;
  private final Object lock = new Object();
  private int lines;

  private CappedLog(Path file) {
    this.file = file;
  }

  /** A capped sink writing to {@code file} (started empty), or stderr if it cannot be written. */
  static Consumer<String> to(Path file) {
    try {
      var parent = file.toAbsolutePath().getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.write(
          file, new byte[0], StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      return new CappedLog(file);
    } catch (IOException cannotOpen) {
      System.err.println(
          "bb-tunneld: cannot write " + file + " (" + cannotOpen + "); logging to stderr");
      return message -> System.err.println("bb-tunneld: " + message);
    }
  }

  @Override
  public void accept(String message) {
    synchronized (lock) {
      try {
        if (lines >= MAX_LINES) {
          Files.write(file, new byte[0], StandardOpenOption.TRUNCATE_EXISTING);
          lines = 0;
        }
        Files.writeString(
            file,
            "bb-tunneld: " + message + "\n",
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND);
        lines++;
      } catch (IOException failed) {
        System.err.println("bb-tunneld: " + message);
      }
    }
  }
}
