package com.jbraddockm.link.endpoint.backup;

import com.jbraddockm.link.transport.xml.XmlElement;
import java.util.Optional;

/** The small numeric reads the backup parsers share. */
final class BackupNumbers {

  private BackupNumbers() {}

  /**
   * Parses a long.
   *
   * @param text the text to parse
   * @return the value, or empty if the text is not a long
   */
  static Optional<Long> parseLong(String text) {
    try {
      return Optional.of(Long.parseLong(text.strip()));
    } catch (NumberFormatException _) {
      return Optional.empty();
    }
  }

  /**
   * Reads a numeric attribute of an element.
   *
   * @param element the element to read
   * @param name the attribute name
   * @return the value, or {@code 0} when the attribute is absent or not a long
   */
  static long attributeOrZero(XmlElement element, String name) {
    return element.attribute(name).flatMap(BackupNumbers::parseLong).orElse(0L);
  }
}
