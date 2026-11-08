package com.jbraddockm.dev.packaging;

import com.jbraddockm.core.bar.Manifest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Builds an unsigned, development-mode {@code .bar} from a {@code bar-descriptor.xml} and the files
 * it names, for one build configuration. This is the whole of the former bb-packager's job minus
 * signing: parse the descriptor, choose the configuration, resolve and flatten its files, add any
 * extra files the command line names, generate the manifest, and write the archive.
 */
final class Packager {

  private Packager() {}

  /**
   * The options that shape a package beyond the descriptor itself, from the command line.
   *
   * @param configuration the configuration to build, or {@code null} for none
   * @param buildId the build id override (the fourth version segment), or {@code null} to take the
   *     descriptor's own
   * @param appVersion the first-three-segment version override, or {@code null} for the
   *     descriptor's
   * @param extraFiles files to add beyond those the descriptor names, each at a given archive path
   * @param devMode whether to build a development-mode bar (the {@code -devMode} option): it marks
   *     {@code Application-Development-Mode: true}, which grants the on-device development sandbox
   *     and lets deploy sync files into it; off by default and not required for an unsigned bar
   */
  record PackageOptions(
      String configuration,
      String buildId,
      String appVersion,
      List<ExtraFile> extraFiles,
      boolean devMode) {

    PackageOptions {
      extraFiles = List.copyOf(extraFiles);
    }

    /** The options for a plain build of one configuration, with no overrides or extra files. */
    static PackageOptions of(String configuration) {
      return new PackageOptions(configuration, null, null, List.of(), false);
    }
  }

  /**
   * A file added from the command line rather than from the descriptor.
   *
   * @param archivePath the path the file takes inside the archive (or, for a folder, the prefix its
   *     files take)
   * @param source the file or folder on the host
   */
  record ExtraFile(String archivePath, Path source) {}

  /**
   * A package worked out but not yet written: the manifest, the files it covers, and the version.
   *
   * @param manifest the generated manifest
   * @param assets the files to write, in order
   * @param version the resolved four-segment package version
   */
  record PackagePlan(Manifest manifest, List<PackagedAsset> assets, String version) {}

  /**
   * Packages an application.
   *
   * @param descriptorFile the {@code bar-descriptor.xml}
   * @param projectRoot the folder the descriptor's asset paths are relative to
   * @param output where to write the bar
   * @param options the command-line options
   * @return the packed files, in the order they were written, for reporting
   * @throws IOException if a file cannot be read or the bar cannot be written
   * @throws InvalidDescriptorException if the descriptor is not usable or the configuration is
   *     unknown or has no entry point
   */
  static List<PackagedAsset> pack(
      Path descriptorFile, Path projectRoot, Path output, PackageOptions options)
      throws IOException, InvalidDescriptorException {
    var plan = plan(descriptorFile, projectRoot, options);
    BarWriter.write(output, plan.manifest(), plan.assets());
    return plan.assets();
  }

  /**
   * Works out the manifest and the files a package would hold, without writing anything. This backs
   * {@code -list} and {@code -listManifest} as well as the write itself.
   *
   * @param descriptorFile the {@code bar-descriptor.xml}
   * @param projectRoot the folder the descriptor's asset paths are relative to
   * @param options the command-line options
   * @return the plan
   * @throws IOException if a file cannot be read
   * @throws InvalidDescriptorException if the descriptor is not usable or the configuration is
   *     unknown or has no entry point
   */
  static PackagePlan plan(Path descriptorFile, Path projectRoot, PackageOptions options)
      throws IOException, InvalidDescriptorException {
    var descriptor = BarDescriptor.parse(Files.readString(descriptorFile));

    DescriptorConfiguration configuration = null;
    if (options.configuration() != null) {
      configuration =
          descriptor
              .configuration(options.configuration())
              .orElseThrow(
                  () ->
                      new InvalidDescriptorException(
                          "it has no configuration named '" + options.configuration() + "'"));
    }

    var assets = AssetFlattener.assetsFor(descriptor, configuration);
    if (assets.stream().noneMatch(DescriptorAsset::entry)) {
      throw new InvalidDescriptorException(
          options.configuration() == null
              ? "it has no entry point: no asset is marked entry=\"true\""
              : "the configuration '" + options.configuration() + "' has no entry point");
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
    addExtras(packaged, options.extraFiles());
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
    var version = version(descriptor, projectRoot, options);
    var manifest =
        ManifestGenerator.generate(
            descriptor,
            architecture,
            configEntryPointType,
            arguments,
            splashScreen(descriptor, sources),
            packaged,
            version,
            options.devMode());

    return new PackagePlan(manifest, List.copyOf(packaged), version);
  }

  /** Adds the command-line files, each at its archive path; a folder is added file by file. */
  private static void addExtras(List<PackagedAsset> packaged, List<ExtraFile> extraFiles)
      throws IOException {
    var taken = new HashSet<String>();
    for (var asset : packaged) {
      taken.add(asset.archivePath());
    }
    for (var extra : extraFiles) {
      if (Files.isDirectory(extra.source())) {
        try (var files = Files.walk(extra.source())) {
          for (var file : files.filter(Files::isRegularFile).sorted().toList()) {
            var relative = extra.source().relativize(file).toString().replace('\\', '/');
            addExtra(packaged, taken, extra.archivePath() + "/" + relative, file);
          }
        }
      } else if (Files.exists(extra.source())) {
        addExtra(packaged, taken, extra.archivePath(), extra.source());
      } else {
        throw new NoSuchFileException(extra.source().toString());
      }
    }
  }

  private static void addExtra(
      List<PackagedAsset> packaged, Set<String> taken, String archivePath, Path source) {
    if (taken.add(archivePath)) {
      packaged.add(new PackagedAsset(archivePath, source, null, false, null));
    }
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

  /** The four-segment version: the app version (override or descriptor) plus the build id. */
  private static String version(BarDescriptor descriptor, Path projectRoot, PackageOptions options)
      throws IOException {
    var appVersion =
        options.appVersion() != null ? options.appVersion() : descriptor.versionNumber();
    var buildId = options.buildId() != null ? options.buildId() : buildId(descriptor, projectRoot);
    return appVersion + "." + buildId;
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
