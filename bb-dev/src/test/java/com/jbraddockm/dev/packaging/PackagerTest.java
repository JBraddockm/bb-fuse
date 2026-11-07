package com.jbraddockm.dev.packaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.bar.BarArchive;
import com.jbraddockm.core.bar.BarAsset;
import com.jbraddockm.core.bar.BarInspector;
import com.jbraddockm.dev.testing.Resources;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Packaging an application")
class PackagerTest {

  @TempDir private Path project;

  private static final byte[] BINARY = {0x7F, 'E', 'L', 'F', 1, 2, 3};

  private Path descriptor(String fixture) throws IOException {
    return Files.writeString(
        project.resolve("bar-descriptor.xml"), Resources.text(PackagerTest.class, fixture));
  }

  private Path file(String relative, byte[] content) throws IOException {
    var file = project.resolve(relative);
    Files.createDirectories(file.getParent());
    return Files.write(file, content);
  }

  @Nested
  @DisplayName("for a chosen configuration")
  class Chosen {

    private Path pack() throws Exception {
      var descriptorFile = descriptor("descriptor--native.xml");
      file("arm/binary", BINARY);
      file("public.key", new byte[] {'k'});
      file("myapp-icon.png", new byte[] {(byte) 0x89, 'P', 'N', 'G'});
      var bar = project.resolve("app.bar");
      Packager.pack(descriptorFile, project, "Device-Release", bar);
      return bar;
    }

    @Test
    @DisplayName("builds a bar bb-core validates clean, with that configuration's architecture")
    void validates() throws Exception {
      var bar = pack();

      assertThat(BarInspector.validate(bar).valid()).isTrue();
      var info = BarInspector.inspect(bar);
      assertThat(info.packageName()).isEqualTo("com.example.myapp");
      assertThat(info.architecture()).isEqualTo("armle-v7");
      assertThat(info.developmentMode()).isTrue();
      assertThat(info.signed()).isFalse();
    }

    @Test
    @DisplayName("packs the top-level and the chosen configuration's assets, not other configs'")
    void packsChosenConfiguration() throws Exception {
      try (var archive = BarArchive.open(pack())) {
        assertThat(archive.assets())
            .extracting(BarAsset::path)
            .containsExactlyInAnyOrder(
                "native/binary",
                "native/public.key",
                "native/myapp-icon.png",
                "native/bar-descriptor.xml");
        assertThat(archive.manifest().value("Entry-Point")).hasValue("1=DEBUG app/native/binary");
        assertThat(archive.manifest().value("Entry-Point-Icon")).hasValue("native/myapp-icon.png");
      }
    }
  }

  @Nested
  @DisplayName("with no configuration")
  class NoConfiguration {

