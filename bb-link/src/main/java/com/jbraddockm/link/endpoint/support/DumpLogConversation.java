package com.jbraddockm.link.endpoint.support;

import com.jbraddockm.core.command.Progress;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.core.xml.MalformedXmlException;
import com.jbraddockm.core.xml.XmlElement;
import com.jbraddockm.core.xml.XmlParser;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.transport.cgi.CgiResponse;
import com.jbraddockm.link.transport.cgi.Conversation;
import com.jbraddockm.link.transport.cgi.Endpoint;
import com.jbraddockm.link.transport.cgi.QueryParameter;
import com.jbraddockm.link.transport.cgi.ResponseEnvelope;
import com.jbraddockm.link.transport.cgi.ResponseFailures;
import com.jbraddockm.link.transport.cgi.Step;
import com.jbraddockm.link.transport.cgi.StepEffect;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/// The conversation of a device-log collection over {@code support.cgi}'s DumpLog facility. The
/// whole lifecycle is driven by repeating the same GET `?facility=dumplog`:
///
/// ```
/// GET ?facility=dumplog        → <DumpLog><Status>Started</Status>      (facility selected, gather forked)
/// GET ?facility=dumplog (poll) → <DumpLog><Status>InProgress</Status>   (quiplog running)
/// GET ?facility=dumplog (poll) → <DumpLog><Status>Ready</Status><Size>N (gather complete)
/// GET ?facility=dumplog        → application/octet-stream  cso-logs-report.zip   (streamed to the file)
/// ```
///
/// The device decides per response whether it answers with the status XML or the archive stream;
/// the transport streams an octet-stream answer to the file and leaves XML in the body. The
/// conversation finishes when it sees the streamed file ({@link CgiResponse#downloaded()}).
///
/// The DumpLog error envelope puts `<Status>Error</Status>` and `<Error>` as direct children of
/// `<RimTabletResponse>` (not nested under `<DumpLog>`), so {@link ResponseEnvelope} — which keeps
/// only the first child — cannot see the `<Error>` sibling. The conversation therefore parses the
/// raw body itself.
final class DumpLogConversation implements Conversation<DeviceLogArchive> {

  /** The lowercase facility selector value the CGI matches to enter the DumpLog state machine. */
  private static final String DUMPLOG_FACILITY = "dumplog";

  /** The archive the device produces, named in progress reports and logs. */
  private static final String ARCHIVE = "cso-logs-report.zip";

  /** How long the device may stay silent while it gathers or streams. */
  private static final Duration SLOW_STEP = Duration.ofMinutes(5);

  /** How long the device may take to gather the logs before it is given up. */
  private static final Duration LONGEST_GATHER = Duration.ofMinutes(5);

  private final Path destination;
  private final Duration pollInterval;
  private final long maximumPolls;
  private long polls;

  DumpLogConversation(Path destination, Duration pollInterval) {
    this.destination = destination;
    this.pollInterval = pollInterval;
    this.maximumPolls =
        Math.max(1, LONGEST_GATHER.toMillis() / Math.max(1, pollInterval.toMillis()));
  }

  private CgiRequest select(long expectedLength) {
    return CgiRequest.StreamingGet.to(
            Endpoint.SUPPORT, destination, SupportPhase.GATHERING, ARCHIVE)
        .withText(QueryParameter.FACILITY, DUMPLOG_FACILITY)
        .withLength(expectedLength)
        .describedAs("Collecting the device logs")
        .waitingUpTo(SLOW_STEP);
  }

  @Override
  public Step<DeviceLogArchive> start() {
    // No progress effect here: the first event the client sees is the request's own description
    // ("Collecting the device logs"), the way backup announces itself before it reports progress.
    // Progress begins once the gather is under way (see pollAgain / stream).
    return new Step.Send<>(select(0));
  }

  @Override
  public Step<DeviceLogArchive> onAnswer(CgiResponse response, ResponseEnvelope envelope) {
    if (response.downloaded() != null) {
      return new Step.Finish<>(
          new DeviceLogArchive(response.downloaded().file(), response.downloaded().bytes()));
    }

    XmlElement root;
    try {
      root = XmlParser.parse(response.body());
    } catch (MalformedXmlException _) {
      return new Step.Fail<>(ResponseFailures.notXml(response));
    }

    var reported = reportedError(root);
    if (reported.isPresent()) {
      return new Step.Fail<>(reported.get());
    }

    var status = root.at("DumpLog").flatMap(dumpLog -> dumpLog.childText("Status")).orElse("");
    return DumpLogStatus.of(status)
        .map(
            dumpLogStatus ->
                switch (dumpLogStatus) {
                  case STARTED, IN_PROGRESS -> pollAgain();
                  case READY -> stream(root);
                })
        .orElseGet(
            () ->
                new Step.Fail<>(
                    new ServiceError(
                        LinkErrorCode.UNEXPECTED_RESPONSE,
                        "The device reported a DumpLog status that this version does not know",
                        Json.object("status", status))));
  }

  private Step<DeviceLogArchive> stream(XmlElement root) {
    var size =
        root.at("DumpLog")
            .flatMap(dumpLog -> dumpLog.childText("Size"))
            .flatMap(DumpLogConversation::parseLong)
            .orElse(0L);
    return new Step.Send<>(select(size), List.of(report(SupportPhase.DOWNLOADING)));
  }

  private Step<DeviceLogArchive> pollAgain() {
    polls++;
    if (polls > maximumPolls) {
      return new Step.Fail<>(
          new ServiceError(
              CoreErrorCode.COMMAND_FAILED,
              "The device did not finish gathering the logs in time",
              Json.object("waitedMinutes", LONGEST_GATHER.toMinutes())));
    }
    return new Step.Send<>(
        select(0), List.of(report(SupportPhase.GATHERING), new StepEffect.Pause(pollInterval)));
  }

  /**
   * Reads a device error from the raw root ({@code <Status>Error</Status>} plus {@code <Error>}).
   */
  private static Optional<ServiceError> reportedError(XmlElement root) {
    if (!root.childText("Status").orElse("").equalsIgnoreCase("Error")) {
      return Optional.empty();
    }
    var identifier =
        root.textAt("Error", "ErrorIdentifier").flatMap(DumpLogConversation::parseInt).orElse(null);
    var description = root.textAt("Error", "ErrorDescription").orElse(null);
    return Optional.of(
        ResponseFailures.deviceError(description, identifier, Json.object("facility", "dumplog")));
  }

  private StepEffect.Report report(SupportPhase phase) {
    // Byte-level percentage during the download comes from the transport's download observer.
    return new StepEffect.Report(new Progress(0, phase, null, null, ARCHIVE));
  }

  private static Optional<Long> parseLong(String text) {
    try {
      return Optional.of(Long.parseLong(text.strip()));
    } catch (NumberFormatException _) {
      return Optional.empty();
    }
  }

  private static Optional<Integer> parseInt(String text) {
    try {
      return Optional.of(Integer.valueOf(text.strip()));
    } catch (NumberFormatException _) {
      return Optional.empty();
    }
  }
}
