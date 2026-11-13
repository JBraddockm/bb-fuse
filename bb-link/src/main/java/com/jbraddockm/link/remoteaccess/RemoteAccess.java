package com.jbraddockm.link.remoteaccess;

import com.jbraddockm.link.transport.session.TunnelScopedServer;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Running host remote access: the single "allow remote access" control that turns it on and off.
 * Starting it binds the {@link WebDavServer} (serving the {@link RemoteAccessConfig}'s jailed root)
 * and publishes the {@link WebdavAdvertiser}; {@link #close()} stops both. The advert is a courtesy
 * — if it cannot start, remote access still serves — so it is withdrawn first and never fails the
 * teardown.
 *
 * <p>As a {@link TunnelScopedServer} it is owned by the connect session, which drains it before
 * releasing the tunnel it serves over.
 *
 * <p>The composition is deliberately small and transport-agnostic: the caller supplies the bound
 * address (the host's tunnel ULA on {@link WebDavServer#RIMDAV_PORT}, from {@link
 * WebDavServer#tunnelBindAddress}) and how the advert starts. A caller holding a tunnel lease uses
 * {@link #onTunnel}.
 */
public final class RemoteAccess implements TunnelScopedServer {

  private final WebDavServer server;
  private final AutoCloseable advert;
  private final AtomicBoolean advertWithdrawn = new AtomicBoolean(false);

  private RemoteAccess(WebDavServer server, AutoCloseable advert) {
    this.server = server;
    this.advert = advert;
  }

  /**
   * Starts remote access: binds the server, then starts the advert.
   *
   * @param bind the address to bind the server on
   * @param config the configuration (the jailed root and read-only flag)
   * @param advert how to start the discovery advert, or {@code null} to advertise nothing; it
   *     returns the running advert (which {@link #close()} withdraws), or {@code null} if it did
   *     not start
   * @return the running remote access
   * @throws IOException if the server cannot be bound
   */
  public static RemoteAccess start(
      InetSocketAddress bind, RemoteAccessConfig config, AdvertStarter advert) throws IOException {
    var server = WebDavServer.start(bind, config.root(), config.readOnly());
    AutoCloseable started = null;
    if (advert != null) {
      started = advert.start();
    }
    return new RemoteAccess(server, started);
  }

  /**
   * Starts remote access on the host's bp2p tunnel ULA — {@code [hostUla]:4487}, the rimdav address
   * — and publishes the webdav discovery advert for the paired device.
   *
   * @param hostUla the host's tunnel ULA (from the tunnel lease), an IPv6 literal
   * @param config the configuration
   * @param peerIdentifier the identifier the device stored for this workstation at pairing, folded
   *     into the advert so the device recognises the server as this computer's
   * @return the running remote access
   * @throws IOException if the server cannot be bound
   */
  public static RemoteAccess onTunnel(
      String hostUla, RemoteAccessConfig config, String peerIdentifier) throws IOException {
    var bind = WebDavServer.tunnelBindAddress(hostUla);
    var ula = (Inet6Address) bind.getAddress();
    return start(bind, config, () -> WebdavAdvertiser.tryStart(peerIdentifier, ula).orElse(null));
  }

  /**
   * The address the remote-access server is listening on.
   *
   * @return the bound address
   */
  @Override
  public InetSocketAddress address() {
    return server.address();
  }

  /**
   * Begins a graceful shutdown: withdraws the advert (so the device discovers it no longer) and
   * stops the server accepting new connections, letting in-flight transfers finish. Pair with
   * {@link #awaitIdle}, {@link #activeRequests} and {@link #forceShutdown}.
   */
  @Override
  public void beginShutdown() {
    withdrawAdvert();
    server.beginShutdown();
  }

  /**
   * Waits for in-flight transfers to finish after {@link #beginShutdown}.
   *
   * @param timeout how long to wait
   * @return {@code true} if remote access drained, {@code false} if the timeout elapsed first
   */
  @Override
  public boolean awaitIdle(Duration timeout) {
    return server.awaitIdle(timeout);
  }

  /**
   * The number of transfers currently in flight — watch this to decide whether to keep waiting or
   * to {@link #forceShutdown}.
   *
   * @return the in-flight request count
   */
  @Override
  public int activeRequests() {
    return server.activeRequests();
  }

  /** Forces remote access to stop now, interrupting any in-flight transfer. */
  @Override
  public void forceShutdown() {
    withdrawAdvert();
    server.forceShutdown();
  }

  /**
   * Stops remote access: a graceful drain within a short grace period, then the server is forced.
   */
  @Override
  public void close() {
    withdrawAdvert();
    server.close();
  }

  private void withdrawAdvert() {
    if (advert != null && advertWithdrawn.compareAndSet(false, true)) {
      try {
        advert.close();
      } catch (Exception ignored) {
        // the advert is a courtesy; a failure to withdraw it must not stop the server teardown
      }
    }
  }

  /** Starts the discovery advert; a seam, so a test needs no mDNS responder. */
  @FunctionalInterface
  public interface AdvertStarter {

    /**
     * Starts the advert.
     *
     * @return the running advert, which {@link RemoteAccess#close()} withdraws, or {@code null} if
     *     it did not start
     * @throws IOException if the advert could not be started
     */
    AutoCloseable start() throws IOException;
  }
}
