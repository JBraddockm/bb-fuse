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
}
