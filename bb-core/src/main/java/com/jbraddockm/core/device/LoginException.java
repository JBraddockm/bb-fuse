package com.jbraddockm.core.device;

import java.io.Serial;

/**
 * A device login that did not succeed. The {@link #reason()} says why, so a caller can tell a wrong
 * password (which it may re-prompt for) from a device error (which it cannot fix) without parsing
 * the message.
 */
public final class LoginException extends Exception {

  @Serial private static final long serialVersionUID = 1L;

  /** Why a login did not succeed. */
  public enum Reason {
    /** The device rejected the password, or accepted it but issued no session. */
    INVALID_PASSWORD,
    /** The device asked for a password and none was given. */
    PASSWORD_REQUIRED,
    /** The device reported an error of its own. */
    DEVICE_ERROR,
    /** The device asked for the legacy login, which is not supported. */
    LEGACY_UNSUPPORTED,
    /** The device's answer could not be understood. */
    UNUSABLE_RESPONSE
  }

  private final transient Reason reason;

  /**
   * Creates the exception.
   *
   * @param reason why the login did not succeed
   * @param message what went wrong
   */
  public LoginException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  /**
   * Why the login did not succeed.
   *
   * @return the reason
   */
  public Reason reason() {
    return reason;
  }
}
