package com.jbraddockm.link.daemon;

import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.core.command.LogLevel;
import com.jbraddockm.core.command.NoData;
import com.jbraddockm.core.daemon.CommandSpec;
import com.jbraddockm.core.daemon.Establishment;
import com.jbraddockm.core.daemon.Reply;
import com.jbraddockm.core.daemon.RequestParams;
import com.jbraddockm.core.daemon.Service;
import com.jbraddockm.core.daemon.ServiceInfo;
import com.jbraddockm.core.daemon.SessionCommand;
import com.jbraddockm.core.daemon.StandaloneCommand;
import com.jbraddockm.core.discovery.DeviceCharacteristics;
import com.jbraddockm.core.discovery.DeviceScanner;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.core.tunnel.TunnelBrokerClient;
import com.jbraddockm.link.endpoint.characteristics.GetDeviceCharacteristics;
import com.jbraddockm.link.endpoint.characteristics.UsbDiscovery;
import com.jbraddockm.link.endpoint.discovery.FriendlyNameAdvertiser;
import com.jbraddockm.link.endpoint.discovery.PairedPinResolver;
import com.jbraddockm.link.endpoint.discovery.Scanners;
import com.jbraddockm.link.endpoint.pairing.CertificateRetrieval;
import com.jbraddockm.link.endpoint.pairing.ClientNames;
import com.jbraddockm.link.endpoint.pairing.Pairing;
import com.jbraddockm.link.endpoint.pairing.store.DeviceDirectory;
import com.jbraddockm.link.endpoint.pairing.store.StoredPairing;
import com.jbraddockm.link.endpoint.pairing.store.StoredRemoteAccess;
import com.jbraddockm.link.endpoint.properties.DeviceProperties;
import com.jbraddockm.link.endpoint.properties.GetDeviceProperties;
import com.jbraddockm.link.remoteaccess.RemoteAccess;
import com.jbraddockm.link.remoteaccess.RemoteAccessConfig;
import com.jbraddockm.link.remoteaccess.RemoteAccessFolders;
import com.jbraddockm.link.transport.cgi.Authentication;
import com.jbraddockm.link.transport.cgi.ConnectionPath;
import com.jbraddockm.link.transport.cgi.DeviceTarget;
import com.jbraddockm.link.transport.cgi.ExchangeObserver;
import com.jbraddockm.link.transport.cgi.Step;
import com.jbraddockm.link.transport.cgi.TransportScheme;
import com.jbraddockm.link.transport.login.LoginOnly;
import com.jbraddockm.link.transport.login.SmbCredentials;
import com.jbraddockm.link.transport.session.DeviceSession;
import com.jbraddockm.link.transport.session.TunnelScopedServer;
import com.jbraddockm.link.transport.tunnel.BrokerTunnelConnector;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/** The bb-link service: what the shared daemon server runs. */
public final class LinkService implements Service<DeviceSession> {

  /** The version reported in {@code hello}. */
  public static final String VERSION = "0.1.0";

  private static final String START_REMOTE_ACCESS = "startRemoteAccess";
  private static final String STOP_REMOTE_ACCESS = "stopRemoteAccess";
  private static final String GET_REMOTE_ACCESS_STATUS = "getRemoteAccessStatus";
  private static final String RESTART_REMOTE_ACCESS = "restartRemoteAccess";
  private static final String SET_REMOTE_ACCESS = "setRemoteAccess";

  private final TransportScheme scheme;
  private final DeviceScanner scanner;
  private final ExchangeObserver observer;
  private final Pairing pairing;
  private final CertificateRetrieval retrieval;
  private final RemoteAccessFolders remoteAccessFolders;
  private final RemoteAccessStarter remoteAccessStarter;

  /**
   * Creates the production service: HTTPS, and the original tool's detector.
   *
   * @param observer told about every exchange with a device, for example to record a trace
   */
  public LinkService(ExchangeObserver observer) {
    this(
        TransportScheme.HTTPS,
        Scanners.all(new PairedPinResolver(devicesDirectory())),
        observer,
        new Pairing(),
        new CertificateRetrieval());
  }

  /** Creates a service with its collaborators replaced, which tests use. */
  LinkService(
      TransportScheme scheme,
      DeviceScanner scanner,
      ExchangeObserver observer,
      Pairing pairing,
      CertificateRetrieval retrieval) {
    this(
        scheme,
        scanner,
        observer,
        pairing,
        retrieval,
        RemoteAccessFolders.withHomeDefault(),
        RemoteAccess::onTunnel);
  }

