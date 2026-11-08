package com.jbraddockm.dev.deploy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PackageRef: a reference to an application on the device")
class PackageRefTest {

  @Nested
  @DisplayName("validation at creation")
  class Validation {

    @Test
    @DisplayName("rejects a name-and-id reference with a blank id")
    void blankId() {
      assertThatThrownBy(() -> new PackageRef.NameAndId("com.example.App", " "))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("package id");
    }

    @Test
    @DisplayName("rejects a blank full name")
    void blankFullName() {
      assertThatThrownBy(() -> new PackageRef.FullName(" "))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("full package name");
    }

    @Test
    @DisplayName("rejects a null bar file")
    void nullBar() {
      assertThatThrownBy(() -> new PackageRef.BarFile(null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("requiring a package name")
  class RequireName {

    @Test
    @DisplayName("rejects a name-and-id reference that carries no name")
    void nameAndIdWithoutName() {
      assertThatThrownBy(() -> new PackageRef.NameAndId(null, "gYABId").requireName())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("package name");
    }

    @Test
    @DisplayName("accepts a name-and-id reference that carries a name, a full name, and a bar file")
    void referencesThatCanName() {
      assertThatCode(
              () -> {
                new PackageRef.NameAndId("com.example.App", "gYABId").requireName();
                new PackageRef.FullName("com.example.App.gYABId").requireName();
                new PackageRef.BarFile(Path.of("app.bar")).requireName();
              })
          .doesNotThrowAnyException();
    }
  }
}
