package com.jbraddockm.link.endpoint.files;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.transport.smb.DeviceShares;
import com.jbraddockm.link.transport.smb.SmbEntry;
import com.jbraddockm.link.transport.smb.SmbLocation;
import com.jbraddockm.link.transport.smb.SmbTransferException;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link SetDeviceFileTime}: setting a file's last-modified time. */
@DisplayName("SetDeviceFileTime: set a file's modified time")
class SetDeviceFileTimeTest {

  private static final SmbLocation PATH =
      DeviceShares.locate("/accounts/1000/shared/documents/report.txt");
  private static final Instant WHEN = Instant.parse("2026-10-02T10:15:30Z");

  @Test
  @DisplayName("sets the time and returns the file's metadata")
  void sets() {
    var store = new FakeFileStore().withStat(PATH, new SmbEntry("report.txt", false, 117, WHEN));

    var outcome = new SetDeviceFileTime(PATH, WHEN).run(store);

    assertThat(store.timeSetOn).isEqualTo(PATH);
    assertThat(store.timeSet).isEqualTo(WHEN);
    assertThat(outcome).isInstanceOf(CommandOutcome.Succeeded.class);
    assertThat(((CommandOutcome.Succeeded<DeviceFileInfo>) outcome).result().toJson())
        .containsEntry("path", "/accounts/1000/shared/documents/report.txt")
        .containsEntry("modifiedAt", "2026-10-02T10:15:30Z");
  }

  @Test
  @DisplayName("surfaces a missing file as a failed outcome")
  void surfacesMissing() {
    var store =
        new FakeFileStore()
            .failingWith(new SmbTransferException(LinkErrorCode.NO_SUCH_PATH, "gone", Map.of()));

    var outcome = new SetDeviceFileTime(PATH, WHEN).run(store);

    assertThat(outcome).isInstanceOf(CommandOutcome.Failed.class);
    assertThat(((CommandOutcome.Failed<DeviceFileInfo>) outcome).error().code())
        .isEqualTo(LinkErrorCode.NO_SUCH_PATH);
  }
}