  /** Creates a service with the remote-access collaborators replaced too, which tests use. */
  LinkService(
      TransportScheme scheme,
      DeviceScanner scanner,
      ExchangeObserver observer,
      Pairing pairing,
      CertificateRetrieval retrieval,
      RemoteAccessFolders remoteAccessFolders,
      RemoteAccessStarter remoteAccessStarter) {
    this.scheme = scheme;
    this.scanner = scanner;
    this.observer = observer;
    this.pairing = pairing;
    this.retrieval = retrieval;
    this.remoteAccessFolders = remoteAccessFolders;
    this.remoteAccessStarter = remoteAccessStarter;
  }

  /**
   * The devices directory the Wi-Fi discovery PIN match reads its paired PINs from: {@code
   * BB_LINK_DEVICES} when set (the {@code bblink} script points it at the active {@code devices/}),
   * otherwise {@code devices/} beside the daemon.
   */
  private static Path devicesDirectory() {
    var configured = System.getenv("BB_LINK_DEVICES");
    return Path.of(configured == null || configured.isBlank() ? "devices" : configured);
  }

  /** Whether a connect asks for the device to be read; it does unless it says {@code false}. */
  private static boolean describesDevice(RequestParams params) {
    return params.flag("deviceInfo", true);
  }

  /** The result of a connection; the device is {@code null} when nothing of it was read. */
  private static Map<String, Object> described(
      DeviceSession session, DeviceSummary summary, Map<String, Object> remoteAccess) {
    return Json.object(
        "host", session.target().host(),
        "port", session.target().port(),
        "authentication", session.target().authentication().type().wireName(),
        // Whether this host holds a broker tunnel to the device — true only for a tunnelled Wi-Fi
        // connect, false for USB/direct. Distinct from the device's own bp2p flag in `device`.
        "tunnel", session.hasTunnel(),
        "device", summary != null ? summary.toJson() : null,
        "smb", session.smbCredentials().map(SmbCredentials::toJson).orElse(null),
        "remoteAccess", remoteAccess);
  }

  private static DeviceCharacteristics readCharacteristics(DeviceSession session, Reply reply) {
    if (session.connectionPath() == ConnectionPath.USB) {
      // Over USB the :8443 management listener does not serve discovery.cgi — it is on the
      // unauthenticated :80 listener — so reading it over the session drops the connection and
      // forces a needless re-login. Read it from :80 directly instead.
      return characteristicsOverHttp(session.target().host(), reply);
    }
    return switch (session.execute(new GetDeviceCharacteristics(), reply)) {
      case CommandOutcome.Succeeded<DeviceCharacteristics>(var characteristics, _) ->
          characteristics;
      case CommandOutcome.Failed<DeviceCharacteristics>(var error, _, _) -> {
        reply.onLog(LogLevel.WARN, "Could not read the device characteristics: " + error.message());
        yield null;
      }
    };
  }

  private static DeviceCharacteristics characteristicsOverHttp(String host, Reply reply) {
    reply.onLog(LogLevel.INFO, "Reading the device characteristics");
    try {
      return switch (UsbDiscovery.read(host)) {
        case Step.Finish<DeviceCharacteristics>(var characteristics) -> characteristics;
        case Step.Fail<DeviceCharacteristics>(var error, var ignored) -> {
          reply.onLog(
              LogLevel.WARN, "Could not read the device characteristics: " + error.message());
          yield null;
        }
        case Step.Send<DeviceCharacteristics> ignored -> null;
      };
    } catch (IOException failure) {
      reply.onLog(
          LogLevel.WARN,
          "Could not read the device characteristics from port 80: " + failure.getMessage());
      return null;
    }
  }

  /**
   * A Wi-Fi LAN (or otherwise routable) address is reached through a tunnel; USB and a ULA are not.
   */
  private static boolean needsTunnel(ConnectionPath path) {
    return path == ConnectionPath.LAN || path == ConnectionPath.OTHER;
  }

