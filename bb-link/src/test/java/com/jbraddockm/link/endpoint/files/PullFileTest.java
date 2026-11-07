package com.jbraddockm.link.endpoint.files;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.transport.smb.DeviceShares;
import com.jbraddockm.link.transport.smb.SmbLocation;
import com.jbraddockm.link.transport.smb.SmbTransferException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link PullFile}: copying a file from the device share to the host. */
@DisplayName("PullFile: device to host")
class PullFileTest {

  private static final SmbLocation SOURCE =
      DeviceShares.locate("/accounts/1000/shared/documents/report.txt");
  private static final Path DESTINATION = Path.of("/tmp/report.txt");

  @Test
  @DisplayName("reads the device file to the destination and reports the byte count")
  void readsFile() {
    var store = new FakeFileStore().returning(4096);

    var outcome = new PullFile(SOURCE, DESTINATION, false).run(store);

    assertThat(store.pulledSource).isEqualTo(SOURCE);
    assertThat(store.pulledDestination).isEqualTo(DESTINATION);
    assertThat(store.pullResume).isFalse();
    assertThat(outcome).isInstanceOf(CommandOutcome.Succeeded.class);
    assertThat(((CommandOutcome.Succeeded<FileTransfer>) outcome).result().toJson())
        .containsEntry("device", "/accounts/1000/shared/documents/report.txt")
        .containsEntry("local", "/tmp/report.txt")
        .containsEntry("bytes", 4096L);
  }

  @Test
  @DisplayName("surfaces a missing source as a failed outcome")
  void surfacesMissingSource() {
    var store =
        new FakeFileStore()
            .failingWith(new SmbTransferException(LinkErrorCode.NO_SUCH_PATH, "gone", Map.of()));

    var outcome = new PullFile(SOURCE, DESTINATION, false).run(store);

    assertThat(outcome).isInstanceOf(CommandOutcome.Failed.class);
    assertThat(((CommandOutcome.Failed<FileTransfer>) outcome).error().code())
        .isEqualTo(LinkErrorCode.NO_SUCH_PATH);
  }

  @Test
  @DisplayName("passes the resume flag through to the store")
  void passesResume() {
    var store = new FakeFileStore().returning(4096);

    new PullFile(SOURCE, DESTINATION, true).run(store);

    assertThat(store.pullResume).isTrue();
  }
}
