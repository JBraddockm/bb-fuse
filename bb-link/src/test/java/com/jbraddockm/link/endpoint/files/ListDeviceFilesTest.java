package com.jbraddockm.link.endpoint.files;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.transport.smb.DeviceShares;
import com.jbraddockm.link.transport.smb.SmbEntry;
import com.jbraddockm.link.transport.smb.SmbLocation;
import com.jbraddockm.link.transport.smb.SmbTransferException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link ListDeviceFiles}: a directory listing on the device share. */
@DisplayName("ListDeviceFiles: a directory listing")
class ListDeviceFilesTest {

  private static final SmbLocation DIRECTORY =
      DeviceShares.locate("/accounts/1000/shared/documents");
  private static final Instant WHEN = Instant.parse("2026-10-02T10:15:30Z");

  @Test
  @DisplayName("lists entries with their device paths, omitting a directory's size")
  @SuppressWarnings("unchecked")
  void listsEntries() {
    var store =
        new FakeFileStore()
            .withListing(
                DIRECTORY,
                List.of(
                    new SmbEntry("report.txt", false, 117, WHEN),
                    new SmbEntry("photos", true, 0, WHEN)));

    var data = succeed(new ListDeviceFiles(DIRECTORY).run(store));

    assertThat(data).containsEntry("path", "/accounts/1000/shared/documents");
    var entries = (List<Map<String, Object>>) data.get("entries");
    assertThat(entries).hasSize(2);
    assertThat(entries.get(0))
        .containsEntry("name", "report.txt")
        .containsEntry("path", "/accounts/1000/shared/documents/report.txt")
        .containsEntry("type", "file")
        .containsEntry("size", 117L)
        .containsEntry("modifiedAt", "2026-10-02T10:15:30Z");
    assertThat(entries.get(1)).containsEntry("name", "photos").containsEntry("type", "directory");
    assertThat(entries.get(1).get("size")).isNull();
  }

  @Test
  @DisplayName("surfaces a transfer failure as a failed outcome")
  void surfacesFailure() {
    var store =
        new FakeFileStore()
            .failingWith(new SmbTransferException(LinkErrorCode.NO_SUCH_PATH, "gone", Map.of()));

    var outcome = new ListDeviceFiles(DIRECTORY).run(store);

    assertThat(outcome).isInstanceOf(CommandOutcome.Failed.class);
    assertThat(((CommandOutcome.Failed<DeviceFileListing>) outcome).error().code())
        .isEqualTo(LinkErrorCode.NO_SUCH_PATH);
  }

  private static Map<String, Object> succeed(CommandOutcome<DeviceFileListing> outcome) {
    assertThat(outcome).isInstanceOf(CommandOutcome.Succeeded.class);
    return ((CommandOutcome.Succeeded<DeviceFileListing>) outcome).result().toJson();
  }
}
