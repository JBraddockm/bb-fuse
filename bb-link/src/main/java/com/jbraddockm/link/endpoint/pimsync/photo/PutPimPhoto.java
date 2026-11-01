package com.jbraddockm.link.endpoint.pimsync.photo;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.endpoint.pimsync.PimArguments;
import com.jbraddockm.link.endpoint.pimsync.PimCommand;
import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.transport.cgi.Conversation;
import com.jbraddockm.link.transport.cgi.DeviceCommand;
import com.jbraddockm.link.transport.cgi.Endpoint;
import com.jbraddockm.link.transport.cgi.QueryParameter;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Sends one contact photo to the device. The multipart body carries two parts: the image (as the
 * {@code upload_filename} part) and a {@code data} field with the contact descriptor, in the exact
 * shape the device expects (see {@link #conversation()}). The device generates the stored size
 * variants from this one image, and refuses a photo over its size limit with status 1004. Changes
 * the device, so it is never repeated after a dropped connection.
 *
 * @param workstationId the client identifier whose account the photo belongs to
 * @param accountId the account the photo is stored under
 * @param objectId the device contact id the photo belongs to
 * @param file the image file to send
 */
public record PutPimPhoto(String workstationId, String accountId, String objectId, Path file)
    implements DeviceCommand<PimPhoto> {

  /** The multipart field name the device reads the uploaded photo from. */
  private static final String UPLOAD_FIELD = "upload_filename";

  /**
   * Validates the command.
   *
   * @throws InvalidArgumentException if the workstation, the account or the object is missing or
   *     blank
   */
  public PutPimPhoto {
    PimArguments.required("workstationId", workstationId);
    PimArguments.required("accountId", accountId);
    PimArguments.required("objectId", objectId);
    Objects.requireNonNull(file, "file");
  }

  @Override
  public Conversation<PimPhoto> conversation() {
    // The backend finds the contact by "id"; account_id is harmless context. The CGI wraps data as
    // [{<data>,"filepath":"…"}], so data is the descriptor's inner fields with no enclosing
    // braces — strip them from the serialized object.
    var object = Json.write(Json.object("id", objectId, "account_id", accountId));
    var data = object.substring(1, object.length() - 1);
    // data rides a "data" form field in the multipart body (not the query): the device reads it
    // there and wraps it as [{<data>,"filepath":"…"}]. command/service/workstation_id stay in the
    // query, where the device reads those.
    var request =
        CgiRequest.Multipart.of(
                Endpoint.PIM_SYNC, file, UPLOAD_FIELD, "image/jpeg", PhotoPhase.SENDING, data)
            .with(QueryParameter.COMMAND, PimCommand.PUT)
            .withText(QueryParameter.SERVICE, PhotoConversation.SERVICE)
            .withText(QueryParameter.WORKSTATION_ID, workstationId)
            .describedAs("Sending a contact photo");
    return new PhotoConversation(request);
  }

  @Override
  public boolean readOnly() {
    return false;
  }
}