  /**
   * The SMB host to propose to the broker on a tunnel connect, in order of precedence: an explicit
   * {@code smbHost}, then {@code stableSmb} (169.254.1.1), then the device's pinned address from
   * {@code device.json} beside the tunnel certificate, then {@code null} (the broker
   * auto-allocates).
   */
  static String proposedSmbHost(RequestParams params, Path tunnelCertificate) {
    return params
        .optionalString("smbHost")
        .orElseGet(
            () -> params.flag("stableSmb") ? "169.254.1.1" : pinnedSmbHost(tunnelCertificate));
  }

  /**
   * The device's pinned SMB host from {@code device.json} beside the tunnel certificate, or {@code
   * null} when there is no certificate, no folder, no pin, or the record cannot be read. Only read
   * here — a connect never writes it, so a broker counter never changes the pin.
   */
  private static String pinnedSmbHost(Path tunnelCertificate) {
    if (tunnelCertificate == null) {
      return null;
    }
    var deviceFolder = tunnelCertificate.getParent();
    if (deviceFolder == null) {
      return null;
    }
    try {
      return DeviceDirectory.at(deviceFolder).pinnedSmbHost().orElse(null);
    } catch (IOException unreadable) {
      return null; // a device.json we cannot read must never block a connect
    }
  }

  /**
   * The client certificate a certificate login offers, or {@code null} for a password or
   * no-password login. The {@code device.json} beside it holds the identifier the device folds to
   * match our Friendly advert.
   */
  private static Path clientCertificate(Authentication authentication) {
    return switch (authentication) {
      case Authentication.Certificate certificate -> certificate.certificate().certificateFile();
      case Authentication.CertificateAndPassword certificate ->
          certificate.certificate().certificateFile();
      case Authentication.NoPassword _, Authentication.Password _ -> null;
    };
  }

  /**
   * Starts the friendly-name advert for an explicit paired {@code deviceId} and attaches it to the
   * session. Used right after a pairing, when the device is adding this computer and so is most
   * likely to be browsing for our Friendly service. Best-effort — it never throws.
   *
   * @param session the session the advert lives with; it is withdrawn when the session closes
   * @param deviceId the identifier the device stored for this computer (which it folds to match us)
   */
  static void advertiseFriendlyName(DeviceSession session, String deviceId) {
    if (deviceId == null || deviceId.isBlank()) {
      return;
    }
    FriendlyNameAdvertiser.tryStart(ClientNames.localWorkstation(), deviceId)
        .ifPresent(session::attach);
  }

  /**
   * The identifier the device stored for this computer, read from the {@code device.json} beside
   * the certificate the connect uses (the tunnel certificate, or the client certificate of the
   * login): the {@code deviceId} the pairing gave itself, which the device folds to match our
   * Friendly advert. Empty when there is no certificate or no readable pairing beside it.
   *
   * @param pairingCertificate the certificate path the connect uses, or {@code null}
   * @return the identifier, or empty
   */
  static Optional<String> pairedDeviceId(Path pairingCertificate) {
    if (pairingCertificate == null) {
      return Optional.empty();
    }
    var folder = pairingCertificate.getParent();
    if (folder == null) {
      return Optional.empty();
    }
    try {
      return DeviceDirectory.at(folder).pairing().map(StoredPairing::deviceId);
    } catch (IOException | RuntimeException noPairing) {
      return Optional.empty();
    }
  }

  /** The device's remote-access setting, or empty when none is stored or it cannot be read. */
  private static Optional<StoredRemoteAccess> storedRemoteAccess(Path deviceFolder) {
    try {
      return DeviceDirectory.at(deviceFolder).remoteAccess();
    } catch (IOException unreadable) {
      return Optional.empty();
    }
  }

  /** The {@code remoteAccess} object of the connect result. */
  private static Map<String, Object> remoteAccessStatus(
      boolean enabled, Path folder, boolean running, InetSocketAddress address) {
    return Json.object(
        "enabled",
        enabled,
        "folder",
        folder == null ? null : folder.toString(),
        "running",
        running,
        "address",
        addressText(address));
  }

  /** The bind address as {@code [host]:port}, or {@code null}. */
  private static String addressText(InetSocketAddress address) {
    return address == null ? null : "[" + address.getHostString() + "]:" + address.getPort();
  }

  private static String messageOf(Throwable failure) {
    var message = failure.getMessage();
    return message == null ? failure.getClass().getSimpleName() : message;
  }

