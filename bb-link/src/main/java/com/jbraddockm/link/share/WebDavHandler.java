package com.jbraddockm.link.share;

import com.jbraddockm.core.webdav.DavMethod;
import com.jbraddockm.core.webdav.DavResource;
import com.jbraddockm.core.webdav.DavStatus;
import com.jbraddockm.core.webdav.MultiStatus;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Serves the {@link JailedRoot} over WebDAV, speaking RIM's rimdav method set. Every path goes
 * through the jail; anything that would escape is a {@code 403}. Read verbs (OPTIONS, PROPFIND,
 * GET/HEAD) are always allowed; write verbs (PUT, MKCOL, DELETE, MOVE, COPY, PROPPATCH) are refused
 * with {@code 403} when the share is read-only.
 */
final class WebDavHandler implements HttpHandler {

  private static final DateTimeFormatter HTTP_DATE =
      DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneId.of("GMT"));
  private static final String DAV_METHODS =
      "OPTIONS, HEAD, GET, PUT, DELETE, MKCOL, COPY, MOVE, PROPFIND, PROPPATCH";

  private final JailedRoot jail;
  private final boolean readOnly;

  WebDavHandler(JailedRoot jail, boolean readOnly) {
    this.jail = jail;
    this.readOnly = readOnly;
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    try {
      var method = DavMethod.of(exchange.getRequestMethod()).orElse(null);
      if (method == null) {
        empty(exchange, DavStatus.METHOD_NOT_ALLOWED);
        return;
      }
      var resolved = jail.resolve(exchange.getRequestURI().getRawPath());
      if (resolved.isEmpty()) {
        empty(exchange, DavStatus.FORBIDDEN);
        return;
      }
      var target = resolved.get();
      if (readOnly && isWrite(method)) {
        empty(exchange, DavStatus.FORBIDDEN);
        return;
      }
      switch (method) {
        case OPTIONS -> options(exchange);
        case PROPFIND -> propfind(exchange, target);
        case GET -> get(exchange, target, true);
        case HEAD -> get(exchange, target, false);
        case PUT -> put(exchange, target);
        case MKCOL -> mkcol(exchange, target);
        case DELETE -> delete(exchange, target);
        case MOVE -> moveOrCopy(exchange, target, true);
        case COPY -> moveOrCopy(exchange, target, false);
        case PROPPATCH -> proppatch(exchange, target);
      }
    } catch (RuntimeException failure) {
      empty(exchange, DavStatus.INTERNAL_SERVER_ERROR);
    } finally {
      exchange.close();
    }
  }

  private static boolean isWrite(DavMethod method) {
    return switch (method) {
      case PUT, DELETE, MKCOL, COPY, MOVE, PROPPATCH -> true;
      case OPTIONS, HEAD, GET, PROPFIND -> false;
    };
  }

  private void options(HttpExchange exchange) throws IOException {
    exchange.getResponseHeaders().add("DAV", "1,2");
    exchange.getResponseHeaders().add("Allow", DAV_METHODS);
    exchange.getResponseHeaders().add("MS-Author-Via", "DAV");
    empty(exchange, DavStatus.OK);
  }

  private void propfind(HttpExchange exchange, Path target) throws IOException {
    if (!Files.exists(target)) {
      empty(exchange, DavStatus.NOT_FOUND);
      return;
    }
    var depth = depthOf(exchange);
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
    exchange.getResponseHeaders().add("Content-Type", "application/xml; charset=utf-8");
    exchange.sendResponseHeaders(DavStatus.MULTI_STATUS.code(), body.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(body);
    }
  }

  private void get(HttpExchange exchange, Path target, boolean body) throws IOException {
    if (!Files.exists(target) || Files.isDirectory(target)) {
      empty(exchange, Files.isDirectory(target) ? DavStatus.FORBIDDEN : DavStatus.NOT_FOUND);
      return;
    }
    var type = probe(target);
    if (type != null) {
      exchange.getResponseHeaders().add("Content-Type", type);
    }
    exchange
        .getResponseHeaders()
        .add("Last-Modified", HTTP_DATE.format(Files.getLastModifiedTime(target).toInstant()));
    if (download(exchange)) {
      exchange
          .getResponseHeaders()
          .add("Content-Disposition", "attachment; filename=\"" + target.getFileName() + "\"");
    }
    var length = Files.size(target);
    if (!body) {
      exchange.getResponseHeaders().add("Content-Length", Long.toString(length));
      exchange.sendResponseHeaders(DavStatus.OK.code(), -1);
      return;
    }
    exchange.sendResponseHeaders(DavStatus.OK.code(), length == 0 ? -1 : length);
    try (OutputStream out = exchange.getResponseBody()) {
      Files.copy(target, out);
    }
  }

  private void put(HttpExchange exchange, Path target) throws IOException {
    if (Files.isDirectory(target)) {
      empty(exchange, DavStatus.CONFLICT);
      return;
    }
    var existed = Files.exists(target);
    // create_full_put_path on: make the parent directories.
    var parent = target.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    try (var in = exchange.getRequestBody()) {
      Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
    }
    empty(exchange, existed ? DavStatus.NO_CONTENT : DavStatus.CREATED);
  }

  private void mkcol(HttpExchange exchange, Path target) throws IOException {
    if (Files.exists(target)) {
      empty(exchange, DavStatus.METHOD_NOT_ALLOWED);
      return;
    }
    if (target.getParent() != null && !Files.isDirectory(target.getParent())) {
      empty(exchange, DavStatus.CONFLICT);
      return;
    }
    Files.createDirectory(target);
    empty(exchange, DavStatus.CREATED);
  }

  private void delete(HttpExchange exchange, Path target) throws IOException {
    if (!Files.exists(target)) {
      empty(exchange, DavStatus.NOT_FOUND);
      return;
    }
    if (target.equals(jail.root())) {
      empty(exchange, DavStatus.FORBIDDEN);
      return;
    }
    deleteTree(target);
    empty(exchange, DavStatus.NO_CONTENT);
  }

  private void moveOrCopy(HttpExchange exchange, Path source, boolean move) throws IOException {
    if (!Files.exists(source)) {
      empty(exchange, DavStatus.NOT_FOUND);
      return;
    }
    var destination = destinationOf(exchange);
    if (destination.isEmpty()) {
      empty(exchange, DavStatus.FORBIDDEN);
      return;
    }
    var dest = destination.get();
    if (dest.equals(jail.root())) {
      empty(exchange, DavStatus.FORBIDDEN);
      return;
    }
    var overwrite = !"F".equalsIgnoreCase(header(exchange, "Overwrite"));
    var existed = Files.exists(dest);
    if (existed && !overwrite) {
      empty(exchange, DavStatus.PRECONDITION_FAILED);
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
    empty(exchange, existed ? DavStatus.NO_CONTENT : DavStatus.CREATED);
  }

  private void proppatch(HttpExchange exchange, Path target) throws IOException {
    // No settable properties are supported; acknowledge with a benign 207 so the File Manager's
    // metadata writes (e.g. a touch after PUT) do not fail the transfer.
    if (!Files.exists(target)) {
      empty(exchange, DavStatus.NOT_FOUND);
      return;
    }
    drain(exchange);
    var body = MultiStatus.build(List.of(describe(target))).getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/xml; charset=utf-8");
    exchange.sendResponseHeaders(DavStatus.MULTI_STATUS.code(), body.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(body);
    }
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

  private Optional<Path> destinationOf(HttpExchange exchange) {
    var raw = header(exchange, "Destination");
    if (raw == null || raw.isEmpty()) {
      return Optional.empty();
    }
    try {
      var path = URI.create(raw).getRawPath();
      return jail.resolve(path == null ? raw : path);
    } catch (IllegalArgumentException _) {
      return jail.resolve(raw);
    }
  }

  private static int depthOf(HttpExchange exchange) {
    var depth = header(exchange, "Depth");
    return "0".equals(depth) ? 0 : 1;
  }

  private static boolean download(HttpExchange exchange) {
    var query = exchange.getRequestURI().getRawQuery();
    return query != null && query.contains("download=1");
  }

  private static String header(HttpExchange exchange, String name) {
    return exchange.getRequestHeaders().getFirst(name);
  }

  private static void drain(HttpExchange exchange) {
    try (var in = exchange.getRequestBody()) {
      in.readAllBytes();
    } catch (IOException _) {
      // The body is already consumed or closed; nothing left to drain.
    }
  }

  private static void empty(HttpExchange exchange, DavStatus status) throws IOException {
    drain(exchange);
    exchange.sendResponseHeaders(status.code(), -1);
  }

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
}
