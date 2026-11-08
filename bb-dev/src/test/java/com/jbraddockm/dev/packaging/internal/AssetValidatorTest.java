package com.jbraddockm.dev.packaging.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.bar.BarValidation.Message;
import com.jbraddockm.dev.packaging.descriptor.BarDescriptor;
import com.jbraddockm.dev.packaging.descriptor.DescriptorAsset;
import com.jbraddockm.dev.packaging.descriptor.DescriptorConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Validating a descriptor's assets")
class AssetValidatorTest {

  @TempDir private Path folder;

  private Path realFile(String name) throws IOException {
    return Files.write(folder.resolve(name), new byte[] {1, 2, 3});
  }

  private static BarDescriptor withAssets(DescriptorAsset... assets) {
    return descriptor(List.of(assets), List.of());
  }

  private static BarDescriptor descriptor(
      List<DescriptorAsset> assets, List<DescriptorConfiguration> configurations) {
    return new BarDescriptor(
        "com.example.app",
        "App",
        null,
        "1.0.0",
        null,
        List.of(),
        null,
        null,
        null,
        null,
        null,
        null,
        List.of(),
        null,
        List.of(),
        assets,
        null,
        configurations,
        null,
        null,
        null,
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  @Nested
  @DisplayName("warns about")
  class Warns {

    @Test
    @DisplayName("an absolute path")
    void absolutePath() throws Exception {
      var descriptor =
          withAssets(
              new DescriptorAsset(
                  "/absolute/binary", null, null, true, null, List.of(), List.of(), false));
      var validation =
          AssetValidator.validate(descriptor, Map.of("/absolute/binary", realFile("binary")));

      assertThat(validation.messages())
          .singleElement()
          .extracting(Message::text)
          .asString()
          .contains("cannot be absolute", "/absolute/binary");
    }

    @Test
    @DisplayName("a path with a backslash, colon or semicolon")
    void wrongSeparators() throws Exception {
      var descriptor =
          withAssets(
              new DescriptorAsset(
                  "dir\\binary", null, null, true, null, List.of(), List.of(), false));
      var validation =
          AssetValidator.validate(descriptor, Map.of("dir\\binary", realFile("binary")));

      assertThat(validation.messages())
          .singleElement()
          .extracting(Message::text)
          .asString()
          .contains("'/' as its separator");
    }

    @Test
    @DisplayName("a path longer than 255 characters")
    void tooLong() throws Exception {
      var longPath = "a".repeat(AssetValidator.MAXIMUM_PATH_LENGTH + 1);
      var descriptor =
          withAssets(
              new DescriptorAsset(longPath, null, null, true, null, List.of(), List.of(), false));
      var validation = AssetValidator.validate(descriptor, Map.of(longPath, realFile("binary")));

      assertThat(validation.messages())
          .singleElement()
          .extracting(Message::text)
          .asString()
          .contains("longer than 255");
    }

    @Test
    @DisplayName("a path that is not among the packaged files")
    void notPackaged() {
      var descriptor =
          withAssets(
              new DescriptorAsset(
                  "missing/binary", null, null, true, null, List.of(), List.of(), false));
      var validation = AssetValidator.validate(descriptor, Map.of());

      assertThat(validation.messages())
          .singleElement()
          .extracting(Message::text)
          .asString()
          .contains("not among the packaged files", "missing/binary");
    }

    @Test
    @DisplayName("a packaged file that does not exist on the host")
    void missingFile() {
      var descriptor =
          withAssets(
              new DescriptorAsset("binary", null, null, true, null, List.of(), List.of(), false));
      var validation =
          AssetValidator.validate(descriptor, Map.of("binary", folder.resolve("absent")));

      assertThat(validation.messages())
          .singleElement()
          .extracting(Message::text)
          .asString()
          .contains("does not exist", "binary");
    }

    @Test
    @DisplayName("an asset declared only inside a configuration")
    void configurationAsset() throws Exception {
      var descriptor =
          descriptor(
              List.of(
                  new DescriptorAsset(
                      "arm/binary", null, null, true, null, List.of(), List.of(), false)),
              List.of(
                  new DescriptorConfiguration(
                      "Device-Release",
                      "id1",
                      null,
                      null,
                      List.of(),
                      List.of(
                          new DescriptorAsset(
                              "/bad/extra",
                              null,
                              null,
                              false,
                              null,
                              List.of(),
                              List.of(),
                              false)))));
      var validation =
          AssetValidator.validate(
              descriptor,
              Map.of("arm/binary", realFile("binary"), "/bad/extra", realFile("extra")));

      assertThat(validation.messages())
          .singleElement()
          .extracting(Message::text)
          .asString()
          .contains("cannot be absolute", "/bad/extra");
    }
  }

  @Nested
  @DisplayName("accepts")
  class Accepts {

    @Test
    @DisplayName("a relative path that names an existing packaged file")
    void cleanAsset() throws Exception {
      var descriptor =
          withAssets(
              new DescriptorAsset(
                  "arm/binary", "binary", "Qnx/Elf", true, null, List.of(), List.of(), false));
      var validation =
          AssetValidator.validate(descriptor, Map.of("arm/binary", realFile("binary")));

      assertThat(validation.clean()).isTrue();
      assertThat(validation.messages()).isEmpty();
    }

    @Test
    @DisplayName("the default icon even though the packager supplies it, not the user")
    void defaultIcon() {
      var descriptor =
          withAssets(
              new DescriptorAsset(
                  AssetValidator.DEFAULT_ICON,
                  null,
                  null,
                  false,
                  null,
                  List.of(),
                  List.of(),
                  false),
              new DescriptorAsset(
                  "arm/binary", null, null, true, null, List.of(), List.of(), false));
      var validation = AssetValidator.validate(descriptor, Map.of("arm/binary", folder));

      assertThat(validation.messages())
          .extracting(Message::text)
          .noneSatisfy(text -> assertThat(text).contains(AssetValidator.DEFAULT_ICON));
    }
  }
}
