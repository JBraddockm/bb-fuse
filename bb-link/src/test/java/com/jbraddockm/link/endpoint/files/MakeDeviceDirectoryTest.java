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

/** Tests for {@link MakeDeviceDirectory}: creating a directory on the share. */
@DisplayName("MakeDeviceDirectory: create a directory")
class MakeDeviceDirectoryTest {

  private static final SmbLocation PATH =
      DeviceShares.locate("/accounts/1000/shared/documents/reports");

  @Test
  @DisplayName("creates the directory and reports the action")
  void creates() {
    var store = new FakeFileStore();

    var outcome = new MakeDeviceDirectory(PATH).run(store);

    assertThat(store.made).isEqualTo(PATH);
    assertThat(outcome).isInstanceOf(CommandOutcome.Succeeded.class);
    assertThat(((CommandOutcome.Succeeded<FileChange>) outcome).result().toJson())
        .containsEntry("action", "created")
        .containsEntry("path", "/accounts/1000/shared/documents/reports");
  }

  @Test
  @DisplayName("surfaces an already-existing directory as a failed outcome")
  void surfacesAlreadyExists() {
    var store =
        new FakeFileStore()
            .failingWith(
                new SmbTransferException(LinkErrorCode.ALREADY_EXISTS, "already exists", Map.of()));

    var outcome = new MakeDeviceDirectory(PATH).run(store);

    assertThat(outcome).isInstanceOf(CommandOutcome.Failed.class);
    assertThat(((CommandOutcome.Failed<FileChange>) outcome).error().code())
        .isEqualTo(LinkErrorCode.ALREADY_EXISTS);
  }
}
