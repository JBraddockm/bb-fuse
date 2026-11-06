package com.jbraddockm.tunnel;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Opens and shares one bp2p tunnel per device, each with a distinct inner-IPv4 pair from an {@link
 * InnerAddressPool}, so several devices can be connected at once without an inner-address
 * collision.
 *
 * <p>The tunnel is a <b>shared transport</b>: bb-link and Blend are peers that each {@link
 * #acquire} a device's tunnel and {@link Lease#close release} it. One tunnel is kept per device,
 * ref-counted — the first consumer opens it, the last to close brings it down and returns its
 * address to the pool.
 *
 * <p>Keyed by the <b>transport address</b> the tunnel is opened with (the device's USB host−1 or
 * its Wi-Fi LAN address), because that is what is known before the tunnel exists: the device's ULA
 * is learned only from {@code CONNECT_RESP} once the tunnel is up, and is then handed to consumers
 * as a coordinate (with the allocated inner IPv4) to connect with.
 *
 * <p>Thread-safe. The actual bring-up is an injectable {@link Opener} seam so the lease and
 * ref-count logic is unit-tested without a device or {@code sudo}; the default opens a real {@link
 * Tunnel}.
 */
public final class TunnelManager implements Tunnels {

  private final InnerAddressPool pool;
  private final Opener opener;
  private final Map<String, Entry> byHost = new HashMap<>();

  /**
   * Told {@code (host, cause)} when a tunnel drops on its own; set by a consumer like the broker.
   */
  private volatile BiConsumer<String, Throwable> onDrop;

  /** A manager that opens real tunnels, with a fresh address pool. */
  public TunnelManager() {
    this(new InnerAddressPool(), null);
  }

  /**
   * A manager with an explicit pool and opener; a {@code null} opener uses the real tunnel
   * bring-up.
   */
  TunnelManager(InnerAddressPool pool, Opener opener) {
    this.pool = Objects.requireNonNull(pool, "pool");
    this.opener = opener != null ? opener : this::liveOpen;
  }

  /**
   * The tunnel for the device reached at {@code host}: opened if it is not already up, or the
   * existing one with its reference count raised. {@code proposedSmbHost} (an inner {@code
   * 169.254.x.1} address, or {@code null} for no preference) is honoured when it is free and valid,
   * otherwise a free pair is allocated; the lease's {@link Lease#deviceIpv4()} is the granted
   * address. The caller closes the returned {@link Lease} when done.
   *
   * @param host the transport address the tunnel is opened with (USB host−1, or the Wi-Fi LAN
   *     address)
   * @param proposedSmbHost the inner {@code 169.254.x.1} address to prefer, or {@code null} for
   *     none
   * @return a hold on the tunnel, carrying the coordinates to connect with
   * @throws Exception if the tunnel cannot be opened; the allocated pair is returned to the pool
   */
  @Override
  public synchronized Lease acquire(String host, String proposedSmbHost, Identity identity)
      throws Exception {
    Objects.requireNonNull(host, "host");
    Objects.requireNonNull(identity, "identity");
    var entry = byHost.get(host);
    if (entry == null) {
      var inner =
          proposedSmbHost == null
              ? pool.allocate()
              : pool.allocate(preferredOctet(proposedSmbHost));
      Connection connection;
      try {
        connection = opener.open(host, inner, identity, cause -> evict(host, cause));
      } catch (Exception e) {
        pool.release(inner);
        throw e;
      }
      entry = new Entry(inner, connection);
      byHost.put(host, entry);
    }
    entry.refs++;
    return new Lease(host, entry);
  }

  /**
   * The device third octet of a proposed inner SMB host ({@code 169.254.<k>.1}), or {@code 0} — an
   * octet the pool counters — when the host is not a {@code 169.254.x.y} address.
   */
  private static int preferredOctet(String smbHost) {
    var parts = smbHost.split("\\.");
    if (parts.length == 4 && parts[0].equals("169") && parts[1].equals("254")) {
      try {
        return Integer.parseInt(parts[2]);
      } catch (NumberFormatException _) {
        return 0;
      }
    }
    return 0;
  }

  @Override
  public synchronized boolean isOpen(String host) {
    return byHost.containsKey(host);
  }

  @Override
  public void onDrop(BiConsumer<String, Throwable> listener) {
    this.onDrop = listener;
  }

  private synchronized void release(String host) {
    var entry = byHost.get(host);
    if (entry == null || --entry.refs > 0) {
      return;
    }
    byHost.remove(host);
    try {
      entry.connection.close();
    } finally {
      pool.release(entry.inner);
    }
  }

  /**
   * Drops a tunnel that closed on its own, returning its address to the pool and notifying {@link
   * #onDrop}. A clean last-lease close reaches here too (the real {@link Tunnel} fires its {@code
   * onClosed} on close), but by then {@link #release} has already removed the entry, so nothing is
   * evicted and no listener fires — only a genuine drop, where the entry is still present, does.
   * The listener is called outside the lock so it cannot deadlock against the manager.
   */
  private void evict(String host, Throwable cause) {
    boolean evicted;
    synchronized (this) {
      var entry = byHost.remove(host);
      evicted = entry != null;
      if (evicted) {
        pool.release(entry.inner);
      }
    }
    var listener = onDrop;
    if (evicted && listener != null) {
      listener.accept(host, cause);
    }
  }

  // The Tunnel outlives this method: it is held by the returned Connection and closed on lease
  // release, not here, so it is deliberately not a try-with-resources.
  @SuppressWarnings("resource")
  private Connection liveOpen(
      String host, InnerIpv4 inner, Identity identity, Consumer<Throwable> onDrop)
      throws Exception {
    var cfg =
        TunnelConfig.fromFiles(
            host, identity.certChainPem(), identity.keyPem(), inner.pcIpv4(), inner.deviceIpv4());
    Tunnel tunnel;
    try {
      tunnel = Tunnel.open(cfg, onDrop);
    } catch (Exception e) {
      throw e; // a normal failure propagates unwrapped
    } catch (Throwable t) {
      // Wrap a non-Exception Throwable (an Error) as an Exception so acquire()'s catch (Exception)
      // still runs and returns the inner-IPv4 pair to the pool.
      throw new IllegalStateException("tunnel bring-up failed for " + host, t);
    }
    return new Connection() {
      @Override
      public String deviceUla() {
        return tunnel.deviceUla();
      }

      @Override
      public void close() {
        tunnel.close();
      }
    };
  }

  /**
   * Opens the transport to {@code host} with the given inner-IPv4 pair, yielding a {@link
   * Connection}.
   */
  @FunctionalInterface
  interface Opener {
    /**
     * Opens the transport to {@code host} with {@code inner}, presenting {@code identity} as the
     * client certificate. The opener wires the connection's <b>spontaneous</b> drop (the link
     * failing on its own, not a {@link Connection#close}) to call {@code onDrop}, so the manager
     * can evict the tunnel and tell its consumers.
     */
    Connection open(String host, InnerIpv4 inner, Identity identity, Consumer<Throwable> onDrop)
        throws Exception;
  }

  /**
   * A live tunnel the manager holds: its device ULA (from {@code CONNECT_RESP}) and how to close
   * it.
   */
  interface Connection {
    String deviceUla();

    void close();
  }

  private static final class Entry {
    final InnerIpv4 inner;
    final Connection connection;
    int refs;

    Entry(InnerIpv4 inner, Connection connection) {
      this.inner = inner;
      this.connection = connection;
    }
  }

  /**
   * A hold on a device's tunnel: the coordinates a consumer connects with, and a ref-counted close.
   */
  public final class Lease implements Tunnels.Lease {
    private final String host;
    private final Entry entry;
    private boolean closed;

    private Lease(String host, Entry entry) {
      this.host = host;
      this.entry = entry;
    }

    @Override
    public String deviceUla() {
      return entry.connection.deviceUla();
    }

    @Override
    public String deviceIpv4() {
      return entry.inner.deviceIpv4();
    }

    @Override
    public String host() {
      return host;
    }

    @Override
    public void close() {
      if (!closed) {
        closed = true;
        release(host);
      }
    }
  }
}
