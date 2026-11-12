package com.jbraddockm.link.share;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.webdav.DavResource;
import com.jbraddockm.core.webdav.MultiStatus;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("The WebDAV file-share server")
class WebDavServerTest {

  @TempDir Path share;

  private WebDavServer server;
  private HttpClient client;
  private URI base;

  @BeforeEach
  void startServer() throws IOException {
    server = WebDavServer.start(new InetSocketAddress("127.0.0.1", 0), share, false);
    client = HttpClient.newHttpClient();
    base = URI.create("http://127.0.0.1:" + server.address().getPort());
  }

  @AfterEach
  void stopServer() {
    server.close();
  }

  private HttpResponse<String> send(String method, String path, HttpRequest.BodyPublisher body)
      throws IOException, InterruptedException {
    var request = HttpRequest.newBuilder(base.resolve(path)).method(method, body).build();
    return client.send(request, BodyHandlers.ofString());
  }

  private HttpResponse<String> send(
      String method, String path, HttpRequest.BodyPublisher body, String headerName, String value)
      throws IOException, InterruptedException {
    var request =
        HttpRequest.newBuilder(base.resolve(path))
            .method(method, body)
            .header(headerName, value)
            .build();
    return client.send(request, BodyHandlers.ofString());
  }

  /** Sends a request exactly as given (raw bytes) and returns {@code [statusLine, body]}. */
  private String[] raw(byte[] request) throws IOException {
    try (var socket = new java.net.Socket(base.getHost(), base.getPort())) {
      socket.getOutputStream().write(request);
      socket.getOutputStream().flush();
      socket.shutdownOutput();
      var response =
          new String(socket.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
      var parts = response.split("\r\n\r\n", 2);
      return new String[] {parts[0].split("\r\n")[0], parts.length > 1 ? parts[1] : ""};
    }
  }

  @Nested
  @DisplayName("advertising its capabilities")
  class Options {

    @Test
    @DisplayName("answers OPTIONS with the DAV level and the rimdav method set")
    void options() throws Exception {
      var response = send("OPTIONS", "/", BodyPublishers.noBody());

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.headers().firstValue("DAV")).hasValue("1,2");
      assertThat(response.headers().firstValue("Allow").orElseThrow())
          .contains("PROPFIND")
          .contains("PUT")
          .contains("MKCOL");
    }
  }

  @Nested
  @DisplayName("listing with PROPFIND")
  class Propfind {

    @Test
    @DisplayName("lists the share's children at depth 1")
    void listsChildren() throws Exception {
      Files.writeString(share.resolve("a.txt"), "hello");
      Files.createDirectory(share.resolve("sub"));

      var response = send("PROPFIND", "/", BodyPublishers.noBody(), "Depth", "1");

      assertThat(response.statusCode()).isEqualTo(207);
      var resources = MultiStatus.parse(response.body());
      assertThat(resources).extracting(DavResource::href).contains("/", "/a.txt", "/sub/");
    }

    @Test
    @DisplayName("describes only the target itself at depth 0")
    void describesSelf() throws Exception {
      Files.writeString(share.resolve("a.txt"), "hello");

      var response = send("PROPFIND", "/a.txt", BodyPublishers.noBody(), "Depth", "0");

      assertThat(response.statusCode()).isEqualTo(207);
      var resources = MultiStatus.parse(response.body());
      assertThat(resources).singleElement().extracting(DavResource::href).isEqualTo("/a.txt");
    }
  }

  @Nested
  @DisplayName("transferring files")
  class Transfer {

    @Test
    @DisplayName("creates a file with PUT (201) and reads it back with GET")
    void putThenGet() throws Exception {
      var put = send("PUT", "/dir/note.txt", BodyPublishers.ofString("body text"));
      assertThat(put.statusCode()).isEqualTo(201);
      assertThat(share.resolve("dir/note.txt")).hasContent("body text");

      var get = send("GET", "/dir/note.txt", BodyPublishers.noBody());
      assertThat(get.statusCode()).isEqualTo(200);
      assertThat(get.body()).isEqualTo("body text");
    }

    @Test
    @DisplayName("overwrites an existing file with PUT (204)")
    void overwrites() throws Exception {
      Files.writeString(share.resolve("f.txt"), "old");

      var put = send("PUT", "/f.txt", BodyPublishers.ofString("new"));

      assertThat(put.statusCode()).isEqualTo(204);
      assertThat(share.resolve("f.txt")).hasContent("new");
    }

    @Test
    @DisplayName("marks a download with Content-Disposition when asked")
    void download() throws Exception {
      Files.writeString(share.resolve("f.txt"), "x");

      var get = send("GET", "/f.txt?download=1", BodyPublishers.noBody());

      assertThat(get.headers().firstValue("Content-Disposition").orElseThrow())
          .contains("attachment")
          .contains("f.txt");
    }

