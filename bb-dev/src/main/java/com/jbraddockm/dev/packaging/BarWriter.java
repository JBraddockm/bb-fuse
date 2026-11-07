package com.jbraddockm.dev.packaging;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Assembles an unsigned, development-mode {@code .bar}: a ZIP of {@code META-INF/MANIFEST.MF} and
 * the flattened assets. The manifest is generated from the descriptor; the descriptor itself is
 * packed alongside the application, as BlackBerry's packager does. No signature and no debug token
 * are written, so the bar installs only on a device in development mode.
 */
final class BarWriter {

  private static final String MANIFEST_ENTRY = "META-INF/MANIFEST.MF";

  private BarWriter() {}

  /**
   * Writes a {@code .bar}.
   *
   * @param descriptor the parsed descriptor
   * @param descriptorFile the descriptor file, which is packed into the bar
   * @param assetFiles each source path the descriptor names, mapped to the file on the host
   * @param output where to write the bar
   * @throws IOException if a file cannot be read or the bar cannot be written
   */
  static void write(
      BarDescriptor descriptor, Path descriptorFile, Map<String, Path> assetFiles, Path output)
      throws IOException {
    var assets = new ArrayList<>(AssetFlattener.flatten(descriptor, assetFiles));
    assets.add(new PackagedAsset(AssetFlattener.DESCRIPTOR_PATH, descriptorFile, null, false));

    var buildId = buildId(descriptor, descriptorFile.toAbsolutePath().getParent());
    var manifest = ManifestGenerator.generate(descriptor, assets, buildId);

    try (var zip = new ZipOutputStream(Files.newOutputStream(output))) {
      write(zip, MANIFEST_ENTRY, manifest.toBytes());
      for (var asset : assets) {
        write(zip, asset.archivePath(), Files.readAllBytes(asset.source()));
      }
    }
  }

  /** The build id: the descriptor's own, the one in its {@code buildIdFile}, or {@code 0}. */
  private static String buildId(BarDescriptor descriptor, Path baseDir) throws IOException {
    if (descriptor.buildId() != null) {
      return descriptor.buildId();
    }
    if (descriptor.buildIdFile() != null) {
      var file = baseDir.resolve(descriptor.buildIdFile());
      if (Files.exists(file)) {
        return Files.readString(file).strip();
      }
    }
    return ManifestGenerator.DEFAULT_BUILD_ID;
  }

  private static void write(ZipOutputStream zip, String path, byte[] bytes) throws IOException {
    var entry = new ZipEntry(path);
    zip.putNextEntry(entry);
    zip.write(bytes);
    zip.closeEntry();
  }
}
