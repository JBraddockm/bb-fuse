package com.jbraddockm.link.remoteaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link RemoteAccess}: that it serves the configured root once started and stops both
 * the server and the advert on close. Binds on IPv6 loopback ({@code [::1]}) so no tunnel is
 * needed.
 */
@DisplayName("The host file share")
// The JDK HttpClient and the share under test are closed where it matters; the client needs no ARM.
@SuppressWarnings("resource")
class RemoteAccessTest {

  @TempDir Path root;

  private static InetSocketAddress loopback() throws IOException {
    return new InetSocketAddress(InetAddress.getByName("::1"), 0);
  }

  @Test
  @DisplayName("serves the configured root and withdraws the advert and server on close")
  void servesAndStops() throws Exception {
    var advertCloses = new AtomicInteger();
    RemoteAccess.AdvertStarter advert = () -> advertCloses::incrementAndGet;

    var share = RemoteAccess.start(loopback(), RemoteAccessConfig.of(root), advert);
    var client = HttpClient.newHttpClient();
    var base = URI.create("http://[::1]:" + share.address().getPort());

    var options =
        client.send(
            HttpRequest.newBuilder(base.resolve("/"))
                .method("OPTIONS", BodyPublishers.noBody())
                .build(),
            BodyHandlers.discarding());
    assertThat(options.statusCode()).isEqualTo(200);

    share.close();

    assertThat(advertCloses).hasValue(1);
    assertThatThrownBy(
            () ->
                client.send(
                    HttpRequest.newBuilder(base.resolve("/")).GET().build(),
                    BodyHandlers.discarding()))
        .isInstanceOf(IOException.class);
  }

  @Test
  @DisplayName("serves without an advert when none is given")
  void noAdvert() throws Exception {
    try (var share = RemoteAccess.start(loopback(), RemoteAccessConfig.of(root), null)) {
      var client = HttpClient.newHttpClient();
      var base = URI.create("http://[::1]:" + share.address().getPort());
      var options =
          client.send(
              HttpRequest.newBuilder(base.resolve("/"))
                  .method("OPTIONS", BodyPublishers.noBody())
                  .build(),
              BodyHandlers.discarding());
      assertThat(options.statusCode()).isEqualTo(200);
    }
  }

  @Test
  @DisplayName("stops the server even when withdrawing the advert fails")
  void advertCloseFailureDoesNotBlockTeardown() throws Exception {
    RemoteAccess.AdvertStarter advert =
        () ->
            () -> {
              throw new IOException("withdraw failed");
            };

    var share = RemoteAccess.start(loopback(), RemoteAccessConfig.of(root), advert);
    var port = share.address().getPort();

    share.close();

    var client = HttpClient.newHttpClient();
    var base = URI.create("http://[::1]:" + port);
    assertThatThrownBy(
            () ->
                client.send(
                    HttpRequest.newBuilder(base.resolve("/")).GET().build(),
                    BodyHandlers.discarding()))
        .isInstanceOf(IOException.class);
  }
}