  /** The folder a start/restart serves: the request's own, else the device's stored folder. */
  private static Path effectiveFolder(DeviceSession session, Path explicitFolder) {
    return explicitFolder != null
        ? explicitFolder
        : storedRemoteAccess(session).map(StoredRemoteAccess::folder).orElse(null);
  }

  /** The device's remote-access setting read from the session's device folder, or empty. */
  private static Optional<StoredRemoteAccess> storedRemoteAccess(DeviceSession session) {
    return session.deviceFolder().flatMap(LinkService::storedRemoteAccess);
  }

  /**
   * Persists the {@code remoteAccess} intent for the session's device — {@code enabled}, and the
   * folder when one is given (otherwise the stored folder is kept). Best-effort: a device folder
   * that cannot be written is logged, never fatal to the command whose live effect already took
   * hold.
   */
  private static void persist(
      DeviceSession session, boolean enabled, Path folderOverride, Reply reply) {
    var deviceFolder = session.deviceFolder().orElse(null);
    if (deviceFolder == null) {
      return;
    }
    try {
      var directory = DeviceDirectory.at(deviceFolder);
      var folder =
          folderOverride != null
              ? folderOverride
              : directory.remoteAccess().map(StoredRemoteAccess::folder).orElse(null);
      directory.writeRemoteAccess(new StoredRemoteAccess(enabled, folder));
    } catch (IOException | RuntimeException failure) {
      reply.onLog(
          LogLevel.WARN, "Could not persist the remote-access setting: " + messageOf(failure));
    }
  }

  /** The error a remote-access command fails with on a session that holds no Wi-Fi tunnel. */
  private static ServiceError noTunnel(String verb) {
    return new ServiceError(
        CoreErrorCode.COMMAND_FAILED,
        verb
            + " needs a Wi-Fi tunnel to the device; it is not available over USB or a direct"
            + " connection.");
  }

  @Override
  public ServiceInfo info() {
    return new ServiceInfo("bb-link", VERSION);
  }

  @Override
  public List<CommandSpec<DeviceSession>> commands() {
    return Stream.concat(
            Arrays.stream(LinkMethod.values())
                .map(method -> method.spec(scanner, pairing, retrieval)),
            remoteAccessCommands().stream())
        .toList();
  }

  /**
   * The remote-access verbs, which live here rather than in {@link LinkMethod} because they drive
   * the session's own tunnel-scoped server through this service's collaborators ({@link
   * #remoteAccessFolders}, {@link #remoteAccessStarter}) instead of sending a command to the
   * device. They need no CGI endpoint, so they stay out of {@link EndpointReachability}; the tunnel
   * requirement is enforced in each command.
   */
  private List<CommandSpec<DeviceSession>> remoteAccessCommands() {
    return List.of(
        new CommandSpec.SessionScoped<>(START_REMOTE_ACCESS, this::startRemoteAccessCommand),
        new CommandSpec.SessionScoped<>(STOP_REMOTE_ACCESS, this::stopRemoteAccessCommand),
        new CommandSpec.SessionScoped<>(GET_REMOTE_ACCESS_STATUS, _ -> remoteAccessStatusCommand()),
        new CommandSpec.SessionScoped<>(RESTART_REMOTE_ACCESS, this::restartRemoteAccessCommand),
        new CommandSpec.Standalone<>(SET_REMOTE_ACCESS, this::setRemoteAccessCommand));
  }

