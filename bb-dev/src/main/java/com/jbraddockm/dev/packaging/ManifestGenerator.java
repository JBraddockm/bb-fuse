package com.jbraddockm.dev.packaging;

import com.jbraddockm.core.bar.Manifest;
import com.jbraddockm.core.bar.ManifestAttribute;
import com.jbraddockm.core.bar.ManifestSection;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds a development {@code .bar} manifest from a {@link BarDescriptor}. The {@code Archive},
 * {@code Package} and {@code Application} sections come from the descriptor alone; the {@code
 * Entry-Point} section and the per-asset {@code Archive-Asset} sections need the flattened archive
 * paths and the asset bytes, so they are built from the {@link PackagedAsset packed assets}. The
 * result validates clean against bb-core's unsigned-application rule set.
 *
 * <p>Only unsigned, development-mode bars are built, so the signing attributes are never written,
 * {@code Application-Development-Mode} is {@code true}, and the asset digests are advisory.
 */
final class ManifestGenerator {

  /** The manifest format this tool writes. */
  static final String MANIFEST_VERSION = "1.6";

  /** What the manifest records as its maker. */
  static final String CREATED_BY = "bb-dev";

  /** The build id used when the descriptor gives none. */
  static final String DEFAULT_BUILD_ID = "0";

  /** The author recorded when the descriptor gives none; signing would otherwise supply it. */
  static final String DEFAULT_AUTHOR = "Development";

  /** The entry-point key; one entry point per development bar. */
  static final String ENTRY_POINT_KEY = "e1";

  /** The entry-point type used when an entry asset gives none. */
  static final String DEFAULT_ENTRY_POINT_TYPE = "Qnx/Elf";

  /** The system action a native application runs under. */
  static final String NATIVE_SYSTEM_ACTION = "run_native";

  private static final String OS_NAME = "BlackBerry 10";

  /** The folder the device mounts the archive's {@code native/} assets under at run time. */
  private static final String RUNTIME_ROOT = "app/";

  /** The descriptor's build architectures, mapped to the names the manifest uses. */
  private static final Map<String, String> ARCHITECTURES = Map.of("armv7-le", "armle-v7");

  private ManifestGenerator() {}

  /**
   * Builds the manifest header alone: the {@code Archive}, {@code Package} and {@code Application}
   * sections, with the build id taken from the descriptor.
   *
   * @param descriptor the descriptor
   * @return the header manifest, which validates clean on its own
   */
  static Manifest generate(BarDescriptor descriptor) {
    var buildId = descriptor.buildId() == null ? DEFAULT_BUILD_ID : descriptor.buildId();
    return new Manifest(header(descriptor, version(descriptor, buildId)));
  }

  /**
   * Builds the whole manifest: the header, the entry point, and a section per packed asset.
   *
   * @param descriptor the descriptor
   * @param assets the files the bar holds, with their archive paths and bytes
   * @param buildId the resolved build id, the fourth version segment
   * @return the manifest
   * @throws IOException if an asset's bytes cannot be read for its digest
   */
  static Manifest generate(BarDescriptor descriptor, List<PackagedAsset> assets, String buildId)
      throws IOException {
    var sections = new ArrayList<>(header(descriptor, version(descriptor, buildId)));
    sections.add(entryPoint(descriptor));
    for (var asset : assets) {
      sections.add(assetSection(asset));
    }
    return new Manifest(sections);
  }

  private static List<ManifestSection> header(BarDescriptor descriptor, String version) {
    var archive = new ArrayList<ManifestAttribute>();
    add(archive, "Archive-Manifest-Version", MANIFEST_VERSION);
    add(archive, "Archive-Created-By", CREATED_BY);

    var pkg = new ArrayList<ManifestAttribute>();
    add(pkg, "Package-Type", "application");
    add(pkg, "Package-Name", descriptor.id());
    add(pkg, "Package-Author", descriptor.author() == null ? DEFAULT_AUTHOR : descriptor.author());
    add(pkg, "Package-Version", version);
    add(pkg, "Package-Architecture", architecture(descriptor));
    add(pkg, "Package-Description", descriptor.description());

    var application = new ArrayList<ManifestAttribute>();
    add(application, "Application-Name", descriptor.name());
    add(application, "Application-Description", descriptor.description());
    add(application, "Application-Version", version);
    add(application, "Application-Category", descriptor.category());
    add(application, "Application-Development-Mode", "true");
    add(application, "Application-Requires-System", requiresSystem(descriptor));

    return new ArrayList<>(
        List.of(
            new ManifestSection(archive),
            new ManifestSection(pkg),
            new ManifestSection(application)));
  }

