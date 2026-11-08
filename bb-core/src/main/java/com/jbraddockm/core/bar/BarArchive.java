package com.jbraddockm.core.bar;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * An opened {@code .bar} — a ZIP whose {@code META-INF/MANIFEST.MF} describes the package and whose
 * other entries are its assets. Reading is pure Java, with no BlackBerry library. The archive stays
 * open until {@link #close}, so use it with try-with-resources.
 */
public final class BarArchive implements AutoCloseable {

  /** The largest manifest that is read; a real one of 200 assets is about 40 KB. */
  public static final int MAXIMUM_MANIFEST_BYTES = 8 * 1024 * 1024;

  private static final String MANIFEST_ENTRY = "META-INF/MANIFEST.MF";

  private final ZipFile archive;

  private BarArchive(ZipFile archive) {
    this.archive = archive;
  }

  /**
   * Opens a {@code .bar}.
   *
   * @param file the archive file
   * @return the opened archive
   * @throws IOException if the file cannot be read
   * @throws InvalidBarException if it is a folder or not a ZIP archive
   */
  public static BarArchive open(Path file) throws IOException, InvalidBarException {
    if (Files.isDirectory(file)) {
      throw new InvalidBarException("it is a folder, not a file");
    }
    if (Files.notExists(file)) {
      throw new NoSuchFileException(file.toString());
    }
    if (!Files.isReadable(file)) {
      throw new AccessDeniedException(file.toString());
    }
    try {
      return new BarArchive(new ZipFile(file.toFile()));
    } catch (ZipException failure) {
      throw new InvalidBarException("it is not a ZIP archive (" + failure.getMessage() + ")");
    }
  }

  /**
   * Reads and parses the manifest.
   *
   * @return the manifest
   * @throws IOException if the archive cannot be read
   * @throws InvalidBarException if there is no manifest, it is too large, or it cannot be
   *     understood
   */
  public Manifest manifest() throws IOException, InvalidBarException {
    var entry = manifestEntry();
    if (entry.getSize() > MAXIMUM_MANIFEST_BYTES) {
      throw tooLarge();
    }
    byte[] bytes;
    try (var stream = archive.getInputStream(entry)) {
      bytes = stream.readNBytes(MAXIMUM_MANIFEST_BYTES + 1);
    }
    if (bytes.length > MAXIMUM_MANIFEST_BYTES) {
      throw tooLarge();
    }
    return Manifest.parse(bytes);
  }

  /**
   * The archive's assets — every entry except the manifest and directory entries, in archive order.
   *
   * @return the assets
   */
  public List<BarAsset> assets() {
    return archive.stream()
        .filter(entry -> !entry.isDirectory() && !isManifest(entry))
        .map(entry -> new BarAsset(entry.getName(), entry.getSize()))
        .toList();
  }

  /**
   * Opens an asset's bytes.
   *
   * @param asset an asset from {@link #assets}
   * @return its content stream; the caller closes it
   * @throws IOException if the entry is gone or cannot be read
   */
  public InputStream open(BarAsset asset) throws IOException {
    var entry = archive.getEntry(asset.path());
    if (entry == null) {
      throw new IOException("the archive has no asset " + asset.path());
    }
    return archive.getInputStream(entry);
  }

  @Override
  public void close() throws IOException {
    archive.close();
  }

  private ZipEntry manifestEntry() throws InvalidBarException {
    var entry = archive.getEntry(MANIFEST_ENTRY);
    if (entry != null) {
      return entry;
    }
    return archive.stream()
        .filter(BarArchive::isManifest)
        .findFirst()
        .orElseThrow(() -> new InvalidBarException("it has no " + MANIFEST_ENTRY));
  }

  private static boolean isManifest(ZipEntry entry) {
    return entry.getName().equalsIgnoreCase(MANIFEST_ENTRY);
  }

  private static InvalidBarException tooLarge() {
    return new InvalidBarException(
        "its manifest is larger than " + MAXIMUM_MANIFEST_BYTES + " bytes");
  }
}
