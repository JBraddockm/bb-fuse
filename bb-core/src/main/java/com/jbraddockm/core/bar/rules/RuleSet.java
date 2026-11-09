package com.jbraddockm.core.bar.rules;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One manifest-specification version's validation rules, parsed from a {@code rulesets_X_Y.xml}.
 *
 * @param version the {@code barSpecificationVersion}, e.g. {@code 1.6}
 * @param userDefinedAttributesAllowed whether attributes outside the rules are permitted
 * @param barNameRegexId the pattern id for the archive's file name; retained from the rule set but
 *     not applied by the manifest-only manifest validator
 * @param packages the rules per package type, keyed by {@code Package-Type}
 */
public record RuleSet(
    String version,
    boolean userDefinedAttributesAllowed,
    String barNameRegexId,
    Map<String, PackageRules> packages) {

  public RuleSet {
    packages = Map.copyOf(packages);
  }

  public Optional<PackageRules> rulesFor(String packageType) {
    return Optional.ofNullable(packages.get(packageType));
  }

  public List<String> packageTypes() {
    return List.copyOf(packages.keySet());
  }
}
