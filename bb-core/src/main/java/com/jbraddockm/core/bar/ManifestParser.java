package com.jbraddockm.core.bar;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * Reads a {@code META-INF/MANIFEST.MF}: UTF-8 text, {@code Name: Value} entries,
 * blank-line-separated sections, continuation lines that begin with a space, and a tolerated
 * leading byte-order mark. See {@code analysis/BAR-FORMAT.md} §2.
 */
final class ManifestParser {

  private static final char BYTE_ORDER_MARK = '﻿';

  private ManifestParser() {}

  static Manifest parse(byte[] bytes) throws InvalidBarException {
    var text = new String(bytes, StandardCharsets.UTF_8);
    if (!text.isEmpty() && text.charAt(0) == BYTE_ORDER_MARK) {
      text = text.substring(1);
    }
    var sections = new ArrayList<ManifestSection>();
    var attributes = new ArrayList<ManifestAttribute>();
    for (var rawLine : text.split("\n", -1)) {
      var line = rawLine.endsWith("\r") ? rawLine.substring(0, rawLine.length() - 1) : rawLine;
      if (line.isEmpty()) {
        if (!attributes.isEmpty()) {
          sections.add(new ManifestSection(attributes));
          attributes = new ArrayList<>();
        }
        continue;
      }
      if (line.charAt(0) == ' ') {
        if (attributes.isEmpty()) {
          throw new InvalidBarException("its manifest begins with a continuation line");
        }
        var previous = attributes.removeLast();
        attributes.add(
            new ManifestAttribute(previous.name(), previous.value() + line.substring(1)));
        continue;
      }
      int colon = line.indexOf(':');
      if (colon <= 0) {
        throw new InvalidBarException("its manifest has an entry with no name: " + line);
      }
      var name = line.substring(0, colon);
      var value = line.substring(colon + 1);
      if (value.startsWith(" ")) {
        value = value.substring(1);
      }
      attributes.add(new ManifestAttribute(name, value));
    }
    if (!attributes.isEmpty()) {
      sections.add(new ManifestSection(attributes));
    }
    return new Manifest(sections);
  }
}
