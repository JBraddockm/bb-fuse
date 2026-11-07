package com.jbraddockm.core.bar;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@code META-INF/MANIFEST.MF}, read and written in pure Java: an ordered list of sections. A
 * package's attributes sit in a section of their own, not at the top, so {@link #value} looks
 * through every section. The read and write grammar is described in {@code analysis/BAR-FORMAT.md}.
 *
 * @param sections the sections, in order
 */
public record Manifest(List<ManifestSection> sections) {

  public Manifest {
    sections = List.copyOf(sections);
  }

  /**
   * Reads a manifest from its bytes (UTF-8).
   *
   * @param bytes the manifest bytes
   * @return the manifest
   * @throws InvalidBarException if the bytes are not a manifest that can be understood
   */
  public static Manifest parse(byte[] bytes) throws InvalidBarException {
    return ManifestParser.parse(bytes);
  }

  /**
   * The manifest as bytes (UTF-8), in the BlackBerry layout: {@code LF} line endings, no wrapping,
   * a blank line between sections, ending on the last attribute.
   *
   * @return the bytes
   */
  public byte[] toBytes() {
    return ManifestWriter.toBytes(this);
  }

  /**
   * The value of the first attribute with this name in any section.
   *
   * @param name the attribute name, matched case-insensitively
   * @return the value, or empty if no section has such an attribute
   */
  public Optional<String> value(String name) {
    Objects.requireNonNull(name, "name");
    return sections.stream()
        .map(section -> section.value(name))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst();
  }
}