  private static ManifestSection entryPoint(BarDescriptor descriptor) {
    var entry =
        descriptor
            .entryPoint()
            .orElseThrow(() -> new IllegalArgumentException("the descriptor has no entry point"));
    var attributes = new ArrayList<ManifestAttribute>();
    add(attributes, "Entry-Point-Name", descriptor.name());
    add(attributes, "Entry-Point-Key", ENTRY_POINT_KEY);
    add(
        attributes,
        "Entry-Point-Type",
        entry.type() == null ? DEFAULT_ENTRY_POINT_TYPE : entry.type());
    add(attributes, "Entry-Point", command(descriptor, entry));
    add(attributes, "Entry-Point-Icon", icon(descriptor));
    add(attributes, "Entry-Point-Orientation", orientation(descriptor));
    add(attributes, "Entry-Point-User-Actions", actions(descriptor));
    add(attributes, "Entry-Point-System-Actions", NATIVE_SYSTEM_ACTION);
    return new ManifestSection(attributes);
  }

  private static ManifestSection assetSection(PackagedAsset asset) throws IOException {
    var attributes = new ArrayList<ManifestAttribute>();
    add(attributes, "Archive-Asset-Name", asset.archivePath());
    add(attributes, "Archive-Asset-SHA-512-Digest", digest(asset.source()));
    add(attributes, "Archive-Asset-Type", asset.type());
    return new ManifestSection(attributes);
  }

  /** The entry-point command: the descriptor's environment, then the run-time path of the entry. */
  private static String command(BarDescriptor descriptor, DescriptorAsset entry) {
    var command = new StringBuilder();
    for (var variable : descriptor.environment()) {
      command.append(variable.variable()).append('=').append(variable.value()).append(' ');
    }
    return command.append(RUNTIME_ROOT).append(AssetFlattener.archivePath(entry)).toString();
  }

  /** The archive path of the first icon, or {@code null} if the descriptor declares none. */
  private static String icon(BarDescriptor descriptor) {
    return descriptor.icons().isEmpty()
        ? null
        : AssetFlattener.iconPath(descriptor.icons().getFirst());
  }

  /** The orientation: {@code auto} when the application orients itself, else the fixed ratio. */
  private static String orientation(BarDescriptor descriptor) {
    var window = descriptor.initialWindow();
    if (window == null) {
      return null;
    }
    return window.autoOrients() ? "auto" : window.aspectRatio();
  }

  /** The user actions: the descriptor's permissions, separated by commas, or {@code null}. */
  private static String actions(BarDescriptor descriptor) {
    return descriptor.permissions().isEmpty() ? null : String.join(",", descriptor.permissions());
  }

  /** The four-segment package version: the descriptor's three segments plus the build id. */
  private static String version(BarDescriptor descriptor, String buildId) {
    return descriptor.versionNumber() + "." + buildId;
  }

  /** The manifest architecture, mapped from the first configuration's; {@code null} if none. */
  private static String architecture(BarDescriptor descriptor) {
    return descriptor.configurations().stream()
        .map(DescriptorConfiguration::platformArchitecture)
        .filter(Objects::nonNull)
        .map(architecture -> ARCHITECTURES.getOrDefault(architecture, architecture))
        .findFirst()
        .orElse(null);
  }

  /** The system requirement, from the descriptor's platform version; {@code null} if none. */
  private static String requiresSystem(BarDescriptor descriptor) {
    return descriptor.platformVersion() == null
        ? null
        : OS_NAME + "/" + descriptor.platformVersion();
  }

  /** The SHA-512 of a file, URL-safe Base64 without padding, as the manifest records digests. */
  private static String digest(java.nio.file.Path file) throws IOException {
    try {
      var sha512 = MessageDigest.getInstance("SHA-512");
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(sha512.digest(Files.readAllBytes(file)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError("SHA-512 is always available", impossible);
    }
  }

  private static void add(List<ManifestAttribute> attributes, String name, String value) {
    if (value != null) {
      attributes.add(new ManifestAttribute(name, value));
    }
  }
}
