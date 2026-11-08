package com.jbraddockm.core.bar;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@code META-INF/MANIFEST.MF}, read and written in pure Java: an ordered list of sections. A
 * package's attributes sit in a section of their own, not at the top, so {@link #value} looks
 * through every section.
 *
 * @param sections the sections, in order
 */
public record Manifest(List<ManifestSection> sections) {

  public Manifest {
    sections = List.copyOf(sections);
  }

  /**
   * Reads a manifest from its bytes (UTF-8).
   *
   * @param bytes the manifest bytes
   * @return the manifest
   * @throws InvalidBarException if the bytes are not a manifest that can be understood
   */
  public static Manifest parse(byte[] bytes) throws InvalidBarException {
    return ManifestParser.parse(bytes);
  }

  /**
   * The manifest as bytes (UTF-8), in the BlackBerry layout: {@code LF} line endings, no wrapping,
   * a blank line between sections, ending on the last attribute.
   *
   * @return the bytes
   */
  public byte[] toBytes() {
    return ManifestWriter.toBytes(this);
  }

  /**
   * The value of the first attribute with this name in any section.
   *
   * @param name the attribute name, matched case-insensitively
   * @return the value, or empty if no section has such an attribute
   */
  public Optional<String> value(String name) {
    Objects.requireNonNull(name, "name");
    return sections.stream()
        .map(section -> section.value(name))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst();
  }

  /**
   * The assets the manifest describes — every section that carries an {@code Archive-Asset-Name},
   * with its SHA-512 digest and type. The dev-mode sync diffs the bar against the device by name
   * and digest.
   *
   * @return the assets, in manifest order
   */
  public List<ManifestAsset> assets() {
    var assets = new ArrayList<ManifestAsset>();
    for (var section : sections) {
      section
          .value("Archive-Asset-Name")
          .ifPresent(
              name ->
                  assets.add(
                      new ManifestAsset(
                          name,
                          section.value("Archive-Asset-SHA-512-Digest").orElse(""),
                          section.value("Archive-Asset-Type").orElse(null))));
    }
    return List.copyOf(assets);
  }

  /**
   * The archive-relative names of the entry-point binaries — the launch executable of each {@code
   * Entry-Point} command. These are the native binaries a mini-BAR must carry.
   *
   * @return the binary asset names, de-duplicated in manifest order
   */
  public List<String> entryPointBinaries() {
    var names = new LinkedHashSet<String>();
    for (var section : sections) {
      for (var attribute : section.attributes()) {
        if (attribute.name().equalsIgnoreCase("Entry-Point")) {
          var binary = EntryPointParser.binaryAssetName(attribute.value());
          if (binary != null) {
            names.add(binary);
          }
        }
      }
    }
    return List.copyOf(names);
  }

  /**
   * The archive-relative names of the image assets — the icons and splash screens named by {@code
   * Entry-Point-Icon}, {@code Entry-Point-Splash-Screen} and {@code Invoke-Target-Icon}. These are
   * the images a mini-BAR must carry.
   *
   * @return the image asset names, de-duplicated in manifest order
   */
  public List<String> imageAssets() {
    var names = new LinkedHashSet<String>();
    for (var section : sections) {
      for (var attribute : section.attributes()) {
        if (attribute.name().equalsIgnoreCase("Entry-Point-Icon")
            || attribute.name().equalsIgnoreCase("Entry-Point-Splash-Screen")
            || attribute.name().equalsIgnoreCase("Invoke-Target-Icon")) {
          names.addAll(EntryPointParser.imageAssetNames(attribute.value()));
        }
      }
    }
    return List.copyOf(names);
  }
}
