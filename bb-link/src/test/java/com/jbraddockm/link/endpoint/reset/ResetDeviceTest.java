package com.jbraddockm.link.endpoint.reset;

import static com.jbraddockm.link.testing.Steps.failed;
import static com.jbraddockm.link.testing.Steps.finished;
import static com.jbraddockm.link.testing.Steps.sent;
import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.link.testing.Resources;
import com.jbraddockm.link.transport.cgi.Endpoint;
import com.jbraddockm.link.transport.cgi.ResponseElement;
import com.jbraddockm.link.transport.cgi.ResponseEnvelope;
import com.jbraddockm.link.transport.cgi.ResponseFailures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link ResetDevice}: the request each mode sends, and the result for each answer. */
@DisplayName("ResetDevice: rebooting and powering off the device")
class ResetDeviceTest {

  private static ResponseEnvelope envelope(String resource) {
    return ResponseEnvelope.parse(Resources.text(ResetDeviceTest.class, resource));
  }

  @Nested
  @DisplayName("the request")
  class Request {

    @Test
    @DisplayName("reboot is to reset.cgi with reset=reset, and says what it does")
    void reboot() {
      var request = ResetDevice.request(ResetMode.REBOOT);

      assertThat(request.endpoint()).isEqualTo(Endpoint.RESET);
      assertThat(request.encodedBody()).isEqualTo("reset=true");
      assertThat(request.describe()).isEqualTo("Rebooting the device");
    }

    @Test
    @DisplayName("shutdown is to reset.cgi with reset=shutdown, and says what it does")
    void shutdown() {
      var request = ResetDevice.request(ResetMode.SHUTDOWN);

      assertThat(request.endpoint()).isEqualTo(Endpoint.RESET);
      assertThat(request.encodedBody()).isEqualTo("reset=shutdown");
      assertThat(request.describe()).isEqualTo("Powering the device off");
    }

    @Test
    @DisplayName("is what the conversation begins with, and the command changes the device")
    void theConversationBeginsWithIt() {
      assertThat(sent(new ResetDevice(ResetMode.REBOOT).conversation().start()))
          .isEqualTo(ResetDevice.request(ResetMode.REBOOT));
      assertThat(new ResetDevice(ResetMode.REBOOT).readOnly()).isFalse();
    }
  }

  @Nested
  @DisplayName("interpret")
  class Interpreting {

    @Test
    @DisplayName("finishes with the mode when the device accepts the reset")
    void finishesWithTheMode() {
      var result =
          finished(ResetDevice.interpret(envelope("reset--success.xml"), ResetMode.SHUTDOWN));

      assertThat(result.mode()).isEqualTo(ResetMode.SHUTDOWN);
      assertThat(result.toJson()).containsEntry("action", "shutdown");
    }

    @Test
    @DisplayName("fails with the error the device reported, with its words and its number")
    void failsWithTheError() {
      var error = failed(ResetDevice.interpret(envelope("reset--error.xml"), ResetMode.REBOOT));

      assertThat(error.code()).isEqualTo(CoreErrorCode.COMMAND_FAILED);
      assertThat(error.message()).isEqualTo("Request not supported");
      assertThat(error.details()).containsEntry("errorIdentifier", 3);
    }

    @Test
    @DisplayName("fails when the answer is about something else than a reset")
    void failsForAnotherElement() {
      var other = envelope("reset--wrong-element.xml");

      assertThat(failed(ResetDevice.interpret(other, ResetMode.REBOOT)))
          .isEqualTo(ResponseFailures.notExpected(other, ResponseElement.RESET));
    }
  }
}
