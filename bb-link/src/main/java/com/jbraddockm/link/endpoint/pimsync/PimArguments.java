package com.jbraddockm.link.endpoint.pimsync;

import com.jbraddockm.core.error.InvalidArgumentException;

/** The argument checks the pimsync commands share. */
public final class PimArguments {

  private PimArguments() {}

  /**
   * Requires a text argument to be present.
   *
   * @param name the parameter name, for the error
   * @param value the value to check
   * @throws InvalidArgumentException if the value is {@code null} or blank
   */
  public static void required(String name, String value) {
    if (value == null || value.isBlank()) {
      throw new InvalidArgumentException(name, "is required");
    }
  }
}
