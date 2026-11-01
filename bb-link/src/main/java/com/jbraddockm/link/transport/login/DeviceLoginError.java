package com.jbraddockm.link.transport.login;

import java.io.Serial;
import java.util.Optional;

/**
 * The device reported a login error of its own: an {@code Error} status (with the machine-readable
 * {@code ErrorIdentifier} and {@code ErrorDescription} it carries) or a {@code SysErr} system
 * error. It is reported as it is, with the identifier and description the device gave, rather than
 * guessed to be the legacy login.
 */
public final class DeviceLoginError extends LoginException {

  @Serial private static final long serialVersionUID = 1L;

  private final transient String identifier;
  private final transient String description;

  DeviceLoginError(String identifier, String description) {
    super(message(identifier, description));
    this.identifier = identifier;
    this.description = description;
  }

  Optional<String> identifier() {
    return Optional.ofNullable(identifier);
  }

  Optional<String> description() {
    return Optional.ofNullable(description);
  }

  private static String message(String identifier, String description) {
    if (identifier != null && description != null) {
      return "The device reported a login error (" + identifier + "): " + description;
    }
    if (identifier != null) {
      return "The device reported a login error: " + identifier;
    }
    if (description != null) {
      return "The device reported a login error: " + description;
    }
    return "The device reported a login error";
  }
}
