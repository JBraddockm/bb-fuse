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
import com.jbraddockm.link.endpoint.properties.DeviceProperties;
import com.jbraddockm.link.endpoint.properties.GetDeviceProperties;
import com.jbraddockm.link.transport.cgi.Authentication;
import com.jbraddockm.link.transport.cgi.ConnectionPath;
import com.jbraddockm.link.transport.cgi.DeviceTarget;
import com.jbraddockm.link.transport.cgi.ExchangeObserver;
import com.jbraddockm.link.transport.cgi.Step;
import com.jbraddockm.link.transport.cgi.TransportScheme;
import com.jbraddockm.link.transport.login.LoginOnly;
import com.jbraddockm.link.transport.login.SmbCredentials;
import com.jbraddockm.link.transport.session.DeviceSession;
import com.jbraddockm.link.transport.tunnel.BrokerTunnelConnector;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The bb-link service: what the shared daemon server runs. */
public final class LinkService implements Service<DeviceSession> {

  /** The version reported in {@code hello}. */
  public static final String VERSION = "0.1.0";

  private final TransportScheme scheme;
  private final DeviceScanner scanner;
  private final ExchangeObserver observer;
  private final Pairing pairing;
  private final CertificateRetrieval retrieval;

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

  /**
   * The devices directory the Wi-Fi discovery PIN match reads its paired PINs from: {@code
   * BB_LINK_DEVICES} when set (the {@code bblink} script points it at the active {@code devices/}),
   * otherwise {@code devices/} beside the daemon.
   */
  private static Path devicesDirectory() {
    var configured = System.getenv("BB_LINK_DEVICES");
    return Path.of(configured == null || configured.isBlank() ? "devices" : configured);
  }

  /** Creates a service with its collaborators replaced, which tests use. */
  LinkService(
      TransportScheme scheme,
      DeviceScanner scanner,
      ExchangeObserver observer,
      Pairing pairing,
      CertificateRetrieval retrieval) {
    this.scheme = scheme;
    this.scanner = scanner;
    this.observer = observer;
    this.pairing = pairing;
    this.retrieval = retrieval;
  }

  /** Whether a connect asks for the device to be read; it does unless it says {@code false}. */
  private static boolean describesDevice(RequestParams params) {
    return params.flag("deviceInfo", true);
  }

  /** The result of a connection; the device is {@code null} when nothing of it was read. */
  private static Map<String, Object> described(DeviceSession session, DeviceSummary summary) {
    return Json.object(
        "host", session.target().host(),
        "port", session.target().port(),
        "authentication", session.target().authentication().type().wireName(),
        // Whether this host holds a broker tunnel to the device — true only for a tunnelled Wi-Fi
        // connect, false for USB/direct. Distinct from the device's own bp2p flag in `device`.
        "tunnel", session.hasTunnel(),
        "device", summary != null ? summary.toJson() : null,
        "smb", session.smbCredentials().map(SmbCredentials::toJson).orElse(null));
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

  @Override
  public ServiceInfo info() {
    return new ServiceInfo("bb-link", VERSION);
  }

  @Override
  public List<CommandSpec<DeviceSession>> commands() {
    return Arrays.stream(LinkMethod.values())
        .map(method -> method.spec(scanner, pairing, retrieval))
        .toList();
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
    advertiseFriendlyName(session, pairingCertificate);
    return session;
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
   * Advertises this workstation's friendly name over mDNS for the device this connect pairs with,
   * so the device shows it in Device Connections instead of the {@code Desktop-XXXX} fallback.
   * Best-effort, and attached to the session so the advert is withdrawn when the connection ends. A
   * connect with no pairing beside its certificate advertises nothing.
   */
  private static void advertiseFriendlyName(DeviceSession session, Path pairingCertificate) {
    pairedDeviceId(pairingCertificate).ifPresent(id -> advertiseFriendlyName(session, id));
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
        case CommandOutcome.Succeeded<NoData> _ ->
            new Establishment.Established(described(session, null));
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
        yield new Establishment.Established(described(session, summary));
      }
      case CommandOutcome.Failed<DeviceProperties>(var error, _, _) ->
          new Establishment.Rejected(error);
    };
  }
}
