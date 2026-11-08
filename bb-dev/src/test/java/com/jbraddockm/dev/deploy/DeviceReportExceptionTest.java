package com.jbraddockm.dev.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DeviceReportException: a failure the device reported")
class DeviceReportExceptionTest {

  @Test
  @DisplayName("keeps the device's error line as the message")
  void keepsTheDeviceLine() {
    var exception = new DeviceReportException("result::failure 500 'Entry-Point-Type'");

    assertThat(exception).hasMessage("result::failure 500 'Entry-Point-Type'");
  }

  @Test
  @DisplayName("recognises the stale-login line as an authentication failure")
  void recognisesTheAuthenticationFailure() {
    assertThat(
            new DeviceReportException("Error: User authentication failed")
                .isAuthenticationFailure())
        .isTrue();
  }

  @Test
  @DisplayName("treats any other error line as not an authentication failure")
  void otherLinesAreNotAuthenticationFailures() {
    assertThat(new DeviceReportException("result::failure 500").isAuthenticationFailure())
        .isFalse();
  }
}
