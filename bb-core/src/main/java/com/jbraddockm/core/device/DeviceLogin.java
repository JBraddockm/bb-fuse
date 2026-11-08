package com.jbraddockm.core.device;

import java.io.IOException;

/**
 * Logs in to the device's web interface with its challenge-response handshake — the first exchange
 * of every device session.
 *
 * <p>The device answers the first request with a challenge; the client replies with a {@link
 * PwCrypto} hash of the password, the device's salt and the challenge, and the device sets the
 * {@code dtmauth} session cookie, which {@link DeviceHttp} then carries on every later request. The
 * password itself never travels. A connection that offers a client certificate may be logged in
 * without a password, in which case the device answers {@code Success} with no cookie.
 */
public final class DeviceLogin {

  static final String LOGIN_PATH = "/cgi-bin/login.cgi";

  private DeviceLogin() {}

  /**
   * Logs in over the connection.
   *
   * @param http the device connection; its cookie jar holds the session after a successful login
   * @param password the device password, or {@code null}/empty when only a client certificate is
   *     offered
   * @return the body of the success response, for a caller that needs what else it carries
   * @throws LoginException if the device rejects the login or its answer cannot be used
   * @throws IOException if a request fails
   */
  public static String authenticate(DeviceHttp http, String password)
      throws LoginException, IOException {
    var first = LoginDocument.parse(http.get(LOGIN_PATH + "?request_version=1"));
    return settle(http, first, password, false);
  }

  private static String settle(
      DeviceHttp http, LoginDocument document, String password, boolean answeringChallenge)
      throws LoginException, IOException {
    return switch (document.status()) {
      case DENIED -> throw denied(document);
      case ERROR ->
          throw new LoginException(
              LoginException.Reason.DEVICE_ERROR,
              document.optional(LoginKey.ERROR_DESCRIPTION).orElse("the device reported an error"));
      case SYS_ERROR ->
          throw new LoginException(
              LoginException.Reason.DEVICE_ERROR, "the device reported a system error");
      case SUCCESS -> accept(http, document);
      case PASSWORD_CHALLENGE -> {
        if (answeringChallenge) {
          throw new LoginException(
              LoginException.Reason.UNUSABLE_RESPONSE,
              "the device did not accept the answer to its challenge");
        }
        yield answerChallenge(http, document, password);
      }
    };
  }

  private static String answerChallenge(DeviceHttp http, LoginDocument challenge, String password)
      throws LoginException, IOException {
    if (challenge.integer(LoginKey.ALGORITHM) == 0) {
      throw new LoginException(
          LoginException.Reason.LEGACY_UNSUPPORTED,
          "the device asked for the legacy login, which is not supported");
    }
    if (password == null || password.isEmpty()) {
      throw new LoginException(
          LoginException.Reason.PASSWORD_REQUIRED, "the device requires a password");
    }
    var answer =
        PwCrypto.hashAndEncode(
            password,
            challenge.integer(LoginKey.ALGORITHM),
            challenge.required(LoginKey.SALT),
            challenge.integer(LoginKey.ITERATION_COUNT),
            challenge.required(LoginKey.CHALLENGE));
    var next =
        LoginDocument.parse(
            http.get(LOGIN_PATH + "?challenge_data=" + answer + "&request_version=1"));
    return settle(http, next, password, true);
  }

  private static String accept(DeviceHttp http, LoginDocument document) throws LoginException {
    if (http.hasLoginCookie() || http.offersClientCertificate()) {
      return document.body();
    }
    throw new LoginException(
        LoginException.Reason.INVALID_PASSWORD,
        "the device accepted the login but set no session cookie");
  }

  private static LoginException denied(LoginDocument document) {
    var remaining =
        document
            .optional(LoginKey.RETRIES_REMAINING)
            .map(count -> " (" + count + " attempts remaining)")
            .orElse("");
    return new LoginException(
        LoginException.Reason.INVALID_PASSWORD, "the device rejected the password" + remaining);
  }
}
