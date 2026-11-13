package com.jbraddockm.link.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.core.daemon.Reply;
import com.jbraddockm.link.endpoint.pairing.CertificateRetrieval;
import com.jbraddockm.link.endpoint.pairing.Pairing;
import com.jbraddockm.link.endpoint.pairing.store.DeviceDirectory;
import com.jbraddockm.link.endpoint.pairing.store.StoredRemoteAccess;
import com.jbraddockm.link.remoteaccess.RemoteAccessConfig;
import com.jbraddockm.link.remoteaccess.RemoteAccessFolders;
import com.jbraddockm.link.transport.cgi.Authentication;
import com.jbraddockm.link.transport.cgi.DeviceTarget;
import com.jbraddockm.link.transport.cgi.ExchangeObserver;
import com.jbraddockm.link.transport.cgi.TransportScheme;
import com.jbraddockm.link.transport.session.DeviceSession;
import com.jbraddockm.link.transport.session.TunnelScopedServer;
import com.jbraddockm.link.transport.tunnel.TunnelBinding;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests {@link LinkService#startRemoteAccess}: how an established session auto-starts host remote
 * access from the device's {@code device.json}. The real tunnel bind is a seam ({@link
 * LinkService.RemoteAccessStarter}), so no device or ULA is needed.
 */
@DisplayName("LinkService: auto-starting remote access on connect")
class LinkServiceRemoteAccessTest {

  private static final InetSocketAddress BOUND = new InetSocketAddress("fd00::host", 4487);
  private final AtomicReference<RemoteAccessConfig> startedWith = new AtomicReference<>();
  private final AtomicReference<String> startedPeer = new AtomicReference<>();
  @TempDir private Path devices;
  @TempDir private Path defaultFolder;
  private boolean starterCalled;

