package com.jbraddockm.dev.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.bar.Manifest;
import com.jbraddockm.core.bar.ManifestAsset;
import com.jbraddockm.dev.testing.Resources;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("The mini-BAR generator")
class MiniBarGeneratorTest {

  private static final byte[] LOCALIZED = "localized-fr".getBytes(StandardCharsets.US_ASCII);

  private static Path fullBar(Path folder) throws IOException {
    var file = folder.resolve("full.bar");
    try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {
      put(
          zip,
          "META-INF/MANIFEST.MF",
          Resources.text(MiniBarGeneratorTest.class, "mini--full-app.mf")
              .getBytes(StandardCharsets.UTF_8));
      put(zip, "native/bar-descriptor.xml", "<descriptor/>".getBytes(StandardCharsets.US_ASCII));
      put(zip, "native/icon.png", "real-icon-bytes".getBytes(StandardCharsets.US_ASCII));
      put(zip, "native/assets/main.qml", "import bb".getBytes(StandardCharsets.US_ASCII));
      put(zip, "native/TLSDeBug", "real-elf-binary".getBytes(StandardCharsets.US_ASCII));
      put(zip, "native/lib/libFoo.so.1", "real-lib".getBytes(StandardCharsets.US_ASCII));
      put(zip, "META-INF/MANIFEST_fr.bbr", LOCALIZED);
    }
    return file;
  }

  private static void put(ZipOutputStream zip, String path, byte[] bytes) throws IOException {
    zip.putNextEntry(new ZipEntry(path));
    zip.write(bytes);
    zip.closeEntry();
  }

  private static Map<String, byte[]> entries(byte[] bar) throws IOException {
    var entries = new LinkedHashMap<String, byte[]>();
    try (var zip = new ZipInputStream(new ByteArrayInputStream(bar))) {
      for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
        entries.put(entry.getName(), zip.readAllBytes());
      }
    }
    return entries;
  }

  @Test
  @DisplayName("keeps only the manifest, the entry binary, the icon and localized manifests")
  void entries(@TempDir Path folder) throws Exception {
    var mini = entries(MiniBarGenerator.generate(fullBar(folder)));

    assertThat(mini.keySet())
        .containsExactlyInAnyOrder(
            "META-INF/MANIFEST.MF",
            "native/icon.png",
            "native/TLSDeBug",
            "META-INF/MANIFEST_fr.bbr");
  }

  @Test
  @DisplayName("empties the kept binary and image but keeps a localized manifest whole")
  void content(@TempDir Path folder) throws Exception {
    var mini = entries(MiniBarGenerator.generate(fullBar(folder)));

    assertThat(mini.get("native/TLSDeBug")).isEmpty();
    assertThat(mini.get("native/icon.png")).isEmpty();
    assertThat(mini.get("META-INF/MANIFEST_fr.bbr")).isEqualTo(LOCALIZED);
  }

  @Test
  @DisplayName("declares only the kept assets, with the empty-content digest on the emptied ones")
  void manifest(@TempDir Path folder) throws Exception {
    var mini = entries(MiniBarGenerator.generate(fullBar(folder)));

    var manifest = Manifest.parse(mini.get("META-INF/MANIFEST.MF"));
    assertThat(manifest.value("Package-Name")).hasValue("com.example.TLSDeBug");
    assertThat(manifest.assets())
        .extracting(ManifestAsset::name)
        .containsExactly("native/icon.png", "native/TLSDeBug", "META-INF/MANIFEST_fr.bbr");

    var byName =
        manifest.assets().stream()
            .collect(java.util.stream.Collectors.toMap(ManifestAsset::name, a -> a));
    assertThat(byName.get("native/icon.png").sha512Digest())
        .isEqualTo(MiniBarGenerator.EMPTY_SHA_512);
    assertThat(byName.get("native/TLSDeBug").sha512Digest())
        .isEqualTo(MiniBarGenerator.EMPTY_SHA_512);
    assertThat(byName.get("native/TLSDeBug").isNativeBinary()).isTrue();
    assertThat(byName.get("META-INF/MANIFEST_fr.bbr").sha512Digest())
        .isEqualTo("FFFFlocalizeddigestFFFF");
  }

  @Test
  @DisplayName("uses the legacy empty-content SHA-512 digest")
  void emptyDigest() {
    assertThat(MiniBarGenerator.EMPTY_SHA_512)
        .isEqualTo(
            "z4PhNX7vuL3xVChQ1m2AB9Yg5AULVxXcg_SpIdNs6c5H0NE8XYXysP-DGNKHfuwvY7kxvUdBeoGlODJ6-SfaPg");
  }
}
