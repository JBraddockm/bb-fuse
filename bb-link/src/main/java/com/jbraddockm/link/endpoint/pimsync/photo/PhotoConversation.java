package com.jbraddockm.link.endpoint.pimsync.photo;

import com.jbraddockm.link.endpoint.pimsync.PimOutcome;
import com.jbraddockm.link.endpoint.pimsync.PimStatus;
import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.transport.cgi.CgiResponse;
import com.jbraddockm.link.transport.cgi.Conversation;
import com.jbraddockm.link.transport.cgi.ResponseEnvelope;
import com.jbraddockm.link.transport.cgi.Step;
import java.util.Optional;

/**
 * One contact-photo request and what to make of its answer. Unlike the other PIM commands, the
 * device's answer here may be binary: when it streamed an image to the file, that is the result;
 * otherwise it answered with a status (JSON or text) — "not modified", an error — classified the
 * same way the rest of {@code pimsync.cgi} is, through {@link PimOutcome}.
 */
final class PhotoConversation implements Conversation<PimPhoto> {

  /** The wire word for the photo service, which is not one of the item services. */
  static final String SERVICE = "photos";

  private final CgiRequest request;

  PhotoConversation(CgiRequest request) {
    this.request = request;
  }

  @Override
  public Step<PimPhoto> start() {
    return new Step.Send<>(request);
  }

  @Override
  public Step<PimPhoto> onAnswer(CgiResponse response, ResponseEnvelope envelope) {
    if (response.downloaded() != null) {
      var downloaded = response.downloaded();
      return new Step.Finish<>(
          new PimPhoto(
              response.statusCode(),
              response.contentType(),
              Optional.of(downloaded.file()),
              downloaded.bytes(),
              null,
              null));
    }
    var status = PimStatus.read(response.body()).orElse(null);
    var answer =
        new PimPhoto(
            response.statusCode(),
            response.contentType(),
            Optional.empty(),
            0,
            status == null ? null : status.code(),
            status == null ? null : status.message());
    return PimOutcome.resultOrRejection(status, answer);
  }
}
