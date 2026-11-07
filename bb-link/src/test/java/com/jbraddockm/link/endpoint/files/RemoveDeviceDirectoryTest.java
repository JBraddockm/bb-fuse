package com.jbraddockm.link.endpoint.files;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.transport.smb.DeviceShares;
import com.jbraddockm.link.transport.smb.SmbLocation;
import com.jbraddockm.link.transport.smb.SmbTransferException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link RemoveDeviceDirectory}: removing an empty directory on the device share. */
@DisplayName("RemoveDeviceDirectory: an empty directory on the device")
class RemoveDeviceDirectoryTest {

  private static final SmbLocation PATH =
      DeviceShares.locate("/accounts/1000/shared/documents/empty");

  @Test
  @DisplayName("removes the directory and reports the action")
  void removes() {
    var store = new FakeFileStore();

    var outcome = new RemoveDeviceDirectory(PATH).run(store);

    assertThat(store.removedDirectory).isEqualTo(PATH);
    assertThat(outcome).isInstanceOf(CommandOutcome.Succeeded.class);
    assertThat(((CommandOutcome.Succeeded<FileChange>) outcome).result().toJson())
        .containsEntry("action", "removed")
        .containsEntry("path", "/accounts/1000/shared/documents/empty");
  }

  @Test
  @DisplayName("surfaces a non-empty directory as a failed outcome")
  void surfacesNotEmpty() {
    var store =
        new FakeFileStore()
            .failingWith(
                new SmbTransferException(LinkErrorCode.DIRECTORY_NOT_EMPTY, "not empty", Map.of()));

    var outcome = new RemoveDeviceDirectory(PATH).run(store);

    assertThat(outcome).isInstanceOf(CommandOutcome.Failed.class);
    assertThat(((CommandOutcome.Failed<FileChange>) outcome).error().code())
        .isEqualTo(LinkErrorCode.DIRECTORY_NOT_EMPTY);
  }
}
