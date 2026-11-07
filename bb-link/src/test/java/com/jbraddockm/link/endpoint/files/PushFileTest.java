package com.jbraddockm.link.endpoint.files;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.link.transport.smb.DeviceShares;
import com.jbraddockm.link.transport.smb.SmbLocation;
import com.jbraddockm.link.transport.smb.SmbTransferException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link PushFile}: copying a host file onto the device share. */
@DisplayName("PushFile: host to device")
class PushFileTest {

  private static final Path SOURCE = Path.of("/tmp/report.txt");
  private static final SmbLocation DESTINATION =
      DeviceShares.locate("/accounts/1000/shared/documents/report.txt");

  @Test
  @DisplayName("sends the local file to the destination and reports the byte count")
  void sendsFile() {
    var store = new FakeFileStore().returning(2048);

    var outcome = new PushFile(SOURCE, DESTINATION).run(store);

    assertThat(store.pushedSource).isEqualTo(SOURCE);
    assertThat(store.pushedDestination).isEqualTo(DESTINATION);
    assertThat(outcome).isInstanceOf(CommandOutcome.Succeeded.class);
    assertThat(((CommandOutcome.Succeeded<FileTransfer>) outcome).result().toJson())
        .containsEntry("device", "/accounts/1000/shared/documents/report.txt")
        .containsEntry("local", "/tmp/report.txt")
        .containsEntry("bytes", 2048L);
  }

  @Test
  @DisplayName("surfaces a transfer failure as a failed outcome")
  void surfacesFailure() {
    var store =
        new FakeFileStore()
            .failingWith(
                new SmbTransferException(CoreErrorCode.CONNECTION_LOST, "dropped", Map.of()));

    var outcome = new PushFile(SOURCE, DESTINATION).run(store);

    assertThat(outcome).isInstanceOf(CommandOutcome.Failed.class);
    assertThat(((CommandOutcome.Failed<FileTransfer>) outcome).error().code())
        .isEqualTo(CoreErrorCode.CONNECTION_LOST);
  }
}
