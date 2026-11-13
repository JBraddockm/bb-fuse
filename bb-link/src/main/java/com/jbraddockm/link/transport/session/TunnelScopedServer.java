package com.jbraddockm.link.transport.session;

import java.net.InetSocketAddress;
import java.time.Duration;

/**
 * A server bound to a {@link DeviceSession}'s tunnel. It serves over the tunnel, so the session
 * tears it down — draining the in-flight work, then forcing what is left — <em>before</em> it
 * releases the tunnel, and never after. Remote access ({@code
 * com.jbraddockm.link.remoteaccess.RemoteAccess}) is the implementation; the session depends only
 * on this narrow lifecycle contract, not on the feature.
 *
 * <p>The methods are the graceful-shutdown primitive the session and the daemon drive two ways: a
 * caller-paced stop ({@link #beginShutdown} then watch {@link #activeRequests}, confirm with {@link
 * #forceShutdown}) for an explicit request, and a deadline-bounded {@link #close} for a disconnect
 * or a daemon stop.
 */
public interface TunnelScopedServer extends AutoCloseable {

  /** The address the server is bound to and serving on. */
  InetSocketAddress address();

  /** The number of requests in flight; watch it to decide whether to keep draining or to force. */
  int activeRequests();

  /** Stops accepting new work and begins a graceful drain of what is in flight. Idempotent. */
  void beginShutdown();

  /**
   * Waits for the in-flight work to finish after {@link #beginShutdown}.
   *
   * @param timeout how long to wait
   * @return {@code true} if it drained, {@code false} if the timeout elapsed first
   */
  boolean awaitIdle(Duration timeout);

  /** Stops now, interrupting any in-flight work. */
  void forceShutdown();

  /** Stops the server: a bounded graceful drain, then a force. */
  @Override
  void close();
}
