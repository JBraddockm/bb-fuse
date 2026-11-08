package com.jbraddockm.dev.deploy;

/** A command line that could not be understood; the message says what was wrong. */
final class UsageException extends Exception {

  UsageException(String message) {
    super(message);
  }
}
