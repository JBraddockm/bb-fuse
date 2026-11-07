package com.jbraddockm.link.transport.smb;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ErrorCode;
import com.jbraddockm.link.LinkErrorCode;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import org.codelibs.jcifs.smb1.NtStatus;
import org.codelibs.jcifs.smb1.SmbException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link SmbErrorMapper}: turning a jcifs failure into a stable error code. */
@DisplayName("SmbErrorMapper: jcifs failure to error code")
class SmbErrorMapperTest {

  private static final SmbLocation WHERE = SmbLocation.root("media").child("documents");

  private static ErrorCode codeOf(IOException failure) {
    return SmbErrorMapper.map(failure, "read", WHERE).code();
  }

  @Nested
  @DisplayName("from an NT status")
  class FromNtStatus {

    @Test
    @DisplayName("a logon failure is an authentication failure")
    void logonFailure() {
      assertThat(codeOf(new SmbException(NtStatus.NT_STATUS_LOGON_FAILURE, false)))
          .isEqualTo(CoreErrorCode.AUTH_FAILED);
    }

    @ParameterizedTest(name = "status 0x{0} is a missing path")
    @ValueSource(
        ints = {
          NtStatus.NT_STATUS_NO_SUCH_FILE,
          NtStatus.NT_STATUS_OBJECT_NAME_NOT_FOUND,
          NtStatus.NT_STATUS_OBJECT_PATH_NOT_FOUND
        })
    @DisplayName("a missing file or path is a missing path")
    void missingPath(int status) {
      assertThat(codeOf(new SmbException(status, false))).isEqualTo(LinkErrorCode.NO_SUCH_PATH);
    }

    @ParameterizedTest(name = "status 0x{0} is a wrong path kind")
    @ValueSource(
        ints = {NtStatus.NT_STATUS_NOT_A_DIRECTORY, NtStatus.NT_STATUS_FILE_IS_A_DIRECTORY})
    @DisplayName("a wrong path kind is reported as not a directory")
    void wrongKind(int status) {
      assertThat(codeOf(new SmbException(status, false))).isEqualTo(LinkErrorCode.NOT_A_DIRECTORY);
    }

    @ParameterizedTest(name = "status 0x{0} is a refusal")
    @ValueSource(
        ints = {
          NtStatus.NT_STATUS_ACCESS_DENIED,
          NtStatus.NT_STATUS_SHARING_VIOLATION,
          NtStatus.NT_STATUS_CANNOT_DELETE
        })
    @DisplayName("a refusal by the device is a failed command")
    void refused(int status) {
      assertThat(codeOf(new SmbException(status, false))).isEqualTo(CoreErrorCode.COMMAND_FAILED);
    }

    @Test
    @DisplayName("an existing-name collision is already-exists")
    void collision() {
      assertThat(codeOf(new SmbException(NtStatus.NT_STATUS_OBJECT_NAME_COLLISION, false)))
          .isEqualTo(LinkErrorCode.ALREADY_EXISTS);
    }
  }

  @Nested
  @DisplayName("from a network failure")
  class FromNetwork {

    @Test
    @DisplayName("a refused or unreachable host is a connection failure")
    void unreachable() {
      assertThat(codeOf(new ConnectException())).isEqualTo(CoreErrorCode.CONNECTION_FAILED);
      assertThat(codeOf(new UnknownHostException())).isEqualTo(CoreErrorCode.CONNECTION_FAILED);
    }

    @Test
    @DisplayName("a timeout is a lost connection")
    void timeout() {
      assertThat(codeOf(new SocketTimeoutException())).isEqualTo(CoreErrorCode.CONNECTION_LOST);
    }

    @Test
    @DisplayName("any other I/O failure is an I/O error")
    void otherIo() {
      assertThat(codeOf(new IOException("boom"))).isEqualTo(CoreErrorCode.IO_ERROR);
    }
  }

  @Nested
  @DisplayName("error details")
  class Details {

    @Test
    @DisplayName("name the share and path")
    void nameShareAndPath() {
      var error =
          SmbErrorMapper.map(
                  new SmbException(NtStatus.NT_STATUS_NO_SUCH_FILE, false), "read", WHERE)
              .toServiceError();

      assertThat(error.details())
          .containsEntry("share", "media")
          .containsEntry("path", "documents");
    }
  }
}
