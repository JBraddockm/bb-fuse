package com.jbraddockm.core.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.testing.Resources;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Logging in to a device")
class DeviceLoginTest {

  private static String fixture(String name) {
    return Resources.text(DeviceLoginTest.class, name);
  }

  @Nested
  @DisplayName("the challenge-response")
  class ChallengeResponse {

    @Test
    @DisplayName("answers the challenge with the PwCrypto hash and returns the success body")
    void answersTheChallenge() throws Exception {
      var http =
          new FakeDeviceHttp(fixture("login--challenge.xml"), fixture("login--success.xml"))
              .withCookie();

      var body = DeviceLogin.authenticate(http, "hunter2");

      assertThat(body).contains("Success").contains("TLS-SHARE");
      var answer = PwCrypto.hashAndEncode("hunter2", 2, "0102030405060708", 10, "c");
      assertThat(http.requested)
          .containsExactly(
              "/cgi-bin/login.cgi?request_version=1",
              "/cgi-bin/login.cgi?challenge_data=" + answer + "&request_version=1");
    }

    @Test
    @DisplayName("accepts a certificate login: Success on the first request with no cookie")
    void acceptsACertificateLogin() throws Exception {
      var http = new FakeDeviceHttp(fixture("login--success.xml")).withCertificate();

      assertThat(DeviceLogin.authenticate(http, null)).contains("Success");
    }
  }

  @Nested
  @DisplayName("that fails with")
  class Failures {

    @Test
    @DisplayName("INVALID_PASSWORD and the attempts remaining when the device denies the password")
    void denied() {
      var http = new FakeDeviceHttp(fixture("login--denied.xml"));

      assertThatThrownBy(() -> DeviceLogin.authenticate(http, "wrong"))
          .isInstanceOfSatisfying(
              LoginException.class,
              login -> assertThat(login.reason()).isEqualTo(LoginException.Reason.INVALID_PASSWORD))
          .hasMessageContaining("3 attempts remaining");
    }

    @Test
    @DisplayName("INVALID_PASSWORD when a Success brings neither a cookie nor a certificate")
    void successWithoutASession() {
      var http = new FakeDeviceHttp(fixture("login--challenge.xml"), fixture("login--success.xml"));

      assertThatThrownBy(() -> DeviceLogin.authenticate(http, "hunter2"))
          .isInstanceOfSatisfying(
              LoginException.class,
              login ->
                  assertThat(login.reason()).isEqualTo(LoginException.Reason.INVALID_PASSWORD));
    }

    @Test
    @DisplayName("DEVICE_ERROR, with the device's description, when the device reports an error")
    void deviceError() {
      var http = new FakeDeviceHttp(fixture("login--error.xml"));

      assertThatThrownBy(() -> DeviceLogin.authenticate(http, "hunter2"))
          .isInstanceOfSatisfying(
              LoginException.class,
              login -> assertThat(login.reason()).isEqualTo(LoginException.Reason.DEVICE_ERROR))
          .hasMessageContaining("the device is busy");
    }

    @Test
    @DisplayName("PASSWORD_REQUIRED when the device challenges and no password is given")
    void passwordRequired() {
      var http = new FakeDeviceHttp(fixture("login--challenge.xml"));

      assertThatThrownBy(() -> DeviceLogin.authenticate(http, ""))
          .isInstanceOfSatisfying(
              LoginException.class,
              login ->
                  assertThat(login.reason()).isEqualTo(LoginException.Reason.PASSWORD_REQUIRED));
    }

    @Test
    @DisplayName("LEGACY_UNSUPPORTED when the device asks for the legacy (algorithm 0) login")
    void legacyUnsupported() {
      var http = new FakeDeviceHttp(fixture("login--challenge-legacy.xml"));

      assertThatThrownBy(() -> DeviceLogin.authenticate(http, "hunter2"))
          .isInstanceOfSatisfying(
              LoginException.class,
              login ->
                  assertThat(login.reason()).isEqualTo(LoginException.Reason.LEGACY_UNSUPPORTED));
    }

    @Test
    @DisplayName("UNUSABLE_RESPONSE when the device sends nothing")
    void unusableResponse() {
      var http = new FakeDeviceHttp("");

      assertThatThrownBy(() -> DeviceLogin.authenticate(http, "hunter2"))
          .isInstanceOfSatisfying(
              LoginException.class,
              login ->
                  assertThat(login.reason()).isEqualTo(LoginException.Reason.UNUSABLE_RESPONSE));
    }
  }

  /**
   * A scripted device connection: returns the given bodies in order and records the paths asked.
   */
  private static final class FakeDeviceHttp implements DeviceHttp {

    private final Deque<String> responses;
    private final List<String> requested = new ArrayList<>();
    private boolean loginCookie;
    private boolean clientCertificate;

    FakeDeviceHttp(String... responses) {
      this.responses = new ArrayDeque<>(List.of(responses));
    }

    FakeDeviceHttp withCookie() {
      this.loginCookie = true;
      return this;
    }

    FakeDeviceHttp withCertificate() {
      this.clientCertificate = true;
      return this;
    }

    @Override
    public String get(String pathAndQuery) {
      requested.add(pathAndQuery);
      return responses.isEmpty() ? "" : responses.removeFirst();
    }

    @Override
    public boolean hasLoginCookie() {
      return loginCookie;
    }

    @Override
    public boolean offersClientCertificate() {
      return clientCertificate;
    }
  }
}
