package com.jbraddockm.link.transport.smb;

import com.jbraddockm.core.error.ErrorCode;
import com.jbraddockm.core.error.ServiceError;
import java.util.Map;
import java.util.Objects;

/**
 * A file-share operation that failed, carrying the protocol error code to report it with. The jcifs
 * library's own exceptions are translated to this one by {@link SmbErrorMapper}, so callers outside
 * this package never see them.
 */
public final class SmbTransferException extends Exception {

  private final transient ErrorCode code;
  private final transient Map<String, Object> details;

  /**
   * Creates a transfer failure.
   *
   * @param code the stable error code to report
   * @param message a human-readable English description
   * @param details additional code-specific data, possibly empty
   */
  public SmbTransferException(ErrorCode code, String message, Map<String, Object> details) {
    super(message);
    this.code = Objects.requireNonNull(code, "code");
    this.details = Map.copyOf(details);
  }

  /**
   * Returns the error code to branch on.
   *
   * @return the code
   */
  public ErrorCode code() {
    return code;
  }

  /**
   * Renders this failure as a protocol error.
   *
   * @return the service error carrying the code, message and details
   */
  public ServiceError toServiceError() {
    return new ServiceError(code, getMessage(), details);
  }
}
