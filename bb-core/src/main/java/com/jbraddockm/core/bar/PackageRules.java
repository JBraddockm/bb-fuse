package com.jbraddockm.core.bar;

import java.util.List;
import java.util.Optional;

/**
 * The rules for one package type, as a {@code <rulesSet>} of a rule set.
 *
 * @param packageType the {@code Package-Type} these rules are for
 * @param sections the section rules, in order
 */
record PackageRules(String packageType, List<SectionRule> sections) {

  PackageRules {
    sections = List.copyOf(sections);
  }

  Optional<SectionRule> section(String sectionName) {
    return sections.stream()
        .filter(section -> section.name().equalsIgnoreCase(sectionName))
        .findFirst();
  }
}
