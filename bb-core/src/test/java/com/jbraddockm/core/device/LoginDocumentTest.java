package com.jbraddockm.core.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.testing.Resources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LoginDocument: a parsed device login answer")
class LoginDocumentTest {

  private static LoginDocument parse(String resource) throws Exception {
    return LoginDocument.parse(Resources.text(LoginDocumentTest.class, resource));
  }

  @Test
  @DisplayName("reads the status, a numeric field, and keeps the raw body")
  void readsTheAnswer() throws Exception {
    var document = parse("login--challenge-legacy.xml");

    assertThat(document.status()).isEqualTo(LoginStatus.PASSWORD_CHALLENGE);
    assertThat(document.integer(LoginKey.ITERATION_COUNT)).isEqualTo(1);
    assertThat(document.body()).contains("AuthChallenge");
  }

  @Test
  @DisplayName("reads the lower-case element names under the legacy root")
  void readsLegacyElementNames() throws Exception {
    var document = parse("login--legacy-root.xml");

    assertThat(document.status()).isEqualTo(LoginStatus.PASSWORD_CHALLENGE);
    assertThat(document.integer(LoginKey.ITERATION_COUNT)).isEqualTo(2);
  }

  @Test
  @DisplayName("rejects an empty body as an unusable response")
  void rejectsAnEmptyBody() {
    assertThatThrownBy(() -> LoginDocument.parse("  "))
        .isInstanceOfSatisfying(
            LoginException.class,
            failure ->
                assertThat(failure.reason()).isEqualTo(LoginException.Reason.UNUSABLE_RESPONSE));
  }

  @Test
  @DisplayName("rejects a body that is not valid XML")
  void rejectsMalformedXml() {
    assertThatThrownBy(() -> parse("login--unclosed.xml"))
        .isInstanceOf(LoginException.class)
        .hasMessageContaining("not valid XML");
  }

  @Test
  @DisplayName("rejects a required field the answer does not carry")
  void rejectsAMissingField() throws Exception {
    var document = parse("login--success.xml");

    assertThatThrownBy(() -> document.required(LoginKey.CHALLENGE))
        .isInstanceOf(LoginException.class)
        .hasMessageContaining("challenge");
  }

  @Test
  @DisplayName("rejects a numeric field whose value is not a number")
  void rejectsANonNumericField() throws Exception {
    var document = parse("login--bad-icount.xml");

    assertThatThrownBy(() -> document.integer(LoginKey.ITERATION_COUNT))
        .isInstanceOf(LoginException.class)
        .hasMessageContaining("non-numeric");
  }

  @Test
  @DisplayName("has no status when the answer carries one it does not know")
  void rejectsAnUnknownStatus() throws Exception {
    var document = parse("login--unknown-status.xml");

    assertThatThrownBy(document::status)
        .isInstanceOf(LoginException.class)
        .hasMessageContaining("unknown login status");
  }
}
