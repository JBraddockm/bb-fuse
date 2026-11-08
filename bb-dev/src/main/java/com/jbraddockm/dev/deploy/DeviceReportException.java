package com.jbraddockm.dev.deploy;

/**
 * A failure the device reported while running a command. The message is the device's own error
 * line, exactly as it was sent (a {@code result::failure …} or {@code Error: …} line).
 */
public final class DeviceReportException extends Exception {

  /**
   * Creates the exception.
   *
   * @param deviceLine the device's error line, kept as the message
   */
  public DeviceReportException(String deviceLine) {
    super(deviceLine);
  }

  /**
   * Tells whether this is the device's stale-login line, which means the session must log in again
   * before the command can be retried.
   *
   * @return {@code true} for the authentication-failed line
   */
  public boolean isAuthenticationFailure() {
    return "Error: User authentication failed".equals(getMessage());
  }
}
