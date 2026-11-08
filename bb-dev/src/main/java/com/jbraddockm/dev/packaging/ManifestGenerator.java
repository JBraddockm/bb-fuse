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
 * <p>Only unsigned bars are built, so the signing attributes are never written and the asset
 * digests are advisory. {@code Application-Development-Mode} is {@code true} only when a
 * development-mode bar was asked for (the {@code -devMode} option); it is independent of signing.
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

  /**
   * The architecture recorded when the descriptor names no configuration or platform: the device.
   */
  static final String DEFAULT_ARCHITECTURE = "armle-v7";

  /** The entry-point key of a single-entry application. */
  static final String ENTRY_POINT_KEY = "e1";

  /** The entry-point type used when nothing else gives one. */
  static final String DEFAULT_ENTRY_POINT_TYPE = "Qnx/Elf";

  /** The system action every native entry point runs under; added if the descriptor omits it. */
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
    return new Manifest(
        header(descriptor, version(descriptor, buildId), architecture(descriptor), false));
  }

  /**
   * Builds the whole manifest: the header, the entry point, and a section per packed asset.
   *
   * @param descriptor the descriptor
   * @param architecture the manifest architecture, from the chosen configuration; may be {@code
   *     null}
   * @param configEntryPointType the entry-point type the configuration sets; may be {@code null}
   * @param arguments the entry-point arguments, top-level and from the chosen configuration
   * @param splashScreen the ready {@code Entry-Point-Splash-Screen} value; may be {@code null}
   * @param assets the files the bar holds, with their archive paths and bytes
   * @param version the resolved four-segment package version
   * @param devMode whether to mark the bar as a development-mode build
   * @return the manifest
   * @throws IOException if an asset's bytes cannot be read for its digest
   */
  static Manifest generate(
      BarDescriptor descriptor,
      String architecture,
      String configEntryPointType,
      List<String> arguments,
      String splashScreen,
      List<PackagedAsset> assets,
      String version,
      boolean devMode)
      throws IOException {
    var sections = new ArrayList<>(header(descriptor, version, architecture, devMode));
    sections.addAll(entryPoints(descriptor, assets, configEntryPointType, arguments, splashScreen));
    for (var target : descriptor.invokeTargets()) {
      sections.add(invokeTarget(target, assets));
    }
    for (var asset : assets) {
      sections.add(assetSection(asset));
    }
    return new Manifest(sections);
  }

  private static List<ManifestSection> header(
      BarDescriptor descriptor, String version, String architecture, boolean devMode) {
    var archive = new ArrayList<ManifestAttribute>();
    add(archive, "Archive-Manifest-Version", MANIFEST_VERSION);
    add(archive, "Archive-Created-By", CREATED_BY);

    var packageId = packageId(descriptor.id(), devMode);
    var author = descriptor.author() == null ? DEFAULT_AUTHOR : descriptor.author();
    var versionId = id(version);
    var pkg = new ArrayList<ManifestAttribute>();
    add(pkg, "Package-Type", "application");
    add(pkg, "Package-Author", author);
    add(pkg, "Package-Author-Id", id(author));
    add(pkg, "Package-Name", descriptor.id());
    add(pkg, "Package-Id", packageId);
    add(pkg, "Package-Version", version);
    add(pkg, "Package-Version-Id", versionId);
    add(pkg, "Package-Architecture", architecture == null ? DEFAULT_ARCHITECTURE : architecture);
    add(pkg, "Package-Locale", locales(descriptor.packageLocale()));
    add(pkg, "Package-Description", descriptor.description());

    var application = new ArrayList<ManifestAttribute>();
    add(application, "Application-Name", descriptor.name());
    add(application, "Application-Id", packageId);
    add(application, "Application-Description", descriptor.description());
    add(application, "Application-Version", version);
    add(application, "Application-Version-Id", versionId);
    add(application, "Application-Category", descriptor.category());
    add(application, "Application-Development-Mode", Boolean.toString(devMode));
    add(application, "Application-Requires-System", requiresSystem(descriptor));

    return new ArrayList<>(
        List.of(
            new ManifestSection(archive),
            new ManifestSection(pkg),
            new ManifestSection(application)));
  }

  /** One {@code Entry-Point} section per entry point: the declared ones, or a single default. */
  private static List<ManifestSection> entryPoints(
      BarDescriptor descriptor,
      List<PackagedAsset> assets,
      String configEntryPointType,
      List<String> arguments,
      String splashScreen) {
    var sections = new ArrayList<ManifestSection>();
    if (descriptor.entryPoints().isEmpty()) {
      assets.stream()
          .filter(PackagedAsset::entry)
          .findFirst()
          .ifPresent(
              entry ->
                  sections.add(
                      entryPoint(
                          descriptor,
                          ENTRY_POINT_KEY,
                          descriptor.name(),
                          entry.archivePath(),
                          entryPointType(null, configEntryPointType, entry.type()),
                          descriptor.permissions(),
                          arguments,
                          splashScreen)));
      return sections;
    }
    for (var entryPoint : descriptor.entryPoints()) {
      var binary =
          assets.stream()
              .filter(asset -> entryPoint.id().equals(asset.entryPointId()))
              .findFirst()
              .orElse(null);
      if (binary == null) {
        continue;
      }
      var permissions = new ArrayList<>(entryPoint.permissions());
      permissions.addAll(descriptor.permissions());
      sections.add(
          entryPoint(
              descriptor,
              entryPoint.id(),
              entryPoint.name() == null ? descriptor.name() : entryPoint.name(),
              binary.archivePath(),
              entryPointType(entryPoint.entryPointType(), configEntryPointType, binary.type()),
              permissions,
              arguments,
              splashScreen));
    }
    return sections;
  }

  private static ManifestSection entryPoint(
      BarDescriptor descriptor,
      String key,
      String name,
      String archivePath,
      String type,
      List<Permission> permissions,
      List<String> arguments,
      String splashScreen) {
    var attributes = new ArrayList<ManifestAttribute>();
    add(attributes, "Entry-Point-Name", name);
    add(attributes, "Entry-Point-Key", key);
    add(attributes, "Entry-Point", command(descriptor, archivePath, arguments));
    add(attributes, "Entry-Point-Type", type);
    add(attributes, "Entry-Point-Icon", icon(descriptor));
    add(attributes, "Entry-Point-Orientation", orientation(descriptor));
    add(attributes, "Entry-Point-Splash-Screen", splashScreen);
    add(attributes, "Entry-Point-User-Actions", userActions(permissions));
    add(attributes, "Entry-Point-System-Actions", systemActions(permissions));
    return new ManifestSection(attributes);
  }

  /** The entry-point type: the entry point's own, else the configuration's, else the asset's. */
  private static String entryPointType(String entryType, String configType, String assetType) {
    if (entryType != null) {
      return entryType;
    }
    if (configType != null) {
      return configType;
    }
    return assetType == null ? DEFAULT_ENTRY_POINT_TYPE : assetType;
  }

  private static ManifestSection invokeTarget(InvokeTarget target, List<PackagedAsset> assets)
      throws IOException {
    var attributes = new ArrayList<ManifestAttribute>();
    add(attributes, "Invoke-Target-Key", target.id());
    add(attributes, "Invoke-Target-Label", target.label());
    add(attributes, "Invoke-Target-Icon", sizedIcon(target.icon(), assets));
    add(
        attributes,
        "Invoke-Target-Entry-Point-Ref",
        target.entryPointId() == null ? ENTRY_POINT_KEY : target.entryPointId());
    add(attributes, "Invoke-Target-Type", target.type());
    for (var filter : target.filters()) {
      add(attributes, "Invoke-Target-Filter", filter(filter));
    }
    return new ManifestSection(attributes);
  }

  /** An invoke target's icon as {@code {WxH}native/icon.png}, sized from the packed file. */
  private static String sizedIcon(String icon, List<PackagedAsset> assets) throws IOException {
    if (icon == null) {
      return null;
    }
    var archivePath = AssetFlattener.iconPath(icon);
    var source =
        assets.stream()
            .filter(asset -> asset.archivePath().equals(archivePath))
            .map(PackagedAsset::source)
            .findFirst()
            .orElse(null);
    if (source == null) {
      return archivePath;
    }
    var size = ImageDimensions.of(source);
    return size == null ? archivePath : size + archivePath;
  }

  /** Serializes a filter as {@code actions=…;types=…;var=value;}, as the manifest records them. */
  private static String filter(InvokeFilter filter) {
    var text = new StringBuilder();
    if (!filter.actions().isEmpty()) {
      text.append("actions=").append(String.join(",", filter.actions())).append(';');
    }
    if (!filter.mimeTypes().isEmpty()) {
      text.append("types=").append(String.join(",", filter.mimeTypes())).append(';');
    }
    for (var property : filter.properties()) {
      text.append(property.variable()).append('=').append(property.value()).append(';');
    }
    return text.toString();
  }

  private static ManifestSection assetSection(PackagedAsset asset) throws IOException {
    var attributes = new ArrayList<ManifestAttribute>();
    add(attributes, "Archive-Asset-Name", asset.archivePath());
    add(attributes, "Archive-Asset-SHA-512-Digest", digest(asset.source()));
    add(attributes, "Archive-Asset-Type", asset.type());
    return new ManifestSection(attributes);
  }

  /** The entry-point command: the environment, the cascades theme, the run-time path, the args. */
  private static String command(
      BarDescriptor descriptor, String archivePath, List<String> arguments) {
    var command = new StringBuilder();
    for (var variable : descriptor.environment()) {
      command.append(variable.variable()).append('=').append(variable.value()).append(' ');
    }
    if (descriptor.cascadesTheme() != null
        && descriptor.environment().stream()
            .noneMatch(variable -> variable.variable().equals("CASCADES_THEME"))) {
      command.append("CASCADES_THEME=").append(descriptor.cascadesTheme()).append(' ');
    }
    command.append(RUNTIME_ROOT).append(archivePath);
    for (var argument : arguments) {
      command.append(' ').append(argument);
    }
    return command.toString();
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

  /** The system actions: the matching permissions, without duplicates, with {@code run_native}. */
  private static String systemActions(List<Permission> permissions) {
    var names =
        new ArrayList<>(
            permissions.stream()
                .filter(Permission::system)
                .map(Permission::name)
                .distinct()
                .toList());
    if (!names.contains(NATIVE_SYSTEM_ACTION)) {
      names.add(NATIVE_SYSTEM_ACTION);
    }
    return String.join(",", names);
  }

  /** The user actions: the non-system permission names, in order, without duplicates. */
  private static String userActions(List<Permission> permissions) {
    var names =
        permissions.stream()
            .filter(permission -> !permission.system())
            .map(Permission::name)
            .distinct()
            .toList();
    return names.isEmpty() ? null : String.join(",", names);
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
        .map(ManifestGenerator::mapArchitecture)
        .findFirst()
        .orElse(null);
  }

  /**
   * Maps a descriptor architecture to the name the manifest uses.
   *
   * @param platformArchitecture the configuration's {@code platformArchitecture}; may be {@code
   *     null}
   * @return the manifest architecture, or {@code null}
   */
  static String mapArchitecture(String platformArchitecture) {
    return platformArchitecture == null
        ? null
        : ARCHITECTURES.getOrDefault(platformArchitecture, platformArchitecture);
  }

  /** The supported locales, in the manifest's form: BCP 47 hyphens become underscores. */
  private static String locales(String packageLocale) {
    return packageLocale == null ? null : packageLocale.replace('-', '_');
  }

  /**
   * A package id, as BlackBerry's packager generates one when no signing supplies it: a
   * 27-character id the device needs to register the package. It is derived from the package name,
   * so the same application keeps the same id, and from whether this is a development build —
   * {@code testDev_} for one, {@code testRel_} otherwise — matching {@code generatePackageId}.
   */
  private static String packageId(String name, boolean devMode) {
    var cleaned = name.replaceAll("[^\\w]", "_");
    var hash = Integer.toHexString(name.hashCode());
    return "test" + (devMode ? "Dev" : "Rel") + "_" + lastChars(cleaned, 11) + lastChars(hash, 8);
  }

  /**
   * A development id for a value (author or version), as BlackBerry generates one: {@code test}
   * then the value padded or trimmed to 17 bytes and URL-safe Base64 encoded, 27 characters in all.
   */
  private static String id(String value) {
    var padded = new byte[17];
    java.util.Arrays.fill(padded, (byte) ' ');
    var raw = value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    System.arraycopy(raw, 0, padded, 0, Math.min(raw.length, padded.length));
    return "test" + Base64.getUrlEncoder().withoutPadding().encodeToString(padded);
  }

  /** The last {@code n} characters, padding the right with {@code _} when there are fewer. */
  private static String lastChars(String value, int n) {
    StringBuilder padded = new StringBuilder(value);
    while (padded.length() < n) {
      padded.append("_");
    }
    return padded.substring(padded.length() - n);
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
