package com.jbraddockm.link;

import com.jbraddockm.core.daemon.DaemonServer;
import com.jbraddockm.core.tunnel.TunnelBrokerClient;
import com.jbraddockm.link.daemon.FixtureReplay;
import com.jbraddockm.link.daemon.LinkService;
import com.jbraddockm.link.share.FileShare;
import com.jbraddockm.link.share.ShareConfig;
import com.jbraddockm.link.share.ShareRunner;
import com.jbraddockm.link.share.WebDavServer;
import com.jbraddockm.link.transport.cgi.ExchangeObserver;
import com.jbraddockm.link.transport.trace.TraceRecorder;
import com.jbraddockm.link.transport.tunnel.BrokerTunnelConnector;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.BindException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The application entry point.
 *
 * <ul>
 *   <li>{@code -daemon [-trace <directory>]} serves the JSON protocol on standard input and output.
 *       With {@code -trace}, every exchange with a device is recorded, scrubbed, in that directory.
 *   <li>{@code -parse <command>} replays recorded answers from standard input through the command's
 *       conversation, without a device.
 * </ul>
 */
public final class Launcher {

  private static final int USAGE_ERROR = 2;

  /**
   * Safety cap on the shutdown hook waiting for the run's final teardown (releasing the tunnel
   * hold) once the share has drained, so the hook cannot hang the JVM indefinitely.
   */
  private static final long SHUTDOWN_JOIN_MILLIS = 30_000;

  private static final String USAGE =
      "usage: bb-link -daemon [-trace <directory>] | -parse <command>"
          + " | -share <directory> --device-host <host> [--broker-socket <path>]"
          + " [--tunnel-cert <path>] [--tunnel-key <path>] [--peer-id <id>] [--read-only]";
  private static final String DAEMON_USAGE = "usage: bb-link -daemon [-trace <directory>]";
  private static final String SHARE_USAGE =
      "usage: bb-link -share <directory> --device-host <host> [--broker-socket <path>]"
          + " [--tunnel-cert <path>] [--tunnel-key <path>] [--peer-id <id>] [--read-only]";

  private Launcher() {}

  /**
   * Starts the requested mode.
   *
   * @param arguments the command-line arguments
   * @throws IOException if reading the daemon's input fails
   */
  static void main(String[] arguments) throws IOException {
    System.setProperty("jdk.tls.client.protocols", "TLSv1");
    // A device certificate names no address, so the host cannot be verified against it; the client
    // turns that check off here (there is no per-client switch) before any HttpClient is built. The
    // trust manager still accepts the certificate, see DeviceTls.
    System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");

    switch (interpret(arguments)) {
      case Invocation.Daemon(var traceDirectory) ->
          DaemonServer.run(new LinkService(observerFor(traceDirectory.orElse(null))));
      case Invocation.Replay(var command) -> System.exit(FixtureReplay.run(command));
      case Invocation.Share share -> runShare(share);
      case Invocation.Usage(var text) -> {
        System.err.println(text);
        System.exit(USAGE_ERROR);
      }
    }
  }

