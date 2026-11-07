package com.jbraddockm.core.bar;

import java.nio.charset.StandardCharsets;

/**
 * Writes a manifest in the BlackBerry layout: UTF-8 with {@code LF} line endings, {@code Name:
 * Value} entries that are not wrapped, one blank line between sections, and no trailing blank line.
 * See {@code analysis/BAR-FORMAT.md} §2.
 */
final class ManifestWriter {

  private ManifestWriter() {}

  static byte[] toBytes(Manifest manifest) {
    var out = new StringBuilder();
    var sections = manifest.sections();
    for (int index = 0; index < sections.size(); index++) {
      for (var attribute : sections.get(index).attributes()) {
        out.append(attribute.name()).append(": ").append(attribute.value()).append('\n');
      }
      if (index < sections.size() - 1) {
        out.append('\n');
      }
    }
    return out.toString().getBytes(StandardCharsets.UTF_8);
  }
}
