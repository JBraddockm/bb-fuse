package com.jbraddockm.core.mdns;

import java.io.IOException;
import java.net.InetAddress;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

/** The {@link MdnsService.Responder} backed by a real {@link JmDNS} instance. */
final class JmDnsResponder implements MdnsService.Responder {

  private final JmDNS jmdns;

  private JmDnsResponder(JmDNS jmdns) {
    this.jmdns = jmdns;
  }

  /**
   * Opens a JmDNS responder on the local host's address.
   *
   * @return the responder
   * @throws IOException if JmDNS could not be started
   */
  static JmDnsResponder open() throws IOException {
    return new JmDnsResponder(JmDNS.create(InetAddress.getLocalHost()));
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
  public void close() throws IOException {
    jmdns.unregisterAllServices();
    jmdns.close();
  }
}