  /**
   * Runs the host file share until interrupted: acquires a broker tunnel to the device, binds the
   * WebDAV server on the host ULA ({@code [hostUla]:4487}) serving the chosen directory, publishes
   * the discovery advert, and parks until Ctrl-C or the tunnel drops. bb-link never opens the
   * tunnel itself — it acquires a hold over the broker socket — so there is no code dependency on
   * bb-tunnel.
   */
  private static void runShare(Invocation.Share share) {
    showRequestLog();
    var socket = share.brokerSocket().orElse(TunnelBrokerClient.DEFAULT_SOCKET);
    var connector =
        new BrokerTunnelConnector(
            socket,
            share.deviceHost(),
            null,
            "bb-link-share",
            share.tunnelCert().orElse(null),
            share.tunnelKey().orElse(null));
    var config = new ShareConfig(share.root(), share.readOnly());
    var peerIdentifier = share.peerIdentifier().orElse("");
    var stop = new CountDownLatch(1);
    var shareRef = new AtomicReference<FileShare>();
    var main = Thread.currentThread();
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(() -> stopShare(shareRef.get(), stop, main), "bb-link-share-stop"));
    try {
      ShareRunner.run(
          connector,
          binding -> {
            var fileShare = FileShare.onTunnel(binding.hostUla(), config, peerIdentifier);
            shareRef.set(fileShare);
            System.err.println(
                "file share up on [" + binding.hostUla() + "]:" + fileShare.address().getPort());
            return fileShare;
          },
          stop::await);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    } catch (IOException | IllegalArgumentException | UncheckedIOException failure) {
      // The share could not start (port already bound, no reachable tunnel, bad share directory):
      // report it as a one-line message instead of letting it escape as a Java stack trace.
      fail(shareFailureMessage(failure));
    }
  }

  /**
   * Stops the share on Ctrl-C. The first interrupt drains gracefully — it stops accepting and lets
   * in-flight transfers finish; a second interrupt makes the JVM halt at once, forcing them. On a
   * startup failure nothing is serving yet, so it just lets the run end.
   */
  private static void stopShare(FileShare share, CountDownLatch stop, Thread main) {
    if (share == null) {
      stop.countDown();
      return;
    }
    var active = share.activeRequests();
    System.err.println(
        active > 0
            ? "stopping — letting " + active + " transfer(s) finish (interrupt again to force)"
            : "stopping the file share");
    share.beginShutdown();
    // Wait for the in-flight transfers to finish. A second interrupt halts the JVM, forcing them;
    // the hour cap is only a last resort so the hook cannot wait forever.
    share.awaitIdle(Duration.ofHours(1));
    stop.countDown();
    try {
      main.join(SHUTDOWN_JOIN_MILLIS);
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
    }
  }

  /** A concise, user-facing message for a share that could not start. */
  static String shareFailureMessage(Throwable failure) {
    return switch (failure) {
      case BindException _ ->
          "a file share is already running on this device's tunnel address (port "
              + WebDavServer.RIMDAV_PORT
              + ") — stop it before starting another";
      case IllegalArgumentException invalid -> messageOf(invalid);
      case UncheckedIOException unreadable -> messageOf(unreadable);
      default -> "could not start the file share: " + messageOf(failure);
    };
  }

  private static String messageOf(Throwable failure) {
    var message = failure.getMessage();
    return message == null ? failure.getClass().getSimpleName() : message;
  }

  private static void fail(String message) {
    System.err.println("error: " + message);
    System.exit(1);
  }

  /**
   * Shows the share's per-request access log (logged at {@code DEBUG}) on the console. The share is
   * a foreground tool, so its request log is worth seeing; elsewhere the logger stays off.
   */
  private static void showRequestLog() {
    var logger = Logger.getLogger(WebDavServer.REQUEST_LOG);
    var console = new ConsoleHandler();
    console.setLevel(Level.FINE);
    logger.addHandler(console);
    logger.setLevel(Level.FINE);
    logger.setUseParentHandlers(false);
  }

  /**
   * Reads what the command line asks for, without doing any of it.
   *
   * @param arguments the command-line arguments
   * @return the mode, or the usage to say when the arguments mean nothing
   */
  static Invocation interpret(String[] arguments) {
    if (arguments.length > 0 && arguments[0].equals("-daemon")) {
      if (arguments.length == 3 && arguments[1].equals("-trace")) {
        return new Invocation.Daemon(Optional.of(Path.of(arguments[2])));
      }
      return arguments.length == 1
          ? new Invocation.Daemon(Optional.empty())
          : new Invocation.Usage(DAEMON_USAGE);
    }
    if (arguments.length == 2 && arguments[0].equals("-parse")) {
      return new Invocation.Replay(arguments[1]);
    }
    if (arguments.length > 0 && arguments[0].equals("-share")) {
      return interpretShare(arguments);
    }
    return new Invocation.Usage(USAGE);
  }

  /**
   * Reads a {@code -share} command line: one positional directory and the tunnel options. The
   * directory and {@code --device-host} are required; a missing value, an unknown option, a
   * repeated or missing directory means the share usage.
   *
   * @param arguments the command-line arguments, starting with {@code -share}
   * @return the share invocation, or the usage when the arguments mean nothing
   */
  private static Invocation interpretShare(String[] arguments) {
    Path root = null;
    String deviceHost = null;
    Optional<Path> brokerSocket = Optional.empty();
    Optional<Path> tunnelCert = Optional.empty();
    Optional<Path> tunnelKey = Optional.empty();
    Optional<String> peerIdentifier = Optional.empty();
    boolean readOnly = false;
    for (var i = 1; i < arguments.length; i++) {
      switch (arguments[i]) {
        case "--device-host" -> {
          if (++i >= arguments.length) {
            return new Invocation.Usage(SHARE_USAGE);
          }
          deviceHost = arguments[i];
        }
        case "--broker-socket" -> {
          if (++i >= arguments.length) {
            return new Invocation.Usage(SHARE_USAGE);
          }
          brokerSocket = Optional.of(Path.of(arguments[i]));
        }
        case "--tunnel-cert" -> {
          if (++i >= arguments.length) {
            return new Invocation.Usage(SHARE_USAGE);
          }
          tunnelCert = Optional.of(Path.of(arguments[i]));
        }
        case "--tunnel-key" -> {
          if (++i >= arguments.length) {
            return new Invocation.Usage(SHARE_USAGE);
          }
          tunnelKey = Optional.of(Path.of(arguments[i]));
        }
        case "--peer-id" -> {
          if (++i >= arguments.length) {
            return new Invocation.Usage(SHARE_USAGE);
          }
          peerIdentifier = Optional.of(arguments[i]);
        }
        case "--read-only" -> readOnly = true;
        default -> {
          if (arguments[i].startsWith("-") || root != null) {
            return new Invocation.Usage(SHARE_USAGE);
          }
          root = Path.of(arguments[i]);
        }
      }
    }
    if (root == null || deviceHost == null) {
      return new Invocation.Usage(SHARE_USAGE);
    }
    return new Invocation.Share(
        root, deviceHost, brokerSocket, tunnelCert, tunnelKey, peerIdentifier, readOnly);
  }

  private static ExchangeObserver observerFor(Path traceDirectory) throws IOException {
    return traceDirectory != null ? new TraceRecorder(traceDirectory) : ExchangeObserver.NONE;
  }

  /** What the command line asks for. */
  sealed interface Invocation {

    /**
     * Serve the protocol on standard input and output.
     *
     * @param traceDirectory the folder in which to record every exchange with a device, when one
     *     was given
     */
    record Daemon(Optional<Path> traceDirectory) implements Invocation {}

    /**
     * Replay recorded answers through a command, without a device.
     *
     * @param command the name of the command
     */
    record Replay(String command) implements Invocation {}

    /**
     * Run the host file share: serve {@code root} to the device over a broker tunnel.
     *
     * @param root the host directory to share
     * @param deviceHost the device's transport address to open the tunnel to
     * @param brokerSocket the tunnel-broker socket, or empty for the default
     * @param tunnelCert the device's tunnel certificate, or empty to leave it to the daemon
     * @param tunnelKey the key for {@code tunnelCert}, or empty
     * @param peerIdentifier the identifier the device stored for this workstation, or empty
     * @param readOnly whether the share refuses writes
     */
    record Share(
        Path root,
        String deviceHost,
        Optional<Path> brokerSocket,
        Optional<Path> tunnelCert,
        Optional<Path> tunnelKey,
        Optional<String> peerIdentifier,
        boolean readOnly)
        implements Invocation {}

    /**
     * Arguments that mean nothing: say how the tool is used.
     *
     * @param text what to say
     */
    record Usage(String text) implements Invocation {}
  }
}
