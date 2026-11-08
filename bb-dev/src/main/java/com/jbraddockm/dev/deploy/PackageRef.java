package com.jbraddockm.dev.deploy;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A reference to an application on the device: by its package name and id, by its full name, or by
 * the bar file whose manifest carries them.
 */
public sealed interface PackageRef {

  /**
   * The application named by its package name and id, as the bar manifest carries them.
   *
   * @param name the {@code Package-Name}, or {@code null} when only the id is known
   * @param id the {@code Package-Id}
   */
  record NameAndId(String name, String id) implements PackageRef {

    /**
     * Creates the reference.
     *
     * @throws IllegalArgumentException if the id is blank
     */
    public NameAndId {
      if (id == null || id.isBlank()) {
        throw new IllegalArgumentException("a package id is required");
      }
    }
  }

  /**
   * The application named by {@code <name>.<id>}, the id being the trailing EcoId.
   *
   * @param fullName the full package name
   */
  record FullName(String fullName) implements PackageRef {

    /**
     * Creates the reference.
     *
     * @throws IllegalArgumentException if the full name is blank
     */
    public FullName {
      Objects.requireNonNull(fullName, "fullName");
      if (fullName.isBlank()) {
        throw new IllegalArgumentException("a full package name is required");
      }
    }
  }

  /**
   * The application identified by a bar file, whose manifest carries the name and id.
   *
   * @param bar the bar file
   */
  record BarFile(Path bar) implements PackageRef {

    /**
     * Creates the reference.
     *
     * @throws NullPointerException if {@code bar} is {@code null}
     */
    public BarFile {
      Objects.requireNonNull(bar, "bar");
    }
  }

  /**
   * Checks that this reference can supply the package name a command needs. A name-and-id reference
   * must then carry the name; a full name and a bar file always can.
   *
   * @throws IllegalArgumentException if a name-and-id reference carries no name
   */
  default void requireName() {
    if (this instanceof NameAndId(var name, var _) && (name == null || name.isBlank())) {
      throw new IllegalArgumentException("a package name is required for this command");
    }
  }
}
