package com.jbraddockm.core.mdns;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import javax.jmdns.ServiceTypeListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link JmDnsResponder}: each operation delegates to the JmDNS instance it wraps. */
@DisplayName("JmDnsResponder: delegating to JmDNS")
class JmDnsResponderTest {

  private static final ServiceInfo SERVICE =
      ServiceInfo.create("_bp2p._tcp.local.", "Friendly_X", "friendly", 4455, 0, 0, true, Map.of());

  private static final ServiceListener NO_OP =
      new ServiceListener() {
        @Override
        public void serviceAdded(ServiceEvent event) {}

        @Override
        public void serviceRemoved(ServiceEvent event) {}

        @Override
        public void serviceResolved(ServiceEvent event) {}
      };

  @Test
  @DisplayName("registers and unregisters a service on the JmDNS instance")
  void registerUnregister() throws IOException {
    var jmdns = new FakeJmDns();
    var responder = new JmDnsResponder(jmdns);

    responder.registerService(SERVICE);
    assertThat(jmdns.registered).containsExactly(SERVICE);

    responder.unregisterService(SERVICE);
    assertThat(jmdns.registered).isEmpty();
  }

  @Test
  @DisplayName("adds and removes a service listener by type")
  void listeners() {
    var jmdns = new FakeJmDns();
    var responder = new JmDnsResponder(jmdns);

    responder.addServiceListener("_bp2p._tcp.local.", NO_OP);
    assertThat(jmdns.listeners).containsExactly("_bp2p._tcp.local.");

    responder.removeServiceListener("_bp2p._tcp.local.", NO_OP);
    assertThat(jmdns.listeners).isEmpty();
  }

  @Test
  @DisplayName("lists a type with a timeout and returns what JmDNS found")
  void list() {
    var jmdns = new FakeJmDns();
    jmdns.toList = new ServiceInfo[] {SERVICE};
    var responder = new JmDnsResponder(jmdns);

    var found = responder.list("_bp2p._tcp.local.", 25);

    assertThat(found).containsExactly(SERVICE);
    assertThat(jmdns.listedType).isEqualTo("_bp2p._tcp.local.");
    assertThat(jmdns.listedTimeout).isEqualTo(25);
  }

  @Test
  @DisplayName("closing withdraws every service and closes the JmDNS instance")
  void close() throws IOException {
    var jmdns = new FakeJmDns();
    var responder = new JmDnsResponder(jmdns);

    responder.close();

    assertThat(jmdns.unregisteredAll).isTrue();
    assertThat(jmdns.closed).isTrue();
  }

  /** A JmDNS stand-in recording the few calls the adapter makes; everything else is unused. */
  private static final class FakeJmDns extends JmDNS {
    final List<ServiceInfo> registered = new ArrayList<>();
    final List<String> listeners = new ArrayList<>();
    ServiceInfo[] toList = new ServiceInfo[0];
    String listedType;
    long listedTimeout;
    boolean unregisteredAll;
    boolean closed;

    @Override
    public void registerService(ServiceInfo info) {
      registered.add(info);
    }

    @Override
    public void unregisterService(ServiceInfo info) {
      registered.remove(info);
    }

    @Override
    public void addServiceListener(String type, ServiceListener listener) {
      listeners.add(type);
    }

    @Override
    public void removeServiceListener(String type, ServiceListener listener) {
      listeners.remove(type);
    }

    @Override
    public ServiceInfo[] list(String type, long timeoutMillis) {
      listedType = type;
      listedTimeout = timeoutMillis;
      return toList;
    }

    @Override
    public void unregisterAllServices() {
      unregisteredAll = true;
    }

    @Override
    public void close() {
      closed = true;
    }

    // --- not used by the adapter -----------------------------------------------------------------

    @Override
    public String getName() {
      throw unused();
    }

    @Override
    public String getHostName() {
      throw unused();
    }

    @Override
    public InetAddress getInetAddress() {
      throw unused();
    }

    @Override
    public InetAddress getInterface() {
      throw unused();
    }

    @Override
    public ServiceInfo getServiceInfo(String type, String name) {
      throw unused();
    }

    @Override
    public ServiceInfo getServiceInfo(String type, String name, long timeout) {
      throw unused();
    }

    @Override
    public ServiceInfo getServiceInfo(String type, String name, boolean persistent) {
      throw unused();
    }

    @Override
    public ServiceInfo getServiceInfo(String type, String name, boolean persistent, long timeout) {
      throw unused();
    }

    @Override
    public void requestServiceInfo(String type, String name) {
      throw unused();
    }

    @Override
    public void requestServiceInfo(String type, String name, boolean persistent) {
      throw unused();
    }

    @Override
    public void requestServiceInfo(String type, String name, long timeout) {
      throw unused();
    }

    @Override
    public void requestServiceInfo(String type, String name, boolean persistent, long timeout) {
      throw unused();
    }

    @Override
    public void addServiceTypeListener(ServiceTypeListener listener) {
      throw unused();
    }

    @Override
    public void removeServiceTypeListener(ServiceTypeListener listener) {
      throw unused();
    }

    @Override
    public boolean registerServiceType(String type) {
      throw unused();
    }

    @Override
    public void printServices() {
      throw unused();
    }

    @Override
    public ServiceInfo[] list(String type) {
      throw unused();
    }

    @Override
    public Map<String, ServiceInfo[]> listBySubtype(String type) {
      throw unused();
    }

    @Override
    public Map<String, ServiceInfo[]> listBySubtype(String type, long timeout) {
      throw unused();
    }

    @Override
    public Delegate getDelegate() {
      throw unused();
    }

    @Override
    public Delegate setDelegate(Delegate delegate) {
      throw unused();
    }

    private static UnsupportedOperationException unused() {
      return new UnsupportedOperationException("not used by JmDnsResponder");
    }
  }
}
