package com.jbraddockm.core.bar.rules;

import java.util.List;
import java.util.Optional;

/**
 * The rule for one manifest section, as a {@code <section>} of a rule set.
 *
 * @param name the section name ({@code Archive}, {@code Package}, {@code Entry-Point}, …)
 * @param required when the section is required
 * @param minCardinality the least number of times the section may appear
 * @param maxCardinality the greatest number of times it may appear ({@link Integer#MAX_VALUE} =
 *     any)
 * @param attributes the attribute rules, in order
 */
public record SectionRule(
    String name,
    Requirement required,
    int minCardinality,
    int maxCardinality,
    List<AttributeRule> attributes) {

  public SectionRule {
    attributes = List.copyOf(attributes);
  }

  public Optional<AttributeRule> attribute(String attributeName) {
    return attributes.stream()
        .filter(attribute -> attribute.name().equalsIgnoreCase(attributeName))
        .findFirst();
  }
}
