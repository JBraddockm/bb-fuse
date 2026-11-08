package com.jbraddockm.core.device;

import com.jbraddockm.core.xml.MalformedXmlException;
import com.jbraddockm.core.xml.XmlElement;
import com.jbraddockm.core.xml.XmlParser;
import java.util.Locale;
import java.util.Optional;

/** A parsed answer of the device login interface, and the raw body it was parsed from. */
final class LoginDocument {

  private static final String LEGACY_ROOT = "RIM_tablet_response";

  private final XmlElement root;
  private final String body;

  private LoginDocument(XmlElement root, String body) {
    this.root = root;
    this.body = body;
  }

  static LoginDocument parse(String body) throws LoginException {
    if (body == null || body.isBlank()) {
      throw new LoginException(
          LoginException.Reason.UNUSABLE_RESPONSE, "the device sent no login response");
    }
    try {
      return new LoginDocument(XmlParser.parse(body), body);
    } catch (MalformedXmlException failure) {
      throw new LoginException(
          LoginException.Reason.UNUSABLE_RESPONSE,
          "the login response is not valid XML: " + failure.getMessage());
    }
  }

  /**
   * The raw response body, so a caller can read anything beyond the login (such as credentials).
   */
  String body() {
    return body;
  }

  LoginStatus status() throws LoginException {
    var text =
        text(LoginKey.STATUS).orElseThrow(() -> unusable("the login response has no status"));
    return LoginStatus.of(text).orElseThrow(() -> unusable("unknown login status: " + text));
  }

  String required(LoginKey key) throws LoginException {
    return text(key).orElseThrow(() -> unusable("the login response has no " + name(key)));
  }

  int integer(LoginKey key) throws LoginException {
    var value = required(key);
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException _) {
      throw unusable("the login response has a non-numeric " + name(key) + ": " + value);
    }
  }

  Optional<String> optional(LoginKey key) {
    return text(key);
  }

  private Optional<String> text(LoginKey key) {
    return root.descendant(key.elementName(root.name().equals(LEGACY_ROOT)))
        .map(XmlElement::text)
        .filter(value -> !value.isEmpty());
  }

  private static String name(LoginKey key) {
    return key.name().toLowerCase(Locale.ROOT);
  }

  private static LoginException unusable(String message) {
    return new LoginException(LoginException.Reason.UNUSABLE_RESPONSE, message);
  }
}
