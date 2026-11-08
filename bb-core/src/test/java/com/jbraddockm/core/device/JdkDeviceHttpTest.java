package com.jbraddockm.core.device;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import javax.net.ssl.KeyManager;
import javax.net.ssl.X509KeyManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

  /** A key manager that offers nothing, enough to build the client. */
  private static final class NoIdentity implements X509KeyManager {

    @Override
    public String[] getClientAliases(String keyType, Principal[] issuers) {
      return null;
    }

    @Override
    public String chooseClientAlias(String[] keyTypes, Principal[] issuers, Socket socket) {
      return null;
    }

    @Override
    public String[] getServerAliases(String keyType, Principal[] issuers) {
      return null;
    }

    @Override
    public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
      return null;
    }

    @Override
    public X509Certificate[] getCertificateChain(String alias) {
      return null;
    }

    @Override
    public PrivateKey getPrivateKey(String alias) {
      return null;
    }
  }
}
