package com.jbraddockm.core.bar.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("The manifest rule sets")
class RuleSetTest {

  @Test
  @DisplayName("load every bundled version and compile the value patterns")
  void loadsEveryVersion() {
    var ruleSets = RuleSets.load();

    assertThat(ruleSets.forVersion("1.0")).isPresent();
    assertThat(ruleSets.forVersion("1.6")).isPresent();
    assertThat(ruleSets.current().version()).isEqualTo("1.6");
    assertThat(ruleSets.regexes().pattern("reverse_dns")).isPresent();
    assertThat(
            ruleSets
                .regexes()
                .pattern("reverse_dns")
                .orElseThrow()
                .matcher("com.example.app")
                .matches())
        .isTrue();
  }

  @Test
  @DisplayName("parse the 1.6 application rules with the right attribute properties")
  void parsesApplicationRules() {
    var rules = RuleSets.load().forVersion("1.6").orElseThrow();

    assertThat(rules.userDefinedAttributesAllowed()).isFalse();
    var application = rules.rulesFor("application").orElseThrow();

    var packageName =
        application.section("Package").orElseThrow().attribute("Package-Name").orElseThrow();
    assertThat(packageName.required()).isEqualTo(Requirement.ALWAYS);
    assertThat(packageName.regexId()).isEqualTo("reverse_dns");
    assertThat(packageName.maxLength()).isEqualTo(50);

    var packageId =
        application.section("Package").orElseThrow().attribute("Package-Id").orElseThrow();
    assertThat(packageId.required()).isEqualTo(Requirement.IF_SIGNED);

    var entryPoint = application.section("Entry-Point").orElseThrow();
    assertThat(entryPoint.maxCardinality()).isEqualTo(Integer.MAX_VALUE);
    assertThat(entryPoint.attribute("Entry-Point-Name").orElseThrow().mandatoryFirstInSection())
        .isTrue();
  }

  @Test
  @DisplayName("cover all six package types")
  void coversEveryPackageType() {
    var rules = RuleSets.load().forVersion("1.6").orElseThrow();

    assertThat(rules.packageTypes())
        .containsExactlyInAnyOrder(
            "application", "system", "patch", "system-data", "device-configuration", "debug-token");
  }
}
