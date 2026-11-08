package com.jbraddockm.core.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.security.cert.X509Certificate;
import javax.net.ssl.KeyManager;
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
}
