package com.jbraddockm.core.bar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ManifestAttribute: one Name: Value entry")
class ManifestAttributeTest {

  @Test
  @DisplayName("keeps its name and value, an empty value included")
  void keepsNameAndValue() {
    var attribute = new ManifestAttribute("Package-Name", "");

    assertThat(attribute.name()).isEqualTo("Package-Name");
    assertThat(attribute.value()).isEmpty();
  }

  @Test
  @DisplayName("rejects a blank name")
  void rejectsABlankName() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new ManifestAttribute("   ", "value"))
        .withMessageContaining("must not be blank");
  }

  @Test
  @DisplayName("rejects a null name or value")
  void rejectsNulls() {
    assertThatNullPointerException().isThrownBy(() -> new ManifestAttribute(null, "value"));
    assertThatNullPointerException().isThrownBy(() -> new ManifestAttribute("Package-Name", null));
  }
}
