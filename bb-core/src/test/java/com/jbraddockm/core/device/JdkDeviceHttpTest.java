package com.jbraddockm.core.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.error.InvalidArgumentException;
import javax.net.ssl.KeyManager;
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
}
