package com.jbraddockm.link.transport.login;

/**
 * The texts of login failures. The wording matches the original tool's message bundle, so bb-link
 * reports a failed login in the same words as bb-deploy.
 */
final class LoginMessages {

  private LoginMessages() {}

  static String passwordRejected(int failedAttempts, int remainingAttempts) {
    return remainingAttempts == 0
        ? "Authentication failed. There have been too many failed attempts ("
            + failedAttempts
            + "). Please enter your password on the device to reset the failed attempts count and"
            + " try again"
        : "Authentication failed. There have been "
            + failedAttempts
            + " of "
            + (remainingAttempts + failedAttempts)
            + " failed attempts";
  }

  static String invalidPassword() {
    return "Authentication failed due to invalid password";
  }

  static String clockOutOfSync() {
    return "Device clock is out of sync.";
  }

  static String systemError() {
    return "Authentication failed due to system error";
  }
}
