package com.jbraddockm.link.share;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
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
