package com.jbraddockm.core.device;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("LoginStatus: the device login status values")
class LoginStatusTest {

  @ParameterizedTest(name = "{0} is read as {1}")
  @CsvSource({
    "PasswdChallenge, PASSWORD_CHALLENGE",
    "Success, SUCCESS",
    "Denied, DENIED",
    "Error, ERROR",
    "SysErr, SYS_ERROR"
  })
  @DisplayName("recognises each status by its wire name")
  void recognisesEachWireName(String wire, LoginStatus expected) {
    assertThat(LoginStatus.of(wire)).contains(expected);
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({"success", "SYSERR", "sysErr", "passwdchallenge"})
  @DisplayName("matches a wire name without regard to case")
  void matchesWithoutRegardToCase(String wire) {
    assertThat(LoginStatus.of(wire)).isPresent();
  }

  @Test
  @DisplayName("returns empty for a status it does not know")
  void returnsEmptyForAnUnknownStatus() {
    assertThat(LoginStatus.of("Pending")).isEmpty();
  }
}
