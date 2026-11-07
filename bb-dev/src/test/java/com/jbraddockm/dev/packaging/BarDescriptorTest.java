package com.jbraddockm.dev.packaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.dev.testing.Resources;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("A bar-descriptor.xml")
class BarDescriptorTest {

  private static BarDescriptor parse(String fixture) throws InvalidDescriptorException {
    return BarDescriptor.parse(Resources.text(BarDescriptorTest.class, fixture));
  }

  @Nested
  @DisplayName("read in full")
  class Reading {

    @Test
    @DisplayName("gives every native field, in document order, past the DOCTYPE")
    void readsEveryField() throws Exception {
      var descriptor = parse("descriptor--native.xml");

      assertThat(descriptor.id()).isEqualTo("com.example.myapp");
      assertThat(descriptor.name()).isEqualTo("My App");
      assertThat(descriptor.description()).isEqualTo("This is a great app");
      assertThat(descriptor.versionNumber()).isEqualTo("1.0.0");
      assertThat(descriptor.initialWindow()).isEqualTo(new InitialWindow(false, "portrait"));
      assertThat(descriptor.icons()).containsExactly("myapp-icon.png");
      assertThat(descriptor.author()).isEqualTo("Jane Doe");
      assertThat(descriptor.authorId()).isEqualTo("exampleAuthorId");
      assertThat(descriptor.packageId()).isNull();
      assertThat(descriptor.buildId()).isNull();
      assertThat(descriptor.buildIdFile()).isEqualTo("buildIdFile.txt");
      assertThat(descriptor.category()).isEqualTo("core.games");
      assertThat(descriptor.environment()).containsExactly(new EnvironmentVariable("1", "DEBUG"));
      assertThat(descriptor.splashScreen()).isEqualTo("splashscreen.jpg:splash_portrait.jpg");
      assertThat(descriptor.permissions()).containsExactly("access_internet", "use_camera");
      assertThat(descriptor.platformVersion()).isEqualTo("2.0.0.0");
    }

    @Test
    @DisplayName("reads the top-level assets, with the entry flag and the optional attributes")
    void readsAssets() throws Exception {
      var descriptor = parse("descriptor--native.xml");

      assertThat(descriptor.assets())
          .containsExactly(new DescriptorAsset("arm/binary", "binary", "Qnx/Elf", true));
    }

    @Test
    @DisplayName("reads each configuration's architecture, arguments and extra assets")
    void readsConfigurations() throws Exception {
      var descriptor = parse("descriptor--native.xml");

      assertThat(descriptor.configurations())
          .containsExactly(
              new DescriptorConfiguration(
                  "id1", "Device-Debug", "armv7-le", List.of("-v"), List.of()),
              new DescriptorConfiguration(
                  "id3",
                  "Device-Release",
                  "armv7-le",
                  List.of(),
                  List.of(new DescriptorAsset("public.key", null, null, false))));
    }

    @Test
    @DisplayName("leaves every absent element null or empty when only the mandatory fields are set")
    void readsMinimal() throws Exception {
      var descriptor = parse("descriptor--minimal.xml");

      assertThat(descriptor.id()).isEqualTo("com.example.minimal");
      assertThat(descriptor.name()).isEqualTo("Minimal");
      assertThat(descriptor.versionNumber()).isEqualTo("1.0.0");
      assertThat(descriptor.description()).isNull();
      assertThat(descriptor.initialWindow()).isNull();
      assertThat(descriptor.category()).isNull();
      assertThat(descriptor.icons()).isEmpty();
      assertThat(descriptor.environment()).isEmpty();
      assertThat(descriptor.permissions()).isEmpty();
      assertThat(descriptor.configurations()).isEmpty();
      assertThat(descriptor.assets())
          .containsExactly(new DescriptorAsset("arm/minimal", null, null, true));
    }
  }

  @Nested
  @DisplayName("entry point")
  class EntryPoint {

    @Test
    @DisplayName("is the asset marked entry=\"true\"")
    void findsTheEntryAsset() throws Exception {
      assertThat(parse("descriptor--native.xml").entryPoint())
          .hasValue(new DescriptorAsset("arm/binary", "binary", "Qnx/Elf", true));
    }
  }

  @Nested
  @DisplayName("that is refused")
  class Refusing {

    @Test
    @DisplayName("a root that is not qnx")
    void rootIsNotQnx() {
      assertThatThrownBy(() -> parse("descriptor--not-qnx.xml"))
          .isInstanceOf(InvalidDescriptorException.class)
          .hasMessage("its root element is 'application', not 'qnx'");
    }

    @Test
    @DisplayName("a missing mandatory element")
    void missingMandatoryElement() {
      assertThatThrownBy(() -> parse("descriptor--no-id.xml"))
          .isInstanceOf(InvalidDescriptorException.class)
          .hasMessage("it has no id");
    }

    @Test
    @DisplayName("no asset marked as the entry point")
    void noEntryPoint() {
      assertThatThrownBy(() -> parse("descriptor--no-entry.xml"))
          .isInstanceOf(InvalidDescriptorException.class)
          .hasMessageContaining("no entry point");
    }

    @Test
    @DisplayName("an asset with neither a path nor a dest")
    void assetWithoutPathOrDest() {
      assertThatThrownBy(() -> parse("descriptor--asset-no-path.xml"))
          .isInstanceOf(InvalidDescriptorException.class)
          .hasMessage("an asset has neither a path nor a dest");
    }

    @Test
    @DisplayName("a configuration with no id")
    void configurationWithoutId() {
      assertThatThrownBy(() -> parse("descriptor--config-no-id.xml"))
          .isInstanceOf(InvalidDescriptorException.class)
          .hasMessage("its configuration has no id");
    }

    @Test
    @DisplayName("text that is not well-formed XML")
    void notWellFormed() {
      assertThatThrownBy(() -> parse("descriptor--malformed.xml"))
          .isInstanceOf(InvalidDescriptorException.class)
          .hasMessageStartingWith("it is not well-formed XML: ");
    }
  }
}
