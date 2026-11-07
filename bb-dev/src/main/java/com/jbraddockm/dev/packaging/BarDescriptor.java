package com.jbraddockm.dev.packaging;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A parsed {@code bar-descriptor.xml} — the input the packager turns into a manifest and a {@code
 * .bar} archive. Only the native-application vocabulary is modelled, as written in {@code
 * analysis/BAR-FORMAT.md} §6; the signing and system-package attributes are not, because
 * development bars are unsigned.
 *
 * <p>The id, the name and the version number are always present, and there is always an entry-point
 * asset; everything else may be absent. The values are carried through as written: paths, versions
 * and orientations are checked when the manifest and the archive are built, which is later work.
 *
 * @param id the {@code id} (reverse-DNS), which becomes {@code Package-Name}
 * @param name the user-visible {@code name}
 * @param description the {@code description}; may be {@code null}
 * @param versionNumber the {@code versionNumber}, the first three segments of the package version
 * @param initialWindow the {@code initialWindow}; may be {@code null}
 * @param icons the icon image paths, from {@code icon/image}, in document order; empty if none
 * @param author the {@code author}; may be {@code null}
 * @param authorId the {@code authorId}; may be {@code null}
 * @param packageId the {@code packageId}; may be {@code null}
 * @param buildId the {@code buildId}, the fourth version segment; may be {@code null}
 * @param buildIdFile the {@code buildIdFile} that holds the build id; may be {@code null}
 * @param category the {@code category}; may be {@code null}
 * @param environment the {@code env} entries, in document order
 * @param splashScreen the {@code splashscreen}, a {@code landscape:portrait} pair of paths; may be
 *     {@code null}
 * @param permissions the {@code permission} entries, in document order
 * @param assets the top-level {@code asset} entries, in document order
 * @param platformVersion the {@code platformVersion}; may be {@code null}
 * @param configurations the {@code configuration} entries, in document order
 */
public record BarDescriptor(
    String id,
    String name,
    String description,
    String versionNumber,
    InitialWindow initialWindow,
    List<String> icons,
    String author,
    String authorId,
    String packageId,
    String buildId,
    String buildIdFile,
    String category,
    List<EnvironmentVariable> environment,
    String splashScreen,
    List<String> permissions,
    List<DescriptorAsset> assets,
    String platformVersion,
    List<DescriptorConfiguration> configurations) {

  /**
   * Validates the components that cannot be absent and takes immutable copies of the lists.
   *
   * @throws NullPointerException if the id, the name or the version number is {@code null}
   */
  public BarDescriptor {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(versionNumber, "versionNumber");
    icons = List.copyOf(icons);
    environment = List.copyOf(environment);
    permissions = List.copyOf(permissions);
    assets = List.copyOf(assets);
    configurations = List.copyOf(configurations);
  }

  /**
   * Parses a {@code bar-descriptor.xml}.
   *
   * @param xml the descriptor text
   * @return the parsed descriptor
   * @throws InvalidDescriptorException if the text is not a usable native descriptor
   */
  public static BarDescriptor parse(String xml) throws InvalidDescriptorException {
    return BarDescriptorParser.parse(xml);
  }

  /**
   * The entry-point asset: the one marked {@code entry="true"}, looked for among the top-level
   * assets first and then each configuration's assets, in document order. A parsed descriptor
   * always has one.
   *
   * @return the entry-point asset, or empty if none is marked
   */
  public Optional<DescriptorAsset> entryPoint() {
    return Stream.concat(
            assets.stream(),
            configurations.stream().flatMap(configuration -> configuration.assets().stream()))
        .filter(DescriptorAsset::entry)
        .findFirst();
  }
}
