package com.jbraddockm.dev.packaging;

import java.util.List;
import java.util.Objects;

/**
 * An {@code entryPoint} of a descriptor: one of an application's entry points, which becomes an
 * {@code Entry-Point} section in the manifest keyed by its id. An application with several entry
 * points — typically a UI front end and a headless service — declares one of these for each, and
 * the asset carrying the matching {@code entryPointId} is that entry point's binary.
 *
 * @param id the entry-point id, from the {@code id} attribute, which becomes {@code
 *     Entry-Point-Key}
 * @param name the entry-point name; may be {@code null}, meaning the application name
 * @param entryPointType the entry-point type, which overrides the asset's and the configuration's;
 *     may be {@code null}
 * @param permissions the permissions this entry point adds to the application's, in document order
 */
public record EntryPoint(
    String id, String name, String entryPointType, List<Permission> permissions) {

  /**
   * Validates the id and takes an immutable copy of the permissions.
   *
   * @throws NullPointerException if the id is {@code null}
   */
  public EntryPoint {
    Objects.requireNonNull(id, "id");
    permissions = List.copyOf(permissions);
  }
}