    @Test
    @DisplayName("packs the top-level assets and the descriptor alone")
    void minimal() throws Exception {
      var descriptorFile = descriptor("descriptor--minimal.xml");
      file("arm/minimal", BINARY);
      var bar = project.resolve("minimal.bar");

      Packager.pack(descriptorFile, project, null, bar);

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
  @DisplayName("that fails")
  class Failing {

    @Test
    @DisplayName("when the chosen configuration is not in the descriptor")
    void unknownConfiguration() throws Exception {
      var descriptorFile = descriptor("descriptor--native.xml");
      file("arm/binary", BINARY);

      assertThatThrownBy(
              () -> Packager.pack(descriptorFile, project, "Device-Nope", project.resolve("x.bar")))
          .isInstanceOf(InvalidDescriptorException.class)
          .hasMessageContaining("no configuration named 'Device-Nope'");
    }
  }

  @Nested
  @DisplayName("with several entry points")
  class MultiEntry {

    @Test
    @DisplayName("writes one Entry-Point per entryPoint, keyed by id, with merged permissions")
    void writesEachEntryPoint() throws Exception {
      var descriptorFile = descriptor("descriptor--multi-entry.xml");
      file("arm/main.so", BINARY);
      file("arm/service", BINARY);
      var bar = project.resolve("multi.bar");

      Packager.pack(descriptorFile, project, "Device-Release", bar);

      assertThat(BarInspector.validate(bar).valid()).isTrue();
      try (var archive = BarArchive.open(bar)) {
        var sections = archive.manifest().sections();
        assertThat(sections)
            .filteredOn(section -> section.value("Entry-Point-Key").isPresent())
            .extracting(section -> section.value("Entry-Point-Key").orElseThrow())
            .containsExactly("main", "service");

        var service =
            sections.stream()
                .filter(
                    section ->
                        section.value("Entry-Point-Key").filter("service"::equals).isPresent())
                .findFirst()
                .orElseThrow();
        assertThat(service.value("Entry-Point-Name")).hasValue("Multi Service");
        assertThat(service.value("Entry-Point-Type")).hasValue("Qnx/Elf");
        assertThat(service.value("Entry-Point")).hasValue("app/native/service");
        assertThat(service.value("Entry-Point-System-Actions"))
            .hasValue("_sys_run_headless,run_native");
        assertThat(archive.manifest().value("Invoke-Target-Entry-Point-Ref")).hasValue("service");
      }
    }
  }

  @Nested
  @DisplayName("with splash screens")
  class Splash {

    private byte[] png(int width, int height) {
      var b = new byte[24];
      b[0] = (byte) 0x89;
      b[1] = 'P';
      b[2] = 'N';
      b[3] = 'G';
      b[12] = 'I';
      b[13] = 'H';
      b[14] = 'D';
      b[15] = 'R';
      for (int i = 0; i < 4; i++) {
        b[16 + i] = (byte) (width >>> (24 - 8 * i));
        b[20 + i] = (byte) (height >>> (24 - 8 * i));
      }
      return b;
    }

    @Test
    @DisplayName("sizes each image and lists them in Entry-Point-Splash-Screen")
    void writesSplashScreens() throws Exception {
      var descriptorFile = descriptor("descriptor--splash.xml");
      file("arm/app", BINARY);
      file("splash_land.png", png(800, 480));
      file("splash_port.png", png(480, 800));
      var bar = project.resolve("splash.bar");

      Packager.pack(descriptorFile, project, null, bar);

      assertThat(BarInspector.validate(bar).valid()).isTrue();
      try (var archive = BarArchive.open(bar)) {
        assertThat(archive.manifest().value("Entry-Point-Splash-Screen"))
            .hasValue("{800x480}native/splash_land.png:{480x800}native/splash_port.png");
        assertThat(archive.assets())
            .extracting(BarAsset::path)
            .contains("native/splash_land.png", "native/splash_port.png");
      }
    }
  }

  @Nested
  @DisplayName("with public assets, excludes and an invoke-target icon")
  class LooseEnds {

    private byte[] png(int width, int height) {
      var b = new byte[24];
      b[0] = (byte) 0x89;
      b[1] = 'P';
      b[2] = 'N';
      b[3] = 'G';
      b[12] = 'I';
      b[13] = 'H';
      b[14] = 'D';
      b[15] = 'R';
      for (int i = 0; i < 4; i++) {
        b[16 + i] = (byte) (width >>> (24 - 8 * i));
        b[20 + i] = (byte) (height >>> (24 - 8 * i));
      }
      return b;
    }

    @Test
    @DisplayName("packs public under public/, drops excluded and junk files, sizes the invoke icon")
    void handlesLooseEnds() throws Exception {
      var descriptorFile = descriptor("descriptor--loose.xml");
      file("arm/app", BINARY);
      file("icon.png", png(100, 100));
      file("pub/lib.so", BINARY);
      file("data/keep.txt", new byte[] {1});
      file("data/drop.tmp", new byte[] {2});
      file("data/.DS_Store", new byte[] {3});
      var bar = project.resolve("loose.bar");

      Packager.pack(descriptorFile, project, null, bar);

      assertThat(BarInspector.validate(bar).valid()).isTrue();
      try (var archive = BarArchive.open(bar)) {
        var paths = archive.assets().stream().map(BarAsset::path).toList();
        assertThat(paths).contains("public/lib/pub.so", "native/data/keep.txt");
        assertThat(paths).doesNotContain("native/data/drop.tmp", "native/data/.DS_Store");
        assertThat(archive.manifest().value("Invoke-Target-Icon"))
            .hasValue("{100x100}native/icon.png");
      }
    }
  }

  @Nested
  @DisplayName("with invocation and configuration metadata")
  class Invocation {

    @Test
    @DisplayName("emits entryPointType, cascadesTheme, packageLocale and the Invoke-Target section")
    void emitsRichManifest() throws Exception {
      var descriptorFile = descriptor("descriptor--invoke.xml");
      file("arm/so.le-v7/libInvoker.so.1", BINARY);
      var bar = project.resolve("invoke.bar");

      Packager.pack(descriptorFile, project, "Device-Release", bar);

      assertThat(BarInspector.validate(bar).valid()).isTrue();
      try (var archive = BarArchive.open(bar)) {
        var manifest = archive.manifest();
        assertThat(manifest.value("Package-Locale")).hasValue("en_US,fr_FR");
        assertThat(manifest.value("Entry-Point-Type")).hasValue("Qnx/Cascades");
        assertThat(manifest.value("Entry-Point"))
            .hasValue("CASCADES_THEME=bright app/native/libInvoker.so.1");
        assertThat(manifest.value("Entry-Point-User-Actions")).hasValue("access_internet");
        assertThat(manifest.value("Entry-Point-System-Actions")).hasValue("run_native");
        assertThat(manifest.value("Invoke-Target-Key")).hasValue("com.example.invoke.card");
        assertThat(manifest.value("Invoke-Target-Entry-Point-Ref")).hasValue("e1");
        assertThat(manifest.value("Invoke-Target-Type")).hasValue("card.composer");
        assertThat(manifest.value("Invoke-Target-Label")).hasValue("Invoke Card");
        assertThat(manifest.value("Invoke-Target-Filter"))
            .hasValue("actions=bb.action.OPEN,bb.action.VIEW;types=text/plain;uris=invoke://;");
      }
    }
  }
}
