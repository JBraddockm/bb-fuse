package com.jbraddockm.dev.packaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.bar.BarArchive;
import com.jbraddockm.core.bar.BarAsset;
import com.jbraddockm.core.bar.BarInspector;
import com.jbraddockm.dev.testing.Resources;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Writing a development .bar")
class BarWriterTest {

  @TempDir private Path folder;

  private static final byte[] BINARY = {0x7F, 'E', 'L', 'F', 1, 2, 3};
  private static final byte[] KEY = {'k', 'e', 'y'};
  private static final byte[] ICON = {(byte) 0x89, 'P', 'N', 'G'};

  private Path descriptorFile(String fixture) throws IOException {
    return Files.writeString(
        folder.resolve("bar-descriptor.xml"), Resources.text(BarWriterTest.class, fixture));
  }

  private Path write(String name, byte[] content) throws IOException {
    return Files.write(folder.resolve(name), content);
  }

  @Nested
  @DisplayName("from a full descriptor")
  class Full {

    private Path bar() throws Exception {
      var file = descriptorFile("descriptor--native.xml");
      var descriptor = BarDescriptor.parse(Files.readString(file));
      var assets =
          Map.of(
              "arm/binary", write("binary", BINARY),
              "public.key", write("public.key", KEY),
              "myapp-icon.png", write("icon.png", ICON));
      var bar = folder.resolve("app.bar");
      BarWriter.write(descriptor, file, assets, bar);
      return bar;
    }

    @Test
    @DisplayName("produces a bar bb-core reads and validates clean as an unsigned dev application")
    void readsAndValidates() throws Exception {
      var bar = bar();

      assertThat(BarInspector.validate(bar).valid()).isTrue();
      var info = BarInspector.inspect(bar);
      assertThat(info.packageName()).isEqualTo("com.example.myapp");
      assertThat(info.packageVersion()).isEqualTo("1.0.0.0");
      assertThat(info.architecture()).isEqualTo("armle-v7");
      assertThat(info.developmentMode()).isTrue();
      assertThat(info.signed()).isFalse();
    }

    @Test
    @DisplayName("flattens every asset and the descriptor under native/, with the bytes intact")
    void flattensAssets() throws Exception {
      try (var archive = BarArchive.open(bar())) {
        assertThat(archive.assets())
            .extracting(BarAsset::path)
            .containsExactlyInAnyOrder(
                "native/binary",
                "native/public.key",
                "native/myapp-icon.png",
                "native/bar-descriptor.xml");

        var binary =
            archive.assets().stream()
                .filter(asset -> asset.path().equals("native/binary"))
                .findFirst()
                .orElseThrow();
        try (var stream = archive.open(binary)) {
          assertThat(stream.readAllBytes()).containsExactly(BINARY);
        }
      }
    }

    @Test
    @DisplayName("points the entry point at the run-time path and carries the icon and actions")
    void writesEntryPoint() throws Exception {
      try (var archive = BarArchive.open(bar())) {
        var manifest = archive.manifest();
        assertThat(manifest.value("Entry-Point")).hasValue("1=DEBUG app/native/binary");
        assertThat(manifest.value("Entry-Point-Key")).hasValue("e1");
        assertThat(manifest.value("Entry-Point-Type")).hasValue("Qnx/Elf");
        assertThat(manifest.value("Entry-Point-Icon")).hasValue("native/myapp-icon.png");
        assertThat(manifest.value("Entry-Point-Orientation")).hasValue("portrait");
        assertThat(manifest.value("Entry-Point-User-Actions"))
            .hasValue("access_internet,use_camera");
        assertThat(manifest.value("Entry-Point-System-Actions")).hasValue("run_native");
      }
    }

    @Test
    @DisplayName("gives each asset a SHA-512 digest over its bytes")
    void writesDigests() throws Exception {
      try (var archive = BarArchive.open(bar())) {
        assertThat(archive.manifest().value("Archive-Asset-SHA-512-Digest")).isPresent();
      }
    }
  }

  @Nested
  @DisplayName("from a minimal descriptor")
  class Minimal {

    @Test
    @DisplayName("writes a clean bar whose entry point is the flattened binary alone")
    void minimal() throws Exception {
      var file = descriptorFile("descriptor--minimal.xml");
      var descriptor = BarDescriptor.parse(Files.readString(file));
      var bar = folder.resolve("minimal.bar");

      BarWriter.write(descriptor, file, Map.of("arm/minimal", write("minimal", BINARY)), bar);

      assertThat(BarInspector.validate(bar).valid()).isTrue();
      try (var archive = BarArchive.open(bar)) {
        assertThat(archive.manifest().value("Entry-Point")).hasValue("app/native/minimal");
        assertThat(archive.assets())
            .extracting(BarAsset::path)
            .containsExactlyInAnyOrder("native/minimal", "native/bar-descriptor.xml");
      }
    }
  }

  @Nested
  @DisplayName("resolving the build id")
  class BuildId {

    @Test
    @DisplayName("reads the fourth version segment from the buildIdFile when there is one")
    void fromBuildIdFile() throws Exception {
      var file = descriptorFile("descriptor--native.xml");
      var descriptor = BarDescriptor.parse(Files.readString(file));
      Files.writeString(folder.resolve("buildIdFile.txt"), "42\n");
      var bar = folder.resolve("app.bar");

      BarWriter.write(
          descriptor,
          file,
          Map.of(
              "arm/binary", write("binary", BINARY),
              "public.key", write("public.key", KEY),
              "myapp-icon.png", write("icon.png", ICON)),
          bar);

      assertThat(BarInspector.inspect(bar).packageVersion()).isEqualTo("1.0.0.42");
    }
  }
}
