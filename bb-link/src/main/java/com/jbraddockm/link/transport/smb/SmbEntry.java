package com.jbraddockm.link.transport.smb;

import java.time.Instant;
import java.util.Objects;

/**
 * One entry of a directory listing on the device's share.
 *
 * @param name the file or directory name, without a trailing separator
 * @param directory whether the entry is a directory
 * @param size the size in bytes; meaningful for a file, {@code 0} for a directory
 * @param modifiedAt the last-modified time
 */
public record SmbEntry(String name, boolean directory, long size, Instant modifiedAt) {

  /**
   * Validates the entry.
   *
   * @throws NullPointerException if {@code name} or {@code modifiedAt} is {@code null}
   */
  public SmbEntry {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(modifiedAt, "modifiedAt");
  }
}
