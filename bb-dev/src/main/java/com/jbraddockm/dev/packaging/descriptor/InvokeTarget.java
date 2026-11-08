package com.jbraddockm.dev.packaging.descriptor;

import java.util.List;
import java.util.Objects;

/**
 * An {@code invoke-target} of a descriptor: an entry the invocation framework can hand data to. It
 * becomes an {@code Invoke-Target} section in the manifest. The descriptor may name the type and
 * filters with the short tags ({@code type}, {@code action}) or the long ones ({@code
 * invoke-target-type}); both are read.
 *
 * @param id the target id, from the {@code id} attribute, which becomes {@code Invoke-Target-Key}
 * @param type the target type, for example {@code card.composer}; may be {@code null}
 * @param entryPointId the id of the entry point it runs under; may be {@code null}, meaning the
 *     application's single entry point
 * @param label the target's user-visible name; may be {@code null}
 * @param icon the target's icon image path; may be {@code null}
 * @param filters the filters that say what the target handles, in document order
 */
public record InvokeTarget(
    String id,
    String type,
    String entryPointId,
    String label,
    String icon,
    List<InvokeFilter> filters) {

  /**
   * Validates the id and takes an immutable copy of the filters.
   *
   * @throws NullPointerException if the id is {@code null}
   */
  public InvokeTarget {
    Objects.requireNonNull(id, "id");
    filters = List.copyOf(filters);
  }
}
