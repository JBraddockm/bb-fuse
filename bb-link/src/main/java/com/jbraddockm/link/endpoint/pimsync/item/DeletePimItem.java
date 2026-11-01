package com.jbraddockm.link.endpoint.pimsync.item;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.endpoint.pimsync.PimAnswer;
import com.jbraddockm.link.endpoint.pimsync.PimArguments;
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
 * object identifier. The {@code data} the device expects differs by service (see {@link
 * #conversation()}): a batch {@code {"type":"id","ids":[…]}} for contacts and events, and the bare
 * id for calendars, which have no batch delete. Changes the device, so it is never repeated after a
 * dropped connection.
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
    PimArguments.required("workstationId", workstationId);
    Objects.requireNonNull(service, "service");
    PimArguments.required("objectId", objectId);
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
