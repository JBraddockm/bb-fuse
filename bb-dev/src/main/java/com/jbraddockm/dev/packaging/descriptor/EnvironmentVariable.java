package com.jbraddockm.dev.packaging.descriptor;

import java.util.Objects;

/**
 * An {@code env} entry of a descriptor: an environment variable set for the application's entry
 * point.
 *
 * @param variable the variable name, from the {@code var} attribute
 * @param value the value, from the {@code value} attribute
 */
public record EnvironmentVariable(String variable, String value) {

  /**
   * Validates that both parts are present.
   *
   * @throws NullPointerException if the variable or the value is {@code null}
   */
  public EnvironmentVariable {
    Objects.requireNonNull(variable, "variable");
    Objects.requireNonNull(value, "value");
  }
}
