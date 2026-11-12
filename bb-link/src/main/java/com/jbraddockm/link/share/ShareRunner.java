package com.jbraddockm.link.share;

import com.jbraddockm.link.transport.tunnel.TunnelBinding;
import com.jbraddockm.link.transport.tunnel.TunnelConnector;
import java.io.IOException;

/**
 * Runs a host file share for the lifetime of a tunnel: acquire a tunnel hold, start the share on
 * the hold's host ULA, park until told to stop, then tear both down. It is the standalone
 * composition root that joins bb-tunnel's host ULA (over the broker socket, so bb-link keeps no
 * code dependency on bb-tunnel), bb-core mDNS, and the {@link WebDavServer}.
 *
 * <p>The three collaborators are seams so the orchestration is tested without a tunnel: how the
 * tunnel is acquired ({@link TunnelConnector}), how the share is started from the resulting hold
 * ({@link ShareStarter}), and how the run parks until stopped ({@link Parker}).
 */
public final class ShareRunner {

  private ShareRunner() {}

  /**
   * Acquires the tunnel, starts the share, parks, and tears everything down in order. The share and
   * the tunnel hold are always released, whether the park returns normally or the run fails.
   *
   * @param connector acquires the tunnel hold
   * @param starter starts the share from the hold (typically {@link FileShare#onTunnel} on the
   *     hold's {@link TunnelBinding#hostUla()})
   * @param parker blocks until the share should stop (a signal, or the tunnel dropping)
   * @throws IOException if the tunnel cannot be acquired or the share cannot be started
   * @throws InterruptedException if the park is interrupted
   */
  public static void run(TunnelConnector connector, ShareStarter starter, Parker parker)
      throws IOException, InterruptedException {
    var binding = connector.connect();
    AutoCloseable share = null;
    try {
      share = starter.start(binding);
      parker.park();
    } finally {
      closeQuietly(share);
      binding.close();
    }
  }

  private static void closeQuietly(AutoCloseable resource) {
    if (resource != null) {
      try {
        resource.close();
      } catch (Exception ignored) {
        // teardown is best-effort; the tunnel hold is released regardless
      }
    }
  }

  /** Starts the share from an acquired tunnel hold; a seam for testing the orchestration. */
  @FunctionalInterface
  public interface ShareStarter {

    /**
     * Starts the share.
     *
     * @param binding the acquired tunnel hold, carrying the host ULA to bind on
     * @return the running share, closed when the run ends
     * @throws IOException if the share cannot be started
     */
    AutoCloseable start(TunnelBinding binding) throws IOException;
  }

  /** Blocks until the share should stop; a seam for testing the orchestration. */
  @FunctionalInterface
  public interface Parker {

    /**
     * Parks until the share should stop.
     *
     * @throws InterruptedException if the wait is interrupted
     */
    void park() throws InterruptedException;
  }
}
