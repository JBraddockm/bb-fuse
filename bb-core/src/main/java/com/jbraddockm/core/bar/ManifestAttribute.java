package com.jbraddockm.core.bar;

import java.util.Objects;

/**
 * One {@code Name: Value} entry of a manifest section.
 *
 * @param name the attribute name
 * @param value the attribute value, possibly empty
 */
public record ManifestAttribute(String name, String value) {

  public ManifestAttribute {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(value, "value");
    if (name.isBlank()) {
      throw new IllegalArgumentException("a manifest attribute name must not be blank");
    }
  }
}
