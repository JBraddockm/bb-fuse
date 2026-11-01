package com.jbraddockm.link.endpoint.pairing;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.endpoint.pairing.pki.CertificateDetails;
import com.jbraddockm.link.testing.Resources;
import com.jbraddockm.link.testing.TestPki;
import com.jbraddockm.link.testing.TlsTestDevice;
import com.jbraddockm.link.transport.cgi.Authentication;
import com.jbraddockm.link.transport.cgi.DeviceTarget;
import com.jbraddockm.link.transport.cgi.TransportScheme;
import com.jbraddockm.link.transport.session.DeviceSession;
import com.jbraddockm.link.transport.tls.ClientCertificate;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link IdentityVerifier}: verifying a new certificate by logging in with it. */
@DisplayName("IdentityVerifier: whether the device takes a new certificate")
class IdentityVerifierTest {

  @TempDir private Path folder;
  private TlsTestDevice device;
  private DeviceSession original;

  @BeforeEach
  void startTheDevice() throws Exception {
    device = new TlsTestDevice();
    original =
        DeviceSession.forTarget(
            new DeviceTarget(
                "127.0.0.1", 443, TransportScheme.HTTPS, new Authentication.NoPassword()));
  }

  @AfterEach
  void stopTheDevice() throws Exception {
    original.close();
    device.close();
  }

  private ClientCertificate identity(String key) {
    return new ClientCertificate(
        Resources.copyTo(TestPki.class, "test-pki--leaf-certificate.pem", folder),
        Resources.copyTo(TestPki.class, key, folder));
  }

  private Verification verify(ClientCertificate identity) {
    return IdentityVerifier.byLoggingIn()
        .verify(original, identity, device.port(), CommandListener.SILENT);
  }

  @Test
  @DisplayName("says verified when the device takes the certificate at the login, and nothing else")
  void aCertificateThatIsTaken() throws Exception {
    var subject =
        CertificateDetails.firstCertificate(
                Resources.text(TestPki.class, "test-pki--leaf-certificate.pem"))
            .getSubjectX500Principal()
            .getName();

    var verification = verify(identity("test-pki--leaf-key-pkcs8.pem"));

    assertThat(verification).isEqualTo(Verification.verified());
    assertThat(device.peers()).containsExactly(subject);
  }

  @Test
  @DisplayName(
      "says failed with certRefused when the device refuses the certificate it just signed")
  void aCertificateThatIsRefused() {
    device.behaviour = TlsTestDevice.Behaviour.REFUSES_EVERYONE;

    var verification = verify(identity("test-pki--leaf-key-pkcs8.pem"));

    assertThat(verification.state()).isEqualTo(Verification.State.FAILED);
    assertThat(verification.error()).isNotNull();
    assertThat(verification.error().code()).isEqualTo(LinkErrorCode.PASSWORD_REQUIRED);
    assertThat(verification.error().details())
        .containsEntry("authRequired", "password")
        .containsEntry("certRefused", true);
  }

  @Test
  @DisplayName(
      "says failed when the device ends the handshake for a certificate that it did not issue")
  void aHandshakeThatEnds() {
    device.behaviour = TlsTestDevice.Behaviour.REJECTS_PRESENTED_CERTIFICATES;

    var verification = verify(identity("test-pki--leaf-key-pkcs8.pem"));

    assertThat(verification.state()).isEqualTo(Verification.State.FAILED);
    assertThat(verification.error()).isNotNull();
    assertThat(verification.error().code())
        .isIn(CoreErrorCode.CONNECTION_FAILED, CoreErrorCode.CONNECTION_LOST);
  }

  @Test
  @DisplayName("says failed when the key cannot be used, and has sent nothing")
  void aKeyThatCannotBeUsed() {
    var verification = verify(identity("test-pki--leaf-key-encrypted.pem"));

    assertThat(verification.state()).isEqualTo(Verification.State.FAILED);
    assertThat(verification.error()).isNotNull();
    assertThat(verification.error().code()).isEqualTo(CoreErrorCode.INVALID_ARGUMENT);
    assertThat(device.paths()).isEmpty();
  }

  @Test
  @DisplayName(
      "does not use the session that it was given: it opens a sibling, on the other port, logs in"
          + " once, and closes it")
  void theOriginalSessionIsNotUsed() {
    verify(identity("test-pki--leaf-key-pkcs8.pem"));

    assertThat(original.target().port()).isEqualTo(443);
    assertThat(device.paths()).containsExactly("/cgi-bin/login.cgi?request_version=1");
  }
}
