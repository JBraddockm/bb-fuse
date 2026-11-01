package com.jbraddockm.link.endpoint.pairing;

import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.core.command.NoData;
import com.jbraddockm.link.transport.cgi.Authentication;
import com.jbraddockm.link.transport.cgi.TransportScheme;
import com.jbraddockm.link.transport.login.LoginOnly;
import com.jbraddockm.link.transport.session.DeviceSession;
import com.jbraddockm.link.transport.tls.ClientCertificate;

/** Tries a new identity against the device that issued its certificate. */
@FunctionalInterface
interface IdentityVerifier {

  /**
   * Tries the identity.
   *
   * @param session the session that did the enrolment; the trial goes to the same host
   * @param identity the files of the new identity
   * @param port the port that takes certificates
   * @param listener where the trial reports
   * @return whether the device took the identity
   */
  Verification verify(
      DeviceSession session, ClientCertificate identity, int port, CommandListener listener);

  /**
   * A trial that logs in with the identity alone, over TLS, and nothing else: the device accepting
   * the login is the device accepting the certificate. (The device's PIN was already confirmed on
   * the enrolling session before the certificate was signed, so the trial does not read it again.)
   *
   * @return the verifier
   */
  static IdentityVerifier byLoggingIn() {
    return IdentityVerifier::logInWithTheCertificate;
  }

  private static Verification logInWithTheCertificate(
      DeviceSession session, ClientCertificate identity, int port, CommandListener listener) {
    try (var certificateSession =
        session.sibling(TransportScheme.HTTPS, port, new Authentication.Certificate(identity))) {
      return switch (certificateSession.execute(new LoginOnly(), listener)) {
        case CommandOutcome.Succeeded<NoData> _ -> Verification.verified();
        case CommandOutcome.Failed<NoData>(var error, _, _) -> Verification.failed(error);
      };
    }
  }
}
