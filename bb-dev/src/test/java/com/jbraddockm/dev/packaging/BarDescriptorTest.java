package com.jbraddockm.dev.packaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.dev.packaging.descriptor.BarDescriptor;
import com.jbraddockm.dev.packaging.descriptor.DescriptorAsset;
import com.jbraddockm.dev.packaging.descriptor.DescriptorConfiguration;
import com.jbraddockm.dev.packaging.descriptor.EnvironmentVariable;
import com.jbraddockm.dev.packaging.descriptor.InitialWindow;
import com.jbraddockm.dev.packaging.descriptor.InvalidDescriptorException;
import com.jbraddockm.dev.packaging.descriptor.Permission;
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
      assertThat(descriptor.permissions())
          .containsExactly(
              new Permission("access_internet", false), new Permission("use_camera", false));
      assertThat(descriptor.platformVersion()).isEqualTo("2.0.0.0");
    }

    @Test
    @DisplayName("reads the top-level assets, with the entry flag and the optional attributes")
    void readsAssets() throws Exception {
      var descriptor = parse("descriptor--native.xml");

      assertThat(descriptor.assets())
          .containsExactly(
              new DescriptorAsset(
                  "arm/binary", "binary", "Qnx/Elf", true, null, List.of(), List.of(), false));
    }

    @Test
    @DisplayName("reads each configuration by name, with its architecture, arguments and assets")
    void readsConfigurations() throws Exception {
      var descriptor = parse("descriptor--native.xml");

      assertThat(descriptor.configurations())
          .containsExactly(
              new DescriptorConfiguration(
                  "Device-Debug", "id1", "armv7-le", null, List.of("-v"), List.of()),
              new DescriptorConfiguration(
                  "Device-Release",
                  "id3",
                  "armv7-le",
                  null,
                  List.of(),
                  List.of(
                      new DescriptorAsset(
                          "public.key", null, null, false, null, List.of(), List.of(), false))));
    }

    @Test
    @DisplayName("accepts a configuration with no name, since the name is optional")
    void readsUnnamedConfiguration() throws Exception {
      var descriptor = parse("descriptor--config-no-name.xml");

      assertThat(descriptor.configurations())
          .singleElement()
          .satisfies(
              configuration -> {
                assertThat(configuration.name()).isNull();
                assertThat(configuration.id()).isEqualTo("id1");
              });
    }

    @Test
    @DisplayName("takes an asset's destination from its text and reads includes and system actions")
    void readsTextDestinationAndSystemPermission() throws Exception {
      var descriptor = parse("descriptor--text-dest.xml");

      assertThat(descriptor.assets())
          .containsExactly(
              new DescriptorAsset(
                  "arm/app", "app", "Qnx/Elf", true, null, List.of(), List.of(), false),
              new DescriptorAsset(
                  "lib/libx.so.4",
                  "lib/libx.so.4",
                  "Qnx/Elf",
                  false,
                  null,
                  List.of(),
                  List.of(),
                  false),
              new DescriptorAsset(
                  "translations", "qm", null, false, null, List.of("*.qm"), List.of(), false));
      assertThat(descriptor.permissions())
          .containsExactly(
              new Permission("run_native", true), new Permission("access_internet", false));
    }

    @Test
    @DisplayName("reads a localized name, description and icon from their first text variant")
    void readsLocalizedText() throws Exception {
      var descriptor = parse("descriptor--localized.xml");

      assertThat(descriptor.name()).isEqualTo("Hello");
      assertThat(descriptor.description()).isEqualTo("A localized app");
      assertThat(descriptor.icons()).containsExactly("icon.png");
      assertThat(descriptor.environment())
          .containsExactly(new EnvironmentVariable("DEBUG_ENABLE", ""));
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
          .containsExactly(
              new DescriptorAsset(
                  "arm/minimal", null, null, true, null, List.of(), List.of(), false));
    }
  }

  @Nested
  @DisplayName("entry point")
  class EntryPoint {

    @Test
    @DisplayName("is the asset marked entry=\"true\"")
    void findsTheEntryAsset() throws Exception {
      assertThat(parse("descriptor--native.xml").entryPoint())
          .hasValue(
              new DescriptorAsset(
                  "arm/binary", "binary", "Qnx/Elf", true, null, List.of(), List.of(), false));
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
    @DisplayName("an asset with neither a path nor a destination")
    void assetWithoutPathOrDestination() {
      assertThatThrownBy(() -> parse("descriptor--asset-no-path.xml"))
          .isInstanceOf(InvalidDescriptorException.class)
          .hasMessage("an asset has neither a path nor a destination");
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
