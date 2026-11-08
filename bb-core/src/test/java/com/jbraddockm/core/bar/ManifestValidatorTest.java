package com.jbraddockm.core.bar;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.testing.Resources;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Validating a manifest")
class ManifestValidatorTest {

  private static Manifest parse(String resource) throws Exception {
    return Manifest.parse(Resources.bytes(ManifestValidatorTest.class, resource));
  }

  @Nested
  @DisplayName("real manifests")
  class Real {

    @Test
    @DisplayName("accepts a real application manifest under the unsigned rules")
    void realApplication() throws Exception {
      var result = ManifestValidator.validate(parse("real-app-manifest.mf"));

      assertThat(result.rules()).isEqualTo("unsigned");
      assertThat(result.messages()).isEmpty();
      assertThat(result.valid()).isTrue();
    }

    @Test
    @DisplayName("accepts a signed application under the signed rules")
    void signedApplication() throws Exception {
      var result = ManifestValidator.validate(parse("manifest--signed-app.mf"));

      assertThat(result.rules()).isEqualTo("signed");
      assertThat(result.valid()).as("%s", result.messages()).isTrue();
    }

    @Test
    @DisplayName("accepts a signed system-data (driver) package")
    void systemDriver() throws Exception {
      var result = ManifestValidator.validate(parse("manifest--system-driver.mf"));

      assertThat(result.rules()).isEqualTo("signed");
      assertThat(result.valid()).as("%s", result.messages()).isTrue();
    }
  }

  @Nested
  @DisplayName("built manifests")
  class Built {

    private ManifestSection archive() {
      return ManifestSection.of(new ManifestAttribute("Archive-Manifest-Version", "1.6"));
    }

    private ManifestSection packageSection(ManifestAttribute... extra) {
      var attributes =
          new ArrayList<>(
              List.of(
                  new ManifestAttribute("Package-Type", "application"),
                  new ManifestAttribute("Package-Name", "com.example.app"),
                  new ManifestAttribute("Package-Author", "Example Inc."),
                  new ManifestAttribute("Package-Version", "1.0.0.0")));
      attributes.addAll(List.of(extra));
      return new ManifestSection(attributes);
    }

    private ManifestSection applicationSection() {
      return ManifestSection.of(
          new ManifestAttribute("Application-Name", "App"),
          new ManifestAttribute("Application-Version", "1.0.0.0"));
    }

    @Test
    @DisplayName("accepts a minimal well-formed application")
    void minimalIsValid() {
      var manifest = new Manifest(List.of(archive(), packageSection(), applicationSection()));

      assertThat(ManifestValidator.validate(manifest).valid()).isTrue();
    }

    @Test
    @DisplayName("reports a missing required attribute")
    void missingRequiredAttribute() {
      var manifest =
          new Manifest(
              List.of(
                  archive(),
                  ManifestSection.of(
                      new ManifestAttribute("Package-Type", "application"),
                      new ManifestAttribute("Package-Name", "com.example.app"),
                      new ManifestAttribute("Package-Author", "Example Inc.")),
                  applicationSection()));

      var result = ManifestValidator.validate(manifest);

      assertThat(result.valid()).isFalse();
      assertThat(result.messages()).anyMatch(message -> message.text().contains("Package-Version"));
    }

    @Test
    @DisplayName("reports a value that breaks its pattern")
    void invalidValue() {
      var manifest =
          new Manifest(
              List.of(
                  archive(),
                  packageSection(),
                  ManifestSection.of(
                      new ManifestAttribute("Application-Name", "App"),
                      new ManifestAttribute("Application-Version", "not a version"))));

      var result = ManifestValidator.validate(manifest);

      assertThat(result.valid()).isFalse();
      assertThat(result.messages())
          .anyMatch(message -> message.text().contains("Application-Version"));
    }

    @Test
    @DisplayName("reports an attribute the rules do not allow")
    void unknownAttribute() {
      var manifest =
          new Manifest(
              List.of(
                  archive(),
                  packageSection(new ManifestAttribute("Totally-Made-Up", "x")),
                  applicationSection()));

      var result = ManifestValidator.validate(manifest);

      assertThat(result.valid()).isFalse();
      assertThat(result.messages()).anyMatch(message -> message.text().contains("Totally-Made-Up"));
    }

    @Test
    @DisplayName("chooses the signed rules when the signing attribute is present")
    void signedRules() {
      var manifest =
          new Manifest(
              List.of(
                  archive(),
                  packageSection(
                      new ManifestAttribute(
                          "Package-Author-Certificate-Hash", "abcDEF123_-abcDEF123_-abcDEF")),
                  applicationSection()));

      assertThat(ManifestValidator.validate(manifest).rules()).isEqualTo("signed");
    }
  }
}
