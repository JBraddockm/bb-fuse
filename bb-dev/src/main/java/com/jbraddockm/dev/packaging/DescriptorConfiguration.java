package com.jbraddockm.dev.packaging;

import java.util.List;

/**
 * A {@code configuration} of a descriptor: the assets, architecture and entry-point arguments that
 * apply to one build configuration, for example {@code Device-Debug} or {@code Device-CoreNative}.
 * A package is built for one configuration, chosen by its name.
 *
 * @param name the configuration name, from the {@code name} attribute, which selects it; may be
 *     {@code null} for an unnamed configuration
 * @param id the configuration id, from the {@code id} attribute; may be {@code null}
 * @param platformArchitecture the target architecture, for example {@code armle-v7}; may be {@code
 *     null}
 * @param entryPointType the entry-point type, for example {@code Qnx/Cascades}, which overrides the
 *     entry asset's type for this configuration; may be {@code null}
 * @param arguments the entry-point arguments, in document order
 * @param assets the assets that this configuration adds, in document order
 */
public record DescriptorConfiguration(
    String name,
    String id,
    String platformArchitecture,
    String entryPointType,
    List<String> arguments,
    List<DescriptorAsset> assets) {

  /** Takes immutable copies of the lists. */
  public DescriptorConfiguration {
    arguments = List.copyOf(arguments);
    assets = List.copyOf(assets);
  }
}
