package com.jbraddockm.link.transport.login;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ErrorCode;
import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.transport.cgi.ConnectionPath;
import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.LinkedHashMap;
import java.util.Locale;
import javax.net.ssl.SSLException;

/** Maps failures to errors and decides what may be retried. */
public final class FailureClassifier {

  private FailureClassifier() {}

  public static ServiceError classify(Throwable failure) {
    return classify(failure, false);
  }

  /**
   * Maps a failure to an error.
   *
   * @param failure what went wrong
   * @param certificateOffered whether a client certificate was offered to the device; a failed
   *     handshake is then explained with that in mind
   */
  public static ServiceError classify(Throwable failure, boolean certificateOffered) {
    return classify(failure, certificateOffered, ConnectionPath.OTHER);
  }

  /**
   * Classifies a failure, naming the connection path where it helps.
   *
   * @param failure what went wrong
   * @param certificateOffered whether a client certificate was offered
   * @param path the path the session was reached over
   * @return the error
   */
  public static ServiceError classify(
      Throwable failure, boolean certificateOffered, ConnectionPath path) {
    return switch (failure) {
      case InvalidArgumentException invalid -> invalid.toServiceError();
      case PasswordRejectedException rejected when rejected.isLocked() ->
          new ServiceError(
              CoreErrorCode.AUTH_LOCKED,
              describe(rejected),
              Json.object(
                  "authRequired",
                  "password",
                  "locked",
                  true,
                  "failedAttempts",
                  rejected.failedAttempts()));
      case PasswordRejectedException rejected ->
          new ServiceError(
              CoreErrorCode.AUTH_FAILED,
              describe(rejected),
              Json.object(
                  "authRequired",
                  "password",
                  "failedAttempts",
                  rejected.failedAttempts(),
                  "retriesRemaining",
                  rejected.remainingAttempts(),
                  "maxAttempts",
                  rejected.failedAttempts() + rejected.remainingAttempts()));
      case InvalidPasswordException invalid ->
          new ServiceError(CoreErrorCode.AUTH_FAILED, describe(invalid));
      case PasswordRequiredException required ->
          new ServiceError(
              LinkErrorCode.PASSWORD_REQUIRED,
              describe(required),
              // certRefused is DERIVED: a certificate was offered yet the device still demanded a
              // password, so it did not honour the certificate. It names only that fact, never a
              // specific cause (expired pin, perimeter-password change, another host re-signing).
              Json.object("authRequired", "password", "certRefused", certificateOffered));
      case ClockOutOfSyncException clock ->
          new ServiceError(CoreErrorCode.CLOCK_OUT_OF_SYNC, describe(clock));
      case DeviceLoginError deviceError -> {
        var details = new LinkedHashMap<String, Object>();
        deviceError.identifier().ifPresent(id -> details.put("errorIdentifier", id));
        deviceError.description().ifPresent(text -> details.put("errorDescription", text));
        yield new ServiceError(CoreErrorCode.DEVICE_ERROR, describe(deviceError), details);
      }
      case UnusableLoginResponseException unusable ->
          new ServiceError(
              CoreErrorCode.DEVICE_ERROR,
              describe(unusable),
              Json.object("extra", unusable.extra()));
      case ConnectException _ -> connection(CoreErrorCode.CONNECTION_FAILED, "refused", failure);
      case HttpConnectTimeoutException _ ->
          connection(CoreErrorCode.CONNECTION_FAILED, "timeout", failure);
      case UnknownHostException _ ->
          connection(CoreErrorCode.CONNECTION_FAILED, "unknown_host", failure);
      case SSLException _ when certificateOffered ->
          new ServiceError(
              CoreErrorCode.CONNECTION_FAILED,
              "The TLS handshake failed while a client certificate was offered; the device may have"
                  + " refused it (original message: "
                  + describe(failure)
                  + ")",
              Json.object("cause", "tls", "certificate", true));
      case SSLException _ when path == ConnectionPath.TUNNEL ->
          new ServiceError(
              CoreErrorCode.CONNECTION_FAILED,
              "The TLS handshake failed; a tunnel connection needs the paired client certificate"
                  + " (original message: "
                  + describe(failure)
                  + ")",
              Json.object("cause", "tls"));
      case SSLException _ -> connection(CoreErrorCode.CONNECTION_FAILED, "tls", failure);
      case HttpTimeoutException _, SocketTimeoutException _ ->
          connection(CoreErrorCode.CONNECTION_LOST, "timeout", failure);
      case SocketException _ -> connection(CoreErrorCode.CONNECTION_LOST, "reset", failure);
      case MalformedURLException _ ->
          new ServiceError(CoreErrorCode.INVALID_ARGUMENT, describe(failure));
      case IOException io when isConnectionDropped(io) ->
          connection(CoreErrorCode.CONNECTION_LOST, "reset", failure);
      case IOException _ -> new ServiceError(CoreErrorCode.IO_ERROR, describe(failure));
      case IllegalArgumentException _ ->
          new ServiceError(CoreErrorCode.INVALID_ARGUMENT, describe(failure));
      default -> ServiceError.internal(failure);
    };
  }

  /**
   * Tells whether a failure looks like a dead pooled connection, which is worth one retry, as
   * opposed to a device that cannot be reached at all.
   */
  public static boolean isStaleConnection(Throwable failure) {
    return switch (failure) {
      case HttpConnectTimeoutException _, ConnectException _, UnknownHostException _ -> false;
      case SocketException _ -> true;
      case IOException io when isConnectionDropped(io) -> true;
      default -> false;
    };
  }

  /**
   * Whether an {@code IOException} is the device closing the connection with no, or only a partial,
   * answer. {@code java.net.http} reports this as a plain {@code IOException} (or an {@link
   * EOFException}) with no dedicated type, so it is recognised by what it says — the same event
   * that the Apache stack reported as {@code NoHttpResponseException}.
   */
  private static boolean isConnectionDropped(Throwable failure) {
    if (failure instanceof EOFException) {
      return true;
    }
    var message = failure.getMessage();
    if (message == null) {
      return false;
    }
    var lower = message.toLowerCase(Locale.ROOT);
    return lower.contains("received no bytes")
        || lower.contains("connection reset")
        || lower.contains("broken pipe")
        || lower.contains("end of stream")
        || lower.contains("eof")
        || lower.contains("goaway")
        || lower.contains("connection closed")
        || lower.contains("stream closed");
  }

  /**
   * Tells whether the connection stays usable after a failure. A bad argument does not damage it;
   * anything else may have left a half-finished exchange behind.
   */
  public static boolean leavesSessionUsable(Throwable failure) {
    return failure instanceof IllegalArgumentException;
  }

  private static ServiceError connection(ErrorCode code, String cause, Throwable failure) {
    return new ServiceError(code, describe(failure), Json.object("cause", cause));
  }

  private static String describe(Throwable failure) {
    var message = failure.getLocalizedMessage();
    return message != null && !message.isBlank()
        ? message.strip()
        : failure.getClass().getSimpleName();
  }
}
