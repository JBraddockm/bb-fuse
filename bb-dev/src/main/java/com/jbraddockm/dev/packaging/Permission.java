package com.jbraddockm.dev.packaging;

import java.util.Objects;

/**
 * A {@code permission} the application requests. A permission marked {@code system="true"} is a
 * system action the device grants to trusted code (for example {@code run_native}); the rest are
 * user actions the user is asked to allow.
 *
 * @param name the permission name
 * @param system whether it is a system action rather than a user action
 */
public record Permission(String name, boolean system) {

  /**
   * Validates the name.
   *
   * @throws NullPointerException if the name is {@code null}
   */
  public Permission {
    Objects.requireNonNull(name, "name");
  }
}
