package com.jbraddockm.link.endpoint.pimsync.item;

import static com.jbraddockm.link.testing.Steps.sent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.endpoint.pimsync.PimAnswer;
import com.jbraddockm.link.endpoint.pimsync.PimService;
import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.transport.cgi.DeviceCommand;
import com.jbraddockm.link.transport.cgi.FormField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for DeletePimItem: the request that removes one item of a service. */
@DisplayName("DeletePimItem: the request that removes one item")
class DeletePimItemTest {

  private static CgiRequest.Form requestOf(DeviceCommand<PimAnswer> command) {
    return (CgiRequest.Form) sent(command.conversation().start());
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({"CONTACTS, contacts", "EVENTS, events"})
  @DisplayName("posts delete for contacts/events with the workstation, service and ids body")
  void asksToDeleteByBody(PimService service, String name) {
    var request = requestOf(new DeletePimItem("WS1", service, "OBJ1"));

    assertThat(request.fields())
        .containsEntry(FormField.COMMAND, "delete")
        .containsEntry(FormField.SERVICE, name)
        .containsEntry(FormField.WORKSTATION_ID, "WS1");
    // contacts/events delete by a batch body {"type":"id","ids":[…]}.
    assertThat(request.fields().get(FormField.DATA))
        .contains("\"type\":\"id\"")
        .contains("\"ids\":[\"OBJ1\"]");
    assertThat(request.describe()).isEqualTo("Removing one " + name + " item");
  }

  @Test
  @DisplayName("posts calendar delete with the bare object id as the data field")
  void asksToDeleteCalendarByBareId() {
    var request = requestOf(new DeletePimItem("WS1", PimService.CALENDARS, "7"));

    assertThat(request.fields())
        .containsEntry(FormField.COMMAND, "delete")
        .containsEntry(FormField.SERVICE, "calendars")
        .containsEntry(FormField.WORKSTATION_ID, "WS1");
    // calendars have no batch delete: the CGI formats the data value straight into the DELETE
    // path, so data is the bare id — not a {"type":"id","ids":[…]} object.
    assertThat(request.fields().get(FormField.DATA)).isEqualTo("7");
    assertThat(request.describe()).isEqualTo("Removing one calendars item");
  }

  @Test
  @DisplayName("encodes the ids body, and changes the device so it is not read-only")
  void encodesAndWrites() {
    var command = new DeletePimItem("WS1", PimService.CONTACTS, "O 1&x");

    assertThat(requestOf(command).fields().get(FormField.DATA)).contains("O 1&x");
    assertThat(requestOf(command).encodedBody())
        .startsWith("command=delete&service=contacts&workstation_id=WS1&data=");
    assertThat(command.readOnly()).isFalse();
  }

  @ParameterizedTest(name = "\"{0}\" is not a workstation")
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  @DisplayName("needs a workstation that is not blank, naming workstationId")
  void needsAWorkstation(String workstation) {
    assertThatThrownBy(() -> new DeletePimItem(workstation, PimService.CONTACTS, "OBJ1"))
        .isInstanceOfSatisfying(
            InvalidArgumentException.class,
            invalid -> assertThat(invalid.parameter()).isEqualTo("workstationId"));
  }

  @ParameterizedTest(name = "\"{0}\" is not an object")
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  @DisplayName("needs an object that is not blank, naming objectId")
  void needsAnObject(String object) {
    assertThatThrownBy(() -> new DeletePimItem("WS1", PimService.CONTACTS, object))
        .isInstanceOfSatisfying(
            InvalidArgumentException.class,
            invalid -> assertThat(invalid.parameter()).isEqualTo("objectId"));
  }

  @Test
  @DisplayName("needs a service")
  void needsAService() {
    assertThatNullPointerException()
        .isThrownBy(() -> new DeletePimItem("WS1", null, "OBJ1"))
        .withMessage("service");
  }
}
