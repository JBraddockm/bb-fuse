package com.jbraddockm.core.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import javax.net.ssl.KeyManager;
import javax.net.ssl.X509KeyManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DeviceTls: the TLS context for talking to a device")
class DeviceTlsTest {

  @Nested
  @DisplayName("the context")
  class Context {

    @Test
    @DisplayName("is a usable TLS client context with no client identity")
    void withoutIdentity() {
      var context = DeviceTls.context(null);

      assertThat(context.getProtocol()).isEqualTo("TLS");
      var engine = context.createSSLEngine();
      engine.setUseClientMode(true);
      assertThat(engine.getUseClientMode()).isTrue();
    }

    @Test
    @DisplayName("builds with an empty identity list and with a client identity")
    void withIdentity() {
      assertThat(DeviceTls.context(new KeyManager[0])).isNotNull();
      assertThat(DeviceTls.context(new KeyManager[] {new NoIdentity()})).isNotNull();
    }
  }

  @Nested
  @DisplayName("trusting the device")
  class Trust {

    @Test
    @DisplayName("accepts any server certificate and names no acceptable issuers")
    void acceptsAnyServerCertificate() {
      var trust = DeviceTls.trustAnyCertificate();

      assertThat(trust.getAcceptedIssuers()).isEmpty();
      assertThatCode(() -> trust.checkServerTrusted(new X509Certificate[0], "RSA"))
          .doesNotThrowAnyException();
    }
  }

  /** A key manager that offers nothing, to show the context builds with one supplied. */
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
