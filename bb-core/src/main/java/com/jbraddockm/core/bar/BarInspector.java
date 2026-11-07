package com.jbraddockm.core.bar;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * Reads what the manifest of a bar says about its package, in pure Java.
 *
 * <p>Only {@code META-INF/MANIFEST.MF} is read from the archive, and at most {@link
 * #MAXIMUM_MANIFEST_BYTES} of it (see {@link BarArchive}), so the size of the bar does not matter
 * and a broken or hostile archive cannot exhaust memory. The package's attributes are in a section
 * of their own, not at the top of the manifest; {@link Manifest#value} looks through every section.
 * Validation is {@link ManifestValidator}'s; no BlackBerry library is used.
 */
public final class BarInspector {

  /** The largest manifest that is read; a real one of 200 assets is about 40 KB. */
  public static final int MAXIMUM_MANIFEST_BYTES = BarArchive.MAXIMUM_MANIFEST_BYTES;

  private static final String SIGNED_MARKER = "Package-Author-Certificate-Hash";

  /** The package types the rule sets know. */
  private static final Set<String> KNOWN_PACKAGE_TYPES =
      Set.of(
          "application", "system", "patch", "debug-token", "device-configuration", "system-data");

  private BarInspector() {}

  /**
   * Reads what the manifest of a bar says about its package.
   *
   * @param file the bar
   * @return the description
   * @throws IOException if the file cannot be read: it does not exist ({@link
   *     java.nio.file.NoSuchFileException}) or access to it is denied ({@link
   *     AccessDeniedException})
   * @throws InvalidBarException if the file is not a ZIP archive, has no usable manifest, or names
   *     a package type that is not known
   */
  public static BarInfo inspect(Path file) throws IOException, InvalidBarException {
    try (var archive = BarArchive.open(file)) {
      var manifest = archive.manifest();
      var type = manifest.value("Package-Type").orElse(null);
      if (type == null || type.isBlank()) {
        throw new InvalidBarException("its manifest has no Package-Type");
      }
      if (!KNOWN_PACKAGE_TYPES.contains(type)) {
        throw new InvalidBarException(
            "its Package-Type '" + type + "' is not a package type that is known");
      }
      var fileName = file.getFileName() == null ? "" : file.getFileName().toString();
      return new BarInfo(
          file,
          Files.size(file),
          classify(
              type, text(manifest, "Patch-Package-Type"), text(manifest, "System-Type"), fileName),
          type,
          text(manifest, "Package-Id"),
          text(manifest, "Package-Name"),
          text(manifest, "Package-Version"),
          text(manifest, "Package-Architecture"),
          text(manifest, "Archive-Manifest-Version"),
          manifest
              .value("Application-Development-Mode")
              .map("true"::equalsIgnoreCase)
              .orElse(false),
          manifest.value(SIGNED_MARKER).isPresent());
    }
  }

  /** The value of an attribute, or {@code null} if it is absent or blank. */
  private static String text(Manifest manifest, String name) {
    return manifest.value(name).filter(value -> !value.isBlank()).orElse(null);
  }

  /**
   * Checks the manifest of a bar against the rules for its kind of package.
   *
   * <p>The rules for a signed package are used if the manifest carries the attribute that signing
   * adds, and those for an unsigned package otherwise. The signature is not checked.
   *
   * @param file the bar
   * @return what the rules found; not valid if a rule is broken
   * @throws IOException if the file cannot be read
   * @throws InvalidBarException if the file is not a ZIP archive or has no usable manifest
   */
  public static BarValidation validate(Path file) throws IOException, InvalidBarException {
    try (var archive = BarArchive.open(file)) {
      return ManifestValidator.validate(archive.manifest());
    }
  }

  /**
   * Decides what a bar contains from its package type.
   *
   * <p>Firmware is a package of type {@code system}, or a patch whose {@code Patch-Package-Type} is
   * {@code system}. It is the radio if {@code System-Type} says {@code radio} or the name of the
   * file contains {@code radio}, and the operating system otherwise. The rule about the file name
   * is the research's; it is applied to firmware only.
   */
  static BarKind classify(
      String type, String patchPackageType, String systemType, String fileName) {
    return switch (type) {
      case "application" -> BarKind.APPLICATION;
      case "system" -> firmware(systemType, fileName);
      case "patch" ->
          "system".equalsIgnoreCase(patchPackageType)
              ? firmware(systemType, fileName)
              : BarKind.OTHER;
      default -> BarKind.OTHER;
    };
  }

  private static BarKind firmware(String systemType, String fileName) {
    var radio =
        "radio".equalsIgnoreCase(systemType) || fileName.toLowerCase(Locale.ROOT).contains("radio");
    return radio ? BarKind.RADIO : BarKind.OPERATING_SYSTEM;
  }
}