  /**
   * Opens a session for a {@code connect}, from the request's host, port and authentication. A
   * Wi-Fi LAN (or other routable) address that carries a {@code tunnelCertificate} is run over a
   * broker-managed tunnel automatically, so the caller reaches the device over its ULA without ever
   * handling it; USB, a direct ULA, and an identity-less connect (a password or login-only connect)
   * stay direct and reach only {@code login.cgi}. The tunnel identity is passed to the daemon by
   * path, so its binary embeds no device cert. The friendly name is advertised over mDNS on every
   * connect — the device learns it however the command channel is reached — and a login with no
   * pairing beside its certificate advertises nothing.
   *
   * @param params the {@code connect} parameters: {@code host}, {@code port}, {@code
   *     authentication}, and the optional tunnel identity, SMB and broker options
   * @return the session, with a tunnel attached when the address and identity call for one
   */
  @Override
  public DeviceSession open(RequestParams params) {
    var target =
        new DeviceTarget(
            params.requireString("host"),
            params.integer("port", DeviceTarget.DEFAULT_PORT, 1, 65_535),
            scheme,
            AuthenticationReader.read(params));
    describesDevice(params); // refuses a malformed option while no session exists yet
    var session =
        DeviceSession.forTarget(target, observer, params.optionalString("smbHost").orElse(null));
    // The pairing that names us to the device is beside the certificate the connect uses: the
    // login's
    // client certificate, or the tunnel certificate on a tunnel connect (set below).
    var pairingCertificate = clientCertificate(target.authentication());
    var tunnelCert = params.optionalString("tunnelCertificate").map(Path::of).orElse(null);
    if (needsTunnel(target.path()) && tunnelCert != null) {
      var socket =
          params
              .optionalString("brokerSocket")
              .map(Path::of)
              .orElse(TunnelBrokerClient.DEFAULT_SOCKET);
      var tunnelKey = params.optionalString("tunnelKey").map(Path::of).orElse(null);
      var proposedSmbHost = proposedSmbHost(params, tunnelCert);
      session.useTunnel(
          new BrokerTunnelConnector(
              socket, target.host(), proposedSmbHost, "bb-link", tunnelCert, tunnelKey));
      pairingCertificate = tunnelCert;
    }
    // Remember the device folder (beside the certificate) and the peer id, so a tunnelled connect
    // can auto-start host remote access and reach the device's device.json. Both are null for a
    // password or no-password connect, which carries no certificate.
    var peerId = pairedDeviceId(pairingCertificate).orElse(null);
    session.rememberDevice(
        pairingCertificate == null ? null : pairingCertificate.getParent(), peerId);
    advertiseFriendlyName(session, peerId);
    return session;
  }

  /**
   * Logs in and reads the device, then reads its PIN. Failing to read the PIN does not prevent the
   * session: the summary then simply has no {@code pin}. A connect with {@code deviceInfo} set to
   * {@code false} only logs in, and the result has no device.
   */
  @Override
  public Establishment establish(DeviceSession session, RequestParams params, Reply reply) {
    try {
      session.establishTunnel(reply); // no-op unless this connect asked for a broker tunnel
    } catch (IOException tunnelFailure) {
      return new Establishment.Rejected(
          new ServiceError(
              CoreErrorCode.COMMAND_FAILED,
              "The Wi-Fi tunnel to the device could not be brought up: "
                  + tunnelFailure.getMessage()));
    }
    if (!describesDevice(params)) {
      return switch (session.execute(new LoginOnly(), reply)) {
        case CommandOutcome.Succeeded<NoData> _ -> established(session, null, reply);
        case CommandOutcome.Failed<NoData>(var error, _, _) -> new Establishment.Rejected(error);
      };
    }
    var refusal =
        EndpointReachability.refusalFor(LinkMethod.GET_DEVICE_PROPERTIES, session.connectionPath());
    if (refusal.isPresent()) {
      return new Establishment.Rejected(refusal.orElseThrow());
    }
    return switch (session.execute(new GetDeviceProperties(), reply)) {
      case CommandOutcome.Succeeded<DeviceProperties>(var properties, _) -> {
        var characteristics = readCharacteristics(session, reply);
        var summary = DeviceSummary.of(properties, characteristics);
        yield established(session, summary, reply);
      }
      case CommandOutcome.Failed<DeviceProperties>(var error, _, _) ->
          new Establishment.Rejected(error);
    };
  }

  /**
   * The established result: what the connect returns, including the remote-access status after
   * auto-starting it when the device's {@code device.json} asks for it.
   */
  private Establishment.Established established(
      DeviceSession session, DeviceSummary summary, Reply reply) {
    return new Establishment.Established(
        described(session, summary, startRemoteAccess(session, reply)));
  }

  /**
   * Auto-starts host remote access for a freshly established session when the device's {@code
   * device.json} enables it and the session is tunnelled (the share binds the host ULA and serves
   * over the tunnel, so a USB or direct connect cannot run it). The server is attached to the
   * session, which drains it before releasing the tunnel. A failure to start never fails the
   * connect — it is reported as not running. Returns the status for the {@code remoteAccess}
   * result.
   */
  Map<String, Object> startRemoteAccess(DeviceSession session, Reply reply) {
    var setting = storedRemoteAccess(session);
    boolean enabled = setting.map(StoredRemoteAccess::enabled).orElse(false);
    var configured = setting.map(StoredRemoteAccess::folder).orElse(null);
    var folder = configured != null ? configured : remoteAccessFolders.defaultRoot();
    if (enabled && session.hasTunnel()) {
      try {
        var address = serve(session, configured, false, reply);
        return remoteAccessStatus(true, folder, true, address);
      } catch (IOException | RuntimeException failure) {
        reply.onLog(LogLevel.WARN, "Remote access could not start: " + messageOf(failure));
        return remoteAccessStatus(true, folder, false, null);
      }
    }
    return remoteAccessStatus(enabled, folder, false, null);
  }

