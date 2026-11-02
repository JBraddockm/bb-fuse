package com.jbraddockm.link.endpoint.properties;

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

/** Tests for {@link SetDeviceName}: the request sent, and the result for each kind of answer. */
@DisplayName("SetDeviceName: setting the device name")
class SetDeviceNameTest {

  private static ResponseEnvelope envelope(String resource) {
    return ResponseEnvelope.parse(Resources.text(SetDeviceNameTest.class, resource));
  }

  @Nested
  @DisplayName("the request")
  class Request {

    @Test
    @DisplayName(
        "is to dynamicProperties.cgi, selects the set action, carries the device-name attribute and"
            + " the new name, and says what it does")
    void isTheRequestOfTheDevice() {
      var request = SetDeviceName.request(DeviceName.of("MyPhone"));

      assertThat(request.endpoint()).isEqualTo(Endpoint.DYNAMIC_PROPERTIES);
      assertThat(request.encodedBody())
          .isEqualTo("action=set&name=DeviceInformation%5CDeviceName&value=MyPhone");
      assertThat(request.describe()).isEqualTo("Setting the device name");
    }

    @Test
    @DisplayName("is what the conversation begins with, and the command changes the device")
    void theConversationBeginsWithIt() {
      assertThat(sent(new SetDeviceName("MyPhone").conversation().start()))
          .isEqualTo(SetDeviceName.request(DeviceName.of("MyPhone")));
      assertThat(new SetDeviceName("MyPhone").readOnly()).isFalse();
    }
  }

  @Nested
  @DisplayName("interpret")
  class Interpreting {

    @Test
    @DisplayName("finishes with the name that was set when the device accepts it")
    void finishesWithTheName() {
      var result =
          finished(
              SetDeviceName.interpret(
                  envelope("set-device-name--success.xml"), DeviceName.of("MyPhone")));

      assertThat(result.deviceName()).isEqualTo("MyPhone");
    }

    @Test
    @DisplayName("fails with the error that the device reported, with its words and its number")
    void failsWithTheErrorOfTheDevice() {
      var error =
          failed(
              SetDeviceName.interpret(envelope("properties--error.xml"), DeviceName.of("MyPhone")));

      assertThat(error.code()).isEqualTo(CoreErrorCode.COMMAND_FAILED);
      assertThat(error.message()).isEqualTo("Error - something failed");
      assertThat(error.details()).containsEntry("errorIdentifier", 1011);
    }

    @Test
    @DisplayName("fails when the answer is about something else than the properties")
    void failsForAnotherElement() {
      var other = envelope("properties--wrong-element.xml");

      assertThat(failed(SetDeviceName.interpret(other, DeviceName.of("MyPhone"))))
          .isEqualTo(ResponseFailures.notExpected(other, ResponseElement.DYNAMIC_PROPERTIES));
    }
  }
}
