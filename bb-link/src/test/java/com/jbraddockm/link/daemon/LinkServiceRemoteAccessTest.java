package com.jbraddockm.link.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.core.daemon.CommandSpec;
import com.jbraddockm.core.daemon.Reply;
import com.jbraddockm.core.daemon.RequestParams;
import com.jbraddockm.core.json.Json;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests host remote access in {@link LinkService}: the auto-start on connect ({@link
 * LinkService#startRemoteAccess}) and the {@code startRemoteAccess} / {@code stopRemoteAccess} /
 * {@code getRemoteAccessStatus} / {@code restartRemoteAccess} / {@code setRemoteAccess} verbs. The
 * real tunnel bind is a seam ({@link LinkService.RemoteAccessStarter}), so no device or ULA is
 * needed.
 */
@DisplayName("LinkService: host remote access")
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

  /** Runs a verb through the wired {@link CommandSpec} and captures its reply. */
  private static Captured run(
      LinkService service, String wireName, Map<String, Object> params, DeviceSession session) {
    var spec =
        service.commands().stream()
            .filter(candidate -> candidate.wireName().equals(wireName))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no such command: " + wireName));
    var out = new ByteArrayOutputStream();
    var reply = Reply.standalone(new PrintStream(out, true, StandardCharsets.UTF_8), 7);
    switch (spec) {
      case CommandSpec.SessionScoped<DeviceSession> scoped ->
          scoped.factory().apply(new RequestParams(params)).run(session, reply);
      case CommandSpec.Standalone<DeviceSession> standalone ->
          standalone.factory().apply(new RequestParams(params)).run(reply);
    }
    return parse(out.toString(StandardCharsets.UTF_8));
  }

  @SuppressWarnings("unchecked")
  private static Captured parse(String events) {
    Map<String, Object> data = Map.of();
    var ok = false;
    String error = null;
    for (var line : events.split("\n")) {
      if (line.isBlank()) {
        continue;
      }
      var event = (Map<String, Object>) Json.parse(line);
      switch (String.valueOf(event.get("type"))) {
        case "result" -> data = (Map<String, Object>) event.get("data");
        case "done" -> {
          ok = Boolean.TRUE.equals(event.get("ok"));
          if (event.get("error") instanceof Map<?, ?> failure) {
            error = String.valueOf(failure.get("message"));
          }
        }
        default -> {}
      }
    }
    return new Captured(ok, data, error);
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

  // --- The remote-access verbs (N6.5): start / stop / status / restart / set ---

  private LinkService.RemoteAccessStarter starterReturning(TunnelScopedServer server) {
    return (_, config, _) -> {
      starterCalled = true;
      startedWith.set(config);
      return server;
    };
  }

  private Optional<StoredRemoteAccess> stored(Path deviceFolder) throws IOException {
    return DeviceDirectory.at(deviceFolder).remoteAccess();
  }

  /** The result and final state of one verb, parsed from the reply stream. */
  private record Captured(boolean ok, Map<String, Object> data, String error) {}

  /** A server whose in-flight count and lifecycle calls a test can set and observe. */
  private static final class FakeServer implements TunnelScopedServer {
    private final InetSocketAddress address;
    private final int active;
    private boolean begun;
    private boolean forced;
    private boolean closed;

    FakeServer(InetSocketAddress address, int active) {
      this.address = address;
      this.active = active;
    }

    @Override
    public InetSocketAddress address() {
      return address;
    }

    @Override
    public int activeRequests() {
      return active;
    }

    @Override
    public void beginShutdown() {
      begun = true;
    }

    @Override
    public boolean awaitIdle(java.time.Duration timeout) {
      return active == 0;
    }

    @Override
    public void forceShutdown() {
      forced = true;
    }

    @Override
    public void close() {
      closed = true;
    }
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

  @Nested
  @DisplayName("startRemoteAccess")
  class Start {

    @Test
    @DisplayName("starts on the host ULA, persists enabled=true, and reports it running")
    void startsAndPersists() throws IOException {
      var folder = deviceFolderWith(new StoredRemoteAccess(false, null));
      var service = serviceWith(recordingStarter());
      var session = tunnelledSession(folder);

      var result = run(service, "startRemoteAccess", Map.of(), session);

      assertThat(result.ok()).isTrue();
      assertThat(result.data())
          .containsEntry("enabled", true)
          .containsEntry("running", true)
          .containsEntry("readOnly", false)
          .containsEntry("activeRequests", 0L)
          .containsEntry("folder", defaultFolder.toString())
          .containsEntry("address", "[fd00::host]:4487");
      assertThat(stored(folder).orElseThrow().enabled()).isTrue();
      session.close();
    }

    @Test
    @DisplayName("a given folder is served read-only and persisted")
    void servesAndPersistsTheGivenFolder() throws IOException {
      var custom = defaultFolder.resolveSibling("picked");
      var folder = deviceFolderWith(new StoredRemoteAccess(false, null));
      var service = serviceWith(recordingStarter());
      var session = tunnelledSession(folder);

      var result =
          run(
              service,
              "startRemoteAccess",
              Map.of("folder", custom.toString(), "readOnly", true),
              session);

      assertThat(startedWith.get().root()).isEqualTo(custom);
      assertThat(startedWith.get().readOnly()).isTrue();
      assertThat(result.data())
          .containsEntry("folder", custom.toString())
          .containsEntry("readOnly", true);
      assertThat(stored(folder).orElseThrow().folder()).isEqualTo(custom);
      session.close();
    }

    @Test
    @DisplayName("refuses a non-tunnelled session")
    void refusesWithoutATunnel() throws IOException {
      var folder = deviceFolderWith(new StoredRemoteAccess(false, null));
      var service = serviceWith(recordingStarter());
      var session = directSession(folder);

      var result = run(service, "startRemoteAccess", Map.of(), session);

      assertThat(result.ok()).isFalse();
      assertThat(result.error()).contains("tunnel");
      assertThat(starterCalled).isFalse();
      session.close();
    }

    @Test
    @DisplayName("is idempotent when already running: reports it, does not start a second server")
    void idempotentWhenRunning() throws IOException {
      var folder = deviceFolderWith(new StoredRemoteAccess(true, null));
      var service = serviceWith(recordingStarter());
      var session = tunnelledSession(folder);
      session.attachTunnelServer(new FakeServer(BOUND, 0), false);

      var result = run(service, "startRemoteAccess", Map.of(), session);

      assertThat(result.ok()).isTrue();
      assertThat(result.data()).containsEntry("running", true);
      assertThat(starterCalled).isFalse();
      session.close();
    }
  }

  @Nested
  @DisplayName("stopRemoteAccess")
  class Stop {

    @Test
    @DisplayName("an idle server stops at once and detaches")
    void idleStopsNow() throws IOException {
      var folder = deviceFolderWith(new StoredRemoteAccess(true, null));
      var service = serviceWith(recordingStarter());
      var session = tunnelledSession(folder);
      var server = new FakeServer(BOUND, 0);
      session.attachTunnelServer(server, false);

      var result = run(service, "stopRemoteAccess", Map.of(), session);

      assertThat(result.data()).containsEntry("state", "stopped").containsEntry("running", false);
      assertThat(server.closed).isTrue();
      assertThat(session.tunnelServer()).isEmpty();
      session.close();
    }

    @Test
    @DisplayName("a busy server begins draining and reports the in-flight count, staying attached")
    void busyDrains() throws IOException {
      var folder = deviceFolderWith(new StoredRemoteAccess(true, null));
      var service = serviceWith(recordingStarter());
      var session = tunnelledSession(folder);
      var server = new FakeServer(BOUND, 3);
      session.attachTunnelServer(server, false);

      var result = run(service, "stopRemoteAccess", Map.of(), session);

      assertThat(result.data())
          .containsEntry("state", "draining")
          .containsEntry("running", true)
          .containsEntry("activeRequests", 3L);
      assertThat(server.begun).isTrue();
      assertThat(server.forced).isFalse();
      assertThat(session.tunnelServer()).isPresent();
      session.close();
    }

    @Test
    @DisplayName("force stops a busy server now and detaches")
    void forceStops() throws IOException {
      var folder = deviceFolderWith(new StoredRemoteAccess(true, null));
      var service = serviceWith(recordingStarter());
      var session = tunnelledSession(folder);
      var server = new FakeServer(BOUND, 3);
      session.attachTunnelServer(server, false);

      var result = run(service, "stopRemoteAccess", Map.of("force", true), session);

      assertThat(result.data()).containsEntry("state", "forced").containsEntry("running", false);
      assertThat(server.forced).isTrue();
      assertThat(session.tunnelServer()).isEmpty();
      session.close();
    }

    @Test
    @DisplayName("disable persists enabled=false")
    void disablePersists() throws IOException {
      var folder = deviceFolderWith(new StoredRemoteAccess(true, null));
      var service = serviceWith(recordingStarter());
      var session = tunnelledSession(folder);
      session.attachTunnelServer(new FakeServer(BOUND, 0), false);

      run(service, "stopRemoteAccess", Map.of("disable", true), session);

      assertThat(stored(folder)).isEmpty(); // enabled=false, no folder → a default, cleared record
      session.close();
    }

    @Test
    @DisplayName("stopping when nothing runs reports notRunning")
    void stopsWhenNotRunning() throws IOException {
      var folder = deviceFolderWith(new StoredRemoteAccess(true, null));
      var service = serviceWith(recordingStarter());
      var session = tunnelledSession(folder);

      var result = run(service, "stopRemoteAccess", Map.of(), session);

      assertThat(result.data())
          .containsEntry("state", "notRunning")
          .containsEntry("running", false);
      session.close();
    }
  }

  @Nested
  @DisplayName("getRemoteAccessStatus")
  class Status {

    @Test
    @DisplayName("reports a running server's folder, read-only flag, in-flight count and address")
    void reportsRunning() throws IOException {
      var folder = deviceFolderWith(new StoredRemoteAccess(true, null));
      var service = serviceWith(recordingStarter());
      var session = tunnelledSession(folder);
      session.attachTunnelServer(new FakeServer(BOUND, 2), true);

      var result = run(service, "getRemoteAccessStatus", Map.of(), session);

      assertThat(result.data())
          .containsEntry("enabled", true)
          .containsEntry("running", true)
          .containsEntry("readOnly", true)
          .containsEntry("activeRequests", 2L)
          .containsEntry("folder", defaultFolder.toString())
          .containsEntry("address", "[fd00::host]:4487");
      session.close();
    }

    @Test
    @DisplayName("reports not running with the enabled flag and default folder when nothing runs")
    void reportsNotRunning() throws IOException {
      var folder = deviceFolderWith(new StoredRemoteAccess(false, null));
      var service = serviceWith(recordingStarter());
      var session = tunnelledSession(folder);

      var result = run(service, "getRemoteAccessStatus", Map.of(), session);

      assertThat(result.data())
          .containsEntry("enabled", false)
          .containsEntry("running", false)
          .containsEntry("readOnly", false)
          .containsEntry("folder", defaultFolder.toString());
      assertThat(result.data()).doesNotContainKey("address");
      session.close();
    }
  }

  @Nested
  @DisplayName("restartRemoteAccess")
  class Restart {

    @Test
    @DisplayName("stops the old server, starts on the new folder, and persists it")
    void repointsToTheNewFolder() throws IOException {
      var custom = defaultFolder.resolveSibling("moved");
      var folder = deviceFolderWith(new StoredRemoteAccess(true, null));
      var old = new FakeServer(BOUND, 0);
      var fresh = new FakeServer(new InetSocketAddress("fd00::host", 4487), 0);
      var service = serviceWith(starterReturning(fresh));
      var session = tunnelledSession(folder);
      session.attachTunnelServer(old, false);

      var result =
          run(service, "restartRemoteAccess", Map.of("folder", custom.toString()), session);

      assertThat(old.closed).isTrue();
      assertThat(startedWith.get().root()).isEqualTo(custom);
      assertThat(result.data())
          .containsEntry("running", true)
          .containsEntry("folder", custom.toString());
      assertThat(session.tunnelServer()).containsSame(fresh);
      assertThat(stored(folder).orElseThrow().folder()).isEqualTo(custom);
      session.close();
    }

    @Test
    @DisplayName("refuses a non-tunnelled session")
    void refusesWithoutATunnel() throws IOException {
      var folder = deviceFolderWith(new StoredRemoteAccess(true, null));
      var service = serviceWith(recordingStarter());
      var session = directSession(folder);

      var result = run(service, "restartRemoteAccess", Map.of(), session);

      assertThat(result.ok()).isFalse();
      assertThat(result.error()).contains("tunnel");
      session.close();
    }
  }

  @Nested
  @DisplayName("setRemoteAccess (standalone)")
  class Set {

    @Test
    @DisplayName("persists the enabled flag and folder for a device without a live session")
    void persistsTheSetting() throws IOException {
      var directory = DeviceDirectory.of(devices, "2C1E7583");
      var custom = defaultFolder.resolveSibling("set");
      var service = serviceWith(recordingStarter());

      var result =
          run(
              service,
              "setRemoteAccess",
              Map.of(
                  "devicesDirectory",
                  devices.toString(),
                  "pin",
                  "2C1E7583",
                  "enabled",
                  true,
                  "folder",
                  custom.toString()),
              null);

      assertThat(result.ok()).isTrue();
      assertThat(result.data())
          .containsEntry("enabled", true)
          .containsEntry("folder", custom.toString());
      assertThat(directory.remoteAccess().orElseThrow())
          .isEqualTo(new StoredRemoteAccess(true, custom));
    }
  }
}
