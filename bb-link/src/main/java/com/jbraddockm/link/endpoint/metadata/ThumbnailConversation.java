package com.jbraddockm.link.endpoint.metadata;

import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.transport.cgi.CgiResponse;
import com.jbraddockm.link.transport.cgi.Conversation;
import com.jbraddockm.link.transport.cgi.ResponseEnvelope;
import com.jbraddockm.link.transport.cgi.ResponseFailures;
import com.jbraddockm.link.transport.cgi.Step;

/**
 * One thumbnail request and what to make of its answer. Unlike the metadata list reads, the
 * device's answer here is binary when it has the thumbnail: that streamed image is the result.
 * Otherwise the device answered with an error envelope (for example error 5, no thumbnail for the
 * id), which is surfaced as a command failure the same way the metadata reads surface theirs.
 */
final class ThumbnailConversation implements Conversation<DeviceThumbnail> {

  private final CgiRequest request;

  ThumbnailConversation(CgiRequest request) {
    this.request = request;
  }

  @Override
  public Step<DeviceThumbnail> start() {
    return new Step.Send<>(request);
  }

  @Override
  public Step<DeviceThumbnail> onAnswer(CgiResponse response, ResponseEnvelope envelope) {
    if (response.downloaded() != null) {
      var downloaded = response.downloaded();
      return new Step.Finish<>(
          new DeviceThumbnail(
              response.statusCode(),
              response.contentType(),
              downloaded.file(),
              downloaded.bytes()));
    }
    var reported = envelope == null ? null : ResponseFailures.reportedError(envelope).orElse(null);
    return new Step.Fail<>(
        reported != null
            ? reported
            : ResponseFailures.deviceError(
                "the device did not return a thumbnail image", null, Json.object()));
  }
}
