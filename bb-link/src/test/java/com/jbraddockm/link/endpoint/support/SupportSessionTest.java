package com.jbraddockm.link.endpoint.support;

import static com.jbraddockm.link.testing.Outcomes.failed;
import static com.jbraddockm.link.testing.Outcomes.succeeded;
import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.link.testing.FakeDevice;
import com.jbraddockm.link.testing.Resources;
import com.jbraddockm.link.transport.cgi.Authentication;
import com.jbraddockm.link.transport.cgi.DeviceTarget;
import com.jbraddockm.link.transport.cgi.TransportScheme;
import com.jbraddockm.link.transport.session.DeviceSession;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Collecting device logs through a session against the fake device")
class SupportSessionTest {

  @TempDir private Path folder;
  private FakeDevice device;
  private DeviceSession session;
  private byte[] archive;

  private DumpDeviceLogs dumpTo(Path destination) {
    // A 1 ms poll keeps the gather loop quick in the test.
    return new DumpDeviceLogs(destination, Duration.ofMillis(1));
  }

  @BeforeEach
  void start() throws Exception {
    archive = Resources.bytes(SupportSessionTest.class, "support--archive.zip");
    device = new FakeDevice("secret");
    device.supportArchive = archive;
    device.supportPreparePolls = 2;
    session =
        DeviceSession.forTarget(
            new DeviceTarget(
                InetAddress.getLoopbackAddress().getHostAddress(),
                device.port(),
                TransportScheme.HTTP,
                new Authentication.Password("secret")));
  }

  @AfterEach
  void close() {
    session.close();
    device.close();
  }

  @Test
  @DisplayName("streams the archive to the file, byte for byte, and reports its size")
  void collectsTheArchive() throws Exception {
    var out = folder.resolve("cso-logs-report.zip");

    var result = succeeded(session.execute(dumpTo(out), CommandListener.SILENT));

    assertThat(result.file()).isEqualTo(out);
    assertThat(result.bytes()).isEqualTo(archive.length);
    assertThat(Files.readAllBytes(out)).isEqualTo(archive);
    assertThat(device.supportRequests).anyMatch(line -> line.contains("select"));
    assertThat(device.supportRequests).anyMatch(line -> line.contains("ready"));
    assertThat(device.supportRequests).anyMatch(line -> line.startsWith("stream"));
  }

  @Test
  @DisplayName("fails when the device rejects the facility")
  void failsOnBadFacility() {
    device.supportBadFacility = true;
    var out = folder.resolve("cso-logs-report.zip");

    var failure = failed(session.execute(dumpTo(out), CommandListener.SILENT));

    assertThat(failure.error().message()).contains("Bad facility");
    assertThat(Files.exists(out)).isFalse();
  }
}
