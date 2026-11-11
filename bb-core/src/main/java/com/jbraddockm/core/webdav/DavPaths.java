package com.jbraddockm.core.webdav;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Encodes and decodes the path part of a WebDAV {@code href}. RIM's rimdav (and the Blend client)
 * percent-encode each path segment the way {@code encodeURIComponent} does, keeping {@code /} as
 * the separator. This never touches a real filesystem — it only transforms the wire path.
 */
public final class DavPaths {

  private DavPaths() {}

  /**
   * Splits a server path into its non-empty, percent-decoded segments. A trailing slash is ignored;
   * {@code .} and {@code ..} segments are preserved for the caller to reject.
   *
   * @param path the path, for example {@code /a%20b/c.txt}
   * @return the decoded segments, for example {@code [a b, c.txt]}
   */
  public static List<String> segments(String path) {
    var segments = new ArrayList<String>();
    for (var raw : path.split("/")) {
      if (!raw.isEmpty()) {
        segments.add(decode(raw));
      }
    }
    return List.copyOf(segments);
  }

  /**
   * Builds an {@code href} path from decoded segments, percent-encoding each. A collection href
   * ends with a slash, as WebDAV expects.
   *
   * @param segments the decoded path segments
   * @param collection whether the target is a collection (directory)
   * @return the encoded href path, always starting with {@code /}
   */
  public static String href(List<String> segments, boolean collection) {
    var builder = new StringBuilder("/");
    for (var i = 0; i < segments.size(); i++) {
      if (i > 0) {
        builder.append('/');
      }
      builder.append(encodeSegment(segments.get(i)));
    }
    if (collection && !segments.isEmpty()) {
      builder.append('/');
    }
    return builder.toString();
  }

  /**
   * Percent-encodes one path segment like {@code encodeURIComponent}: everything except the
   * unreserved set {@code A-Z a-z 0-9 - _ . ~} is encoded as UTF-8 {@code %XX}.
   *
   * @param segment the raw segment
   * @return the encoded segment
   */
  public static String encodeSegment(String segment) {
    var out = new StringBuilder();
    for (var b : segment.getBytes(StandardCharsets.UTF_8)) {
      var c = (char) (b & 0xff);
      if ((c >= 'A' && c <= 'Z')
          || (c >= 'a' && c <= 'z')
          || (c >= '0' && c <= '9')
          || c == '-'
          || c == '_'
          || c == '.'
          || c == '~') {
        out.append(c);
      } else {
        out.append('%');
        out.append(Character.toUpperCase(Character.forDigit((b >> 4) & 0xf, 16)));
        out.append(Character.toUpperCase(Character.forDigit(b & 0xf, 16)));
      }
    }
    return out.toString();
  }

  /**
   * Percent-decodes one path segment (UTF-8).
   *
   * @param segment the encoded segment
   * @return the decoded segment
   */
  public static String decode(String segment) {
    var bytes = new java.io.ByteArrayOutputStream();
    for (var i = 0; i < segment.length(); i++) {
      var c = segment.charAt(i);
      if (c == '%' && i + 2 < segment.length()) {
        var hi = Character.digit(segment.charAt(i + 1), 16);
        var lo = Character.digit(segment.charAt(i + 2), 16);
        if (hi >= 0 && lo >= 0) {
          bytes.write((hi << 4) | lo);
          i += 2;
          continue;
        }
      }
      bytes.write(c);
    }
    return bytes.toString(StandardCharsets.UTF_8);
  }
}
