package com.jbraddockm.core.bar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.testing.Resources;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("A bar archive")
class BarArchiveTest {

  @TempDir private Path folder;

  private static final byte[] ICON = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
  private static final byte[] BINARY = {0x7F, 'E', 'L', 'F', 4, 5, 6, 7};

  @Test
  @DisplayName("reads the manifest and lists the assets, not the manifest or directories")
  void readsManifestAndAssets() throws Exception {
    var manifest = Resources.bytes(BarArchiveTest.class, "real-app-manifest.mf");
    var file =
        BarFiles.withEntries(
            folder,
            "app.bar",
            Map.of(
                "META-INF/MANIFEST.MF", manifest,
                "native/icon.png", ICON,
                "native/TLSDeBug", BINARY));

    try (var archive = BarArchive.open(file)) {
      assertThat(archive.manifest().value("Package-Name")).hasValue("com.example.TLSDeBug");
      assertThat(archive.assets())
          .extracting(BarAsset::path)
          .containsExactlyInAnyOrder("native/icon.png", "native/TLSDeBug");
      var icon =
          archive.assets().stream()
              .filter(asset -> asset.path().endsWith("icon.png"))
              .findFirst()
              .orElseThrow();
      try (var stream = archive.open(icon)) {
        assertThat(stream.readAllBytes()).containsExactly(ICON);
      }
    }
  }

  @Test
  @DisplayName("rejects a file that is not a ZIP archive")
  void rejectsNonZip() throws Exception {
    var notZip = Files.write(folder.resolve("x.bar"), new byte[] {1, 2, 3, 4, 5});

    assertThatThrownBy(() -> BarArchive.open(notZip).close())
        .isInstanceOf(InvalidBarException.class);
  }

  @Test
  @DisplayName("rejects an archive that has no manifest")
  void rejectsMissingManifest() throws Exception {
    var file = BarFiles.withEntries(folder, "no-manifest.bar", Map.of("native/x", new byte[] {1}));

    try (var archive = BarArchive.open(file)) {
      assertThatThrownBy(archive::manifest).isInstanceOf(InvalidBarException.class);
    }
  }
}
