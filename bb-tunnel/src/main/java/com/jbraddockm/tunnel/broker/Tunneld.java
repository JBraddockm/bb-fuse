package com.jbraddockm.tunnel.broker;

import com.jbraddockm.core.tunnel.TunnelBrokerClient;
import com.jbraddockm.tunnel.BuildInfo;
import com.jbraddockm.tunnel.DeviceIdentitySources;
import com.jbraddockm.tunnel.TunnelManager;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * The tunnel-broker daemon entry point: wires a real {@link TunnelManager} into a {@link
 * TunnelBroker} and serves it on a Unix-domain socket until the process is stopped.
 *
 * <p>Run it as {@code sudo bb-tunnel serve [socket-path]}, or through the installed launchd
 * service; root is required because bringing up a tunnel creates a privileged {@code utun}.
 */
public final class Tunneld {

  /** The default socket path when none is given; overridable on the command line. */
  public static final String DEFAULT_SOCKET = TunnelBrokerClient.DEFAULT_SOCKET.toString();

  private Tunneld() {}

  /** Serves the broker on {@code socketPath}. Blocks until the process is interrupted or killed. */
  public static void run(Path socketPath) throws IOException {
    run(socketPath, null, null, null, null);
  }

  /**
   * As {@link #run(Path)}, but with CLI overrides for the daemon's identity fallback — the client
   * certificate presented for an acquire that carries none of its own. {@code certArg}/{@code
   * keyArg} name an explicit identity; {@code deviceArg} a PIN; {@code devicesArg} the devices
   * directory. Any left {@code null} fall back to the environment and the {@code devices/}
   * convention.
   */
  public static void run(
      Path socketPath, Path certArg, Path keyArg, String deviceArg, Path devicesArg)
      throws IOException {
    var log = logSink();
    var sources =
        DeviceIdentitySources.fromEnvironment(
            System.getenv(), certArg, keyArg, deviceArg, devicesArg);
    var broker =
        new TunnelBroker(
            new TunnelManager(), request -> sources.resolve(request.optionalString("pin")), log);
    try (var server = new BrokerServer(broker, socketPath, BuildInfo.VERSION)) {
      // Stop the accept loop on SIGTERM/SIGINT so the socket file is removed on a clean signal,
      // not only on a graceful close(). A stale file from a hard kill is cleared at next startup.
      Runtime.getRuntime().addShutdownHook(new Thread(server::close, "bb-tunneld-shutdown"));
      log.accept("listening on " + socketPath);
      server.serve();
    }
  }

  /**
   * Where the daemon's log goes: a size-capped, self-clearing file when {@code BB_TUNNELD_LOG}
   * names one (the installed launchd service points it at {@code /var/log/bb-tunneld.log}),
   * otherwise standard error — so a terminal {@code sudo bb-tunnel serve} still prints to the
   * console.
   */
  private static Consumer<String> logSink() {
    var path = System.getenv("BB_TUNNELD_LOG");
    return path == null || path.isBlank()
        ? message -> System.err.println("bb-tunneld: " + message)
        : CappedLog.to(Path.of(path));
  }
}