    @Test
    @DisplayName("answers GET for a missing file with 404")
    void missing() throws Exception {
      var get = send("GET", "/nope.txt", BodyPublishers.noBody());

      assertThat(get.statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("rejects a PUT shorter than its Content-Length and leaves no file behind")
    void rejectsTruncatedUpload() throws Exception {
      var request =
          ("PUT /short.txt HTTP/1.1\r\nHost: x\r\nContent-Length: 10\r\n\r\nabc")
              .getBytes(StandardCharsets.ISO_8859_1);

      assertThat(raw(request)[0]).contains("400");
      assertThat(share.resolve("short.txt")).doesNotExist();
      try (var leftovers = Files.list(share)) {
        assertThat(leftovers).noneMatch(entry -> entry.getFileName().toString().endsWith(".part"));
      }
    }
  }

  @Nested
  @DisplayName("managing collections")
  class Collections {

    @Test
    @DisplayName("creates a directory with MKCOL (201)")
    void mkcol() throws Exception {
      var response = send("MKCOL", "/newdir", BodyPublishers.noBody());

      assertThat(response.statusCode()).isEqualTo(201);
      assertThat(share.resolve("newdir")).isDirectory();
    }

    @Test
    @DisplayName("removes a tree with DELETE (204)")
    void delete() throws Exception {
      Files.createDirectory(share.resolve("d"));
      Files.writeString(share.resolve("d/inner.txt"), "x");

      var response = send("DELETE", "/d", BodyPublishers.noBody());

      assertThat(response.statusCode()).isEqualTo(204);
      assertThat(share.resolve("d")).doesNotExist();
    }

    @Test
    @DisplayName("refuses to DELETE the share root (403)")
    void refusesRootDelete() throws Exception {
      var response = send("DELETE", "/", BodyPublishers.noBody());

      assertThat(response.statusCode()).isEqualTo(403);
      assertThat(share).exists();
    }

    @Test
    @DisplayName("renames a file with MOVE (201)")
    void move() throws Exception {
      Files.writeString(share.resolve("from.txt"), "payload");

      var response =
          send(
              "MOVE",
              "/from.txt",
              BodyPublishers.noBody(),
              "Destination",
              base.resolve("/to.txt").toString());

      assertThat(response.statusCode()).isEqualTo(201);
      assertThat(share.resolve("from.txt")).doesNotExist();
      assertThat(share.resolve("to.txt")).hasContent("payload");
    }
  }

  @Nested
  @DisplayName("holding the jail at the wire")
  class Jailing {

    @Test
    @DisplayName("refuses a percent-encoded parent-traversal path with 403")
    void refusesTraversal() throws Exception {
      var response = send("GET", "/%2e%2e/%2e%2e/etc/passwd", BodyPublishers.noBody());

      assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("refuses a MOVE whose destination escapes the jail with 403")
    void refusesEscapingMove() throws Exception {
      Files.writeString(share.resolve("f.txt"), "x");

      var response =
          send(
              "MOVE",
              "/f.txt",
              BodyPublishers.noBody(),
              "Destination",
              base.resolve("/%2e%2e/escaped.txt").toString());

      assertThat(response.statusCode()).isEqualTo(403);
      assertThat(share.resolve("f.txt")).exists();
    }
  }

  @Nested
  @DisplayName("binding on the tunnel ULA")
  class TunnelBind {

    @Test
    @DisplayName("binds the rimdav port on a host IPv6 ULA literal")
    void bindsUla() {
      var address = WebDavServer.tunnelBindAddress("fd2d:1a2b:3c4d::9a66");

      assertThat(address.getPort()).isEqualTo(WebDavServer.RIMDAV_PORT);
      assertThat(address.getAddress()).isInstanceOf(java.net.Inet6Address.class);
    }

    @Test
    @DisplayName(
        "refuses anything that is not an IPv6 literal, so the share never faces a routable"
            + " interface")
    void refusesNonUla() {
      org.assertj.core.api.Assertions.assertThatThrownBy(
              () -> WebDavServer.tunnelBindAddress("169.254.2.2"))
          .isInstanceOf(IllegalArgumentException.class);
      org.assertj.core.api.Assertions.assertThatThrownBy(
              () -> WebDavServer.tunnelBindAddress("example.com"))
          .isInstanceOf(IllegalArgumentException.class);
      org.assertj.core.api.Assertions.assertThatThrownBy(() -> WebDavServer.tunnelBindAddress("  "))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("as a read-only share")
  class ReadOnly {

    @Test
    @DisplayName("allows reads but refuses writes with 403")
    void refusesWrites() throws Exception {
      try (var readOnly = WebDavServer.start(new InetSocketAddress("127.0.0.1", 0), share, true)) {
        var ro = URI.create("http://127.0.0.1:" + readOnly.address().getPort());
        Files.writeString(share.resolve("f.txt"), "x");

        var get =
            client.send(
                HttpRequest.newBuilder(ro.resolve("/f.txt")).GET().build(),
                BodyHandlers.ofString());
        assertThat(get.statusCode()).isEqualTo(200);

        var put =
            client.send(
                HttpRequest.newBuilder(ro.resolve("/g.txt"))
                    .method("PUT", BodyPublishers.ofString("nope"))
                    .build(),
                BodyHandlers.ofString());
        assertThat(put.statusCode()).isEqualTo(403);
        assertThat(share.resolve("g.txt")).doesNotExist();
      }
    }
  }

  @Nested
  @DisplayName("file names with non-ASCII characters")
  class NonAsciiNames {

    @Test
    @DisplayName("stores and serves a file whose name has a raw UTF-8 byte (an em dash)")
    void rawUtf8Name() throws Exception {
      // The device sends a non-ASCII file name as raw UTF-8 bytes in the path, not percent-encoded;
      // 0xE2 0x80 0x94 is U+2014 (em dash). The server must keep those bytes intact so the jail can
      // decode them as UTF-8, rather than rejecting the request as a malformed URI.
      var path = "/Blâ\u0080\u0094ck.txt";
      var put =
          ("PUT " + path + " HTTP/1.1\r\nHost: x\r\nContent-Length: 5\r\n\r\nhello")
              .getBytes(StandardCharsets.ISO_8859_1);
      assertThat(raw(put)[0]).contains("201");
      assertThat(share.resolve("Bl—ck.txt")).hasContent("hello");

      var get =
          ("GET " + path + " HTTP/1.1\r\nHost: x\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1);
      var response = raw(get);
      assertThat(response[0]).contains("200");
      assertThat(response[1]).isEqualTo("hello");
    }
  }
}
