package com.jbraddockm.link.endpoint.pimsync;

import java.util.Optional;

/**
 * What each status code of {@code pimsync.cgi} means, established by reverse engineering (see
 * {@code analysis/endpoints/PimSyncCGI.md} §11).
 *
 * <p>bb-link forwards the device's own code and message unchanged and adds this explanation beside
 * them, so a client sees both exactly what the device said and what it means — the device's own
 * messages are terse (and for {@code 1007} there is none). The explanation is bb-link's words, not
 * the device's.
 */
enum PimStatusCode {
  SUCCESS(0, "The request succeeded."),
  INVALID_ARGUMENTS(1001, "The device rejected the request's arguments as missing or invalid."),
  OUT_OF_MEMORY(1002, "The device could not allocate memory to handle the request."),
  RESOURCE_MISSING(1003, "The device reported that the resource does not exist."),
  UPLOAD_TOO_LARGE(1004, "The image to upload exceeds the device's size limit."),
  NO_PERMISSIONS(1005, "The device reported that the request was not permitted."),
  INTERRUPTED(1006, "The request was interrupted before it finished."),
  TRANSPORT_ERROR(1007, "The device could not reach its local PIM backend (a transport error)."),
  SERVER_ERROR(1008, "The device's PIM backend returned an error (HTTP 400, 404, 413 or 5xx)."),
  NO_SESSION(1009, "The device could not obtain a PIM backend session for the request."),
  UNEXPECTED_RESPONSE(1010, "The device could not make sense of its PIM backend's reply."),
  UNAUTHORIZED(1013, "The device's PIM backend refused the request (HTTP 401 or 403)."),
  UNSUPPORTED(1015, "The device does not support this command or service."),
  NO_CHANGES(1016, "There was nothing to return or to commit; this is not an error."),
  UNKNOWN(-1, "The device reported an unspecified error.");

  private final int number;
  private final String meaning;

  PimStatusCode(int number, String meaning) {
    this.number = number;
    this.meaning = meaning;
  }

  /** The status number as it appears on the wire. */
  int number() {
    return number;
  }

  /** What the code means, in bb-link's words. */
  String meaning() {
    return meaning;
  }

  /**
   * Explains a status code for a client to show beside the device's own code and message.
   *
   * @param code the code the device returned, or {@code null} if it returned none
   * @return the meaning, or empty when there is no code or the code is not one this version knows
   */
  static Optional<String> explain(Integer code) {
    if (code == null) {
      return Optional.empty();
    }
    for (var known : values()) {
      if (known.number == code) {
        return Optional.of(known.meaning);
      }
    }
    return Optional.empty();
  }
}
