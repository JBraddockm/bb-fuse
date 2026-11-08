package com.jbraddockm.core.device;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import javax.net.ssl.KeyManager;

/**
 * A {@link DeviceHttp} on {@code java.net.http}: a client that trusts the device certificate
 * ({@link DeviceTls}), optionally offers a client identity, and keeps the {@code dtmauth} session
 * cookie in a {@link CookieManager} across requests.
 *
 * <p>A device presents a self-signed certificate for an address it does not name, so the host
 * cannot be verified against it. {@code java.net.http} verifies the host by default, so the program
 * that builds this client must start the JVM with {@code
 * -Djdk.internal.httpclient.disableHostnameVerification=true} (alongside the device TLS protocol
 * setting); there is no per-client switch. The trust manager still accepts the certificate, so only
 * the name check is turned off.
 */
public final class JdkDeviceHttp implements DeviceHttp {

  private static final String LOGIN_COOKIE = "dtmauth";
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

  private final HttpClient client;
  private final CookieManager cookies;
  private final URI base;
  private final boolean clientCertificate;

  /**
   * Opens a connection to a device.
   *
   * @param host the device host or address
   * @param port the HTTPS port (8443 over the tunnel, 443 direct)
   * @param identity the client identity to offer, or {@code null} to offer none (a password login)
   */
  public JdkDeviceHttp(String host, int port, KeyManager[] identity) {
    this.cookies = new CookieManager();
    this.client =
        HttpClient.newBuilder()
            .sslContext(DeviceTls.context(identity))
            .cookieHandler(cookies)
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
    this.base = URI.create("https://" + host + ":" + port);
    this.clientCertificate = identity != null && identity.length > 0;
  }

  @Override
  public String get(String pathAndQuery) throws IOException {
    var request = HttpRequest.newBuilder(base.resolve(pathAndQuery)).GET().build();
    try {
      return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IOException("the device request was interrupted", interrupted);
    }
  }

  @Override
  public boolean hasLoginCookie() {
    return cookies.getCookieStore().getCookies().stream()
        .anyMatch(cookie -> cookie.getName().equals(LOGIN_COOKIE));
  }

  @Override
  public boolean offersClientCertificate() {
    return clientCertificate;
  }
}
