package com.jbraddockm.tunnel.broker;

import com.jbraddockm.core.daemon.RequestParams;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.tunnel.TunnelCertificateException;
import com.jbraddockm.tunnel.Tunnels;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * The shared, thread-safe core of the tunnel broker daemon: it layers consumer-facing leases,
 * identity and events on top of {@link Tunnels}, the one-shared-tunnel-per-device transport.
 * bb-link and Blend are peer consumers; neither owns a tunnel's lifecycle.
 *
 * <p>This class is transport-agnostic — it speaks {@link RequestParams}/JSON maps and knows nothing
 * of sockets — so it is unit-tested directly. {@link BrokerServer} wraps it in a Unix-domain-socket
 * server, one connection per client.
 *
 * <h2>What it adds over {@link Tunnels}</h2>
 *
 * <ul>
 *   <li><b>Tokens &amp; connection-scoped leases.</b> Every {@code acquire} hands back an opaque
 *       token and is remembered against the connection that made it; when that connection goes away
 *       ({@link #disconnected}) its leases are released, so a crashed client cannot pin a tunnel up
 *       forever.
 *   <li><b>Identity.</b> A consumer that has learned a device's GUID/PIN (over {@code
 *       discovery.cgi}, which only it can reach) reports them with {@code identify}; the broker
 *       records {@code GUID → host} so the {@code acquire {guid}} rejoin form works, and tells the
 *       device's other consumers with an {@code identity} event.
 *   <li><b>Drop events.</b> When a tunnel drops on its own, {@link Tunnels#onDrop} fires; the
 *       broker forgets the device and sends a {@code dropped} event to the consumers that held it.
 * </ul>
 */
final class TunnelBroker {

  private final Tunnels tunnels;
  private final IdentityResolver fallbackIdentity;
  private final Consumer<String> log;
  private final Object lock = new Object();
  private final AtomicLong tokens = new AtomicLong();
  private final Map<String, Hold> byToken = new HashMap<>();
  private final Map<String, DeviceInfo> byHost = new HashMap<>();
  private final Map<String, String> hostByGuid = new HashMap<>();
  private final Map<String, EventSink> connections = new HashMap<>();

  /**
   * Wraps the tunnel service with no daemon-side identity fallback (every acquire must carry one).
   */
  TunnelBroker(Tunnels tunnels) {
    this(tunnels, _ -> Optional.empty());
  }

  /**
   * Wraps the tunnel service and subscribes to its drop notifications. {@code fallbackIdentity}
   * supplies the client identity for an {@code acquire} that does not carry {@code
   * certificate}/{@code key} paths of its own — the daemon's own resolution (CLI args, env, or the
   * {@code devices/<PIN>/} convention beside the binary); it may yield nothing, in which case such
   * an acquire is refused.
   */
  TunnelBroker(Tunnels tunnels, IdentityResolver fallbackIdentity) {
    this(tunnels, fallbackIdentity, _ -> {});
  }

  /**
   * As {@link #TunnelBroker(Tunnels, IdentityResolver)}, with a {@code log} for the daemon's
   * lifecycle lines (a tunnel coming up or down, an identity learned). It defaults to a no-op; the
   * daemon ({@code Tunneld}) points it at standard error.
   */
  TunnelBroker(Tunnels tunnels, IdentityResolver fallbackIdentity, Consumer<String> log) {
    this.tunnels = Objects.requireNonNull(tunnels, "tunnels");
    this.fallbackIdentity = Objects.requireNonNull(fallbackIdentity, "fallbackIdentity");
    this.log = Objects.requireNonNull(log, "log");
    tunnels.onDrop(this::dropped);
  }

  /** Delivers an event to each sink outside the lock; a failed write never breaks the others. */
  private static void fan(List<EventSink> sinks, Map<String, Object> event) {
    for (var sink : sinks) {
      try {
        sink.event(event);
      } catch (RuntimeException ignore) {
        // a wedged or closed connection must not stop the rest being told
      }
    }
  }

  /**
   * The device PIN for a log line: the acquire's explicit {@code pin}, else the name of the device
   * folder holding the tunnel certificate ({@code devices/<PIN>/tunnel_cert.pem}), or {@code null}
   * when neither is present.
   */
  private static String pinFrom(RequestParams params) {
    var explicit = params.optionalString("pin");
    return explicit.orElseGet(() ->
        params
            .optionalString("certificate")
            .map(Path::of)
            .map(Path::getParent)
            .map(Path::getFileName)
            .map(Path::toString)
            .filter(name -> name.matches("[A-Za-z0-9]{1,16}"))
            .orElse(null));
  }

  /** The label a log line leads with: the device PIN when known, else the transport host. */
  private static String deviceLabel(String host, String pin) {
    return pin != null && !pin.isBlank() ? pin : host;
  }

  /**
   * A short, human reason for a drop log line. A bare {@code SocketException: Socket closed} / EOF
   * reads like an error, but it is just the link going away, so it is mapped to calmer text. The
   * full raw cause stays in the machine {@code dropped} event for debugging and is kept out of the
   * human line.
   */
  static String friendlyCause(Throwable cause) {
    if (cause == null) {
      return null;
    }
    var message = cause.getMessage();
    if (message != null) {
      var lower = message.toLowerCase(java.util.Locale.ROOT);
      if (lower.contains("closed") || lower.contains("eof")) {
        return "connection closed";
      }
      if (lower.contains("reset")) {
        return "connection reset";
      }
      if (lower.contains("timed out") || lower.contains("timeout")) {
        return "timed out";
      }
    }
    return message != null && !message.isBlank() ? message : cause.getClass().getSimpleName();
  }

  /** Registers a connection and where to write its events. Call before handling its requests. */
  void connected(String connection, EventSink events) {
    synchronized (lock) {
      connections.put(connection, events);
    }
  }

  /** Forgets a connection and releases every lease it still held (its socket closed). */
  void disconnected(String connection) {
    List<String> lines = new ArrayList<>();
    synchronized (lock) {
      connections.remove(connection);
      var holds =
          byToken.values().stream().filter(hold -> hold.connection.equals(connection)).toList();
      for (var hold : holds) {
        var info = byHost.get(hold.host);
        var label = deviceLabel(hold.host, info == null ? null : info.pin);
        var remaining = releaseLocked(hold.token);
        lines.add(
            label
                + " ["
                + hold.consumer
                + "] down  (socket closed, refs "
                + remaining
                + (remaining == 0 ? ", tunnel closed)" : ")"));
      }
    }
    lines.forEach(log);
  }

  /**
   * Opens or joins a device's tunnel. Keyed by {@code host} (the transport address) or, in the
   * rejoin form, by a {@code guid} the broker already recorded. Returns the coordinates to connect
   * with plus the lease {@code token}. The slow bring-up runs without the broker lock, so other
   * connections are not blocked by it.
   */
  Map<String, Object> acquire(String connection, RequestParams params) throws Exception {
    var consumer = params.optionalString("consumer").orElse("unknown");
    // A proposed SMB host is honoured when free; the legacy "stable" flag maps to proposing the
    // constant 169.254.1.1.
    var proposedSmb =
        params.optionalString("smbHost").orElse(params.flag("stable") ? "169.254.1.1" : null);
    var guid = params.optionalString("guid");
    String host;
    if (guid.isPresent()) {
      synchronized (lock) {
        host = hostByGuid.get(guid.get());
      }
      if (host == null) {
        throw new BrokerException("unknown_guid", "no tunnel is recorded for guid " + guid.get());
      }
    } else {
      host = params.requireString("host");
    }

    var identity = resolveIdentity(params);
    Tunnels.Lease lease;
    try {
      // may block on bring-up; no broker lock held
      lease = tunnels.acquire(host, proposedSmb, identity);
    } catch (TunnelCertificateException certFailure) {
      throw new BrokerException(certFailure.code(), certFailure.getMessage());
    }

    String message;
    Map<String, Object> coordinates;
    synchronized (lock) {
      var info = byHost.computeIfAbsent(host, _ -> new DeviceInfo());
      info.ula = lease.deviceUla();
      info.hostUla = lease.hostUla();
      info.innerIpv4 = lease.deviceIpv4();
      var pin = pinFrom(params);
      if (pin != null) {
        info.pin = pin;
      }
      var token = "t" + tokens.incrementAndGet();
      byToken.put(token, new Hold(token, host, connection, consumer, lease));
      var refs = holdsFor(host).size();
      var label = deviceLabel(host, info.pin);
      message =
          refs == 1
              ? label
                  + " ["
                  + consumer
                  + "] up  "
                  + host
                  + "  ula "
                  + info.ula
                  + "  smb "
                  + info.innerIpv4
              : label + " [" + consumer + "] joined  (refs " + refs + ")";
      coordinates = describe(token, host, info);
    }
    log.accept(message);
    return coordinates;
  }

  /**
   * The client identity an {@code acquire} opens the tunnel with: the request's {@code
   * certificate}/{@code key} paths when it carries them (the consumer resolved the device's
   * identity and points the daemon at it), otherwise the daemon's own {@link IdentityResolver}
   * fallback.
   */
  private Tunnels.Identity resolveIdentity(RequestParams params) {
    var cert = params.optionalString("certificate");
    var key = params.optionalString("key");
    if (cert.isPresent() && key.isPresent()) {
      return new Tunnels.Identity(Path.of(cert.get()), Path.of(key.get()));
    }
    if (cert.isPresent() != key.isPresent()) {
      throw new BrokerException(
          "cert_incomplete", "pass both 'certificate' and 'key' paths, or neither");
    }
    return fallbackIdentity
        .resolve(params)
        .orElseThrow(() ->
            new BrokerException(
                "identity_missing",
                "no client certificate for the tunnel: pass 'certificate' and 'key' paths, or"
                    + " give the daemon a device identity (--cert/--key, BB_DEVICE, or a"
                    + " devices/<PIN>/ folder beside the binary)"));
  }

  /** Releases one lease by token. Idempotent; reports the references remaining for the device. */
  Map<String, Object> release(RequestParams params) {
    var token = params.requireString("token");
    int remaining;
    String line = null;
    synchronized (lock) {
      var hold = byToken.get(token);
      if (hold == null) {
        remaining = 0;
      } else {
        var info = byHost.get(hold.host);
        var label = deviceLabel(hold.host, info == null ? null : info.pin);
        remaining = releaseLocked(token);
        line =
            label
                + " ["
                + hold.consumer
                + "] down  (refs "
                + remaining
                + (remaining == 0 ? ", tunnel closed)" : ")");
      }
    }
    if (line != null) {
      log.accept(line);
    }
    return Json.object("refs", (long) remaining);
  }

  /** Lists the tunnels the broker holds (optionally one {@code host}). */
  Map<String, Object> status(RequestParams params) {
    var only = params.optionalString("host");
    synchronized (lock) {
      var tunnels = new ArrayList<>();
      byHost.forEach((host, info) -> {
        if (only.isPresent() && !only.get().equals(host)) {
          return;
        }
        var holds = holdsFor(host);
        var consumers = new LinkedHashSet<String>();
        holds.forEach(hold -> consumers.add(hold.consumer));
        tunnels.add(
            Json.object(
                "host",
                host,
                "state",
                "UP",
                "ula",
                info.ula,
                "innerIpv4",
                info.innerIpv4,
                "guid",
                info.guid,
                "pin",
                info.pin,
                "refs",
                (long) holds.size(),
                "consumers",
                List.copyOf(consumers)));
      });
      return Json.object("tunnels", tunnels);
    }
  }

  // --- internals -------------------------------------------------------------------------------

  /**
   * Records the GUID/PIN a consumer learned for a device it holds, so the {@code acquire {guid}}
   * rejoin form resolves and the device's other consumers get an {@code identity} event. The broker
   * never reads {@code discovery.cgi} itself — only a consumer can, over the tunnel — so identity
   * arrives this way.
   */
  Map<String, Object> identify(RequestParams params) {
    var host = params.requireString("host");
    var guid = params.requireString("guid");
    var pin = params.optionalString("pin").orElse(null);
    List<EventSink> sinks;
    Map<String, Object> event;
    synchronized (lock) {
      var info = byHost.get(host);
      if (info == null) {
        throw new BrokerException("unknown_host", "no tunnel is held for host " + host);
      }
      info.guid = guid;
      info.pin = pin;
      hostByGuid.put(guid, host);
      event = Json.object("event", "identity", "host", host, "guid", guid, "pin", pin);
      sinks = subscribers(host);
    }
    fan(sinks, event);
    log.accept(deviceLabel(host, pin) + " identified  guid " + guid);
    return Json.object("ok", true);
  }

  /** Called by {@link Tunnels} when a tunnel drops on its own. */
  private void dropped(String host, Throwable cause) {
    List<EventSink> sinks;
    Map<String, Object> event;
    String label;
    synchronized (lock) {
      // The tunnel is already gone and the manager has evicted it; just forget our holds for it
      // (closing their dead leases is pointless) and notify whoever held it.
      var info = byHost.get(host);
      label = deviceLabel(host, info == null ? null : info.pin);
      sinks = subscribers(host);
      holdsFor(host).forEach(hold -> byToken.remove(hold.token));
      byHost.remove(host);
      hostByGuid.values().removeIf(host::equals);
      event =
          Json.object(
              "event",
              "dropped",
              "host",
              host,
              "cause",
              cause == null ? null : String.valueOf(cause));
    }
    fan(sinks, event);
    var reason = friendlyCause(cause);
    log.accept(label + " dropped  " + host + (reason == null ? "" : " (" + reason + ")"));
  }

  /** Caller holds {@link #lock}. Returns the references remaining for the released device. */
  private int releaseLocked(String token) {
    var hold = byToken.remove(token);
    if (hold == null) {
      return 0; // already released, or never existed — idempotent
    }
    hold.lease.close(); // tunnel ref--; brings the tunnel down on the last lease
    var remaining = holdsFor(hold.host).size();
    if (remaining == 0) {
      byHost.remove(hold.host);
      hostByGuid.values().removeIf(hold.host::equals);
    }
    return remaining;
  }

  /** Caller holds {@link #lock}. */
  private List<Hold> holdsFor(String host) {
    return byToken.values().stream().filter(hold -> hold.host.equals(host)).toList();
  }

  /** Caller holds {@link #lock}. The event sinks of the connections holding {@code host}. */
  private List<EventSink> subscribers(String host) {
    var sinks = new ArrayList<EventSink>();
    var seen = new LinkedHashSet<String>();
    for (var hold : holdsFor(host)) {
      if (seen.add(hold.connection)) {
        var sink = connections.get(hold.connection);
        if (sink != null) {
          sinks.add(sink);
        }
      }
    }
    return sinks;
  }

  /** Caller holds {@link #lock}. */
  private Map<String, Object> describe(String token, String host, DeviceInfo info) {
    return Json.object(
        "token", token,
        "host", host,
        "ula", info.ula,
        "hostUla", info.hostUla,
        "innerIpv4", info.innerIpv4,
        "guid", info.guid,
        "pin", info.pin,
        "refs", (long) holdsFor(host).size());
  }

  /**
   * Where a connection's events (server-initiated {@code dropped}/{@code identity}) are written.
   */
  @FunctionalInterface
  interface EventSink {
    void event(Map<String, Object> event);
  }

  /**
   * The daemon's own resolution of a client identity for an {@code acquire} that carries no {@code
   * certificate}/{@code key} of its own: CLI args, an env-named device, or the {@code
   * devices/<PIN>/} convention beside the binary. Returns empty when the daemon has no identity
   * configured.
   */
  @FunctionalInterface
  interface IdentityResolver {
    Optional<Tunnels.Identity> resolve(RequestParams request);
  }

  /** One consumer hold on a device's tunnel. */
  private record Hold(
      String token, String host, String connection, String consumer, Tunnels.Lease lease) {}

  /**
   * What the broker knows about a device whose tunnel is up: its coordinates and (later) identity.
   */
  private static final class DeviceInfo {
    String ula;
    String hostUla;
    String innerIpv4;
    String guid; // null until an identity
    String pin; // null until an identity
  }
}
