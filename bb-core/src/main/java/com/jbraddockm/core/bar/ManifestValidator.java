package com.jbraddockm.core.bar;

import com.jbraddockm.core.bar.BarValidation.Message;
import com.jbraddockm.core.bar.BarValidation.Severity;
import com.jbraddockm.core.bar.rules.AttributeRule;
import com.jbraddockm.core.bar.rules.PackageRules;
import com.jbraddockm.core.bar.rules.Requirement;
import com.jbraddockm.core.bar.rules.RuleSet;
import com.jbraddockm.core.bar.rules.RuleSets;
import com.jbraddockm.core.bar.rules.SectionRule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Checks a {@link Manifest} against the bundled rule sets, in pure Java. The rule set is chosen by
 * {@code Archive-Manifest-Version}, the package type by {@code Package-Type}, and the signed or
 * unsigned rules by whether the manifest carries the signing attribute. The result is the same
 * {@link BarValidation} the inspector returns.
 *
 * <p>It checks the required sections and their cardinality, the required attributes and their
 * cardinality, each value's length and pattern, and that an attribute marked mandatory-first begins
 * its section. Line continuation is carried in the rules but not enforced (the parsed model does
 * not record it). This validation is informational: the device is the authority on what it accepts.
 */
public final class ManifestValidator {

  private static final RuleSets RULE_SETS = RuleSets.load();
  private static final String SIGNED_MARKER = "Package-Author-Certificate-Hash";
  private static final String ASSET_SECTION = "Archive-Asset";
  private static final String ASSET_DIGEST_RULE = "Archive-Asset-x-Digest";
  private static final Pattern ASSET_DIGEST_NAME = Pattern.compile("Archive-Asset-.+-Digest");

  private ManifestValidator() {}

  /**
   * Validates a manifest against the rules for its version and package type.
   *
   * @param manifest the manifest
   * @return the findings; {@link BarValidation#valid()} is false if any rule is broken
   */
  public static BarValidation validate(Manifest manifest) {
    var signed = manifest.value(SIGNED_MARKER).isPresent();
    var rulesName = signed ? "signed" : "unsigned";

    var version = manifest.value("Archive-Manifest-Version").orElse(null);
    if (version == null) {
      return rejected(rulesName, "The manifest has no Archive-Manifest-Version.");
    }
    var ruleSet = RULE_SETS.forVersion(version).orElse(null);
    if (ruleSet == null) {
      return rejected(rulesName, "The manifest format version " + version + " is not supported.");
    }
    var packageType = manifest.value("Package-Type").orElse(null);
    if (packageType == null) {
      return rejected(rulesName, "The manifest has no Package-Type.");
    }
    var packageRules = ruleSet.rulesFor(packageType).orElse(null);
    if (packageRules == null) {
      return rejected(rulesName, "The package type '" + packageType + "' is not known.");
    }
    var messages = new Validation(manifest, ruleSet, packageRules, signed).run();
    return new BarValidation(rulesName, messages);
  }

  private static BarValidation rejected(String rules, String text) {
    return new BarValidation(rules, List.of(new Message(Severity.ERROR, text)));
  }

  /** Holds the state of one validation run. */
  private static final class Validation {

    private final Manifest manifest;
    private final RuleSet ruleSet;
    private final PackageRules rules;
    private final boolean signed;
    private final List<Message> messages = new ArrayList<>();
    private final Map<String, Integer> sectionCounts = new HashMap<>();

    Validation(Manifest manifest, RuleSet ruleSet, PackageRules rules, boolean signed) {
      this.manifest = manifest;
      this.ruleSet = ruleSet;
      this.rules = rules;
      this.signed = signed;
    }

    List<Message> run() {
      for (var section : manifest.sections()) {
        validateSection(section);
      }
      checkRequiredSections();
      return messages;
    }

    private void validateSection(ManifestSection section) {
      var sectionRule = identify(section);
      sectionRule.ifPresent(rule -> sectionCounts.merge(rule.name(), 1, Integer::sum));
      for (var attribute : section.attributes()) {
        var rule = ruleFor(attribute.name());
        if (rule.isEmpty()) {
          if (!ruleSet.userDefinedAttributesAllowed()) {
            error("Attribute " + attribute.name() + " is not allowed.");
          }
          continue;
        }
        validateValue(attribute, rule.orElseThrow());
      }
      sectionRule.ifPresent(
          rule -> {
            checkRequiredAttributes(section, rule);
            checkAttributeCardinality(section, rule);
            checkMandatoryFirst(section, rule);
          });
    }

