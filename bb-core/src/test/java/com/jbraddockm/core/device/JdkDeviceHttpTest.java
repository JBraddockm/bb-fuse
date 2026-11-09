package com.jbraddockm.core.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.core.testing.Resources;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyStore;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("JdkDeviceHttp: the java.net.http device connection")
class JdkDeviceHttpTest {

  @Test
  @DisplayName("offers no certificate and holds no cookie when built without an identity")
  void withoutIdentity() {
    try (var http = new JdkDeviceHttp("device.local", 8443, null)) {
      assertThat(http.offersClientCertificate()).isFalse();
      assertThat(http.hasLoginCookie()).isFalse();
    }
  }

  @Test
  @DisplayName("reports that it offers a certificate when built with a client identity")
  void withIdentity() {
    try (var http = new JdkDeviceHttp("device.local", 443, new KeyManager[] {new NoIdentity()})) {
      assertThat(http.offersClientCertificate()).isTrue();
    }
  }

  @Test
  @DisplayName("rejects a null host, and a blank one naming the host argument")
  @SuppressWarnings("resource")
  void rejectsABadHost() {
    assertThatThrownBy(() -> new JdkDeviceHttp(null, 443, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new JdkDeviceHttp("  ", 443, null))
        .isInstanceOfSatisfying(
            InvalidArgumentException.class,
            invalid -> assertThat(invalid.parameter()).isEqualTo("host"));
  }

  @Test
  @DisplayName("rejects a port outside 1..65535, naming the port argument")
  @SuppressWarnings("resource")
  void rejectsABadPort() {
    assertThatThrownBy(() -> new JdkDeviceHttp("device.local", 0, null))
        .isInstanceOfSatisfying(
            InvalidArgumentException.class,
            invalid -> assertThat(invalid.parameter()).isEqualTo("port"));
    assertThatThrownBy(() -> new JdkDeviceHttp("device.local", 70000, null))
        .isInstanceOf(InvalidArgumentException.class);
  }

  @Nested
  @DisplayName("over a loopback HTTPS server")
  class OverALoopbackServer {

    @TempDir private Path folder;

    @Test
    @DisplayName("GETs the body and keeps the dtmauth cookie the device sets")
    void getsTheBodyAndKeepsTheCookie() throws Exception {
      var server =
          HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
      server.setHttpsConfigurator(new HttpsConfigurator(serverContext()));
      server.createContext(
          "/",
          exchange -> {
            exchange.getResponseHeaders().add("Set-Cookie", "dtmauth=abc123; Path=/");
            var body = "OK".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) {
              out.write(body);
            }
          });
      server.start();
      try (var http = new JdkDeviceHttp("localhost", server.getAddress().getPort(), null)) {
        assertThat(http.get("/cgi-bin/login.cgi")).isEqualTo("OK");
        assertThat(http.hasLoginCookie()).isTrue();
      } finally {
        server.stop(0);
      }
    }

    /** A server TLS context presenting the committed {@code localhost} certificate. */
    private SSLContext serverContext() throws Exception {
      var identity =
          PemIdentity.load(
              new ClientCertificate(
                  Resources.copyTo(JdkDeviceHttpTest.class, "loopback--certificate.pem", folder),
                  Resources.copyTo(JdkDeviceHttpTest.class, "loopback--key-pkcs8.pem", folder)));
      var keyStore = KeyStore.getInstance("PKCS12");
      keyStore.load(null, null);
      keyStore.setKeyEntry("server", identity.key(), new char[0], identity.chain());
      var keyManagers = KeyManagerFactory.getInstance("SunX509");
      keyManagers.init(keyStore, new char[0]);
      var context = SSLContext.getInstance("TLS");
      context.init(keyManagers.getKeyManagers(), null, null);
      return context;
    }
  }
}
