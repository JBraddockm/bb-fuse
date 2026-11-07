package com.jbraddockm.link.endpoint.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.transport.cgi.CgiResponse;
import com.jbraddockm.link.transport.cgi.Endpoint;
import com.jbraddockm.link.transport.cgi.FormField;
import com.jbraddockm.link.transport.cgi.Step;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link GetDeviceThumbnail}: a {@code metadata.cgi} thumbnail streamed to a file. */
@DisplayName("GetDeviceThumbnail: a metadata.cgi thumbnail streamed to a file")
class GetDeviceThumbnailTest {

  private static final Path DESTINATION = Path.of("/tmp/thumb.jpg");

  @Nested
  @DisplayName("construction")
  class Construction {

    @Test
    @DisplayName("rejects neither fid nor aid")
    void rejectsNeither() {
      assertThatExceptionOfType(InvalidArgumentException.class)
          .isThrownBy(() -> new GetDeviceThumbnail(null, null, DESTINATION));
    }

    @Test
    @DisplayName("rejects both fid and aid")
    void rejectsBoth() {
      assertThatExceptionOfType(InvalidArgumentException.class)
          .isThrownBy(() -> new GetDeviceThumbnail(1L, 2L, DESTINATION));
    }

    @Test
    @DisplayName("rejects a negative id")
    void rejectsNegative() {
      assertThatExceptionOfType(InvalidArgumentException.class)
          .isThrownBy(() -> new GetDeviceThumbnail(-1L, null, DESTINATION));
    }
  }

  @Nested
  @DisplayName("request")
  class Request {

    @SuppressWarnings("unchecked")
    private CgiRequest.Download download(GetDeviceThumbnail command) {
      var step = command.conversation().start();
      assertThat(step).isInstanceOf(Step.Send.class);
      var request = ((Step.Send<DeviceThumbnail>) step).request();
      assertThat(request).isInstanceOf(CgiRequest.Download.class);
      return (CgiRequest.Download) request;
    }

    @Test
    @DisplayName("a fid thumbnail is a download to metadata.cgi carrying type and fid")
    void fid() {
      var download = download(new GetDeviceThumbnail(42L, null, DESTINATION));
      assertThat(download.endpoint()).isEqualTo(Endpoint.METADATA);
      assertThat(download.destination()).isEqualTo(DESTINATION);
      assertThat(download.fields())
          .containsEntry(FormField.TYPE, "thumbnail")
          .containsEntry(FormField.FID, "42")
          .doesNotContainKey(FormField.AID);
    }

    @Test
    @DisplayName("an aid thumbnail carries aid instead of fid")
    void aid() {
      var download = download(new GetDeviceThumbnail(null, 7L, DESTINATION));
      assertThat(download.fields())
          .containsEntry(FormField.TYPE, "thumbnail")
          .containsEntry(FormField.AID, "7")
          .doesNotContainKey(FormField.FID);
    }

    @Test
    @DisplayName("only reads")
    void readOnly() {
      assertThat(new GetDeviceThumbnail(1L, null, DESTINATION).readOnly()).isTrue();
    }
  }

  @Nested
  @DisplayName("answer")
  class Answer {

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("a streamed image is the result")
    void image() {
      var command = new GetDeviceThumbnail(1L, null, DESTINATION);
      var response =
          new CgiResponse(
              200, "image/jpeg", "", null, new CgiResponse.Downloaded(DESTINATION, 2048L));

      var step = command.conversation().onAnswer(response, null);

      assertThat(step).isInstanceOf(Step.Finish.class);
      var result = ((Step.Finish<DeviceThumbnail>) step).result();
      assertThat(result.file()).isEqualTo(DESTINATION);
      assertThat(result.bytes()).isEqualTo(2048L);
      assertThat(result.contentType()).isEqualTo("image/jpeg");
      assertThat(result.toJson())
          .containsEntry("bytes", 2048L)
          .containsEntry("file", DESTINATION.toString());
    }

    @Test
    @DisplayName("no image and no envelope is a failure")
    void failure() {
      var command = new GetDeviceThumbnail(1L, null, DESTINATION);
      var response = new CgiResponse(200, "text/plain", "", null);

      var step = command.conversation().onAnswer(response, null);

      assertThat(step).isInstanceOf(Step.Fail.class);
    }
  }
}
