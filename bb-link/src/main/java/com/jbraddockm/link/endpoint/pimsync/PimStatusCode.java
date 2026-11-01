package com.jbraddockm.link.endpoint.pimsync;

import java.util.Optional;

/**
 * What each status code of {@code pimsync.cgi} means, established by reverse engineering (see
 * {@code analysis/endpoints/PimSyncCGI.md} §11), and which codes are a refusal.
 *
 * <p>bb-link forwards the device's own code and message unchanged and adds this explanation beside
 * them, so a client sees both exactly what the device said and what it means — the device's own
 * messages are terse (and for {@code 1007} there is none). The explanation is bb-link's words, not
 * the device's. The {@code rejection} codes (1001, 1013, 1015) are the ones that end a command with
 * {@code COMMAND_FAILED}; every other code is a result the client judges for itself.
 */
public enum PimStatusCode {
  SUCCESS(0, false, "The request succeeded."),
  INVALID_ARGUMENTS(
      1001, true, "The device rejected the request's arguments as missing or invalid."),
  OUT_OF_MEMORY(1002, false, "The device could not allocate memory to handle the request."),
  RESOURCE_MISSING(1003, false, "The device reported that the resource does not exist."),
  UPLOAD_TOO_LARGE(1004, false, "The image to upload exceeds the device's size limit."),
  NO_PERMISSIONS(1005, false, "The device reported that the request was not permitted."),
  INTERRUPTED(1006, false, "The request was interrupted before it finished."),
  TRANSPORT_ERROR(
      1007, false, "The device could not reach its local PIM backend (a transport error)."),
  SERVER_ERROR(
      1008, false, "The device's PIM backend returned an error (HTTP 400, 404, 413 or 5xx)."),
  NO_SESSION(1009, false, "The device could not obtain a PIM backend session for the request."),
  UNEXPECTED_RESPONSE(1010, false, "The device could not make sense of its PIM backend's reply."),
  UNAUTHORIZED(1013, true, "The device's PIM backend refused the request (HTTP 401 or 403)."),
  UNSUPPORTED(1015, true, "The device does not support this command or service."),
  NO_CHANGES(1016, false, "There was nothing to return or to commit; this is not an error."),
  UNKNOWN(-1, false, "The device reported an unspecified error.");

  private final int number;
  private final boolean rejection;
  private final String meaning;

  PimStatusCode(int number, boolean rejection, String meaning) {
    this.number = number;
    this.rejection = rejection;
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
  public static Optional<String> explain(Integer code) {
    return find(code).map(known -> known.meaning);
  }

  /**
   * Tells whether a code is one of the refusals that end a command with {@code COMMAND_FAILED}.
   *
   * @param code the code the device returned, or {@code null}
   * @return {@code true} for 1001, 1013 or 1015; {@code false} otherwise, including an unknown code
   */
  static boolean isRejection(Integer code) {
    return find(code).map(known -> known.rejection).orElse(false);
  }

  private static Optional<PimStatusCode> find(Integer code) {
    if (code == null) {
      return Optional.empty();
    }
    for (var known : values()) {
      if (known.number == code) {
        return Optional.of(known);
      }
    }
    return Optional.empty();
  }
}
