package com.jbraddockm.core.webdav;

import java.time.Instant;
import java.util.Objects;

/**
 * One resource in a WebDAV {@code PROPFIND} answer: a file or a collection. The server builds these
 * from its jailed filesystem; the client parses them from a {@code DAV:multistatus}.
 *
 * @param href the resource's href path, percent-encoded, collection hrefs ending in {@code /}
 * @param collection whether this is a collection (directory)
 * @param contentLength the byte length of a file; {@code 0} for a collection
 * @param lastModified the last-modified time, or {@code null} if unknown
 * @param displayName the human-readable name (the last path segment)
 * @param contentType the MIME type of file, or {@code null} for a collection or when unknown
 */
public record DavResource(
    String href,
    boolean collection,
    long contentLength,
    Instant lastModified,
    String displayName,
    String contentType) {

  /** Validates the resource. */
  public DavResource {
    Objects.requireNonNull(href, "href");
    Objects.requireNonNull(displayName, "displayName");
    if (contentLength < 0) {
      throw new IllegalArgumentException("contentLength cannot be negative");
    }
  }
}
