package com.jbraddockm.dev.packaging.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.bar.Manifest;
import com.jbraddockm.core.bar.ManifestValidator;
import com.jbraddockm.dev.packaging.descriptor.BarDescriptor;
import com.jbraddockm.dev.packaging.descriptor.InvalidDescriptorException;
import com.jbraddockm.dev.testing.Resources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Generating a manifest from a descriptor")
class ManifestGeneratorTest {

  private static Manifest generate(String fixture) throws InvalidDescriptorException {
    return ManifestGenerator.generate(
        BarDescriptor.parse(Resources.text(ManifestGeneratorTest.class, fixture)));
  }

  @Nested
  @DisplayName("maps the descriptor fields")
  class Mapping {

    @Test
    @DisplayName(
        "to the Archive, Package and Application attributes, with the four-segment version")
    void mapsFields() throws Exception {
      var manifest = generate("descriptor--native.xml");

      assertThat(manifest.value("Archive-Manifest-Version")).hasValue("1.6");
      assertThat(manifest.value("Archive-Created-By")).hasValue("bb-dev");
      assertThat(manifest.value("Package-Type")).hasValue("application");
      assertThat(manifest.value("Package-Name")).hasValue("com.example.myapp");
      assertThat(manifest.value("Package-Author")).hasValue("Jane Doe");
      assertThat(manifest.value("Package-Version")).hasValue("1.0.0.0");
      assertThat(manifest.value("Package-Architecture")).hasValue("armle-v7");
      assertThat(manifest.value("Package-Description")).hasValue("This is a great app");
      assertThat(manifest.value("Application-Name")).hasValue("My App");
      assertThat(manifest.value("Application-Version")).hasValue("1.0.0.0");
      assertThat(manifest.value("Application-Category")).hasValue("core.games");
      assertThat(manifest.value("Application-Development-Mode")).hasValue("false");
      assertThat(manifest.value("Application-Requires-System")).hasValue("BlackBerry 10/2.0.0.0");
    }

    @Test
    @DisplayName("using the dev defaults when the author, architecture and system are absent")
    void usesDefaults() throws Exception {
      var manifest = generate("descriptor--minimal.xml");

      assertThat(manifest.value("Package-Author")).hasValue("Development");
      assertThat(manifest.value("Package-Version")).hasValue("1.0.0.0");
      assertThat(manifest.value("Package-Architecture")).hasValue("armle-v7");
      assertThat(manifest.value("Application-Requires-System")).isEmpty();
      assertThat(manifest.value("Application-Development-Mode")).hasValue("false");
    }

    @Test
    @DisplayName("the development identity the device needs, but never a signature")
    void writesDevelopmentIdentity() throws Exception {
      var manifest = generate("descriptor--native.xml");

      // The device needs these generated ids to register an unsigned package, with or without a
      // signature.
      assertThat(manifest.value("Package-Id")).hasValueSatisfying(id -> assertThat(id).hasSize(27));
      assertThat(manifest.value("Application-Id")).isEqualTo(manifest.value("Package-Id"));
      assertThat(manifest.value("Package-Author-Id"))
          .hasValueSatisfying(id -> assertThat(id).hasSize(27));
      assertThat(manifest.value("Package-Version-Id"))
          .hasValueSatisfying(id -> assertThat(id).hasSize(27));
      // But no actual signature.
      assertThat(manifest.value("Package-Author-Certificate-Hash")).isEmpty();
    }
  }

  @Nested
  @DisplayName("produces a manifest that")
  class Validates {

    @Test
    @DisplayName("passes bb-core's unsigned-application rules, in full and at the minimum")
    void validatesClean() throws Exception {
      for (var fixture : new String[] {"descriptor--native.xml", "descriptor--minimal.xml"}) {
        var validation = ManifestValidator.validate(generate(fixture));

        assertThat(validation.rules()).isEqualTo("unsigned");
        assertThat(validation.valid()).isTrue();
        assertThat(validation.messages()).isEmpty();
      }
    }
  }
}
