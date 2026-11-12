package com.jbraddockm.link.share;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A running WebDAV file-share server: a plain blocking-socket HTTP/1.1 server serving one jailed
 * directory over RIM's rimdav method set. The caller binds it on the host's bp2p tunnel address and
 * advertises it; here it is just "bind an address, serve this folder".
 *
 * <p>The share is confined to {@code shareRoot} by {@link JailedRoot}; nothing outside that tree is
 * reachable, whatever the request path.
 *
 * <p><b>Why a blocking socket server, not {@code com.sun.net.httpserver}.</b> The JDK {@code
 * HttpServer} parses the request target with {@link java.net.URI} and rejects a path that carries a
 * raw non-ASCII byte with {@code 400} before the handler runs. The device sends file names as raw
 * UTF-8 in the request path, so the server must keep those bytes intact for {@link JailedRoot} to
 * percent-decode as UTF-8; reading the request line directly does that. A blocking {@link
 * ServerSocket#accept()} loop with a thread per connection is also simpler than an NIO selector.
 */
public final class WebDavServer implements AutoCloseable {

  /**
   * The port RIM's rimdav file server binds on the host's bp2p tunnel ULA. The device's File
   * Manager reaches the share at {@code [hostUla]:4487} over the tunnel.
   */
  public static final int RIMDAV_PORT = 4487;

  private final ServerSocket socket;
  private final ExecutorService workers;
  private volatile boolean running = true;

  private WebDavServer(ServerSocket socket, ExecutorService workers) {
    this.socket = socket;
    this.workers = workers;
  }

  /**
   * Starts a file-share server.
   *
   * @param bind the address and port to listen on (the host's bp2p ULA and 4487)
   * @param shareRoot the single directory to serve; it must exist and be a directory
   * @param readOnly when {@code true}, write verbs are refused with {@code 403}
   * @return the running server
   * @throws IOException if the address cannot be bound
   * @throws IllegalArgumentException if {@code shareRoot} is not a directory
   */
  public static WebDavServer start(InetSocketAddress bind, Path shareRoot, boolean readOnly)
      throws IOException {
    var handler = new WebDavHandler(JailedRoot.of(shareRoot), readOnly);
    var socket = new ServerSocket();
    socket.setReuseAddress(true);
    socket.bind(bind);
    var workers =
        Executors.newCachedThreadPool(
            runnable -> {
              var thread = new Thread(runnable, "webdav-worker");
              thread.setDaemon(true);
              return thread;
            });
    var server = new WebDavServer(socket, workers);
    var acceptor = new Thread(() -> server.acceptLoop(handler), "webdav-accept");
    acceptor.setDaemon(true);
    acceptor.start();
    return server;
  }

  private void acceptLoop(WebDavHandler handler) {
    while (running) {
      Socket connection;
      try {
        connection = socket.accept();
      } catch (IOException closed) {
        // close() races the accept; a failure while still running is transient, otherwise we stop.
        if (running) {
          continue;
        }
        return;
      }
      workers.execute(() -> serve(handler, connection));
    }
  }

  private static void serve(WebDavHandler handler, Socket connection) {
    try (connection) {
      connection.setTcpNoDelay(true);
      handler.handle(connection.getInputStream(), connection.getOutputStream());
    } catch (IOException dropped) {
      // a dropped connection is the client's to retry; nothing for the server to do
    }
  }

  /**
   * The bind address for the file share on the host's bp2p tunnel ULA — {@code [hostUla]:4487}, the
   * address RIM's rimdav uses. The device reaches it over the tunnel; the share is never exposed on
   * a routable interface, so the ULA must be an IPv6 literal and is never resolved through DNS.
   *
   * @param hostUla the host's tunnel ULA (from the tunnel lease), an IPv6 literal such as {@code
   *     fd2d:…}
   * @return the bind address on {@link #RIMDAV_PORT}
   * @throws IllegalArgumentException if {@code hostUla} is blank or not an IPv6 literal
   */
  public static InetSocketAddress tunnelBindAddress(String hostUla) {
    if (hostUla == null || hostUla.isBlank()) {
      throw new IllegalArgumentException("the host tunnel ULA is required");
    }
    var literal = hostUla.strip();
    // A colon marks an IPv6 literal; requiring it keeps getByName from resolving a hostname via
    // DNS.
    if (literal.indexOf(':') < 0) {
      throw new IllegalArgumentException("the host tunnel ULA must be an IPv6 literal: " + hostUla);
    }
    InetAddress address;
    try {
      address = InetAddress.getByName(literal);
    } catch (UnknownHostException malformed) {
      throw new IllegalArgumentException("not a valid IPv6 ULA literal: " + hostUla, malformed);
    }
    if (!(address instanceof Inet6Address)) {
      throw new IllegalArgumentException("the host tunnel ULA must be an IPv6 literal: " + hostUla);
    }
    return new InetSocketAddress(address, RIMDAV_PORT);
  }

  /**
   * Returns the address the server is listening on (with the resolved port when {@code 0} was
   * requested).
   *
   * @return the bound address
   */
  public InetSocketAddress address() {
    return (InetSocketAddress) socket.getLocalSocketAddress();
  }

  @Override
  public void close() {
    running = false;
    try {
      socket.close();
    } catch (IOException ignored) {
      // closing the listening socket is best-effort; the accept loop exits on it
    }
    workers.shutdownNow();
  }
}
