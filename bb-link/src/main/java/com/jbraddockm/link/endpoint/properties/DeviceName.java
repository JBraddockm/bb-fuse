package com.jbraddockm.link.endpoint.properties;

import com.jbraddockm.core.error.InvalidArgumentException;
import java.nio.charset.StandardCharsets;

/**
 * A device name that has passed the same checks the device applies before it writes the name.
 *
 * <p>Setting the device name writes the system host name, so the device accepts only host-name
 * characters: ASCII letters, digits, {@code '.'} and {@code '-'}, and the name must start and end
 * with a letter or digit. It also requires a value and caps its length at {@value #MAX_LENGTH}
 * bytes. These checks are made here so an unacceptable name is refused at once, with a message that
 * says why, rather than sent for the device to reject with a terse error.
 *
 * @param value the name, guaranteed to be the one the device accepts
 */
public record DeviceName(String value) {

  /** The longest name the device keeps; it truncates into a buffer one byte larger for the null. */
  public static final int MAX_LENGTH = 99;

  /**
   * Checks the name against the device's rules.
   *
   * @throws InvalidArgumentException if the name is missing, too long, or holds a character the
   *     device would not accept
   */
  public DeviceName {
    if (value == null || value.isEmpty()) {
      throw new InvalidArgumentException("name", "is required");
    }
    if (value.getBytes(StandardCharsets.UTF_8).length > MAX_LENGTH) {
      throw new InvalidArgumentException(
          "name", "is too long: a device name may be at most " + MAX_LENGTH + " characters");
    }
    for (var index = 0; index < value.length(); index++) {
      if (!isAllowed(value.charAt(index))) {
        throw new InvalidArgumentException(
            "name",
            "may use only letters, digits, '.' and '-', but holds '" + value.charAt(index) + "'");
      }
    }
    if (!isAlphanumeric(value.charAt(0)) || !isAlphanumeric(value.charAt(value.length() - 1))) {
      throw new InvalidArgumentException("name", "must start and end with a letter or digit");
    }
  }

  /**
   * Validates a name and wraps it.
   *
   * @param value the name to set
   * @return the validated name
   * @throws InvalidArgumentException if the name is one the device would reject
   */
  public static DeviceName of(String value) {
    return new DeviceName(value);
  }

  /**
   * The host-name characters the device allows: ASCII alphanumerics plus {@code '.'} and {@code
   * '-'}.
   */
  private static boolean isAllowed(char character) {
    return isAlphanumeric(character) || character == '.' || character == '-';
  }

  /** Whether a character is an ASCII letter or digit, which a name must start and end with. */
  private static boolean isAlphanumeric(char character) {
    return (character >= 'a' && character <= 'z')
        || (character >= 'A' && character <= 'Z')
        || (character >= '0' && character <= '9');
  }
}