    private void validateValue(ManifestAttribute attribute, AttributeRule rule) {
      var value = attribute.value();
      if (rule.minLength() >= 0 && value.length() < rule.minLength()) {
        error("Attribute " + attribute.name() + ": value is too short.");
      }
      if (rule.maxLength() >= 0 && value.length() > rule.maxLength()) {
        error("Attribute " + attribute.name() + ": value is too long.");
      }
      if (!rule.regexId().isEmpty()) {
        // A rule naming a pattern that regex.properties does not define is treated as no
        // constraint, not an error, so an incomplete rule set never rejects an otherwise valid
        // value.
        RULE_SETS
            .regexes()
            .pattern(rule.regexId())
            .filter(pattern -> !pattern.matcher(value).matches())
            .ifPresent(
                _ -> error("Attribute " + attribute.name() + ": value is not valid: " + value));
      }
    }

    private void checkRequiredAttributes(ManifestSection section, SectionRule sectionRule) {
      for (var rule : sectionRule.attributes()) {
        if (!requiredHere(rule.required()) || present(section, rule)) {
          continue;
        }
        error(
            "Section "
                + sectionRule.name()
                + " is missing required attribute "
                + rule.name()
                + ".");
      }
    }

    private void checkAttributeCardinality(ManifestSection section, SectionRule sectionRule) {
      for (var rule : sectionRule.attributes()) {
        var count =
            section.attributes().stream()
                .filter(attribute -> attribute.name().equalsIgnoreCase(rule.name()))
                .count();
        if (count > rule.maxCardinality()) {
          error("Section " + sectionRule.name() + " has too many " + rule.name() + " attributes.");
        }
      }
    }

    private void checkMandatoryFirst(ManifestSection section, SectionRule sectionRule) {
      if (section.attributes().isEmpty()) {
        return;
      }
      var first = section.attributes().getFirst().name();
      for (var rule : sectionRule.attributes()) {
        if (rule.mandatoryFirstInSection()
            && section.value(rule.name()).isPresent()
            && !first.equalsIgnoreCase(rule.name())) {
          error("Section " + sectionRule.name() + " must begin with " + rule.name() + ".");
        }
      }
    }

    private void checkRequiredSections() {
      for (var sectionRule : rules.sections()) {
        int count = sectionCounts.getOrDefault(sectionRule.name(), 0);
        if (count < sectionRule.minCardinality()) {
          error("The manifest is missing the required " + sectionRule.name() + " section.");
        } else if (count > sectionRule.maxCardinality()) {
          error("The manifest has too many " + sectionRule.name() + " sections.");
        }
      }
    }

    private boolean present(ManifestSection section, AttributeRule rule) {
      if (section.value(rule.name()).isPresent()) {
        return true;
      }
      return rule.name().equals(ASSET_DIGEST_RULE)
          && section.attributes().stream()
              .anyMatch(attribute -> ASSET_DIGEST_NAME.matcher(attribute.name()).matches());
    }

    private boolean requiredHere(Requirement requirement) {
      return requirement == Requirement.ALWAYS
          || requirement == Requirement.IF_SECTION
          || (requirement == Requirement.IF_SIGNED && signed);
    }

    private Optional<SectionRule> identify(ManifestSection section) {
      return section.attributes().stream()
          .map(attribute -> sectionOf(attribute.name()))
          .flatMap(Optional::stream)
          .findFirst();
    }

    private Optional<SectionRule> sectionOf(String attributeName) {
      for (var sectionRule : rules.sections()) {
        if (sectionRule.attribute(attributeName).isPresent()) {
          return Optional.of(sectionRule);
        }
      }
      if (ASSET_DIGEST_NAME.matcher(attributeName).matches()) {
        return rules.section(ASSET_SECTION);
      }
      return localizedOwner(attributeName).map(Owner::section);
    }

    private Optional<AttributeRule> ruleFor(String attributeName) {
      for (var sectionRule : rules.sections()) {
        var attribute = sectionRule.attribute(attributeName);
        if (attribute.isPresent()) {
          return attribute;
        }
      }
      if (ASSET_DIGEST_NAME.matcher(attributeName).matches()) {
        return rules
            .section(ASSET_SECTION)
            .flatMap(section -> section.attribute(ASSET_DIGEST_RULE));
      }
      return localizedOwner(attributeName).map(Owner::attribute);
    }

    /** A localizable attribute of which {@code name} is a locale-suffixed variant. */
    private Optional<Owner> localizedOwner(String name) {
      for (var sectionRule : rules.sections()) {
        for (var attribute : sectionRule.attributes()) {
          if (attribute.canLocalize() && name.startsWith(attribute.name() + "-")) {
            return Optional.of(new Owner(sectionRule, attribute));
          }
        }
      }
      return Optional.empty();
    }

    private void error(String text) {
      messages.add(new Message(Severity.ERROR, text));
    }

    private record Owner(SectionRule section, AttributeRule attribute) {}
  }
}
