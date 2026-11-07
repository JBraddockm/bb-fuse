package com.jbraddockm.link.endpoint.metadata;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.transport.cgi.Conversation;
import com.jbraddockm.link.transport.cgi.DeviceCommand;
import com.jbraddockm.link.transport.cgi.Endpoint;
import com.jbraddockm.link.transport.cgi.QueryParameter;
import com.jbraddockm.link.transport.cgi.ResponseBody;
import com.jbraddockm.link.transport.cgi.ResponseElement;
import com.jbraddockm.link.transport.cgi.ResponseEnvelope;
import com.jbraddockm.link.transport.cgi.ResponseFailures;
import com.jbraddockm.link.transport.cgi.Step;
import com.jbraddockm.link.transport.cgi.XmlConversation;
import java.util.Objects;

/**
 * Enumerates a media collection on {@code metadata.cgi}: one GET, one answer. Only reads.
 *
 * @param type the collection to enumerate, or {@link MediaType#SUMMARY} for the library summary
 * @param source the storage area; {@link MediaSource#INTERNAL} unless stated
 * @param order the sort direction; {@link SortOrder#DEFAULT} unless stated
 * @param collection the music grouping; {@link MusicCollection#NONE} unless stated, and only valid
 *     with {@link MediaType#MUSIC}
 */
public record GetDeviceMetadata(
    MediaType type, MediaSource source, SortOrder order, MusicCollection collection)
    implements DeviceCommand<MetadataAnswer> {

  /**
   * Validates the query.
   *
   * @throws NullPointerException if any field is {@code null}
   * @throws InvalidArgumentException if a music grouping is asked for a type other than music
   */
  public GetDeviceMetadata {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(order, "order");
    Objects.requireNonNull(collection, "collection");
    if (collection != MusicCollection.NONE && type != MediaType.MUSIC) {
      throw new InvalidArgumentException("collection", "is only valid with type=music");
    }
  }

  /**
   * Enumerates a type with the device's defaults.
   *
   * @param type the collection to enumerate
   * @return the query
   */
  public static GetDeviceMetadata of(MediaType type) {
    return new GetDeviceMetadata(
        type, MediaSource.INTERNAL, SortOrder.DEFAULT, MusicCollection.NONE);
  }

  /** The request that enumerates the collection. */
  CgiRequest.Get request() {
    var request =
        CgiRequest.Get.to(Endpoint.METADATA).describedAs("Reading the device " + type.wireName());
    request =
        type == MediaType.SUMMARY
            ? request.withText(QueryParameter.SUMMARY, "")
            : request.with(QueryParameter.TYPE, type);
    if (source != MediaSource.INTERNAL) {
      request = request.with(QueryParameter.SOURCE, source);
    }
    if (order != SortOrder.DEFAULT) {
      request = request.with(QueryParameter.ORDER, order);
    }
    if (collection != MusicCollection.NONE) {
      request = request.with(QueryParameter.COLLECTION, collection);
    }
    return request;
  }

  /**
   * Reads the device's answer: the rows inside the {@code Metadata} envelope, or the device's
   * error.
   *
   * @param response the parsed answer
   * @return the rows as a {@link Step.Finish}, or the failure as a {@link Step.Fail}
   */
  Step<MetadataAnswer> interpret(ResponseEnvelope response) {
    return ResponseFailures.failureFor(response, ResponseElement.METADATA)
        .<Step<MetadataAnswer>>map(Step.Fail::new)
        .orElseGet(
            () ->
                new Step.Finish<>(
                    new MetadataAnswer(type, source, ResponseBody.of(response.payload().text()))));
  }

  @Override
  public Conversation<MetadataAnswer> conversation() {
    return new XmlConversation<>() {
      @Override
      public Step<MetadataAnswer> start() {
        return new Step.Send<>(request());
      }

      @Override
      protected Step<MetadataAnswer> onResponse(ResponseEnvelope response) {
        return interpret(response);
      }
    };
  }

  @Override
  public boolean readOnly() {
    return true;
  }
}
