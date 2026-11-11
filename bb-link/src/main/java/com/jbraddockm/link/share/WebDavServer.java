package com.jbraddockm.link.share;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.concurrent.Executors;

/**
 * A running WebDAV file-share server: a plain JDK {@link HttpServer} serving one jailed directory
 * over RIM's rimdav method set. The daemon starts one bound on the host's bp2p tunnel address and
 * advertises it (tracker N1); here it is just "bind an address, serve this folder".
 *
 * <p>The share is confined to {@code shareRoot} by {@link JailedRoot}; nothing outside that tree is
 * reachable, whatever the request path.
 */
public final class WebDavServer implements AutoCloseable {

  /**
   * The port RIM's rimdav file server binds on the host's bp2p tunnel ULA (tracker N1). The
   * device's File Manager reaches the share at {@code [hostUla]:4487} over the tunnel.
   */
  public static final int RIMDAV_PORT = 4487;

  private final HttpServer server;

  private WebDavServer(HttpServer server) {
    this.server = server;
  }

  /**
   * Starts a file-share server.
   *
   * @param bind the address and port to listen on (for N1, the host's bp2p ULA and 4487)
   * @param shareRoot the single directory to serve; it must exist and be a directory
   * @param readOnly when {@code true}, write verbs are refused with {@code 403}
   * @return the running server
   * @throws IOException if the address cannot be bound
   * @throws IllegalArgumentException if {@code shareRoot} is not a directory
   */
  public static WebDavServer start(InetSocketAddress bind, Path shareRoot, boolean readOnly)
      throws IOException {
    var jail = JailedRoot.of(shareRoot);
    var server = HttpServer.create(bind, 0);
    server.createContext("/", new WebDavHandler(jail, readOnly));
    server.setExecutor(Executors.newCachedThreadPool());
    server.start();
    return new WebDavServer(server);
  }

  /**
   * The bind address for the file share on the host's bp2p tunnel ULA — {@code [hostUla]:4487}, the
   * address RIM's rimdav uses (tracker N1.2). The device reaches it over the tunnel; the share is
   * never exposed on a routable interface, so the ULA must be an IPv6 literal and is never resolved
   * through DNS.
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
    return server.getAddress();
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
