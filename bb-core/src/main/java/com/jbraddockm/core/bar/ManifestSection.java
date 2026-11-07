package com.jbraddockm.core.bar;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One section of a manifest: the ordered run of attributes that a blank-line-separated group of a
 * {@code META-INF/MANIFEST.MF} holds. Attribute names are matched without regard to case.
 *
 * @param attributes the attributes, in order
 */
public record ManifestSection(List<ManifestAttribute> attributes) {

  public ManifestSection {
    attributes = List.copyOf(attributes);
  }

  /**
   * A section of these attributes, in order.
   *
   * @param attributes the attributes
   * @return the section
   */
  public static ManifestSection of(ManifestAttribute... attributes) {
    return new ManifestSection(List.of(attributes));
  }

  /**
   * The value of the first attribute with this name.
   *
   * @param name the attribute name, matched case-insensitively
   * @return the value, or empty if the section has no such attribute
   */
  public Optional<String> value(String name) {
    Objects.requireNonNull(name, "name");
    return attributes.stream()
        .filter(attribute -> attribute.name().equalsIgnoreCase(name))
        .map(ManifestAttribute::value)
        .findFirst();
  }
}
