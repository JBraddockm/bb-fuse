package com.jbraddockm.core.bar;

import com.jbraddockm.core.xml.XmlElement;
import com.jbraddockm.core.xml.XmlParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Parses a {@code rulesets_X_Y.xml} into a {@link RuleSet}. The element and attribute names, and
 * the property defaults, follow the rule-set format (see {@code analysis/BAR-FORMAT.md} §5).
 */
final class RuleSetParser {

  private RuleSetParser() {}

  static RuleSet parse(String xml) {
    var rules = XmlParser.parse(xml);
    var version = rules.attribute("barSpecificationVersion").orElse("");
    var userDefined =
        rules.childText("userDefinedAttributesAllowed").map(Boolean::parseBoolean).orElse(false);
    var barNameRegexId =
        rules.child("barName").flatMap(name -> name.attribute("regexId")).orElse("");
    var packages = new LinkedHashMap<String, PackageRules>();
    for (var rulesSet : childrenAt(rules, "rulesSets", "rulesSet")) {
      var packageType = rulesSet.attribute("packageType").orElse("");
      var sections = new ArrayList<SectionRule>();
      for (var section : childrenAt(rulesSet, "sections", "section")) {
        sections.add(parseSection(section));
      }
      packages.put(packageType, new PackageRules(packageType, sections));
    }
    return new RuleSet(version, userDefined, barNameRegexId, packages);
  }

  private static SectionRule parseSection(XmlElement section) {
    var required = Requirement.parse(section.attribute("required").orElse(null));
    var cardinality = cardinality(section.attribute("cardinality").orElse(null), required);
    var attributes = new ArrayList<AttributeRule>();
    // Most sections wrap their attributes in an <attributes> element, but a few in the rule sets
    // (e.g. System-Data in 1_5) list <attribute> directly under <section>; accept both.
    for (var attribute : childrenAt(section, "attributes", "attribute")) {
      attributes.add(parseAttribute(attribute));
    }
    for (var attribute : section.children("attribute")) {
      attributes.add(parseAttribute(attribute));
    }
    return new SectionRule(
        section.attribute("name").orElse(""), required, cardinality[0], cardinality[1], attributes);
  }

  private static AttributeRule parseAttribute(XmlElement attribute) {
    var required = Requirement.parse(attribute.attribute("required").orElse(null));
    var cardinality = cardinality(attribute.attribute("cardinality").orElse(null), required);
    return new AttributeRule(
        attribute.attribute("name").orElse(""),
        required,
        attribute.attribute("regexId").orElse(""),
        length(attribute, "minLength"),
        length(attribute, "maxLength"),
        cardinality[0],
        cardinality[1],
        bool(attribute, "continuationAllowed", true),
        bool(attribute, "canLocalize", false),
        bool(attribute, "mandatoryFirstInSection", false));
  }

  private static List<XmlElement> childrenAt(XmlElement element, String wrapper, String child) {
    return element.child(wrapper).map(found -> found.children(child)).orElse(List.of());
  }

  /**
   * {@code [min, max]}; {@code *} is {@link Integer#MAX_VALUE}; no cardinality means required?1:0
   * to 1.
   */
  private static int[] cardinality(String value, Requirement required) {
    if (value == null) {
      return new int[] {required == Requirement.ALWAYS ? 1 : 0, 1};
    }
    int range = value.indexOf("..");
    if (range < 0) {
      int exact = Integer.parseInt(value.trim());
      return new int[] {exact, exact};
    }
    int min = Integer.parseInt(value.substring(0, range).trim());
    var maxText = value.substring(range + 2).trim();
    int max = maxText.equals("*") ? Integer.MAX_VALUE : Integer.parseInt(maxText);
    return new int[] {min, max};
  }

  private static int length(XmlElement element, String name) {
    return element.attribute(name).map(Integer::parseInt).orElse(-1);
  }

  private static boolean bool(XmlElement element, String name, boolean fallback) {
    return element.attribute(name).map(Boolean::parseBoolean).orElse(fallback);
  }
}
