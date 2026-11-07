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

/** Tests for {@link DeleteDeviceFile}: removing one file on the share. */
@DisplayName("DeleteDeviceFile: remove one file")
class DeleteDeviceFileTest {

  private static final SmbLocation PATH =
      DeviceShares.locate("/accounts/1000/shared/documents/report.txt");

  @Test
  @DisplayName("deletes the file and reports the action")
  void deletes() {
    var store = new FakeFileStore();

    var outcome = new DeleteDeviceFile(PATH).run(store);

    assertThat(store.deleted).isEqualTo(PATH);
    assertThat(outcome).isInstanceOf(CommandOutcome.Succeeded.class);
    var json = ((CommandOutcome.Succeeded<FileChange>) outcome).result().toJson();
    assertThat(json)
        .containsEntry("action", "deleted")
        .containsEntry("path", "/accounts/1000/shared/documents/report.txt");
    assertThat(json.get("target")).isNull();
  }

  @Test
  @DisplayName("surfaces a refusal (e.g. a directory) as a failed outcome")
  void surfacesFailure() {
    var store =
        new FakeFileStore()
            .failingWith(
                new SmbTransferException(
                    LinkErrorCode.NOT_A_DIRECTORY, "is a directory", Map.of()));

    var outcome = new DeleteDeviceFile(PATH).run(store);

    assertThat(outcome).isInstanceOf(CommandOutcome.Failed.class);
    assertThat(((CommandOutcome.Failed<FileChange>) outcome).error().code())
        .isEqualTo(LinkErrorCode.NOT_A_DIRECTORY);
  }
}
