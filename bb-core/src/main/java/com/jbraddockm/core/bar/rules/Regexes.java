package com.jbraddockm.core.bar.rules;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * The value patterns of {@code regex.properties}, compiled and keyed by {@code regexId}. The file
 * is a {@link Properties} file; each value is compiled once to a {@link Pattern}.
 */
public final class Regexes {

  private final Map<String, Pattern> patterns;

  private Regexes(Map<String, Pattern> patterns) {
    this.patterns = patterns;
  }

  static Regexes load() {
    var properties = new Properties();
    try (InputStream in =
        Regexes.class.getResourceAsStream("/com/jbraddockm/core/bar/rulesets/regex.properties")) {
      if (in == null) {
        throw new IllegalStateException("regex.properties is missing from the build");
      }
      properties.load(in);
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
    var patterns = new HashMap<String, Pattern>();
    for (var name : properties.stringPropertyNames()) {
      patterns.put(name, Pattern.compile(properties.getProperty(name)));
    }
    return new Regexes(patterns);
  }

  public Optional<Pattern> pattern(String regexId) {
    return Optional.ofNullable(patterns.get(regexId));
  }
}
