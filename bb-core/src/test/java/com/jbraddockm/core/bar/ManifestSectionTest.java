package com.jbraddockm.core.bar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ManifestSection: a group of attributes")
class ManifestSectionTest {

  @Test
  @DisplayName("finds an attribute's value without regard to case, and empty for an absent one")
  void findsByName() {
    var section = ManifestSection.of(new ManifestAttribute("Package-Name", "app"));

    assertThat(section.value("package-name")).hasValue("app");
    assertThat(section.value("Missing")).isEmpty();
  }

  @Test
  @DisplayName("rejects a null name in a lookup")
  void rejectsANullName() {
    var section = ManifestSection.of(new ManifestAttribute("Package-Name", "app"));

    assertThatNullPointerException().isThrownBy(() -> section.value(null));
  }
}
