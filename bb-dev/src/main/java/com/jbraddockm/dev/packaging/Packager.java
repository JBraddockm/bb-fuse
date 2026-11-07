package com.jbraddockm.dev.packaging;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Builds an unsigned, development-mode {@code .bar} from a {@code bar-descriptor.xml} and the files
 * it names, for one build configuration. This is the whole of the former bb-packager's job minus
 * signing: parse the descriptor, choose the configuration, resolve and flatten its files, generate
 * the manifest, and write the archive.
 */
final class Packager {

  private Packager() {}

  /**
   * Packages an application.
   *
   * @param descriptorFile the {@code bar-descriptor.xml}
   * @param projectRoot the folder the descriptor's asset paths are relative to
   * @param configurationName the configuration to build, or {@code null} for none
   * @param output where to write the bar
   * @return the packed files, in the order they were written, for reporting
   * @throws IOException if a file cannot be read or the bar cannot be written
   * @throws InvalidDescriptorException if the descriptor is not usable or the configuration is
   *     unknown or has no entry point
   */
  static List<PackagedAsset> pack(
      Path descriptorFile, Path projectRoot, String configurationName, Path output)
      throws IOException, InvalidDescriptorException {
    var descriptor = BarDescriptor.parse(Files.readString(descriptorFile));

    DescriptorConfiguration configuration = null;
    if (configurationName != null) {
      configuration =
          descriptor
              .configuration(configurationName)
              .orElseThrow(() ->
                  new InvalidDescriptorException(
                      "it has no configuration named '" + configurationName + "'"));
    }

    var assets = AssetFlattener.assetsFor(descriptor, configuration);
    if (assets.stream().noneMatch(DescriptorAsset::entry)) {
      throw new InvalidDescriptorException(
          configurationName == null
              ? "it has no entry point: no asset is marked entry=\"true\""
              : "the configuration '" + configurationName + "' has no entry point");
    }

    var icons =
        Stream.concat(
                descriptor.icons().stream(),
                descriptor.invokeTargets().stream()
                    .map(InvokeTarget::icon)
                    .filter(Objects::nonNull))
            .distinct()
            .toList();
    var sources = sources(assets, icons, descriptor.splashScreens(), projectRoot);
    var packaged =
        new ArrayList<>(AssetFlattener.flatten(assets, icons, descriptor.splashScreens(), sources));
    packaged.add(
        new PackagedAsset(
            AssetFlattener.ARCHIVE_ROOT + descriptorFile.getFileName(),
            descriptorFile,
            null,
            false,
            null));

    var architecture =
        ManifestGenerator.mapArchitecture(
            configuration == null ? null : configuration.platformArchitecture());
    var configEntryPointType =
        configuration != null && configuration.entryPointType() != null
            ? configuration.entryPointType()
            : descriptor.entryPointType();
    var arguments =
        Stream.concat(
                descriptor.arguments().stream(),
                configuration == null ? Stream.of() : configuration.arguments().stream())
            .toList();
    var manifest =
        ManifestGenerator.generate(
            descriptor,
            architecture,
            configEntryPointType,
            arguments,
            splashScreen(descriptor, sources),
            packaged,
            buildId(descriptor, projectRoot));

    BarWriter.write(output, manifest, packaged);
    return packaged;
  }

  /** The {@code Entry-Point-Splash-Screen} value: each image sized and archive-pathed, or null. */
  private static String splashScreen(BarDescriptor descriptor, Map<String, Path> sources)
      throws IOException {
    var parts = new ArrayList<String>();
    for (var image : descriptor.splashScreens()) {
      var source = sources.get(image);
      if (source == null) {
        continue;
      }
      var size = ImageDimensions.of(source);
      if (size != null) {
        parts.add(size + AssetFlattener.ARCHIVE_ROOT + image);
      }
    }
    return parts.isEmpty() ? null : String.join(":", parts);
  }

  /** Each declared source path, resolved against the project, kept if it exists. */
  private static Map<String, Path> sources(
      List<DescriptorAsset> assets,
      List<String> icons,
      List<String> splashScreens,
      Path projectRoot) {
    var sources = new LinkedHashMap<String, Path>();
    for (var asset : assets) {
      resolve(sources, asset.path(), projectRoot);
    }
    for (var icon : icons) {
      resolve(sources, icon, projectRoot);
    }
    for (var image : splashScreens) {
      resolve(sources, image, projectRoot);
    }
    return sources;
  }

  private static void resolve(Map<String, Path> sources, String path, Path projectRoot) {
    if (path != null && !sources.containsKey(path)) {
      var file = projectRoot.resolve(path);
      if (Files.exists(file)) {
        sources.put(path, file);
      }
    }
  }

  /** The build id: the descriptor's own, the one in its {@code buildIdFile}, or {@code 0}. */
  private static String buildId(BarDescriptor descriptor, Path projectRoot) throws IOException {
    if (descriptor.buildId() != null) {
      return descriptor.buildId();
    }
    if (descriptor.buildIdFile() != null) {
      var file = projectRoot.resolve(descriptor.buildIdFile());
      if (Files.exists(file)) {
        return Files.readString(file).strip();
      }
    }
    return ManifestGenerator.DEFAULT_BUILD_ID;
  }
}
