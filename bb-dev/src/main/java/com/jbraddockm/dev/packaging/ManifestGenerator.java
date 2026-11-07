package com.jbraddockm.dev.packaging;

import com.jbraddockm.core.bar.Manifest;
import com.jbraddockm.core.bar.ManifestAttribute;
import com.jbraddockm.core.bar.ManifestSection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds the header of a development {@code .bar} manifest from a {@link BarDescriptor}: the {@code
 * Archive}, {@code Package} and {@code Application} sections. The result validates clean against
 * bb-core's unsigned-application rule set.
 *
 * <p>Only unsigned, development-mode bars are built, so the signing attributes are never written
 * and {@code Application-Development-Mode} is {@code true}. The {@code Entry-Point} section and the
 * per-asset {@code Archive-Asset} sections are not written here: their values are the flattened
 * archive paths, which the archive assembly produces, so they are added then. The build id is taken
 * from the descriptor's {@code buildId}; resolving a {@code buildIdFile} reads a file and so is the
 * assembler's work.
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

  private static final String OS_NAME = "BlackBerry 10";

  /** The descriptor's build architectures, mapped to the names the manifest uses. */
  private static final Map<String, String> ARCHITECTURES = Map.of("armv7-le", "armle-v7");

  private ManifestGenerator() {}

  static Manifest generate(BarDescriptor descriptor) {
    var version = version(descriptor);

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

    return new Manifest(
        List.of(
            new ManifestSection(archive),
            new ManifestSection(pkg),
            new ManifestSection(application)));
  }

  /** The four-segment package version: the descriptor's three segments plus the build id. */
  private static String version(BarDescriptor descriptor) {
    var buildId = descriptor.buildId() == null ? DEFAULT_BUILD_ID : descriptor.buildId();
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

  private static void add(List<ManifestAttribute> attributes, String name, String value) {
    if (value != null) {
      attributes.add(new ManifestAttribute(name, value));
    }
  }
}
