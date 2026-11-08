package com.jbraddockm.core.bar;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.testing.Resources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RuleSetParser: reading a rulesets XML")
class RuleSetParserTest {

  @Test
  @DisplayName(
      "reads exact and open cardinalities, and attributes wrapped or directly in a section")
  void readsCardinalitiesAndBothAttributePlaces() {
    var ruleSet =
        RuleSetParser.parse(Resources.text(RuleSetParserTest.class, "ruleset--cardinality.xml"));

    assertThat(ruleSet.version()).isEqualTo("9.9");
    var section = ruleSet.rulesFor("application").orElseThrow().section("Repeated").orElseThrow();
    assertThat(section.minCardinality()).isEqualTo(2);
    assertThat(section.maxCardinality()).isEqualTo(2);

    var wrapped = section.attribute("Wrapped").orElseThrow();
    assertThat(wrapped.minCardinality()).isEqualTo(1);
    assertThat(wrapped.maxCardinality()).isEqualTo(Integer.MAX_VALUE);

    var direct = section.attribute("Direct").orElseThrow();
    assertThat(direct.minCardinality()).isEqualTo(3);
    assertThat(direct.maxCardinality()).isEqualTo(3);
  }
}
