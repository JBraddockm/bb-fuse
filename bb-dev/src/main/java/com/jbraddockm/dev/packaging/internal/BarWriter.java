package com.jbraddockm.dev.packaging.internal;

import com.jbraddockm.core.bar.Manifest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes the archive itself: a ZIP of {@code META-INF/MANIFEST.MF} and the packed files, each at
 * its archive path. The manifest and the file list are prepared by the {@link Packager}.
 */
final class BarWriter {

  private static final String MANIFEST_ENTRY = "META-INF/MANIFEST.MF";

  private BarWriter() {}

  /**
   * Writes a {@code .bar}.
   *
   * @param output where to write the bar
   * @param manifest the manifest
   * @param assets the files to pack, each with its archive path and source
   * @throws IOException if a file cannot be read or the bar cannot be written
   */
  static void write(Path output, Manifest manifest, List<PackagedAsset> assets) throws IOException {
    try (var zip = new ZipOutputStream(Files.newOutputStream(output))) {
      write(zip, MANIFEST_ENTRY, manifest.toBytes());
      for (var asset : assets) {
        write(zip, asset.archivePath(), Files.readAllBytes(asset.source()));
      }
    }
  }

  private static void write(ZipOutputStream zip, String path, byte[] bytes) throws IOException {
    zip.putNextEntry(new ZipEntry(path));
    zip.write(bytes);
    zip.closeEntry();
  }
}
