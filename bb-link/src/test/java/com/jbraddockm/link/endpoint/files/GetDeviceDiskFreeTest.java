package com.jbraddockm.link.endpoint.files;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.link.transport.smb.DeviceShares;
import com.jbraddockm.link.transport.smb.SmbLocation;
import com.jbraddockm.link.transport.smb.SmbTransferException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link GetDeviceDiskFree}: reading the share's free space. */
@DisplayName("GetDeviceDiskFree: free space of the share")
class GetDeviceDiskFreeTest {

  private static final SmbLocation PATH = DeviceShares.locate("/accounts/1000/shared");

  @Test
  @DisplayName("reports the free space in bytes")
  void reports() {
    var store = new FakeFileStore().withFreeSpace(23_622_320_128L);

    var outcome = new GetDeviceDiskFree(PATH).run(store);

    assertThat(store.freeSpaceOf).isEqualTo(PATH);
    assertThat(outcome).isInstanceOf(CommandOutcome.Succeeded.class);
    assertThat(((CommandOutcome.Succeeded<DiskFree>) outcome).result().toJson())
        .containsEntry("path", "/accounts/1000/shared")
        .containsEntry("freeBytes", 23_622_320_128L);
  }

  @Test
  @DisplayName("surfaces an unreachable share as a failed outcome")
  void surfacesFailure() {
    var store =
        new FakeFileStore()
            .failingWith(
                new SmbTransferException(CoreErrorCode.CONNECTION_FAILED, "unreachable", Map.of()));

    var outcome = new GetDeviceDiskFree(PATH).run(store);

    assertThat(outcome).isInstanceOf(CommandOutcome.Failed.class);
    assertThat(((CommandOutcome.Failed<DiskFree>) outcome).error().code())
        .isEqualTo(CoreErrorCode.CONNECTION_FAILED);
  }
}
