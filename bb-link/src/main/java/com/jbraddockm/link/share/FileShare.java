package com.jbraddockm.link.share;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetSocketAddress;

/**
 * A running host file share: the single "allow remote access" control that turns the share on and
 * off (tracker N1.5). Starting it binds the {@link WebDavServer} (serving the {@link ShareConfig}'s
 * jailed root) and publishes the {@link WebdavAdvertiser}; {@link #close()} stops both. The advert
 * is a courtesy — if it cannot start, the share still serves — so it is withdrawn first and never
 * fails the teardown.
 *
 * <p>The composition is deliberately small and transport-agnostic: the caller supplies the bound
 * address (for N1 the host's tunnel ULA on {@link WebDavServer#RIMDAV_PORT}, from {@link
 * WebDavServer#tunnelBindAddress}) and how the advert starts. A caller holding a tunnel lease uses
 * {@link #onTunnel}.
 */
public final class FileShare implements AutoCloseable {

  private final WebDavServer server;
  private final AutoCloseable advert;

  private FileShare(WebDavServer server, AutoCloseable advert) {
    this.server = server;
    this.advert = advert;
  }

  /**
   * Starts a file share: binds the server, then starts the advert.
   *
   * @param bind the address to bind the server on
   * @param config the share configuration (the jailed root and read-only flag)
   * @param advert how to start the discovery advert, or {@code null} to advertise nothing; it
   *     returns the running advert (which {@link #close()} withdraws), or {@code null} if it did
   *     not start
   * @return the running share
   * @throws IOException if the server cannot be bound
   */
  public static FileShare start(InetSocketAddress bind, ShareConfig config, AdvertStarter advert)
      throws IOException {
    var server = WebDavServer.start(bind, config.root(), config.readOnly());
    AutoCloseable started = null;
    if (advert != null) {
      started = advert.start();
    }
    return new FileShare(server, started);
  }

  /**
   * Starts a file share on the host's bp2p tunnel ULA — {@code [hostUla]:4487}, the rimdav address
   * — and publishes the webdav discovery advert for the paired device.
   *
   * @param hostUla the host's tunnel ULA (from the tunnel lease), an IPv6 literal
   * @param config the share configuration
   * @param peerIdentifier the identifier the device stored for this workstation at pairing, folded
   *     into the advert so the device recognises the share as this computer's
   * @return the running share
   * @throws IOException if the server cannot be bound
   */
  public static FileShare onTunnel(String hostUla, ShareConfig config, String peerIdentifier)
      throws IOException {
    var bind = WebDavServer.tunnelBindAddress(hostUla);
    var ula = (Inet6Address) bind.getAddress();
    return start(bind, config, () -> WebdavAdvertiser.tryStart(peerIdentifier, ula).orElse(null));
  }

  /**
   * The address the share's server is listening on.
   *
   * @return the bound address
   */
  public InetSocketAddress address() {
    return server.address();
  }

  /** Stops the share: withdraws the advert (best-effort), then stops the server. */
  @Override
  public void close() {
    if (advert != null) {
      try {
        advert.close();
      } catch (Exception ignored) {
        // the advert is a courtesy; a failure to withdraw it must not stop the server teardown
      }
    }
    server.close();
  }

  /** Starts the discovery advert; a seam, so a test needs no mDNS responder. */
  @FunctionalInterface
  public interface AdvertStarter {

    /**
     * Starts the advert.
     *
     * @return the running advert, which {@link FileShare#close()} withdraws, or {@code null} if it
     *     did not start
     * @throws IOException if the advert could not be started
     */
    AutoCloseable start() throws IOException;
  }
}
