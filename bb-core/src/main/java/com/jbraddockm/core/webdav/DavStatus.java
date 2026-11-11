package com.jbraddockm.core.webdav;

/**
 * The HTTP status codes RIM's rimdav uses, with their reason phrases. The set is the one the Blend
 * client maps (see {@code analysis/BLEND-client-implementation-PRD.md} §5) and the one the
 * file-share server emits.
 */
public enum DavStatus {
  OK(200, "OK"),
  CREATED(201, "Created"),
  NO_CONTENT(204, "No Content"),
  MULTI_STATUS(207, "Multi-Status"),
  BAD_REQUEST(400, "Bad Request"),
  UNAUTHORIZED(401, "Unauthorized"),
  FORBIDDEN(403, "Forbidden"),
  NOT_FOUND(404, "Not Found"),
  METHOD_NOT_ALLOWED(405, "Method Not Allowed"),
  CONFLICT(409, "Conflict"),
  PRECONDITION_FAILED(412, "Precondition Failed"),
  UNSUPPORTED_MEDIA_TYPE(415, "Unsupported Media Type"),
  LOCKED(423, "Locked"),
  INTERNAL_SERVER_ERROR(500, "Internal Server Error"),
  NOT_IMPLEMENTED(501, "Not Implemented"),
  BAD_GATEWAY(502, "Bad Gateway"),
  INSUFFICIENT_STORAGE(507, "Insufficient Storage");

  private final int code;
  private final String reason;

  DavStatus(int code, String reason) {
    this.code = code;
    this.reason = reason;
  }

  /**
   * Returns the numeric HTTP status code.
   *
   * @return the code, for example {@code 207}
   */
  public int code() {
    return code;
  }

  /**
   * Returns the reason phrase.
   *
   * @return the phrase, for example {@code Multi-Status}
   */
  public String reason() {
    return reason;
  }

  /**
   * Formats the status for a {@code DAV:status} element: {@code HTTP/1.1 <code> <reason>}.
   *
   * @return the status line text
   */
  public String line() {
    return "HTTP/1.1 " + code + " " + reason;
  }
}
