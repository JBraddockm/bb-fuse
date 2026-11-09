package com.jbraddockm.core.bar.rules;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The bundled manifest rule sets (versions 1.0 through 1.6) and the shared value patterns, loaded
 * from the resources under {@code rulesets/}. A rule set is chosen by {@code
 * Archive-Manifest-Version}.
 */
public final class RuleSets {

  private static final String[] VERSIONS = {"1_0", "1_1", "1_2", "1_3", "1_4", "1_5", "1_6"};
  private static final String CURRENT = "1.6";

  private final Map<String, RuleSet> byVersion;
  private final Regexes regexes;

  private RuleSets(Map<String, RuleSet> byVersion, Regexes regexes) {
    this.byVersion = byVersion;
    this.regexes = regexes;
  }

  public static RuleSets load() {
    var byVersion = new LinkedHashMap<String, RuleSet>();
    for (var version : VERSIONS) {
      var ruleSet = RuleSetParser.parse(resource("rulesets/rulesets_" + version + ".xml"));
      byVersion.put(ruleSet.version(), ruleSet);
    }
    return new RuleSets(byVersion, Regexes.load());
  }

  public Optional<RuleSet> forVersion(String version) {
    return Optional.ofNullable(byVersion.get(version));
  }

  public RuleSet current() {
    return byVersion.get(CURRENT);
  }

  public Regexes regexes() {
    return regexes;
  }

  private static String resource(String name) {
    try (InputStream in = RuleSets.class.getResourceAsStream("/com/jbraddockm/core/bar/" + name)) {
      if (in == null) {
        throw new IllegalStateException(name + " is missing from the build");
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }
}
