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

/** Tests for {@link MoveDeviceFile}: renaming or moving a path on the share. */
@DisplayName("MoveDeviceFile: rename or move a path")
class MoveDeviceFileTest {

  private static final SmbLocation FROM =
      DeviceShares.locate("/accounts/1000/shared/documents/old.txt");
  private static final SmbLocation TO =
      DeviceShares.locate("/accounts/1000/shared/documents/new.txt");

  @Test
  @DisplayName("moves the path and reports both ends")
  void moves() {
    var store = new FakeFileStore();

    var outcome = new MoveDeviceFile(FROM, TO).run(store);

    assertThat(store.movedFrom).isEqualTo(FROM);
    assertThat(store.movedTo).isEqualTo(TO);
    assertThat(outcome).isInstanceOf(CommandOutcome.Succeeded.class);
    assertThat(((CommandOutcome.Succeeded<FileChange>) outcome).result().toJson())
        .containsEntry("action", "moved")
        .containsEntry("path", "/accounts/1000/shared/documents/old.txt")
        .containsEntry("target", "/accounts/1000/shared/documents/new.txt");
  }

  @Test
  @DisplayName("surfaces an existing destination as a failed outcome")
  void surfacesAlreadyExists() {
    var store =
        new FakeFileStore()
            .failingWith(
                new SmbTransferException(LinkErrorCode.ALREADY_EXISTS, "already exists", Map.of()));

    var outcome = new MoveDeviceFile(FROM, TO).run(store);

    assertThat(outcome).isInstanceOf(CommandOutcome.Failed.class);
    assertThat(((CommandOutcome.Failed<FileChange>) outcome).error().code())
        .isEqualTo(LinkErrorCode.ALREADY_EXISTS);
  }
}
