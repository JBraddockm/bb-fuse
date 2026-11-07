package com.jbraddockm.core.bar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.testing.Resources;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("A manifest")
class ManifestTest {

  private static byte[] bytes(String resource) {
    return Resources.bytes(ManifestTest.class, resource);
  }

  @Nested
  @DisplayName("read from bytes")
  class Reading {

    @Test
    @DisplayName("exposes the package attributes of a real application manifest")
    void realApplication() throws Exception {
      var manifest = Manifest.parse(bytes("real-app-manifest.mf"));

      assertThat(manifest.sections()).hasSize(10);
      assertThat(manifest.value("Package-Type")).hasValue("application");
      assertThat(manifest.value("Package-Name")).hasValue("com.example.TLSDeBug");
      assertThat(manifest.value("Package-Version")).hasValue("1.0.0.1");
      assertThat(manifest.value("Application-Development-Mode")).hasValue("false");
    }

    @Test
    @DisplayName("tolerates a byte-order mark and joins continuation lines")
    void bomAndContinuations() throws Exception {
      var manifest = Manifest.parse(bytes("manifest--awkward.mf"));

      assertThat(manifest.value("Archive-Manifest-Version")).hasValue("1.5");
      assertThat(manifest.value("Package-Name"))
          .hasValue("a long name that continues on the next line");
    }

    @Test
    @DisplayName("parses a large CRLF-terminated signed application manifest")
    void largeSignedApplication() throws Exception {
      var manifest = Manifest.parse(bytes("manifest--signed-app.mf"));

      assertThat(manifest.sections()).hasSize(665);
      assertThat(manifest.value("Package-Name")).hasValue("PowerTools");
      assertThat(manifest.value("Package-Author-Certificate-Hash")).isPresent();
      assertThat(manifest.value("Application-Development-Mode")).hasValue("false");
    }

    @Test
    @DisplayName("parses a signed system-data (driver) manifest")
    void systemDriver() throws Exception {
      var manifest = Manifest.parse(bytes("manifest--system-driver.mf"));

      assertThat(manifest.value("Package-Type")).hasValue("system-data");
      assertThat(manifest.value("System-Data-Type")).hasValue("os_driver");
      assertThat(manifest.value("System-Data-Persistence")).hasValue("removable");
    }

    @Test
    @DisplayName("looks attribute names up without regard to case")
    void caseInsensitive() throws Exception {
      var manifest = Manifest.parse(bytes("real-app-manifest.mf"));

      assertThat(manifest.value("package-name")).isEqualTo(manifest.value("Package-Name"));
    }

    @Test
    @DisplayName("rejects a non-continuation line that has no name")
    void rejectsNamelessEntry() {
      assertThatThrownBy(() -> Manifest.parse(bytes("manifest--broken.mf")))
          .isInstanceOf(InvalidBarException.class);
    }
  }

  @Nested
  @DisplayName("written back to bytes")
  class Writing {

    @Test
    @DisplayName("round-trips a real manifest byte for byte")
    void byteForByteRoundTrip() throws Exception {
      byte[] original = bytes("real-app-manifest.mf");

      assertThat(Manifest.parse(original).toBytes()).isEqualTo(original);
    }

    @Test
    @DisplayName("round-trips a large CRLF manifest without loss (writing LF)")
    void semanticRoundTrip() throws Exception {
      var manifest = Manifest.parse(bytes("manifest--signed-app.mf"));

      assertThat(Manifest.parse(manifest.toBytes())).isEqualTo(manifest);
    }

    @Test
    @DisplayName(
        "writes the BlackBerry layout: LF, a blank line between sections, no trailing blank")
    void layout() throws Exception {
      var manifest =
          new Manifest(
              List.of(
                  ManifestSection.of(new ManifestAttribute("Archive-Manifest-Version", "1.6")),
                  ManifestSection.of(
                      new ManifestAttribute("Package-Type", "application"),
                      new ManifestAttribute("Package-Name", "com.example.app"))));

      var text = new String(manifest.toBytes(), StandardCharsets.UTF_8);

      assertThat(text).doesNotContain("\r");
      assertThat(text).endsWith("com.example.app\n");
      assertThat(text).doesNotEndWith("\n\n");
      assertThat(text).contains("Archive-Manifest-Version: 1.6\n\nPackage-Type: application\n");
      assertThat(Manifest.parse(manifest.toBytes())).isEqualTo(manifest);
    }
  }
}
