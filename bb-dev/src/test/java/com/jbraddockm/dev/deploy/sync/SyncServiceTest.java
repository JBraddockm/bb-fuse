package com.jbraddockm.dev.deploy.sync;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.bar.Manifest;
import com.jbraddockm.core.bar.ManifestAsset;
import com.jbraddockm.core.bar.ManifestAttribute;
import com.jbraddockm.core.bar.ManifestSection;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("The dev-mode sync service")
class SyncServiceTest {

  private static ManifestSection asset(String name, String digest) {
    return ManifestSection.of(
        new ManifestAttribute("Archive-Asset-Name", name),
        new ManifestAttribute("Archive-Asset-SHA-512-Digest", digest));
  }

  @Nested
  @DisplayName("deciding whether sync applies")
  class Compatibility {

    @Test
    @DisplayName("applies to a development-mode bar with a native entry point")
    void nativeDevelopmentBar() {
      var manifest =
          new Manifest(
              List.of(
                  ManifestSection.of(
                      new ManifestAttribute("Application-Development-Mode", "true"),
                      new ManifestAttribute("Entry-Point-Type", "Qnx/Elf"))));

      assertThat(SyncService.isSyncCompatible(manifest)).isTrue();
    }

    @Test
    @DisplayName("does not apply when the bar is not built for development mode")
    void releaseBar() {
      var manifest =
          new Manifest(
              List.of(
                  ManifestSection.of(
                      new ManifestAttribute("Application-Development-Mode", "false"),
                      new ManifestAttribute("Entry-Point-Type", "Qnx/Elf"))));

      assertThat(SyncService.isSyncCompatible(manifest)).isFalse();
    }

    @Test
    @DisplayName("does not apply to an Android-runtime entry point")
    void androidEntryPoint() {
      var manifest =
          new Manifest(
              List.of(
                  ManifestSection.of(
                      new ManifestAttribute("Application-Development-Mode", "true"),
                      new ManifestAttribute("Entry-Point-Type", "Qnx/Android"))));

      assertThat(SyncService.isSyncCompatible(manifest)).isFalse();
    }
  }

  @Test
  @DisplayName("computes the personal-perimeter device sandbox path")
  void deviceAppBasePath() {
    var manifest =
        new Manifest(
            List.of(
                ManifestSection.of(
                    new ManifestAttribute("Package-Name", "com.example.App"),
                    new ManifestAttribute("Package-Id", "testRel_le_App-abc123"))));

    assertThat(SyncService.deviceAppBasePath(manifest))
        .isEqualTo("/apps/com.example.App.testRel_le_App.abc123/");
  }

  @Nested
  @DisplayName("choosing the assets to upload")
  class Diff {

    @Test
    @DisplayName("uploads the assets the device is missing or holds at a different digest")
    void changedAndNew() {
      var host =
          new Manifest(
              List.of(
                  asset("native/a", "dig-a"),
                  asset("native/b", "dig-b2"),
                  asset("native/c", "dig-c")));
      var device = new Manifest(List.of(asset("native/a", "dig-a"), asset("native/b", "dig-b1")));

      assertThat(SyncService.assetsToUpload(host, device))
          .extracting(ManifestAsset::name)
          .containsExactly("native/b", "native/c");
    }

    @Test
    @DisplayName("uploads everything when the device has no manifest yet")
    void emptyDevice() {
      var host = new Manifest(List.of(asset("native/a", "dig-a"), asset("native/b", "dig-b")));

      assertThat(SyncService.assetsToUpload(host, new Manifest(List.of())))
          .extracting(ManifestAsset::name)
          .containsExactly("native/a", "native/b");
    }

    @Test
    @DisplayName("uploads nothing when every digest already matches")
    void allInSync() {
      var manifest = new Manifest(List.of(asset("native/a", "dig-a"), asset("native/b", "dig-b")));

      assertThat(SyncService.assetsToUpload(manifest, manifest)).isEmpty();
    }
  }
}
