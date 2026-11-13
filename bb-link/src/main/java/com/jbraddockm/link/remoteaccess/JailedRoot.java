package com.jbraddockm.link.remoteaccess;

import com.jbraddockm.core.webdav.DavPaths;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The confinement of the file share: a single root directory below which, and only below which, the
 * WebDAV server may resolve request paths. This is the server's one security primitive — every
 * request path passes through {@link #resolve}, which rejects anything that would escape the root
 * (parent traversal, absolute paths, path separators inside a segment, and symlinks that point
 * outside the jail). The root is <em>selectable</em>: the share serves exactly the tree the caller
 * names, never the whole filesystem.
 *
 * <p>The root's real path (symlinks resolved) is fixed at construction; every resolved path is
 * checked against it both lexically and, where it or an ancestor exists, by real path.
 *
 * <p>The real-path check and the handler's use of the path are separate steps, so a symlink swapped
 * in between them is a time-of-check/time-of-use window the pure-JDK file API cannot close (there
 * is no {@code openat}/{@code O_NOFOLLOW}). It is left open deliberately: the share faces a single
 * paired device over the tunnel and serves a host-selected root, so an attacker able to race
 * symlinks under that root already has local write access to it.
 */
final class JailedRoot {

  private final Path realRoot;

  private JailedRoot(Path realRoot) {
    this.realRoot = realRoot;
  }

  /**
   * Jails the share to a directory.
   *
   * @param root the directory to serve; it must exist and be a directory
   * @return the jail
   * @throws IllegalArgumentException if the root does not exist or is not a directory
   * @throws UncheckedIOException if its real path cannot be read
   */
  static JailedRoot of(Path root) {
    try {
      var real = root.toRealPath();
      if (!Files.isDirectory(real)) {
        throw new IllegalArgumentException("the share root is not a directory: " + root);
      }
      return new JailedRoot(real);
    } catch (IOException failure) {
      throw new UncheckedIOException("the share root cannot be read: " + root, failure);
    }
  }

  /** The real (symlink-resolved) root directory of the share. */
  Path root() {
    return realRoot;
  }

  /**
   * Resolves a WebDAV request path to a filesystem path within the jail, or refuses it.
   *
   * @param requestPath the request path, for example {@code /sub/dir/file.txt} (percent-encoded)
   * @return the confined path, or empty when the path would escape the jail or is malformed; an
   *     empty path (the root itself) resolves to the root
   */
  Optional<Path> resolve(String requestPath) {
    var segments = DavPaths.segments(requestPath);
    for (var segment : segments) {
      if (segment.isEmpty()
          || segment.equals(".")
          || segment.equals("..")
          || segment.indexOf('/') >= 0
          || segment.indexOf('\\') >= 0
          || segment.indexOf('\0') >= 0) {
        return Optional.empty();
      }
    }

    var candidate = realRoot;
    for (var segment : segments) {
      candidate = candidate.resolve(segment);
    }
    candidate = candidate.normalize();

    // Lexical guard: the normalized path must stay under the root.
    if (!candidate.startsWith(realRoot)) {
      return Optional.empty();
    }
    // Symlink guard: the real path of the nearest existing ancestor must stay under the root, so a
    // symlink anywhere on the path cannot point out of the jail.
    if (!nearestRealAncestorIsInside(candidate)) {
      return Optional.empty();
    }
    return Optional.of(candidate);
  }

  private boolean nearestRealAncestorIsInside(Path candidate) {
    var path = candidate;
    while (path != null) {
      if (Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS) || Files.exists(path)) {
        try {
          return path.toRealPath().startsWith(realRoot);
        } catch (IOException failure) {
          return false;
        }
      }
      path = path.getParent();
    }
    return false;
  }

  /**
   * Builds the WebDAV {@code href} path for a resolved path, relative to the root.
   *
   * @param path a path returned by {@link #resolve}
   * @param collection whether the path is a collection (directory)
   * @return the href path, percent-encoded, collection hrefs ending in {@code /}
   */
  String href(Path path, boolean collection) {
    var relative = realRoot.relativize(path);
    var segments = new java.util.ArrayList<String>();
    for (var part : relative) {
      var name = part.toString();
      if (!name.isEmpty()) {
        segments.add(name);
      }
    }
    return DavPaths.href(List.copyOf(segments), collection);
  }
}
