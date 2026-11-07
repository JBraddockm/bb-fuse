package com.jbraddockm.link.transport.smb;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ErrorCode;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.LinkErrorCode;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HexFormat;
import org.codelibs.jcifs.smb1.NtStatus;
import org.codelibs.jcifs.smb1.SmbAuthException;
import org.codelibs.jcifs.smb1.SmbException;

/**
 * Translates a jcifs failure into a {@link SmbTransferException} with a stable {@link ErrorCode}.
 * This is the only place the library's exceptions are read, so the rest of the daemon sees only the
 * neutral error. An authentication failure maps to {@link CoreErrorCode#AUTH_FAILED}; a missing
 * path and a wrong path kind map to the file-share codes; a server that cannot be reached maps to a
 * connection code; everything else is an I/O error.
 */
final class SmbErrorMapper {

  private SmbErrorMapper() {}

  /**
   * Maps a failure that arose while acting on a location.
   *
   * @param failure the jcifs or I/O failure
   * @param action what was being done, for the message, for example {@code "read"} or {@code
   *     "write"}
   * @param where the location being acted on
   * @return the translated failure
   */
  static SmbTransferException map(IOException failure, String action, SmbLocation where) {
    var place = where.share() + "/" + where.path();
    if (failure instanceof SmbAuthException) {
      return at(
          CoreErrorCode.AUTH_FAILED,
          "The device rejected the file-share credentials; they change with every login, so"
              + " reconnect and try again",
          where,
          null);
    }
    if (failure instanceof SmbException smb) {
      return fromStatus(smb, action, where, place);
    }
    return switch (failure) {
      case UnknownHostException _, ConnectException _, NoRouteToHostException _ ->
          at(
              CoreErrorCode.CONNECTION_FAILED,
              "The device's file share could not be reached",
              where,
              null);
      case SocketTimeoutException _, SocketException _ ->
          at(
              CoreErrorCode.CONNECTION_LOST,
              "The connection to the file share dropped",
              where,
              null);
      default ->
          at(
              CoreErrorCode.IO_ERROR,
              "Could not " + action + " " + place + ": " + describe(failure),
              where,
              null);
    };
  }

  private static SmbTransferException fromStatus(
      SmbException smb, String action, SmbLocation where, String place) {
    var status = smb.getNtStatus();
    return switch (status) {
      case NtStatus.NT_STATUS_LOGON_FAILURE ->
          at(
              CoreErrorCode.AUTH_FAILED,
              "The device rejected the file-share credentials",
              where,
              status);
      case NtStatus.NT_STATUS_NO_SUCH_FILE,
          NtStatus.NT_STATUS_OBJECT_NAME_NOT_FOUND,
          NtStatus.NT_STATUS_OBJECT_PATH_NOT_FOUND ->
          at(LinkErrorCode.NO_SUCH_PATH, place + " does not exist on the device", where, status);
      case NtStatus.NT_STATUS_NOT_A_DIRECTORY, NtStatus.NT_STATUS_FILE_IS_A_DIRECTORY ->
          at(
              LinkErrorCode.NOT_A_DIRECTORY,
              place + " is not the expected kind of path",
              where,
              status);
      case NtStatus.NT_STATUS_ACCESS_DENIED,
          NtStatus.NT_STATUS_SHARING_VIOLATION,
          NtStatus.NT_STATUS_CANNOT_DELETE,
          NtStatus.NT_STATUS_OBJECT_NAME_COLLISION ->
          at(
              CoreErrorCode.COMMAND_FAILED,
              "The device refused to " + action + " " + place,
              where,
              status);
      default ->
          at(
              CoreErrorCode.IO_ERROR,
              "Could not " + action + " " + place + ": " + describe(smb),
              where,
              status);
    };
  }

  private static SmbTransferException at(
      ErrorCode code, String message, SmbLocation where, Integer status) {
    var details =
        status == null
            ? Json.object("share", where.share(), "path", where.path())
            : Json.object(
                "share",
                where.share(),
                "path",
                where.path(),
                "ntStatus",
                "0x" + HexFormat.of().toHexDigits(status));
    return new SmbTransferException(code, message, details);
  }

  private static String describe(Throwable failure) {
    var message = failure.getLocalizedMessage();
    return message != null && !message.isBlank()
        ? message.strip()
        : failure.getClass().getSimpleName();
  }
}
