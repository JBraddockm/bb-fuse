package com.jbraddockm.core.mdns;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

/**
 * The shared mDNS / DNS-SD facility: one responder for the whole process, used both to advertise
 * services and to browse for them. Two JmDNS instances in a process would contend on UDP 5353 and
 * keep duplicate caches, so every advert and browse goes through the one {@link #shared()}
 * instance.
 *
 * <p>The responder is opened on the first advert or browse and closed when the last one ends — each
 * {@link #advertise} and {@link #browse} returns a {@link Registration} that withdraws just that
 * one and releases its hold. The opening of the responder is a seam ({@link Responders}), so tests
 * drive the facility without a network.
 */
public final class MdnsService {

  /** Opens the one responder when a first advert or browse needs it. */
  @FunctionalInterface
  interface Responders {

    /**
     * Opens a responder.
     *
     * @return the open responder
     * @throws IOException if mDNS could not be started
     */
    Responder open() throws IOException;
  }

  /** The operations the facility needs of the one responder; a seam, so tests need no network. */
  interface Responder extends Closeable {

    void registerService(ServiceInfo service) throws IOException;

    void unregisterService(ServiceInfo service);

    void addServiceListener(String type, ServiceListener listener);

    void removeServiceListener(String type, ServiceListener listener);

    ServiceInfo[] list(String type, long timeoutMillis);
  }

  private static final System.Logger LOG = System.getLogger(MdnsService.class.getName());
  private static final MdnsService SHARED = new MdnsService(JmDnsResponder::open);

  /**
   * The process-wide facility.
   *
   * @return the shared instance
   */
  public static MdnsService shared() {
    return SHARED;
  }

  private final Responders responders;
  private final Object lock = new Object();
  private Responder responder;
  private int holds;

  MdnsService(Responders responders) {
    this.responders = responders;
  }

  /**
   * Advertises a service until the returned registration is closed.
   *
   * @param advert the service to advertise
   * @return the registration; closing it withdraws the advert
   * @throws IOException if mDNS could not be started or the service could not be registered
   */
  public Registration advertise(ServiceAdvert advert) throws IOException {
    var service = advert.toServiceInfo();
    synchronized (lock) {
      acquire();
      try {
        responder.registerService(service);
      } catch (IOException failure) {
        release();
        throw failure;
      }
      return registration(() -> responder.unregisterService(service));
    }
  }

  /**
   * Browses for a service type until the returned registration is closed.
   *
   * @param type the service type to browse, for example {@code _bp2p._tcp.local.}
   * @param listener told as instances come and go
   * @return the registration; closing it stops the browse
   * @throws IOException if mDNS could not be started
   */
  public Registration browse(String type, ServiceListener listener) throws IOException {
    synchronized (lock) {
      acquire();
      responder.addServiceListener(type, listener);
      return registration(() -> responder.removeServiceListener(type, listener));
    }
  }

  /**
   * Browses for a service type once, resolving the instances found within a window — a one-shot
   * scan rather than a standing browse.
   *
   * @param type the service type to list, for example {@code _bp2p._tcp.local.}
   * @param timeoutMillis how long to wait for instances to resolve
   * @return the resolved instances
   * @throws IOException if mDNS could not be started
   */
  public List<ServiceInfo> list(String type, long timeoutMillis) throws IOException {
    synchronized (lock) {
      acquire();
      try {
        return List.of(responder.list(type, timeoutMillis));
      } finally {
        release();
      }
    }
  }

  private void acquire() throws IOException {
    if (responder == null) {
      responder = responders.open();
    }
    holds++;
  }

  private void release() {
    if (--holds == 0 && responder != null) {
      try {
        responder.close();
      } catch (IOException failure) {
        LOG.log(System.Logger.Level.WARNING, "Failed to close the mDNS responder", failure);
      }
      responder = null;
    }
  }

  private Registration registration(Runnable withdraw) {
    return new Registration() {
      private boolean closed;

      @Override
      public void close() {
        synchronized (lock) {
          if (closed) {
            return;
          }
          closed = true;
          if (responder != null) {
            withdraw.run();
          }
          release();
        }
      }
    };
  }

  /** A live advert or browse; closing it withdraws that one and releases its hold. */
  public interface Registration extends AutoCloseable {

    /** Withdraws this advert or browse. Safe to call more than once. */
    @Override
    void close();
  }
}
