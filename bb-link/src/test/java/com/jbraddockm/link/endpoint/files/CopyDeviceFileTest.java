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

/** Tests for {@link CopyDeviceFile}: a server-side copy on the device. */
@DisplayName("CopyDeviceFile: device-side copy")
class CopyDeviceFileTest {

  private static final SmbLocation FROM = DeviceShares.locate("/accounts/1000/shared/music/a.mp3");
  private static final SmbLocation TO = DeviceShares.locate("/accounts/1000/shared/music/copy.mp3");

  @Test
  @DisplayName("copies the file and reports both ends")
  void copies() {
    var store = new FakeFileStore();

    var outcome = new CopyDeviceFile(FROM, TO).run(store);

    assertThat(store.copiedFrom).isEqualTo(FROM);
    assertThat(store.copiedTo).isEqualTo(TO);
    assertThat(outcome).isInstanceOf(CommandOutcome.Succeeded.class);
    assertThat(((CommandOutcome.Succeeded<FileChange>) outcome).result().toJson())
        .containsEntry("action", "copied")
        .containsEntry("path", "/accounts/1000/shared/music/a.mp3")
        .containsEntry("target", "/accounts/1000/shared/music/copy.mp3");
  }

  @Test
  @DisplayName("surfaces an existing destination as a failed outcome")
  void surfacesAlreadyExists() {
    var store =
        new FakeFileStore()
            .failingWith(
                new SmbTransferException(LinkErrorCode.ALREADY_EXISTS, "already exists", Map.of()));

    var outcome = new CopyDeviceFile(FROM, TO).run(store);

    assertThat(outcome).isInstanceOf(CommandOutcome.Failed.class);
    assertThat(((CommandOutcome.Failed<FileChange>) outcome).error().code())
        .isEqualTo(LinkErrorCode.ALREADY_EXISTS);
  }
}
