package com.jbraddockm.link.transport.cgi;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * A single unauthenticated plain-HTTP {@code GET}, separate from the device-manager {@link
 * CgiTransport} session. It exists for the one part of the device interface served only on the
 * plain {@code :80} listener — {@code discovery.cgi} over USB — which the TLS session on {@code
 * :8443} cannot reach; everything else goes through the authenticated session.
 */
public final class PlainHttpGet {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
  private static final String USER_AGENT = "QNXWebClient/1.0";

  private PlainHttpGet() {}

  /**
   * Fetches a URL with a {@code GET}: no authentication, no cookies, no TLS.
   *
   * @param url the absolute {@code http://…} URL
   * @return the answer's status, content type and body (no cookie, no downloaded file)
   * @throws IOException if the request could not be made
   */
  public static CgiResponse fetch(String url) throws IOException {
    try (var client =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(CONNECT_TIMEOUT)
            .build()) {
      var request =
          HttpRequest.newBuilder(URI.create(url))
              .timeout(READ_TIMEOUT)
              .header("User-Agent", USER_AGENT)
              .GET()
              .build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
      return new CgiResponse(
          response.statusCode(),
          response.headers().firstValue("Content-Type").orElse(null),
          new String(response.body(), StandardCharsets.UTF_8),
          null);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IOException("the request was interrupted", interrupted);
    }
  }
}
