package com.jbraddockm.core.device;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * The TLS context for talking to a device over HTTPS. It accepts any server certificate, because a
 * device presents a self-signed certificate for an address there is nothing to validate against,
 * and offers an optional client identity when the device asks for one. Trusting the transport this
 * way exposes nothing: the login never sends the password, only a hash of a device challenge (see
 * {@link PwCrypto}).
 *
 * <p>The client identity is the caller's, passed in as a {@link KeyManager}: bb-link, bb-tunnel and
 * bb-blend offer their paired certificate, while a password-only consumer such as deploy offers
 * none. Hostname verification is also the caller's concern — {@code java.net.http} checks the host
 * against the certificate by default, which a device certificate fails, so a device client turns it
 * off when it builds its {@code HttpClient}.
 */
public final class DeviceTls {

  private DeviceTls() {}

  /**
   * A TLS context that trusts any device certificate.
   *
   * @param keyManagers the client identity to offer if the device asks for one, or {@code null} to
   *     offer none
   * @return the context
   */
  public static SSLContext context(KeyManager[] keyManagers) {
    try {
      var context = SSLContext.getInstance("TLS");
      context.init(keyManagers, new TrustManager[] {trustAnyCertificate()}, new SecureRandom());
      return context;
    } catch (GeneralSecurityException impossible) {
      throw new AssertionError("the device TLS context could not be created", impossible);
    }
  }

  /** A trust manager that accepts every certificate; see the class description for why. */
  static X509TrustManager trustAnyCertificate() {
    return new AcceptAnyCertificate();
  }

  private static final class AcceptAnyCertificate implements X509TrustManager {

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[0];
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) {
      // A device is never asked for a client certificate.
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) {
      // Deliberately accepts every device certificate.
    }
  }
}
