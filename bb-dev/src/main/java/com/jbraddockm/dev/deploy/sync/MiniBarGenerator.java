package com.jbraddockm.dev.deploy.sync;

import com.jbraddockm.core.bar.BarArchive;
import com.jbraddockm.core.bar.BarAsset;
import com.jbraddockm.core.bar.InvalidBarException;
import com.jbraddockm.core.bar.Manifest;
import com.jbraddockm.core.bar.ManifestAttribute;
import com.jbraddockm.core.bar.ManifestSection;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds the dev-mode sync's <em>mini-BAR</em>: the smallest archive that installs the app so its
 * writable sandbox exists, after which the real assets are streamed in over qconn. It keeps the
 * manifest and the localized manifests whole, keeps the entry-point binaries and image assets as
 * <strong>empty</strong> entries (their content is pushed by the sync, so the mini-BAR stays tiny),
 * and drops every other asset — section and file both — so the installed manifest declares only
 * what the archive carries and the first sync's diff pushes everything.
 *
 * <p>This mirrors the legacy {@code MiniBARGenerator} (prune to the manifest + image + binary
 * whitelist, then flatten the kept binaries and images to empty with the empty-content digest).
 */
final class MiniBarGenerator {

  private static final String MANIFEST_ENTRY = "META-INF/MANIFEST.MF";
  private static final String ASSET_NAME = "Archive-Asset-Name";
  private static final String ASSET_DIGEST = "Archive-Asset-SHA-512-Digest";

  /** A localized manifest, kept whole: {@code META-INF/MANIFEST_<locale>.bbr}. */
  private static final Pattern LOCALIZED_MANIFEST =
      Pattern.compile("META-INF/MANIFEST_[a-zA-Z_]{2,5}\\.bbr");

  /** The SHA-512 of empty content, the digest a flattened (emptied) asset carries. */
  static final String EMPTY_SHA_512 = emptyDigest();

  private MiniBarGenerator() {}

  /**
   * Builds the mini-BAR for a full bar.
   *
   * @param fullBar the full {@code .bar}
   * @return the mini-BAR bytes
   * @throws IOException if the bar cannot be read
   * @throws InvalidBarException if it is not a readable bar with a manifest
   */
  static byte[] generate(Path fullBar) throws IOException, InvalidBarException {
    try (var bar = BarArchive.open(fullBar)) {
      return generate(bar);
    }
  }

  /**
   * Builds the mini-BAR from an open bar.
   *
   * @param bar the full bar
   * @return the mini-BAR bytes
   * @throws IOException if the bar cannot be read
   * @throws InvalidBarException if it has no manifest that can be understood
   */
  static byte[] generate(BarArchive bar) throws IOException, InvalidBarException {
    var manifest = bar.manifest();
    var keep = new LinkedHashSet<String>();
    keep.addAll(manifest.entryPointBinaries());
    keep.addAll(manifest.imageAssets());

    var sections = new ArrayList<ManifestSection>();
    var emptied = new ArrayList<String>();
    var localizedManifests = new ArrayList<String>();
    for (var section : manifest.sections()) {
      var name = section.value(ASSET_NAME).orElse(null);
      if (name == null) {
        sections.add(section);
      } else if (LOCALIZED_MANIFEST.matcher(name).matches()) {
        sections.add(section);
        localizedManifests.add(name);
      } else if (keep.contains(name)) {
        sections.add(flatten(section));
        emptied.add(name);
      }
      // every other asset section is dropped, section and file both
    }

    var output = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(output)) {
      write(zip, MANIFEST_ENTRY, new Manifest(sections).toBytes());
      for (var name : emptied) {
        write(zip, name, new byte[0]);
      }
      for (var name : localizedManifests) {
        write(zip, name, read(bar, name));
      }
    }
    return output.toByteArray();
  }

  /** Rebuilds an asset section with its SHA-512 digest replaced by the empty-content digest. */
  private static ManifestSection flatten(ManifestSection section) {
    var attributes = new ArrayList<ManifestAttribute>();
    for (var attribute : section.attributes()) {
      attributes.add(
          attribute.name().equalsIgnoreCase(ASSET_DIGEST)
              ? new ManifestAttribute(attribute.name(), EMPTY_SHA_512)
              : attribute);
    }
    return new ManifestSection(attributes);
  }

  private static byte[] read(BarArchive bar, String name) throws IOException {
    try (var stream = bar.open(new BarAsset(name, -1))) {
      return stream.readAllBytes();
    }
  }

  private static void write(ZipOutputStream zip, String path, byte[] bytes) throws IOException {
    zip.putNextEntry(new ZipEntry(path));
    zip.write(bytes);
    zip.closeEntry();
  }

  private static String emptyDigest() {
    try {
      var hash = MessageDigest.getInstance("SHA-512").digest(new byte[0]);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-512 is required by the platform", impossible);
    }
  }
}
