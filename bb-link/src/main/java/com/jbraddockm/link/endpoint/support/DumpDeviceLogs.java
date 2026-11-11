package com.jbraddockm.link.endpoint.support;

import com.jbraddockm.link.transport.cgi.Conversation;
import com.jbraddockm.link.transport.cgi.DeviceCommand;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Collects the device's diagnostic logs over {@code support.cgi}'s DumpLog facility and streams the
 * resulting {@code cso-logs-report.zip} to a file. One GET-driven conversation on one connection:
 * select the facility, poll while the device gathers the logs, then stream the archive.
 *
 * <p>The command changes nothing on the device, but it is not marked read-only: it drives a
 * cookie-stateful server state machine (and the device {@code unlink}s the archive once streamed),
 * so it must not be replayed from the start after a dropped connection.
 *
 * @param destination the file the archive is written to
 * @param pollInterval how long to wait between two questions about whether the archive is ready
 */
public record DumpDeviceLogs(Path destination, Duration pollInterval)
    implements DeviceCommand<DeviceLogArchive> {

  /** How often the device is asked whether the archive is ready. */
  static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(500);

  /**
   * Validates the command.
   *
   * @throws NullPointerException if a component is {@code null}
   * @throws IllegalArgumentException if the poll interval is negative
   */
  public DumpDeviceLogs {
    Objects.requireNonNull(destination, "destination");
    Objects.requireNonNull(pollInterval, "pollInterval");
    if (pollInterval.isNegative()) {
      throw new IllegalArgumentException("the poll interval cannot be negative");
    }
  }

  /**
   * Collects the logs into the given file, polling the device every 500 ms while it gathers them.
   *
   * @param destination the file the archive is written to
   */
  public DumpDeviceLogs(Path destination) {
    this(destination, DEFAULT_POLL_INTERVAL);
  }

  @Override
  public Conversation<DeviceLogArchive> conversation() {
    return new DumpLogConversation(destination, pollInterval);
  }

  @Override
  public boolean readOnly() {
    return false;
  }
}
