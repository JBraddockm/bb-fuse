package com.jbraddockm.link.endpoint.support;

import java.util.Optional;

/**
 * The {@code <Status>} values the DumpLog facility reports while it gathers the archive, in the
 * order they occur: {@code Started} on the first request, {@code InProgress} while {@code quiplog}
 * runs, and {@code Ready} (with a {@code <Size>}) when the archive can be streamed.
 */
enum DumpLogStatus {
  STARTED("Started"),
  IN_PROGRESS("InProgress"),
  READY("Ready");

  private final String wireName;

  DumpLogStatus(String wireName) {
    this.wireName = wireName;
  }

  /**
   * Recognises a status value, ignoring case.
   *
   * @param value the {@code <Status>} text
   * @return the status, or empty if this version does not know it
   */
  static Optional<DumpLogStatus> of(String value) {
    for (var status : values()) {
      if (status.wireName.equalsIgnoreCase(value)) {
        return Optional.of(status);
      }
    }
    return Optional.empty();
  }
}
