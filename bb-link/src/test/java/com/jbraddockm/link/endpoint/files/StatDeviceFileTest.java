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

/** Tests for {@link StatDeviceFile}: the metadata of one path on the device share. */
@DisplayName("StatDeviceFile: metadata of one path")
class StatDeviceFileTest {

  private static final SmbLocation PATH =
      DeviceShares.locate("/accounts/1000/shared/documents/report.txt");
  private static final Instant WHEN = Instant.parse("2026-10-02T10:15:30Z");

  @Test
  @DisplayName("reads a file's metadata")
  void readsMetadata() {
    var store = new FakeFileStore().withStat(PATH, new SmbEntry("report.txt", false, 117, WHEN));

    var outcome = new StatDeviceFile(PATH).run(store);

    assertThat(outcome).isInstanceOf(CommandOutcome.Succeeded.class);
    var data = ((CommandOutcome.Succeeded<DeviceFileInfo>) outcome).result().toJson();
    assertThat(data)
        .containsEntry("path", "/accounts/1000/shared/documents/report.txt")
        .containsEntry("type", "file")
        .containsEntry("size", 117L)
        .containsEntry("modifiedAt", "2026-10-02T10:15:30Z");
  }

  @Test
  @DisplayName("surfaces a missing path as a failed outcome")
  void surfacesMissingPath() {
    var store =
        new FakeFileStore()
            .failingWith(
                new SmbTransferException(LinkErrorCode.NO_SUCH_PATH, "not found", Map.of()));

    var outcome = new StatDeviceFile(PATH).run(store);

    assertThat(outcome).isInstanceOf(CommandOutcome.Failed.class);
    assertThat(((CommandOutcome.Failed<DeviceFileInfo>) outcome).error().code())
        .isEqualTo(LinkErrorCode.NO_SUCH_PATH);
  }
}
