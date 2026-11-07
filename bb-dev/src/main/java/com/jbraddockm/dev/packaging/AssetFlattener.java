package com.jbraddockm.dev.packaging;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Works out where each of a descriptor's files goes inside the archive. Native-application files
 * live under {@value #ARCHIVE_ROOT}, at the destination the descriptor gives or, failing that, the
 * file's own name. A folder asset is packed file by file under its destination, keeping its shape
 * and taking only the files its {@code include} patterns select. A path packed more than once — an
 * icon that is also listed as an asset — is kept once.
 */
final class AssetFlattener {

  /** The folder in the archive that holds a native application's files. */
  static final String ARCHIVE_ROOT = "native/";

  /** The folder that holds assets other applications may read ({@code public="true"}). */
  static final String PUBLIC_ROOT = "public/";

  /** Folder names a folder asset never packs, as BlackBerry's default excludes drop them. */
  private static final Set<String> EXCLUDED_FOLDERS =
      Set.of(".git", ".svn", ".hg", ".bzr", "CVS", "SCCS");

  /** File names a folder asset never packs. */
  private static final Set<String> EXCLUDED_NAMES =
      Set.of(
          ".DS_Store",
          ".gitignore",
          ".gitattributes",
          ".gitmodules",
          ".cvsignore",
          ".hgignore",
          ".hgsub",
          ".hgsubstate",
          ".hgtags",
          ".bzrignore",
          "vssver.scc");

  private AssetFlattener() {}

  /**
   * Resolves the files to pack: the given assets and the icons the descriptor declares.
   *
   * @param assets the assets to pack, already narrowed to the chosen configuration
   * @param icons the icon image source paths
   * @param splashScreens the splash-screen image source paths
   * @param sources each source path the descriptor names, mapped to the file or folder on the host
   * @return the packed files, in document order, each archive path once
   * @throws IOException if a folder asset cannot be walked
   */
  static List<PackagedAsset> flatten(
      List<DescriptorAsset> assets,
      List<String> icons,
      List<String> splashScreens,
      Map<String, Path> sources)
      throws IOException {
    var packaged = new LinkedHashMap<String, PackagedAsset>();
    for (var asset : assets) {
      var source = asset.path() == null ? null : sources.get(asset.path());
      if (source == null) {
        continue;
      }
      if (Files.isDirectory(source)) {
        packFolder(asset, source, packaged);
      } else {
        put(
            packaged,
            new PackagedAsset(
                archivePath(asset), source, asset.type(), asset.entry(), asset.entryPointId()));
      }
    }
    for (var image : icons) {
      pack(packaged, image, iconPath(image), sources);
    }
    for (var image : splashScreens) {
      pack(packaged, image, ARCHIVE_ROOT + image, sources);
    }
    return List.copyOf(packaged.values());
  }

  private static void pack(
      Map<String, PackagedAsset> packaged,
      String path,
      String archivePath,
      Map<String, Path> sources) {
    var source = sources.get(path);
    if (source != null && !Files.isDirectory(source)) {
      put(packaged, new PackagedAsset(archivePath, source, null, false, null));
    }
  }

  private static void packFolder(
      DescriptorAsset asset, Path folder, Map<String, PackagedAsset> packaged) throws IOException {
    var root = asset.isPublic() ? PUBLIC_ROOT : ARCHIVE_ROOT;
    var destination = destination(asset);
    try (var files = Files.walk(folder)) {
      var selected =
          files
              .filter(Files::isRegularFile)
              .filter(file -> !defaultExcluded(folder.relativize(file)))
              .filter(
                  file ->
                      matchesIncludes(asset.includes(), file) && !matches(asset.excludes(), file))
              .sorted(Comparator.comparing(Path::toString))
              .toList();
      for (var file : selected) {
        var relative = folder.relativize(file).toString().replace('\\', '/');
        put(
            packaged,
            new PackagedAsset(root + destination + "/" + relative, file, null, false, null));
      }
    }
  }

  /** Whether a file matches any of the glob patterns; empty patterns match nothing here. */
  private static boolean matches(List<String> patterns, Path file) {
    var name = file.getFileName();
    return patterns.stream()
        .anyMatch(
            pattern -> FileSystems.getDefault().getPathMatcher("glob:" + pattern).matches(name));
  }

  /** Whether an include list keeps the file: an empty include list keeps everything. */
  private static boolean matchesIncludes(List<String> includes, Path file) {
    return includes.isEmpty() || matches(includes, file);
  }

  /** Whether BlackBerry's default excludes drop this relative path (VCS folders, junk files). */
  private static boolean defaultExcluded(Path relative) {
    for (var segment : relative) {
      if (EXCLUDED_FOLDERS.contains(segment.toString())) {
        return true;
      }
    }
    var name = relative.getFileName().toString();
    return EXCLUDED_NAMES.contains(name)
        || name.endsWith("~")
        || name.startsWith(".#")
        || name.startsWith("._")
        || (name.startsWith("#") && name.endsWith("#"))
        || (name.startsWith("%") && name.endsWith("%"));
  }

  /** The archive path of a file asset: its root ({@code native/} or {@code public/}) and dest. */
  static String archivePath(DescriptorAsset asset) {
    return (asset.isPublic() ? PUBLIC_ROOT : ARCHIVE_ROOT) + destination(asset);
  }

  /** The archive path of an icon image. */
  static String iconPath(String icon) {
    return ARCHIVE_ROOT + name(icon);
  }

  private static String destination(DescriptorAsset asset) {
    return asset.destination() == null ? name(asset.path()) : asset.destination();
  }

  private static void put(Map<String, PackagedAsset> packaged, PackagedAsset asset) {
    packaged.putIfAbsent(asset.archivePath(), asset);
  }

  private static String name(String path) {
    var separator = path.lastIndexOf('/');
    return separator < 0 ? path : path.substring(separator + 1);
  }

  /** A stream of the assets to pack for a configuration: the top-level ones, then that one's. */
  static List<DescriptorAsset> assetsFor(BarDescriptor descriptor, DescriptorConfiguration config) {
    return Stream.concat(
            descriptor.assets().stream(), config == null ? Stream.of() : config.assets().stream())
        .toList();
  }
}
