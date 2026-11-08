package com.jbraddockm.dev;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.json.Json;
import com.jbraddockm.dev.testing.Resources;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("The bb-dev package command line")
class PackageCliTest {

  @TempDir private Path project;

  private final ByteArrayOutputStream out = new ByteArrayOutputStream();
  private final ByteArrayOutputStream err = new ByteArrayOutputStream();

  private int dispatch(String... arguments) {
    return Launcher.dispatch(
        arguments,
        new PrintStream(out, true, StandardCharsets.UTF_8),
        new PrintStream(err, true, StandardCharsets.UTF_8));
  }

  private String out() {
    return out.toString(StandardCharsets.UTF_8);
  }

  /** Writes the fixture descriptor and its one binary, and returns the descriptor path. */
  private Path sampleProject() throws Exception {
    var descriptor = project.resolve("bar-descriptor.xml");
    Files.writeString(
        descriptor, Resources.text(PackageCliTest.class, "package-cli--descriptor.xml"));
    var binary = project.resolve("arm/app");
    Files.createDirectories(binary.getParent());
    Files.write(binary, new byte[] {0x7F, 'E', 'L', 'F'});
    return descriptor;
  }

  private Path pack() throws Exception {
    var descriptor = sampleProject();
    var bar = project.resolve("app.bar");
    assertThat(dispatch("package", "-package", bar.toString(), descriptor.toString())).isZero();
    out.reset();
    return bar;
  }

  @Nested
  @DisplayName("help and version")
  class HelpAndVersion {

    @Test
    @DisplayName("prints the command menu for 'package -help' and exits zero")
    void packageHelp() {
      assertThat(dispatch("package", "-help")).isZero();
      assertThat(out())
          .contains("bb-dev package - build")
          .contains("-configuration")
          .contains("-buildId");
    }

    @Test
    @DisplayName("prints the top-level menu for 'help' and exits zero")
    void topLevelHelp() {
      assertThat(dispatch("help")).isZero();
      assertThat(out()).contains("all-in-one").contains("connect").contains("package");
    }

    @Test
    @DisplayName("prints the version for 'package -version'")
    void version() {
      assertThat(dispatch("package", "-version")).isZero();
      assertThat(out()).contains("bb-dev package 1.0");
    }
  }

  @Nested
  @DisplayName("scaffolding a descriptor")
  class GenerateXml {

    @Test
    @DisplayName("writes a starter descriptor that parses")
    void writesStarter() throws Exception {
      var file = project.resolve("bar-descriptor.xml");

      assertThat(dispatch("package", "-generateXml", file.toString())).isZero();

      assertThat(Files.readString(file))
          .contains("<qnx")
          .contains("<id>")
          .contains("entry=\"true\"");
    }

    @Test
    @DisplayName("refuses to overwrite an existing file")
    void refusesOverwrite() throws Exception {
      var file = Files.writeString(project.resolve("bar-descriptor.xml"), "keep me");

      assertThat(dispatch("package", "-generateXml", file.toString())).isEqualTo(1);
      assertThat(Files.readString(file)).isEqualTo("keep me");
    }
  }

  @Nested
  @DisplayName("inspecting a bar it built")
  class Inspecting {

    @Test
    @DisplayName("lists the files with -list")
    void list() throws Exception {
      var bar = pack();

      assertThat(dispatch("package", "-list", bar.toString())).isZero();
      assertThat(out()).contains("native/app").contains("native/bar-descriptor.xml");
    }

    @Test
    @DisplayName("prints the manifest with -listManifest")
    void listManifest() throws Exception {
      var bar = pack();

      assertThat(dispatch("package", "-listManifest", bar.toString())).isZero();
      assertThat(out())
          .contains("Archive-Manifest-Version")
          .contains("Package-Name")
          .contains("com.example.cli");
    }

    @Test
    @DisplayName("defaults to a non-development bar, and -devMode opts in")
    void devMode() throws Exception {
      var descriptor = sampleProject();
      var plain = project.resolve("plain.bar");
      var dev = project.resolve("dev.bar");

      assertThat(dispatch("package", "-package", plain.toString(), descriptor.toString())).isZero();
      out.reset();
      assertThat(dispatch("package", "-listManifest", plain.toString())).isZero();
      assertThat(out()).contains("Application-Development-Mode: false");
      out.reset();

      assertThat(dispatch("package", "-package", dev.toString(), "-devMode", descriptor.toString()))
          .isZero();
      out.reset();
      assertThat(dispatch("package", "-listManifest", dev.toString())).isZero();
      assertThat(out()).contains("Application-Development-Mode: true");
    }
  }

  @Nested
  @DisplayName("reading options from a file")
  class OptionFile {

    @Test
    @DisplayName("expands @file into the options it holds")
    void expandsOptionFile() throws Exception {
      var descriptor = sampleProject();
      var bar = project.resolve("app.bar");
      var options =
          Files.writeString(
              project.resolve("opts"), "-package " + bar + System.lineSeparator() + descriptor);

      assertThat(dispatch("package", "@" + options)).isZero();
      assertThat(Files.exists(bar)).isTrue();
    }

    @Test
    @DisplayName("emits one JSON object for a build when --json is given")
    void jsonBuild() throws Exception {
      var descriptor = sampleProject();
      var bar = project.resolve("app.bar");

      assertThat(dispatch("--json", "package", "-package", bar.toString(), descriptor.toString()))
          .isZero();

      var parsed = Json.parse(out().strip());
      assertThat(parsed).isInstanceOf(Map.class);
      @SuppressWarnings("unchecked")
      var object = (Map<String, Object>) parsed;
      assertThat(object).containsEntry("ok", true).containsEntry("bar", bar.toString());
    }
  }
}
