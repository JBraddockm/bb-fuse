package com.jbraddockm.dev.packaging.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.dev.packaging.descriptor.DescriptorAsset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link AssetFlattener}: resolving a folder asset's files into archive paths. */
@DisplayName("AssetFlattener: resolving a folder asset into archive paths")
class AssetFlattenerTest {

  @TempDir private Path project;

  @BeforeEach
  void buildTree() throws Exception {
    Files.createDirectories(project.resolve("web/sub"));
    Files.createDirectories(project.resolve("web/.git"));
    Files.writeString(project.resolve("web/index.qml"), "a");
    Files.writeString(project.resolve("web/helper.txt"), "b");
    Files.writeString(project.resolve("web/sub/page.qml"), "c");
    Files.writeString(project.resolve("web/.DS_Store"), "x");
    Files.writeString(project.resolve("web/backup.qml~"), "x");
    Files.writeString(project.resolve("web/.git/config"), "x");
  }

  private List<String> flatten(List<String> includes, List<String> excludes, boolean isPublic)
      throws Exception {
    var asset = new DescriptorAsset("web", "web", null, false, null, includes, excludes, isPublic);
    return AssetFlattener.flatten(
            List.of(asset), List.of(), List.of(), Map.of("web", project.resolve("web")))
        .stream()
        .map(PackagedAsset::archivePath)
        .toList();
  }

  @Test
  @DisplayName("packs the folder's files under native/<dest>, keeping the folder shape")
  void packsTheFolder() throws Exception {
    assertThat(flatten(List.of(), List.of(), false))
        .containsExactlyInAnyOrder(
            "native/web/index.qml", "native/web/helper.txt", "native/web/sub/page.qml");
  }

  @Test
  @DisplayName("drops BlackBerry's default excludes: VCS folders and junk files")
  void dropsDefaultExcludes() throws Exception {
    assertThat(flatten(List.of(), List.of(), false))
        .doesNotContain("native/web/.DS_Store", "native/web/backup.qml~", "native/web/.git/config");
  }

  @Test
  @DisplayName("an include pattern keeps only the files whose name matches it")
  void keepsOnlyIncluded() throws Exception {
    assertThat(flatten(List.of("*.qml"), List.of(), false))
        .containsExactlyInAnyOrder("native/web/index.qml", "native/web/sub/page.qml");
  }

  @Test
  @DisplayName("an exclude pattern drops the files whose name matches it")
  void dropsExcluded() throws Exception {
    assertThat(flatten(List.of(), List.of("*.txt"), false))
        .contains("native/web/index.qml")
        .doesNotContain("native/web/helper.txt");
  }

  @Test
  @DisplayName("a public asset is packed under public/ instead of native/")
  void packsPublicUnderPublicRoot() throws Exception {
    assertThat(flatten(List.of(), List.of(), true)).contains("public/web/index.qml");
  }
}
