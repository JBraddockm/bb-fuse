package com.jbraddockm.core.bar.rules;

import java.util.List;
import java.util.Optional;

/**
 * The rules for one package type, as a {@code <rulesSet>} of a rule set.
 *
 * @param packageType the {@code Package-Type} these rules are for
 * @param sections the section rules, in order
 */
public record PackageRules(String packageType, List<SectionRule> sections) {

  public PackageRules {
    sections = List.copyOf(sections);
  }

  public Optional<SectionRule> section(String sectionName) {
    return sections.stream()
        .filter(section -> section.name().equalsIgnoreCase(sectionName))
        .findFirst();
  }
}
