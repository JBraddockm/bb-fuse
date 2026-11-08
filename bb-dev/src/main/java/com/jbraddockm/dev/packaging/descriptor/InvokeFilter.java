package com.jbraddockm.dev.packaging.descriptor;

import java.util.List;

/**
 * One {@code filter} of an invoke target: the actions and MIME types the target handles, and any
 * extra match properties. It becomes one {@code Invoke-Target-Filter} line in the manifest.
 *
 * @param actions the {@code action} names, in document order
 * @param mimeTypes the {@code mime-type} values, in document order
 * @param properties the extra {@code property} var/value pairs, in document order
 */
public record InvokeFilter(
    List<String> actions, List<String> mimeTypes, List<EnvironmentVariable> properties) {

  /** Takes immutable copies of the lists. */
  public InvokeFilter {
    actions = List.copyOf(actions);
    mimeTypes = List.copyOf(mimeTypes);
    properties = List.copyOf(properties);
  }
}
