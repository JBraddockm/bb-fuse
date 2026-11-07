package com.jbraddockm.dev.packaging;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A file placed in a {@code .bar}: where it goes in the archive, the host file it comes from, its
 * type, and whether it is the entry point. The archive path is the flattened {@code native/…} path
 * under which the asset is stored.
 *
 * @param archivePath the path of the entry inside the archive, for example {@code native/binary}
 * @param source the file on the host
 * @param type the asset type, for example {@code Qnx/Elf}; may be {@code null}
 * @param entry whether this asset is the application entry point
 * @param entryPointId the id of the entry point this asset is the binary of; may be {@code null}
 */
record PackagedAsset(
    String archivePath, Path source, String type, boolean entry, String entryPointId) {

  /**
   * Validates the components that cannot be absent.
   *
   * @throws NullPointerException if the archive path or the source is {@code null}
   */
  PackagedAsset {
    Objects.requireNonNull(archivePath, "archivePath");
    Objects.requireNonNull(source, "source");
  }
}
