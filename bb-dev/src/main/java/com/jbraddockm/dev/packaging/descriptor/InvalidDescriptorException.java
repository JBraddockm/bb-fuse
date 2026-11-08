package com.jbraddockm.dev.packaging.descriptor;

import java.io.Serial;

/**
 * A {@code bar-descriptor.xml} that was read but cannot be used: not well-formed, not rooted at
 * {@code qnx}, missing a mandatory element or attribute, or without an entry point. The message
 * says what is wrong, without naming the file; the caller knows which one it asked about.
 */
public final class InvalidDescriptorException extends Exception {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param reason what is wrong with the descriptor
   */
  public InvalidDescriptorException(String reason) {
    super(reason);
  }
}
