package com.jbraddockm.core.mdns;

import java.io.IOException;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

/** The {@link MdnsService.Responder} backed by a real {@link JmDNS} instance. */
final class JmDnsResponder implements MdnsService.Responder {

  private final JmDNS jmdns;

  // Package-private for tests, which inject a fake JmDNS; production opens a real one via open().
  JmDnsResponder(JmDNS jmdns) {
    this.jmdns = jmdns;
  }

  /**
   * Opens a JmDNS responder on the default interface. Binding to a specific address (such as the
   * one {@code InetAddress.getLocalHost()} returns, which can be loopback) misses the multicast, so
   * the no-argument {@link JmDNS#create()} is used.
   *
   * @return the responder
   * @throws IOException if JmDNS could not be started
   */
  static JmDnsResponder open() throws IOException {
    return new JmDnsResponder(JmDNS.create());
  }

  @Override
  public void registerService(ServiceInfo service) throws IOException {
    jmdns.registerService(service);
  }

  @Override
  public void unregisterService(ServiceInfo service) {
    jmdns.unregisterService(service);
  }

  @Override
  public void addServiceListener(String type, ServiceListener listener) {
    jmdns.addServiceListener(type, listener);
  }

  @Override
  public void removeServiceListener(String type, ServiceListener listener) {
    jmdns.removeServiceListener(type, listener);
  }

  @Override
  public ServiceInfo[] list(String type, long timeoutMillis) {
    return jmdns.list(type, timeoutMillis);
  }

  @Override
  public void close() throws IOException {
    jmdns.unregisterAllServices();
    jmdns.close();
  }
}
