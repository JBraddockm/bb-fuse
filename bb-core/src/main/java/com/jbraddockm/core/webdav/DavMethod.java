package com.jbraddockm.core.webdav;

import java.util.Optional;

/**
 * The HTTP/WebDAV methods of RIM's {@code rimdav} surface ({@code rim_dav_ext_methods PROPFIND
 * PROPPATCH OPTIONS PUT DELETE MKCOL COPY MOVE}, plus {@code GET}/{@code HEAD}). The wire name is
 * the enum constant's own name.
 */
public enum DavMethod {
  OPTIONS,
  HEAD,
  GET,
  PUT,
  DELETE,
  MKCOL,
  COPY,
  MOVE,
  PROPFIND,
  PROPPATCH;

  /**
   * Recognises an HTTP method name, case-sensitively as HTTP requires.
   *
   * @param name the request method, for example {@code PROPFIND}
   * @return the method, or empty if this version does not handle it
   */
  public static Optional<DavMethod> of(String name) {
    for (var method : values()) {
      if (method.name().equals(name)) {
        return Optional.of(method);
      }
    }
    return Optional.empty();
  }
}
