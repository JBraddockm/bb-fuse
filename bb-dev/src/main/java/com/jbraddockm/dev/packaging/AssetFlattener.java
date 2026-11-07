package com.jbraddockm.dev.packaging;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Works out where each of a descriptor's files goes inside the archive. Native-application assets
 * live under {@value #ARCHIVE_ROOT}, keeping the destination the descriptor gives, or the file's
 * own name when it gives none. The icons the descriptor declares are packed too, since the manifest
 * points its entry point at one.
 */
final class AssetFlattener {

  /** The folder in the archive that holds a native application's files. */
  static final String ARCHIVE_ROOT = "native/";

  /** The archive path of the descriptor, which is packed with the application. */
  static final String DESCRIPTOR_PATH = ARCHIVE_ROOT + "bar-descriptor.xml";

  private AssetFlattener() {}

  /**
   * Resolves the files to pack: the descriptor's assets and its icons that the caller supplies.
   *
   * @param descriptor the descriptor
   * @param assetFiles each source path the descriptor names, mapped to the file on the host
   * @return the packed assets, in the order the descriptor lists them
   */
  static List<PackagedAsset> flatten(BarDescriptor descriptor, Map<String, Path> assetFiles) {
    var packaged = new ArrayList<PackagedAsset>();
    Stream.concat(
            descriptor.assets().stream(),
            descriptor.configurations().stream()
                .flatMap(configuration -> configuration.assets().stream()))
        .forEach(
            asset -> {
              var source = asset.path() == null ? null : assetFiles.get(asset.path());
              if (source != null) {
                packaged.add(
                    new PackagedAsset(archivePath(asset), source, asset.type(), asset.entry()));
              }
            });
    for (var icon : descriptor.icons()) {
      var source = assetFiles.get(icon);
      if (source != null) {
        packaged.add(new PackagedAsset(iconPath(icon), source, null, false));
      }
    }
    return packaged;
  }

  /** The archive path of an asset: the destination if it has one, otherwise the file's own name. */
  static String archivePath(DescriptorAsset asset) {
    var target = asset.destination() == null ? name(asset.path()) : asset.destination();
    return ARCHIVE_ROOT + target;
  }

  /** The archive path of an icon image. */
  static String iconPath(String icon) {
    return ARCHIVE_ROOT + name(icon);
  }

  private static String name(String path) {
    var separator = path.lastIndexOf('/');
    return separator < 0 ? path : path.substring(separator + 1);
  }
}
