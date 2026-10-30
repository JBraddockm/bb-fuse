package com.jbraddockm.link.endpoint.pimsync.item;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.endpoint.pimsync.PimAnswer;
import com.jbraddockm.link.endpoint.pimsync.PimCommand;
import com.jbraddockm.link.endpoint.pimsync.PimService;
import com.jbraddockm.link.endpoint.pimsync.PimSyncConversation;
import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.transport.cgi.Conversation;
import com.jbraddockm.link.transport.cgi.DeviceCommand;
import com.jbraddockm.link.transport.cgi.Endpoint;
import com.jbraddockm.link.transport.cgi.FormField;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Removes one item — a contact, event or calendar — from a workstation's account, named by its
 * object identifier. Every delete is a POST carrying a {@code data} field, but what {@code data}
 * holds differs by service. For {@code contacts} and {@code events} the endpoint is a
 * <em>batch</em> that reads the ids from {@code data} as {@code {"type":"id","ids":[…]}} (the
 * {@code type} selects whether an id is the device's own object id or a sync id), so the one object
 * id is posted as a one-element {@code ids} array. For {@code calendars} there is no batch delete:
 * the device removes a calendar only through {@code DELETE /bblink/calendar/<account>/<id>} (the id
 * in the path, no body), and the CGI builds that path by formatting the {@code data} value straight
 * in — so {@code data} is the bare object id, not a JSON object. (Confirmed from the CGI: {@code
 * extract_data_param} feeds the calendar-delete path builder, which does {@code snprintf("/%s",
 * data)} with an HTTP {@code DELETE}.) Changes the device, so it is never repeated after a dropped
 * connection.
 *
 * @param workstationId the client identifier whose account holds the item
 * @param service the kind of item
 * @param objectId the device object identifier of the item to remove
 */
public record DeletePimItem(String workstationId, PimService service, String objectId)
    implements DeviceCommand<PimAnswer> {

  /**
   * Validates the command.
   *
   * @throws InvalidArgumentException if the workstation or the object identifier is missing or
   *     blank
   */
  public DeletePimItem {
    if (workstationId == null || workstationId.isBlank()) {
      throw new InvalidArgumentException("workstationId", "is required");
    }
    Objects.requireNonNull(service, "service");
    if (objectId == null || objectId.isBlank()) {
      throw new InvalidArgumentException("objectId", "is required");
    }
  }

  @Override
  public Conversation<PimAnswer> conversation() {
    // The data field differs by service: contacts/events read the batch {"type":"id","ids":[…]};
    // calendars have no batch delete, so the CGI formats data straight into the DELETE path, which
    // means data is the bare object id.
    var data =
        service == PimService.CALENDARS
            ? objectId
            : Json.write(Map.of("type", "id", "ids", List.of(objectId)));
    var request =
        CgiRequest.Form.to(Endpoint.PIM_SYNC)
            .with(FormField.COMMAND, PimCommand.DELETE)
            .with(FormField.SERVICE, service)
            .withText(FormField.WORKSTATION_ID, workstationId)
            .withText(FormField.DATA, data)
            .describedAs("Removing one " + service.wireName() + " item");
    return new PimSyncConversation(PimCommand.DELETE, service, request);
  }

  @Override
  public boolean readOnly() {
    return false;
  }
}
