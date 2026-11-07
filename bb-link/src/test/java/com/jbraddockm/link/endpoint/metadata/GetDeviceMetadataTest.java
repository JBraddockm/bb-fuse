package com.jbraddockm.link.endpoint.metadata;

import static com.jbraddockm.link.testing.Steps.failed;
import static com.jbraddockm.link.testing.Steps.finished;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.testing.Resources;
import com.jbraddockm.link.transport.cgi.CgiResponse;
import com.jbraddockm.link.transport.cgi.Endpoint;
import com.jbraddockm.link.transport.cgi.ResponseEnvelope;
import com.jbraddockm.link.transport.cgi.Step;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link GetDeviceMetadata}: reading {@code metadata.cgi} as a passthrough of its rows.
 */
@DisplayName("GetDeviceMetadata: reading metadata.cgi")
class GetDeviceMetadataTest {

  private static Step<MetadataAnswer> answer(GetDeviceMetadata query, String resource) {
    var body = Resources.text(GetDeviceMetadataTest.class, resource);
    var response = new CgiResponse(200, "text/xml", body, null);
    return query.conversation().onAnswer(response, ResponseEnvelope.parse(body));
  }

  @Nested
  @DisplayName("the request it sends")
  class TheRequest {

    @Test
    @DisplayName("asks metadata.cgi for the type")
    void asksForType() {
      var request = GetDeviceMetadata.of(MediaType.PICTURE).request();

      assertThat(request.endpoint()).isEqualTo(Endpoint.METADATA);
      assertThat(request.pathAndQuery()).isEqualTo("/cgi-bin/metadata.cgi?type=picture");
    }

    @Test
    @DisplayName("asks for the summary as a flag, with no type")
    void asksForSummary() {
      var request = GetDeviceMetadata.of(MediaType.SUMMARY).request();

      assertThat(request.pathAndQuery()).contains("summary").doesNotContain("type=");
    }

    @Test
    @DisplayName("carries source, order and music grouping when set")
    void carriesOptions() {
      var request =
          new GetDeviceMetadata(
                  MediaType.MUSIC, MediaSource.SDCARD, SortOrder.DESC, MusicCollection.ALBUM)
              .request();

      assertThat(request.pathAndQuery())
          .contains("type=music")
          .contains("source=sdcard")
          .contains("order=desc")
          .contains("collection=album");
    }

    @Test
    @DisplayName("only reads: it changes nothing on the device")
    void onlyReads() {
      assertThat(GetDeviceMetadata.of(MediaType.MUSIC).readOnly()).isTrue();
    }
  }

  @Nested
  @DisplayName("validation")
  class Validation {

    @Test
    @DisplayName("rejects a music grouping for a type other than music")
    void rejectsGroupingWithoutMusic() {
      assertThatExceptionOfType(InvalidArgumentException.class)
          .isThrownBy(() ->
              new GetDeviceMetadata(
                  MediaType.PICTURE,
                  MediaSource.INTERNAL,
                  SortOrder.DEFAULT,
                  MusicCollection.ALBUM));
    }
  }

  @Nested
  @DisplayName("the answer")
  class TheAnswer {

    @Test
    @DisplayName("hands back the rows from the Metadata CDATA as parsed JSON")
    @SuppressWarnings("unchecked")
    void handsBackRows() {
      var result =
          finished(
              answer(GetDeviceMetadata.of(MediaType.MUSIC), "metadata--music-flat--synthetic.xml"));

      var json = result.toJson();
      assertThat(json)
          .containsEntry("type", "music")
          .containsEntry("source", "internal")
          .containsEntry("bodyFormat", "json");
      var rows = (List<Map<String, Object>>) json.get("rows");
      assertThat(rows).hasSize(2);
      assertThat(rows.getFirst())
          .containsEntry("filename", "first.mp3")
          .containsEntry("title", "First Track");
    }

    @Test
    @DisplayName("fails with the device's error when it reports one")
    void failsOnDeviceError() {
      var error =
          failed(answer(GetDeviceMetadata.of(MediaType.PICTURE), "metadata--error--synthetic.xml"));

      assertThat(error.code()).isEqualTo(CoreErrorCode.COMMAND_FAILED);
      assertThat(error.message()).contains("invalid type");
    }
  }
}
