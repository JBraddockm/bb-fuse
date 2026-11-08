package com.jbraddockm.dev.packaging.descriptor;

import java.util.List;

/**
 * An {@code asset} of a descriptor: a file or folder to pack, where it goes in the archive, and
 * whether it is the entry point. The destination is the {@code dest} attribute, or the element's
 * text, or — failing both — the source file's own name; a folder asset packs its files under that
 * destination, filtered by any {@code include} patterns.
 *
 * @param path the source path on the host, relative to the project, from the {@code path}
 *     attribute; may be {@code null}
 * @param destination the path inside the archive's {@code native/} folder, from the {@code dest}
 *     attribute or the element text; may be {@code null}, meaning the source file's own name
 * @param type the asset type, for example {@code Qnx/Elf}, from the {@code type} attribute; may be
 *     {@code null}
 * @param entry whether this asset is the application entry point ({@code entry: true})
 * @param entryPointId the id of the entry point this asset is the binary of, from the {@code
 *     entryPointId} attribute; may be {@code null}
 * @param includes the {@code include} name patterns that select which files of a folder asset to
 *     pack; empty means every file
 * @param excludes the {@code exclude} name patterns that drop files of a folder asset from the pack
 * @param isPublic whether the asset is {@code public="true"} and so is packed under {@code public/}
 *     for other applications to read, rather than {@code native/}
 */
public record DescriptorAsset(
    String path,
    String destination,
    String type,
    boolean entry,
    String entryPointId,
    List<String> includes,
    List<String> excludes,
    boolean isPublic) {

  /** Takes immutable copies of the pattern lists. */
  public DescriptorAsset {
    includes = List.copyOf(includes);
    excludes = List.copyOf(excludes);
  }
}
