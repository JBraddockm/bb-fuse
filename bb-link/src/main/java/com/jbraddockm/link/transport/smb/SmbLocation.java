package com.jbraddockm.link.transport.smb;

import java.util.Arrays;
import java.util.Objects;

/**
 * A file or directory on one of the device's SMB shares, as a share name and a path within it.
 *
 * <p>The path is normalised: no leading or trailing separator, forward slashes only, and no empty,
 * {@code .} or {@code ..} segment. Rejecting {@code ..} keeps a location from escaping its share.
 * The empty path denotes the share root.
 *
 * @param share the share name, for example {@code media}
 * @param path the path within the share, for example {@code documents/report.txt}; empty for the
 *     share root
 */
public record SmbLocation(String share, String path) {

  /**
   * Validates and normalises the location.
   *
   * @throws NullPointerException if {@code share} or {@code path} is {@code null}
   * @throws IllegalArgumentException if the share is blank or the path has an empty, {@code .} or
   *     {@code ..} segment
   */
  public SmbLocation {
    Objects.requireNonNull(share, "share");
    Objects.requireNonNull(path, "path");
    if (share.isBlank()) {
      throw new IllegalArgumentException("share must not be blank");
    }
    path = normalise(path);
  }

  /**
   * Returns the share root.
   *
   * @param share the share name
   * @return the location of the share's top directory
   */
  public static SmbLocation root(String share) {
    return new SmbLocation(share, "");
  }

  /**
   * Returns the location of a named child of this directory.
   *
   * @param name a single path segment, without a separator
   * @return the child location
   * @throws IllegalArgumentException if the name is empty, {@code .}, {@code ..} or contains a
   *     separator
   */
  public SmbLocation child(String name) {
    if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
      throw new IllegalArgumentException("a child name must not contain a separator: " + name);
    }
    return new SmbLocation(share, path.isEmpty() ? name : path + "/" + name);
  }

  /**
   * Returns the final path segment — the file or directory name.
   *
   * @return the last segment, or the empty string for the share root
   */
  public String name() {
    var cut = path.lastIndexOf('/');
    return cut < 0 ? path : path.substring(cut + 1);
  }

  private static String normalise(String raw) {
    var slashed = raw.replace('\\', '/');
    var segments = Arrays.stream(slashed.split("/", -1)).filter(segment -> !segment.isEmpty());
    var cleaned = new StringBuilder();
    for (var segment : (Iterable<String>) segments::iterator) {
      if (segment.equals(".") || segment.equals("..")) {
        throw new IllegalArgumentException("a path segment must not be '.' or '..': " + raw);
      }
      cleaned.append(cleaned.isEmpty() ? "" : "/").append(segment);
    }
    return cleaned.toString();
  }
}