  /**
   * Resolves the configuration, starts the server on the session's host ULA, and attaches it to the
   * session. The caller has already checked {@link DeviceSession#hasTunnel()}. The started server
   * is owned by the session — {@link DeviceSession#attachTunnelServer} takes it and the session
   * drains and closes it on teardown — so it is deliberately not closed here (hence
   * {@code @SuppressWarnings("resource")}).
   *
   * @param folder the folder to serve, or {@code null} for the global default
   * @param readOnly whether to serve read-only
   * @return the address the server bound
   * @throws IOException if the folder cannot be created or the server cannot be bound
   */
  private InetSocketAddress serve(DeviceSession session, Path folder, boolean readOnly, Reply reply)
      throws IOException {
    var config = remoteAccessFolders.resolve(folder, readOnly);
    var server =
        remoteAccessStarter.start(
            session.hostUla().orElseThrow(), config, session.peerIdentifier().orElse(""));
    session.attachTunnelServer(server, config.readOnly());
    reply.onLog(LogLevel.INFO, "Remote access is serving " + config.root());
    return server.address();
  }

  /** Starts host remote access on a live session by request, persisting the opt-in. */
  private SessionCommand<DeviceSession> startRemoteAccessCommand(RequestParams params) {
    var explicitFolder = params.optionalPath("folder").orElse(null);
    var readOnly = params.flag("readOnly");
    return (session, reply) -> {
      if (!session.hasTunnel()) {
        reply.fail(noTunnel(START_REMOTE_ACCESS));
        return;
      }
      if (session.tunnelServer().isPresent()) {
        reply.onLog(
            LogLevel.INFO,
            "Remote access is already running; use restartRemoteAccess to change the folder");
        reply.result(START_REMOTE_ACCESS, accessStatus(session), null);
        reply.succeed();
        return;
      }
      try {
        serve(session, effectiveFolder(session, explicitFolder), readOnly, reply);
        persist(session, true, explicitFolder, reply);
        reply.result(START_REMOTE_ACCESS, accessStatus(session), null);
        reply.succeed();
      } catch (IOException | RuntimeException failure) {
        reply.fail(
            new ServiceError(
                CoreErrorCode.COMMAND_FAILED,
                "Remote access could not start: " + messageOf(failure)));
      }
    };
  }

  /**
   * Stops host remote access on a live session. Caller-paced so the session's command queue never
   * blocks: a graceful stop begins the drain and returns at once with {@code state: "draining"} and
   * the in-flight count, which the caller polls with {@code getRemoteAccessStatus} and finalises by
   * calling {@code stopRemoteAccess} again once idle (or with {@code force} to cut it short).
   * {@code disable} persists the opt-out so the next connect does not auto-start it.
   */
  private SessionCommand<DeviceSession> stopRemoteAccessCommand(RequestParams params) {
    var force = params.flag("force");
    var disable = params.flag("disable");
    return (session, reply) -> {
      if (disable) {
        persist(session, false, null, reply);
      }
      var running = session.tunnelServer();
      if (running.isEmpty()) {
        reply.result(STOP_REMOTE_ACCESS, stateStatus(session, "notRunning"), null);
        reply.succeed();
        return;
      }
      var server = running.orElseThrow();
      if (force) {
        server.forceShutdown();
        session.detachTunnelServer();
        reply.result(STOP_REMOTE_ACCESS, stateStatus(session, "forced"), null);
        reply.succeed();
        return;
      }
      server.beginShutdown();
      if (server.activeRequests() == 0) {
        server.close();
        session.detachTunnelServer();
        reply.result(STOP_REMOTE_ACCESS, stateStatus(session, "stopped"), null);
        reply.succeed();
        return;
      }
      reply.result(STOP_REMOTE_ACCESS, stateStatus(session, "draining"), null);
      reply.succeed();
    };
  }

