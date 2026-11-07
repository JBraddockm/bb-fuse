package com.jbraddockm.link.endpoint.metadata;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.transport.cgi.Conversation;
import com.jbraddockm.link.transport.cgi.DeviceCommand;
import com.jbraddockm.link.transport.cgi.Endpoint;
import com.jbraddockm.link.transport.cgi.FormField;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Fetches one media thumbnail from {@code metadata.cgi} and streams it to a file. A thumbnail is
 * named by a file id ({@code fid} — the usual case, the {@code fid} of a picture / video / music /
 * document row) or an album id ({@code aid}); exactly one of the two is given. The image bytes are
 * written to {@code destination} and never pass through memory as text. The device answers an error
 * envelope (error 5) when it has no thumbnail for the id. Only reads.
 *
 * @param fid the file id of the item, or {@code null} to use {@code aid}
 * @param aid the album id, or {@code null} to use {@code fid}
 * @param destination the file the image is written to
 */
public record GetDeviceThumbnail(Long fid, Long aid, Path destination)
    implements DeviceCommand<DeviceThumbnail> {

  private static final String THUMBNAIL = "thumbnail";

  /**
   * Validates the request.
   *
   * @throws NullPointerException if the destination is missing
   * @throws InvalidArgumentException unless exactly one of {@code fid} / {@code aid} is given, and
   *     it is not negative
   */
  public GetDeviceThumbnail {
    Objects.requireNonNull(destination, "destination");
    if ((fid == null) == (aid == null)) {
      throw new InvalidArgumentException("fid", "give exactly one of fid or aid");
    }
    if (fid != null && fid < 0) {
      throw new InvalidArgumentException("fid", "must not be negative");
    }
    if (aid != null && aid < 0) {
      throw new InvalidArgumentException("aid", "must not be negative");
    }
  }

  @Override
  public Conversation<DeviceThumbnail> conversation() {
    var field = fid != null ? FormField.FID : FormField.AID;
    var id = fid != null ? fid : aid;
    var request =
        CgiRequest.Download.to(
                Endpoint.METADATA, destination, 0, ThumbnailPhase.FETCHING, THUMBNAIL)
            .withText(FormField.TYPE, THUMBNAIL)
            .withText(field, Long.toUnsignedString(id))
            .describedAs("Fetching a media thumbnail");
    return new ThumbnailConversation(request);
  }

  @Override
  public boolean readOnly() {
    return true;
  }
}
