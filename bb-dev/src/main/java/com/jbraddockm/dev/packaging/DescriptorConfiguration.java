package com.jbraddockm.dev.packaging;

import java.util.List;
import java.util.Objects;

/**
 * A {@code configuration} of a descriptor: assets and entry-point arguments that apply only to one
 * build configuration, for example {@code Device-Debug} or {@code Device-Release}.
 *
 * @param id the configuration id, from the {@code id} attribute
 * @param name the configuration name, from the {@code name} attribute; may be {@code null}
 * @param platformArchitecture the target architecture, for example {@code armv7-le}; may be {@code
 *     null}
 * @param arguments the entry-point arguments, in document order
 * @param assets the assets that this configuration adds, in document order
 */
public record DescriptorConfiguration(
    String id,
    String name,
    String platformArchitecture,
    List<String> arguments,
    List<DescriptorAsset> assets) {

  /**
   * Validates the id and takes immutable copies of the lists.
   *
   * @throws NullPointerException if the id is {@code null}
   */
  public DescriptorConfiguration {
    Objects.requireNonNull(id, "id");
    arguments = List.copyOf(arguments);
    assets = List.copyOf(assets);
  }
}