  /** A reply that discards its events. */
  private static Reply silentReply() {
    return Reply.standalone(
        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8), 1);
  }

  private static TunnelScopedServer cannedServer() {
    return new TunnelScopedServer() {
      @Override
      public InetSocketAddress address() {
        return BOUND;
      }

      @Override
      public int activeRequests() {
        return 0;
      }

      @Override
      public void beginShutdown() {}

      @Override
      public boolean awaitIdle(java.time.Duration timeout) {
        return true;
      }

      @Override
      public void forceShutdown() {}

      @Override
      public void close() {}
    };
  }

  private static TunnelBinding binding() {
    return new TunnelBinding() {
      @Override
      public String ula() {
        return "fd00::1";
      }

      @Override
      public String hostUla() {
        return "fd00::host";
      }

      @Override
      public String smbHost() {
        return "169.254.2.1";
      }

      @Override
      public boolean isDown() {
        return false;
      }

      @Override
      public void close() {}
    };
  }

  private LinkService serviceWith(LinkService.RemoteAccessStarter starter) {
    return new LinkService(
        TransportScheme.HTTP,
        _ -> {},
        ExchangeObserver.NONE,
        new Pairing(),
        new CertificateRetrieval(),
        new RemoteAccessFolders(defaultFolder),
        starter);
  }

  /** The seam, recording what it is asked to start and handing back a canned bound server. */
  private LinkService.RemoteAccessStarter recordingStarter() {
    return (_, config, peerId) -> {
      starterCalled = true;
      startedWith.set(config);
      startedPeer.set(peerId);
      return cannedServer();
    };
  }

  private Path deviceFolderWith(StoredRemoteAccess setting) throws IOException {
    var directory = DeviceDirectory.of(devices, "2A3B4C5D");
    directory.writeRemoteAccess(setting);
    return directory.directory();
  }

  private DeviceSession tunnelledSession(Path deviceFolder) throws IOException {
    var session =
        DeviceSession.forTarget(
            new DeviceTarget(
                "10.0.0.5", 8443, TransportScheme.HTTP, new Authentication.Password("x")),
            ExchangeObserver.NONE,
            null);
    session.useTunnel(LinkServiceRemoteAccessTest::binding);
    session.establishTunnel(CommandListener.SILENT);
    session.rememberDevice(deviceFolder, "PEER");
    return session;
  }

  private DeviceSession directSession(Path deviceFolder) {
    var session =
        DeviceSession.forTarget(
            new DeviceTarget(
                "169.254.0.2", 8443, TransportScheme.HTTP, new Authentication.Password("x")),
            ExchangeObserver.NONE,
            null);
    session.rememberDevice(deviceFolder, "PEER");
    return session;
  }

  @Nested
  @DisplayName("when the device enables it and the session is tunnelled")
  class EnabledAndTunnelled {

    @Test
    @DisplayName("starts remote access on the host ULA, attaches it, and reports it running")
    void startsAndReports() throws IOException {
      var folder = deviceFolderWith(new StoredRemoteAccess(true, null));
      var service = serviceWith(recordingStarter());
      var session = tunnelledSession(folder);

      var result = service.startRemoteAccess(session, silentReply());

      assertThat(starterCalled).isTrue();
      assertThat(startedWith.get().root())
          .isEqualTo(defaultFolder); // no per-device folder → default
      assertThat(startedWith.get().readOnly()).isFalse();
      assertThat(startedPeer.get()).isEqualTo("PEER");
      assertThat(session.tunnelServer()).isPresent();
      assertThat(result)
          .containsEntry("enabled", true)
          .containsEntry("running", true)
          .containsEntry("folder", defaultFolder.toString())
          .containsEntry("address", "[fd00::host]:4487");
      session.close();
    }

    @Test
    @DisplayName("serves the device's own folder when it names one")
    void usesThePerDeviceFolder() throws IOException {
      var custom = defaultFolder.resolveSibling("custom");
      var folder = deviceFolderWith(new StoredRemoteAccess(true, custom));
      var service = serviceWith(recordingStarter());
      var session = tunnelledSession(folder);

      var result = service.startRemoteAccess(session, silentReply());

      assertThat(startedWith.get().root()).isEqualTo(custom);
      assertThat(result).containsEntry("folder", custom.toString());
      session.close();
    }

    @Test
    @DisplayName("a failing start never fails the connect: reported enabled but not running")
    void failingStartIsReportedNotRunning() throws IOException {
      var folder = deviceFolderWith(new StoredRemoteAccess(true, null));
      var service =
          serviceWith((_, _, _) -> {
            throw new IOException("port in use");
          });
      var session = tunnelledSession(folder);

      var result = service.startRemoteAccess(session, silentReply());

      assertThat(session.tunnelServer()).isEmpty();
      assertThat(result)
          .containsEntry("enabled", true)
          .containsEntry("running", false)
          .containsEntry("address", null);
      session.close();
    }
  }

  @Nested
  @DisplayName("when it should not start")
  class DoesNotStart {

    @Test
    @DisplayName("a non-tunnelled session is reported enabled but not running, and nothing starts")
    void nonTunnelledDoesNotStart() throws IOException {
      var folder = deviceFolderWith(new StoredRemoteAccess(true, null));
      var service = serviceWith(recordingStarter());
      var session = directSession(folder);

      var result = service.startRemoteAccess(session, silentReply());

      assertThat(starterCalled).isFalse();
      assertThat(session.tunnelServer()).isEmpty();
      assertThat(result).containsEntry("enabled", true).containsEntry("running", false);
      session.close();
    }

    @Test
    @DisplayName("a device that has not enabled it reports disabled and the default folder")
    void disabledReportsTheDefault() throws IOException {
      var service = serviceWith(recordingStarter());
      var session = tunnelledSession(devices.resolve("2A3B4C5D")); // no device.json written

      var result = service.startRemoteAccess(session, silentReply());

      assertThat(starterCalled).isFalse();
      assertThat(session.tunnelServer()).isEmpty();
      assertThat(result)
          .containsEntry("enabled", false)
          .containsEntry("running", false)
          .containsEntry("folder", defaultFolder.toString());
      session.close();
    }
  }
}
