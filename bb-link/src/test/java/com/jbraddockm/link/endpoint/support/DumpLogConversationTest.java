package com.jbraddockm.link.endpoint.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.jbraddockm.link.testing.Resources;
import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.transport.cgi.CgiResponse;
import com.jbraddockm.link.transport.cgi.Conversation;
import com.jbraddockm.link.transport.cgi.Endpoint;
import com.jbraddockm.link.transport.cgi.Step;
import com.jbraddockm.link.transport.cgi.StepEffect;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("The DumpLog conversation")
class DumpLogConversationTest {

  private static final Path DESTINATION = Path.of("cso-logs-report.zip");

  private static CgiResponse xml(String fixture) {
    return new CgiResponse(
        200, "text/xml", Resources.text(DumpLogConversationTest.class, fixture), null);
  }

  private static CgiResponse ready(long size) {
    return new CgiResponse(
        200,
        "text/xml",
        Resources.text(
            DumpLogConversationTest.class,
            "support--dumplog-ready.xml",
            Map.of("size", Long.toString(size))),
        null);
  }

  private static CgiRequest.StreamingGet sent(Step<DeviceLogArchive> step) {
    assertThat(step).isInstanceOf(Step.Send.class);
    var request = ((Step.Send<DeviceLogArchive>) step).request();
    assertThat(request).isInstanceOf(CgiRequest.StreamingGet.class);
    return (CgiRequest.StreamingGet) request;
  }

  private static Conversation<DeviceLogArchive> conversation() {
    return new DumpDeviceLogs(DESTINATION).conversation();
  }

  @Nested
  @DisplayName("the request it sends")
  class Requests {

    @Test
    @DisplayName("selects the dumplog facility on support.cgi with a GET")
    void selectsFacility() {
      var first = sent(conversation().start());

      assertThat(first.endpoint()).isEqualTo(Endpoint.SUPPORT);
      assertThat(first.pathAndQuery()).contains("facility=dumplog");
      assertThat(first.destination()).isEqualTo(DESTINATION);
    }

    @Test
    @DisplayName("announces the command first: the opening request reports no progress")
    void announcesBeforeProgress() {
      var first = (Step.Send<DeviceLogArchive>) conversation().start();

      assertThat(first.before()).noneMatch(StepEffect.Report.class::isInstance);
      assertThat(first.request().describe()).contains("Collecting the device logs");
    }

    @Test
    @DisplayName("keeps polling the same facility while the device gathers")
    void pollsWhileGathering() {
      var conversation = conversation();
      conversation.start();

      var afterStarted = conversation.onAnswer(xml("support--dumplog-started.xml"), null);
      assertThat(sent(afterStarted).pathAndQuery()).contains("facility=dumplog");

      var afterInProgress = conversation.onAnswer(xml("support--dumplog-inprogress.xml"), null);
      assertThat(sent(afterInProgress).pathAndQuery()).contains("facility=dumplog");
      assertThat(((Step.Send<DeviceLogArchive>) afterInProgress).before())
          .anyMatch(StepEffect.Pause.class::isInstance);
    }

    @Test
    @DisplayName("sends one more request with the reported size once the archive is ready")
    void streamsWhenReady() {
      var conversation = conversation();
      conversation.start();
      conversation.onAnswer(xml("support--dumplog-started.xml"), null);

      var afterReady = conversation.onAnswer(ready(1_769_721), null);

      var download = sent(afterReady);
      assertThat(download.pathAndQuery()).contains("facility=dumplog");
      assertThat(download.expectedLength()).isEqualTo(1_769_721);
    }
  }

  @Nested
  @DisplayName("finishing")
  class Finishing {

    @Test
    @DisplayName("finishes with the streamed archive file and its size")
    void finishesWithArchive() {
      var conversation = conversation();
      conversation.start();
      conversation.onAnswer(xml("support--dumplog-started.xml"), null);
      conversation.onAnswer(ready(1_830_535), null);
      var streamed =
          new CgiResponse(
              200,
              "application/octet-stream",
              "",
              null,
              new CgiResponse.Downloaded(DESTINATION, 1_830_535));

      var finish = conversation.onAnswer(streamed, null);

      assertThat(finish).isInstanceOf(Step.Finish.class);
      var result = ((Step.Finish<DeviceLogArchive>) finish).result();
      assertThat(result.file()).isEqualTo(DESTINATION);
      assertThat(result.bytes()).isEqualTo(1_830_535);
    }
  }

  @Nested
  @DisplayName("failing")
  class Failing {

    @Test
    @DisplayName("fails when the device reports Bad facility")
    void failsOnBadFacility() {
      var conversation = conversation();
      conversation.start();

      var step = conversation.onAnswer(xml("support--bad-facility.xml"), null);

      assertThat(step).isInstanceOf(Step.Fail.class);
      var error = ((Step.Fail<DeviceLogArchive>) step).error();
      assertThat(error.message()).contains("Bad facility");
      assertThat(error.details()).containsEntry("errorIdentifier", 4);
    }

    @Test
    @DisplayName("gives up when the device never finishes gathering in time")
    void failsWhenGatherNeverFinishes() {
      // A poll interval as long as the gather budget leaves room for a single poll.
      var conversation = new DumpLogConversation(DESTINATION, Duration.ofMinutes(5));
      conversation.start();
      conversation.onAnswer(xml("support--dumplog-started.xml"), null);

      Step<DeviceLogArchive> last = null;
      for (var index = 0; index < 4; index++) {
        last = conversation.onAnswer(xml("support--dumplog-inprogress.xml"), null);
        if (last instanceof Step.Fail) {
          break;
        }
      }

      assertThat(last).isInstanceOf(Step.Fail.class);
      if (last instanceof Step.Fail<DeviceLogArchive> fail) {
        assertThat(fail.error().message()).contains("did not finish gathering");
      }
    }

    @Test
    @DisplayName("does not throw on a malformed body")
    void handlesMalformedBody() {
      var conversation = conversation();
      conversation.start();

      assertThatCode(() -> conversation.onAnswer(new CgiResponse(200, "text/xml", "<", null), null))
          .doesNotThrowAnyException();
    }
  }
}
