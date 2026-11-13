package com.jbraddockm.link.remoteaccess;

import com.jbraddockm.core.webdav.DavMethod;
import com.jbraddockm.core.webdav.DavResource;
import com.jbraddockm.core.webdav.DavStatus;
import com.jbraddockm.core.webdav.MultiStatus;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Serves one connection over WebDAV, speaking RIM's rimdav method set on a plain HTTP/1.1 socket.
 * Every path goes through the {@link JailedRoot}; anything that would escape is a {@code 403}. Read
 * verbs (OPTIONS, PROPFIND, GET/HEAD) are always allowed; write verbs (PUT, MKCOL, DELETE, MOVE,
 * COPY, PROPPATCH) are refused with {@code 403} when the share is read-only.
 *
 * <p>The request target is read as ISO-8859-1 (one byte per char) and handed to {@link JailedRoot}
 * unchanged, so a raw non-ASCII byte in a file name survives to be percent-decoded as UTF-8 there.
 * One request is served per connection, which is then closed ({@code Connection: close}); the
 * device opens a fresh connection per request.
 */
final class WebDavHandler implements WebDavServer.RequestHandler {

  private static final DateTimeFormatter HTTP_DATE =
      DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneId.of("GMT"));
  private static final String DAV_METHODS =
      "OPTIONS, HEAD, GET, PUT, DELETE, MKCOL, COPY, MOVE, PROPFIND, PROPPATCH";

  /** Caps the request line and headers so a malformed peer cannot exhaust memory. */
  private static final int MAX_HEAD_BYTES = 64 * 1024;

  /** The interim response for a client that waits with {@code Expect: 100-continue}. */
  private static final byte[] CONTINUE =
      "HTTP/1.1 100 Continue\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);

  private static final System.Logger LOG = System.getLogger(WebDavHandler.class.getName());

  /**
   * Request headers worth logging, to see whether the device sends conditional or range requests.
   */
  private static final String[] LOGGED_HEADERS = {
    "Range", "If-Range", "If-Modified-Since", "If-None-Match"
  };

  private final JailedRoot jail;
  private final boolean readOnly;

  WebDavHandler(JailedRoot jail, boolean readOnly) {
    this.jail = jail;
    this.readOnly = readOnly;
  }

  /**
   * Reads one HTTP request from {@code rawIn}, serves it, and writes the response to {@code
   * rawOut}.
   *
   * @param rawIn the connection's input
   * @param rawOut the connection's output
   * @throws IOException if the connection fails while reading or writing
   */
  @Override
  public void handle(InputStream rawIn, OutputStream rawOut) throws IOException {
    var in = new BufferedInputStream(rawIn);
    var out = new BufferedOutputStream(rawOut);
    try {
      var request = Request.read(in);
      if (request == null) {
        send(out, DavStatus.BAD_REQUEST, null, null);
        return;
      }
      logAccess(request);
      serve(request, in, out);
    } catch (RuntimeException failure) {
      send(out, DavStatus.INTERNAL_SERVER_ERROR, null, null);
    } finally {
      out.flush();
    }
  }

  private void serve(Request request, BufferedInputStream in, BufferedOutputStream out)
      throws IOException {
    var method = DavMethod.of(request.method()).orElse(null);
    if (method == null) {
      drainBody(request, in);
      send(out, DavStatus.METHOD_NOT_ALLOWED, null, null);
      return;
    }
    var resolved = jail.resolve(request.rawPath());
    if (resolved.isEmpty()) {
      drainBody(request, in);
      send(out, DavStatus.FORBIDDEN, null, null);
      return;
    }
    var target = resolved.get();
    if (readOnly && isWrite(method)) {
      drainBody(request, in);
      send(out, DavStatus.FORBIDDEN, null, null);
      return;
    }
    try {
      switch (method) {
        case OPTIONS -> options(out);
        case PROPFIND -> propfind(request, in, out, target);
        case GET -> get(request, out, target, true);
        case HEAD -> get(request, out, target, false);
        case PUT -> put(request, in, out, target);
        case MKCOL -> mkcol(request, in, out, target);
        case DELETE -> delete(request, in, out, target);
        case MOVE -> moveOrCopy(request, in, out, target, true);
        case COPY -> moveOrCopy(request, in, out, target, false);
        case PROPPATCH -> proppatch(request, in, out, target);
      }
    } catch (IOException failure) {
      if (!isWrite(method)) {
        throw failure; // a read streams its response; a mid-stream failure just drops the socket
      }
      // A write failed before its response was sent (the disk is full, a path component is not a
      // directory, and so on): answer rather than dropping the connection.
      send(
          out,
          outOfSpace(failure) ? DavStatus.INSUFFICIENT_STORAGE : DavStatus.INTERNAL_SERVER_ERROR,
          null,
          null);
    }
  }

  /**
   * Whether a write failure looks like storage exhaustion. There is no dedicated exception for it,
   * so this matches the common message wording to answer {@code 507} rather than {@code 500}.
   */
  private static boolean outOfSpace(IOException failure) {
    var message = failure.getMessage();
    return message != null && message.toLowerCase(Locale.ROOT).contains("space");
  }

  private static boolean isWrite(DavMethod method) {
    return switch (method) {
      case PUT, DELETE, MKCOL, COPY, MOVE, PROPPATCH -> true;
      case OPTIONS, HEAD, GET, PROPFIND -> false;
    };
  }

  private void options(BufferedOutputStream out) throws IOException {
    var headers = new LinkedHashMap<String, String>();
    headers.put("DAV", "1,2");
    headers.put("Allow", DAV_METHODS);
    headers.put("MS-Author-Via", "DAV");
    send(out, DavStatus.OK, headers, null);
  }

  private void propfind(
      Request request, BufferedInputStream in, BufferedOutputStream out, Path target)
      throws IOException {
    drainBody(request, in);
    if (!Files.exists(target)) {
      send(out, DavStatus.NOT_FOUND, null, null);
      return;
    }
    var depth = depthOf(request);
    var resources = new ArrayList<DavResource>();
    resources.add(describe(target));
    if (depth == 1 && Files.isDirectory(target)) {
      try (Stream<Path> children = Files.list(target)) {
        children
            .sorted(Comparator.comparing(path -> path.getFileName().toString()))
            .forEach(child -> resources.add(describe(child)));
      }
    }
    var body = MultiStatus.build(resources).getBytes(StandardCharsets.UTF_8);
    var headers = new LinkedHashMap<String, String>();
    headers.put("Content-Type", "application/xml; charset=utf-8");
    send(out, DavStatus.MULTI_STATUS, headers, body);
  }

  private void get(Request request, BufferedOutputStream out, Path target, boolean body)
      throws IOException {
    if (Files.isDirectory(target)) {
      send(out, DavStatus.FORBIDDEN, null, null);
      return;
    }
    if (!Files.exists(target)) {
      send(out, DavStatus.NOT_FOUND, null, null);
      return;
    }
    var length = Files.size(target);
    var headers = new LinkedHashMap<String, String>();
    var type = probe(target);
    if (type != null) {
      headers.put("Content-Type", type);
    }
    headers.put("Last-Modified", HTTP_DATE.format(Files.getLastModifiedTime(target).toInstant()));
    headers.put("Accept-Ranges", "bytes");
    if (download(request)) {
      headers.put("Content-Disposition", contentDisposition(target.getFileName().toString()));
    }
    switch (rangeFor(request.header("Range"), length)) {
      case Unsatisfiable _ -> {
        headers.put("Content-Range", "bytes */" + length);
        send(out, DavStatus.RANGE_NOT_SATISFIABLE, headers, null);
      }
      case Partial partial -> {
        headers.put(
            "Content-Range", "bytes " + partial.start() + "-" + partial.end() + "/" + length);
        sendHead(out, DavStatus.PARTIAL_CONTENT, headers, partial.length());
        if (body) {
          copyRange(target, out, partial.start(), partial.length());
        }
      }
      case Whole _ -> {
        sendHead(out, DavStatus.OK, headers, length);
        if (body) {
          Files.copy(target, out);
        }
      }
    }
  }

  /** The outcome of matching a {@code Range} header against the entity length. */
  private sealed interface Ranged permits Whole, Partial, Unsatisfiable {}

  /** No usable range: serve the whole entity with {@code 200}. */
  private record Whole() implements Ranged {}

  /** A satisfiable single byte range, {@code start} and {@code end} both inclusive. */
  private record Partial(long start, long end) implements Ranged {
    long length() {
      return end - start + 1;
    }
  }

  /** A range that cannot be met: respond {@code 416}. */
  private record Unsatisfiable() implements Ranged {}

  /**
   * Parses a byte {@code Range} against the entity length. Only a single {@code bytes=} range is
   * honoured; a multi-range or any header that is absent, of another unit, or malformed is served
   * whole. A start past the end (or an empty suffix) is unsatisfiable.
   */
  private static Ranged rangeFor(String header, long length) {
    var prefix = "bytes=";
    if (header == null || !header.startsWith(prefix)) {
      return new Whole();
    }
    var spec = header.substring(prefix.length()).trim();
    var dash = spec.indexOf('-');
    if (spec.indexOf(',') >= 0 || dash < 0) {
      return new Whole();
    }
    var startText = spec.substring(0, dash).trim();
    var endText = spec.substring(dash + 1).trim();
    try {
      if (startText.isEmpty()) {
        if (endText.isEmpty()) {
          return new Whole();
        }
        var suffix = Long.parseLong(endText);
        if (suffix <= 0 || length == 0) {
          return new Unsatisfiable();
        }
        return new Partial(Math.max(0, length - suffix), length - 1);
      }
      var start = Long.parseLong(startText);
      if (start >= length) {
        return new Unsatisfiable();
      }
      var end = endText.isEmpty() ? length - 1 : Math.min(Long.parseLong(endText), length - 1);
      return end < start ? new Unsatisfiable() : new Partial(start, end);
    } catch (NumberFormatException malformed) {
      return new Whole();
    }
  }

  private static void copyRange(Path target, OutputStream out, long start, long count)
      throws IOException {
    try (var in = Files.newInputStream(target)) {
      in.skipNBytes(start);
      var buffer = new byte[8192];
      var remaining = count;
      while (remaining > 0) {
        var read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
        if (read < 0) {
          break;
        }
        out.write(buffer, 0, read);
        remaining -= read;
      }
    }
  }

  private void put(Request request, BufferedInputStream in, BufferedOutputStream out, Path target)
      throws IOException {
    if (Files.isDirectory(target)) {
      drainBody(request, in);
      send(out, DavStatus.CONFLICT, null, null);
      return;
    }
    sendContinueIfExpected(request, out);
    var existed = Files.exists(target);
    // create_full_put_path on: make the parent directories. The jail resolves every file target
    // under its root directory, so the target always has a parent (a PUT on the root itself is a
    // directory and was refused above).
    var parent = Objects.requireNonNull(target.getParent(), "a jailed file target has a parent");
    Files.createDirectories(parent);
    // Stream the body to a temporary file beside the target, then rename it into place, so an
    // interrupted or short upload never leaves a partial file at the real name. The temp file is
    // inside the jail, in the target's own directory, so the rename stays on one filesystem.
    var temp = Files.createTempFile(parent, ".bbdav-", ".part");
    long written;
    try (var bodyStream = request.body(in)) {
      written = Files.copy(bodyStream, temp, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException truncated) {
      Files.deleteIfExists(temp);
      throw truncated;
    }
    var declared = request.declaredLength();
    if (declared >= 0 && written != declared) {
      Files.deleteIfExists(temp);
      send(out, DavStatus.BAD_REQUEST, null, null);
      return;
    }
    moveIntoPlace(temp, target);
    send(out, existed ? DavStatus.NO_CONTENT : DavStatus.CREATED, null, null);
  }

  private static void moveIntoPlace(Path temp, Path target) throws IOException {
    try {
      Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException unsupported) {
      Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private void mkcol(Request request, BufferedInputStream in, BufferedOutputStream out, Path target)
      throws IOException {
    drainBody(request, in);
    if (Files.exists(target)) {
      send(out, DavStatus.METHOD_NOT_ALLOWED, null, null);
      return;
    }
    if (target.getParent() != null && !Files.isDirectory(target.getParent())) {
      send(out, DavStatus.CONFLICT, null, null);
      return;
    }
    Files.createDirectory(target);
    send(out, DavStatus.CREATED, null, null);
  }

  private void delete(
      Request request, BufferedInputStream in, BufferedOutputStream out, Path target)
      throws IOException {
    drainBody(request, in);
    if (!Files.exists(target)) {
      send(out, DavStatus.NOT_FOUND, null, null);
      return;
    }
    if (target.equals(jail.root())) {
      send(out, DavStatus.FORBIDDEN, null, null);
      return;
    }
    deleteTree(target);
    send(out, DavStatus.NO_CONTENT, null, null);
  }

  private void moveOrCopy(
      Request request, BufferedInputStream in, BufferedOutputStream out, Path source, boolean move)
      throws IOException {
    drainBody(request, in);
    if (!Files.exists(source)) {
      send(out, DavStatus.NOT_FOUND, null, null);
      return;
    }
    var destination = destinationOf(request);
    if (destination.isEmpty()) {
      send(out, DavStatus.FORBIDDEN, null, null);
      return;
    }
    var dest = destination.get();
    if (dest.equals(jail.root())) {
      send(out, DavStatus.FORBIDDEN, null, null);
      return;
    }
    var overwrite = !"F".equalsIgnoreCase(request.header("Overwrite"));
    var existed = Files.exists(dest);
    if (existed && !overwrite) {
      send(out, DavStatus.PRECONDITION_FAILED, null, null);
      return;
    }
    var destParent = dest.getParent();
    if (destParent != null) {
      Files.createDirectories(destParent);
    }
    if (move) {
      Files.move(source, dest, StandardCopyOption.REPLACE_EXISTING);
    } else {
      copyTree(source, dest);
    }
    send(out, existed ? DavStatus.NO_CONTENT : DavStatus.CREATED, null, null);
  }

  private void proppatch(
      Request request, BufferedInputStream in, BufferedOutputStream out, Path target)
      throws IOException {
    // No settable properties are supported; acknowledge with a benign 207 so the File Manager's
    // metadata writes (e.g. a touch after PUT) do not fail the transfer.
    drainBody(request, in);
    if (!Files.exists(target)) {
      send(out, DavStatus.NOT_FOUND, null, null);
      return;
    }
    var body = MultiStatus.build(List.of(describe(target))).getBytes(StandardCharsets.UTF_8);
    var headers = new LinkedHashMap<String, String>();
    headers.put("Content-Type", "application/xml; charset=utf-8");
    send(out, DavStatus.MULTI_STATUS, headers, body);
  }

  private DavResource describe(Path path) {
    var collection = Files.isDirectory(path);
    var name =
        path.equals(jail.root())
            ? (path.getFileName() == null ? "" : path.getFileName().toString())
            : path.getFileName().toString();
    long length = 0;
    java.time.Instant modified = null;
    String type = null;
    try {
      if (!collection) {
        length = Files.size(path);
        type = probe(path);
      }
      modified = Files.getLastModifiedTime(path).toInstant();
    } catch (IOException _) {
      // leave the optional fields unset
    }
    return new DavResource(jail.href(path, collection), collection, length, modified, name, type);
  }

  private static String probe(Path path) {
    try {
      return Files.probeContentType(path);
    } catch (IOException _) {
      return null;
    }
  }

  private Optional<Path> destinationOf(Request request) {
    var raw = request.header("Destination");
    if (raw == null || raw.isEmpty()) {
      return Optional.empty();
    }
    return jail.resolve(destinationPath(raw.trim()));
  }

  /**
   * Extracts the path from a {@code Destination} header, which may be an absolute URI ({@code
   * scheme://authority/path}) or an absolute path. The bytes are kept intact — not parsed through
   * {@link java.net.URI}, which rejects a raw non-ASCII byte — so a non-ASCII destination name
   * survives to be percent-decoded as UTF-8 by the jail, exactly like the request path.
   */
  private static String destinationPath(String destination) {
    var scheme = destination.indexOf("://");
    if (scheme < 0) {
      return destination;
    }
    var pathStart = destination.indexOf('/', scheme + 3);
    return pathStart < 0 ? "/" : destination.substring(pathStart);
  }

  private static int depthOf(Request request) {
    return "0".equals(request.header("Depth")) ? 0 : 1;
  }

  private static boolean download(Request request) {
    var query = request.rawQuery();
    return query != null && query.contains("download=1");
  }

  /**
   * Builds a {@code Content-Disposition} for a download: a plain {@code filename} fallback with
   * non-ASCII characters flattened, and an RFC 5987 {@code filename*} carrying the real UTF-8 name
   * for clients that understand it.
   */
  private static String contentDisposition(String name) {
    var fallback = new StringBuilder(name.length());
    for (var i = 0; i < name.length(); i++) {
      var c = name.charAt(i);
      fallback.append(c < 0x20 || c > 0x7e || c == '"' || c == '\\' ? '_' : c);
    }
    return "attachment; filename=\"" + fallback + "\"; filename*=UTF-8''" + rfc5987(name);
  }

  /** Percent-encodes a name as an RFC 5987 {@code ext-value}: UTF-8 bytes, attr-char kept raw. */
  private static String rfc5987(String name) {
    var encoded = new StringBuilder();
    for (var b : name.getBytes(StandardCharsets.UTF_8)) {
      var c = (char) (b & 0xff);
      if ((c >= 'A' && c <= 'Z')
          || (c >= 'a' && c <= 'z')
          || (c >= '0' && c <= '9')
          || "!#$&+-.^_`|~".indexOf(c) >= 0) {
        encoded.append(c);
      } else {
        encoded.append(String.format("%%%02X", b & 0xff));
      }
    }
    return encoded.toString();
  }

  // --- HTTP wire I/O --------------------------------------------------------------------------

  /** Logs one line per request, with any conditional or range headers it carried. */
  private static void logAccess(Request request) {
    LOG.log(System.Logger.Level.DEBUG, () -> accessLine(request));
  }

  private static String accessLine(Request request) {
    var line = new StringBuilder(request.method()).append(' ').append(request.rawPath());
    for (var header : LOGGED_HEADERS) {
      var value = request.header(header);
      if (value != null) {
        line.append(' ').append(header).append('=').append(value);
      }
    }
    return line.toString();
  }

  private static void drainBody(Request request, BufferedInputStream in) {
    try (var body = request.body(in)) {
      body.readAllBytes();
    } catch (IOException _) {
      // the body is already gone or the peer closed; nothing to drain
    }
  }

  /**
   * Sends an interim {@code 100 Continue} when the client asked to wait with {@code Expect:
   * 100-continue}, so it streams the request body at once instead of pausing for a timeout.
   */
  private static void sendContinueIfExpected(Request request, BufferedOutputStream out)
      throws IOException {
    var expect = request.header("Expect");
    if (expect != null && expect.equalsIgnoreCase("100-continue")) {
      out.write(CONTINUE);
      out.flush();
    }
  }

  /** Sends a status line, headers and an in-memory body (empty when {@code body} is null). */
  private void send(
      BufferedOutputStream out, DavStatus status, Map<String, String> headers, byte[] body)
      throws IOException {
    sendHead(out, status, headers, body == null ? 0 : body.length);
    if (body != null && body.length > 0) {
      out.write(body);
    }
  }

  /** Sends a status line and headers with a declared body length; the caller streams the body. */
  private void sendHead(
      BufferedOutputStream out, DavStatus status, Map<String, String> headers, long contentLength)
      throws IOException {
    var head = new StringBuilder();
    head.append(status.line()).append("\r\n");
    if (headers != null) {
      headers.forEach((name, value) -> head.append(name).append(": ").append(value).append("\r\n"));
    }
    // A 204 carries no body and must not declare a Content-Length (RFC 7230 §3.3.2); every other
    // status the server sends does, so the client knows exactly how many body bytes to read.
    if (status != DavStatus.NO_CONTENT) {
      head.append("Content-Length: ").append(contentLength).append("\r\n");
    }
    head.append("Connection: close\r\n");
    head.append("\r\n");
    out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
  }

  // --- deletion and copy ----------------------------------------------------------------------

  private static void deleteTree(Path path) throws IOException {
    if (Files.isDirectory(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
      try (Stream<Path> entries = Files.list(path)) {
        for (var entry : entries.toList()) {
          deleteTree(entry);
        }
      }
    }
    Files.deleteIfExists(path);
  }

  private static void copyTree(Path source, Path dest) throws IOException {
    if (Files.isDirectory(source)) {
      Files.createDirectories(dest);
      try (Stream<Path> entries = Files.list(source)) {
        for (var entry : entries.toList()) {
          copyTree(entry, dest.resolve(entry.getFileName().toString()));
        }
      }
    } else {
      Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  /**
   * One parsed HTTP request: the method, the raw (still percent-encoded, ISO-8859-1) request path
   * and query, and the headers (keys lower-cased). The body is read lazily from the connection
   * through {@link #body(BufferedInputStream)} so a handler reads only what it needs.
   */
  private record Request(
      String method, String rawPath, String rawQuery, Map<String, String> headers) {

    String header(String name) {
      return headers.get(name.toLowerCase(Locale.ROOT));
    }

    /**
     * The request body as a stream over the connection: a length-bounded view for {@code
     * Content-Length}, a de-chunking view for {@code Transfer-Encoding: chunked}, or an empty
     * stream when the request carries no body. Closing it does not close the connection.
     */
    InputStream body(BufferedInputStream connection) {
      if ("chunked".equalsIgnoreCase(header("Transfer-Encoding"))) {
        return new ChunkedInputStream(connection);
      }
      var length = declaredLength();
      return length < 0
          ? InputStream.nullInputStream()
          : new BoundedInputStream(connection, length);
    }

    /** The body length declared by {@code Content-Length}, or {@code -1} when chunked or absent. */
    long declaredLength() {
      if ("chunked".equalsIgnoreCase(header("Transfer-Encoding"))) {
        return -1;
      }
      var length = header("Content-Length");
      if (length == null) {
        return -1;
      }
      try {
        return Long.parseLong(length.trim());
      } catch (NumberFormatException malformed) {
        return -1;
      }
    }

    /** Reads the request line and headers; returns {@code null} if the request is malformed. */
    static Request read(BufferedInputStream in) throws IOException {
      var head = readHead(in);
      if (head == null || head.isEmpty()) {
        return null;
      }
      var lines = head.split("\r\n");
      var requestLine = lines[0].split(" ");
      if (requestLine.length < 2) {
        return null;
      }
      var method = requestLine[0];
      var target = requestLine[1];
      var queryAt = target.indexOf('?');
      var rawPath = queryAt < 0 ? target : target.substring(0, queryAt);
      var rawQuery = queryAt < 0 ? null : target.substring(queryAt + 1);

      var headers = new LinkedHashMap<String, String>();
      for (var i = 1; i < lines.length; i++) {
        var line = lines[i];
        var colon = line.indexOf(':');
        if (colon > 0) {
          headers.put(
              line.substring(0, colon).trim().toLowerCase(Locale.ROOT),
              line.substring(colon + 1).trim());
        }
      }
      return new Request(method, rawPath, rawQuery, headers);
    }

    /** Reads up to the {@code CRLF CRLF} that ends the request head, as ISO-8859-1 text. */
    private static String readHead(BufferedInputStream in) throws IOException {
      var buffer = new ByteArrayOutputStream(512);
      var matched = 0; // how much of \r\n\r\n has been matched
      int b;
      while ((b = in.read()) != -1) {
        buffer.write(b);
        var expected = (matched % 2 == 0) ? '\r' : '\n';
        matched = (b == expected) ? matched + 1 : (b == '\r' ? 1 : 0);
        if (matched == 4) {
          var text = buffer.toString(StandardCharsets.ISO_8859_1);
          return text.substring(0, text.length() - 4);
        }
        if (buffer.size() > MAX_HEAD_BYTES) {
          return null;
        }
      }
      return null; // connection closed before a complete head
    }
  }

  /** Reads at most {@code remaining} bytes from the connection, then reports end of stream. */
  private static final class BoundedInputStream extends InputStream {
    private final InputStream source;
    private long remaining;

    BoundedInputStream(InputStream source, long remaining) {
      this.source = source;
      this.remaining = remaining;
    }

    @Override
    public int read() throws IOException {
      if (remaining <= 0) {
        return -1;
      }
      var value = source.read();
      if (value != -1) {
        remaining--;
      }
      return value;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      if (remaining <= 0) {
        return -1;
      }
      var read = source.read(b, off, (int) Math.min(len, remaining));
      if (read != -1) {
        remaining -= read;
      }
      return read;
    }

    @Override
    public void close() {
      // the connection outlives the body view; closing the view must not close the socket
    }
  }

  /** Decodes an HTTP/1.1 {@code Transfer-Encoding: chunked} body from the connection. */
  private static final class ChunkedInputStream extends InputStream {
    private final BufferedInputStream source;
    private long chunkRemaining;
    private boolean done;
    private boolean atStart = true;

    ChunkedInputStream(BufferedInputStream source) {
      this.source = source;
    }

    @Override
    public int read() throws IOException {
      if (atEndOfBody()) {
        return -1;
      }
      var value = source.read();
      if (value != -1) {
        chunkRemaining--;
      }
      return value;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      if (atEndOfBody()) {
        return -1;
      }
      var read = source.read(b, off, (int) Math.min(len, chunkRemaining));
      if (read != -1) {
        chunkRemaining -= read;
      }
      return read;
    }

    /** Advances to the next chunk that carries data; returns true once the body is exhausted. */
    private boolean atEndOfBody() throws IOException {
      if (done) {
        return true;
      }
      if (chunkRemaining > 0) {
        return false;
      }
      if (!atStart) {
        skipLineEnd(); // the CRLF that terminates the previous chunk's data
      }
      atStart = false;
      var header = readLine();
      var semicolon = header.indexOf(';');
      var sizeText = (semicolon < 0 ? header : header.substring(0, semicolon)).trim();
      long size;
      try {
        size = Long.parseLong(sizeText, 16);
      } catch (NumberFormatException malformed) {
        done = true;
        return true;
      }
      if (size == 0) {
        done = true;
        return true;
      }
      chunkRemaining = size;
      return false;
    }

    /** Consumes bytes up to and including the next line feed (a chunk's trailing CRLF). */
    private void skipLineEnd() throws IOException {
      int b;
      do {
        b = source.read();
      } while (b != -1 && b != '\n');
    }

    private String readLine() throws IOException {
      var line = new StringBuilder();
      int b;
      while ((b = source.read()) != -1 && b != '\n') {
        if (b != '\r') {
          line.append((char) b);
        }
      }
      return line.toString();
    }

    @Override
    public void close() {
      // the connection outlives the body view
    }
  }
}