  /** Reports the live remote-access status of a session. */
  private SessionCommand<DeviceSession> remoteAccessStatusCommand() {
    return (session, reply) -> {
      reply.result(GET_REMOTE_ACCESS_STATUS, accessStatus(session), null);
      reply.succeed();
    };
  }

  /**
   * Changes the folder host remote access serves on a live session without a reconnect: a bounded
   * graceful stop of the current server, then a fresh start on the (new) folder, keeping the
   * read-only choice unless the request overrides it. The new folder is persisted so a reconnect
   * auto-starts on it.
   */
  private SessionCommand<DeviceSession> restartRemoteAccessCommand(RequestParams params) {
    var explicitFolder = params.optionalPath("folder").orElse(null);
    return (session, reply) -> {
      if (!session.hasTunnel()) {
        reply.fail(noTunnel(RESTART_REMOTE_ACCESS));
        return;
      }
      var readOnly = params.flag("readOnly", session.tunnelServerReadOnly());
      session.detachTunnelServer().ifPresent(TunnelScopedServer::close);
      try {
        serve(session, effectiveFolder(session, explicitFolder), readOnly, reply);
        persist(session, true, explicitFolder, reply);
        reply.result(RESTART_REMOTE_ACCESS, accessStatus(session), null);
        reply.succeed();
      } catch (IOException | RuntimeException failure) {
        reply.fail(
            new ServiceError(
                CoreErrorCode.COMMAND_FAILED,
                "Remote access could not restart: " + messageOf(failure)));
      }
    };
  }

  /** Persists the {@code remoteAccess} setting for a device without a live session. */
  private StandaloneCommand setRemoteAccessCommand(RequestParams params) {
    var devices = params.requirePath("devicesDirectory");
    var pin = params.requireString("pin");
    var enabled = params.flag("enabled");
    var folder = params.optionalPath("folder").orElse(null);
    return reply -> {
      try {
        var directory = DeviceDirectory.of(devices, pin);
        var effective =
            folder != null
                ? folder
                : directory.remoteAccess().map(StoredRemoteAccess::folder).orElse(null);
        directory.writeRemoteAccess(new StoredRemoteAccess(enabled, effective));
        reply.result(
            SET_REMOTE_ACCESS,
            Json.object(
                "enabled", enabled, "folder", effective == null ? null : effective.toString()),
            null);
        reply.succeed();
      } catch (IOException failure) {
        reply.fail(
            new ServiceError(
                CoreErrorCode.COMMAND_FAILED,
                "Could not write the remote-access setting: " + messageOf(failure)));
      }
    };
  }

  /**
   * The live remote-access status reported by the status, start and restart commands: {@code
   * enabled} from the stored setting, the rest from the running server. The folder is the stored or
   * default folder, which start and restart keep in step with what is served.
   */
  private Map<String, Object> accessStatus(DeviceSession session) {
    var stored = storedRemoteAccess(session);
    var enabled = stored.map(StoredRemoteAccess::enabled).orElse(false);
    var folder = stored.map(StoredRemoteAccess::folder).orElse(remoteAccessFolders.defaultRoot());
    return session
        .tunnelServer()
        .map(server ->
            Json.object(
                "enabled",
                enabled,
                "running",
                true,
                "folder",
                folder.toString(),
                "readOnly",
                session.tunnelServerReadOnly(),
                "activeRequests",
                (long) server.activeRequests(),
                "address",
                addressText(server.address())))
        .orElseGet(() ->
            Json.object(
                "enabled",
                enabled,
                "running",
                false,
                "folder",
                folder.toString(),
                "readOnly",
                false,
                "activeRequests",
                0L,
                "address",
                null));
  }

  /** The {@link #accessStatus} map with the {@code state} the stop command reports. */
  private Map<String, Object> stateStatus(DeviceSession session, String state) {
    var status = accessStatus(session);
    status.put("state", state);
    return status;
  }

  /**
   * How host remote access is started from a tunnelled session; a seam so a test can verify the
   * connect wiring without a real tunnel bind. Production uses {@link RemoteAccess#onTunnel}.
   */
  @FunctionalInterface
  interface RemoteAccessStarter {
    TunnelScopedServer start(String hostUla, RemoteAccessConfig config, String peerIdentifier)
        throws IOException;
  }
}
