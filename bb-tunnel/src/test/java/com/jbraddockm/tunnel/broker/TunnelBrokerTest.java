package com.jbraddockm.tunnel.broker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.daemon.RequestParams;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.tunnel.Tunnels;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link TunnelBroker}: leases, identity and events over a faked {@link Tunnels}. */
@DisplayName("TunnelBroker: leases, identity and events over the tunnel service")
class TunnelBrokerTest {

  /** A fixed identity so acquires that carry no certificate of their own still resolve in tests. */
  private static final TunnelBroker.IdentityResolver FALLBACK =
      _ -> Optional.of(new Tunnels.Identity(Path.of("cert.pem"), Path.of("key.pem")));

  private final FakeTunnels tunnels = new FakeTunnels();
  private final TunnelBroker broker = new TunnelBroker(tunnels, FALLBACK);

  private static RequestParams params(Object... keysAndValues) {
    return new RequestParams(Json.object(keysAndValues));
  }

  @Nested
  @DisplayName("the drop-log reason")
  class DropReason {

    @Test
    @DisplayName("maps a benign 'Socket closed' to calm text, not the exception class")
    void socketClosed() {
      assertThat(TunnelBroker.friendlyCause(new java.net.SocketException("Socket closed")))
          .isEqualTo("connection closed");
    }

    @Test
    @DisplayName("maps a connection reset to a short reason")
    void reset() {
      assertThat(TunnelBroker.friendlyCause(new java.io.IOException("Connection reset by peer")))
          .isEqualTo("connection reset");
    }

    @Test
    @DisplayName("keeps a genuine message otherwise, and is null for no cause")
    void otherwise() {
      assertThat(TunnelBroker.friendlyCause(new RuntimeException("device said no")))
          .isEqualTo("device said no");
      assertThat(TunnelBroker.friendlyCause(null)).isNull();
    }
  }

  /**
   * A {@link Tunnels} stand-in: one shared, ref-counted entry per host with canned coordinates, and
   * a hook to fire a spontaneous drop. The real pool and ref-count are covered by {@code
   * TunnelManagerTest}; here it only has to behave like the service the broker talks to.
   */
  private static final class FakeTunnels implements Tunnels {
    private record Info(String ula, String hostUla, String innerIpv4) {}

    private final Map<String, Info> up = new HashMap<>();
    private final Map<String, Integer> refs = new HashMap<>();
    private BiConsumer<String, Throwable> onDrop;

    @Override
    public synchronized Lease acquire(String host, String proposedSmbHost, Identity identity) {
      var info =
          up.computeIfAbsent(
              host,
              h ->
                  new Info(
                      "fd00::" + h,
                      "fd00::host:" + h,
                      proposedSmbHost != null ? proposedSmbHost : "169.254.9.1"));
      refs.merge(host, 1, Integer::sum);
      return new FakeLease(host, info);
    }

    @Override
    public synchronized boolean isOpen(String host) {
      return up.containsKey(host);
    }

    @Override
    public void onDrop(BiConsumer<String, Throwable> listener) {
      this.onDrop = listener;
    }

    /** Simulates a spontaneous drop of {@code host}, as a real tunnel failing would. */
    @SuppressWarnings("SameParameterValue")
    void drop(String host, Throwable cause) {
      synchronized (this) {
        up.remove(host);
        refs.remove(host);
      }
      if (onDrop != null) {
        onDrop.accept(host, cause);
      }
    }

    private synchronized void release(String host) {
      if (refs.merge(host, -1, Integer::sum) <= 0) {
        refs.remove(host);
        up.remove(host);
      }
    }

    private final class FakeLease implements Lease {
      private final String host;
      private final Info info;
      private boolean closed;

      FakeLease(String host, Info info) {
        this.host = host;
        this.info = info;
      }

      @Override
      public String deviceUla() {
        return info.ula();
      }

      @Override
      public String hostUla() {
        return info.hostUla();
      }

      @Override
      public String deviceIpv4() {
        return info.innerIpv4();
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

  /** Collects the events written to one connection. */
  private static final class Events implements TunnelBroker.EventSink {
    final List<Map<String, Object>> received = new ArrayList<>();

    @Override
    public void event(Map<String, Object> event) {
      received.add(event);
    }
  }

  @Nested
  @DisplayName("acquire / status / release")
  class Lifecycle {

    @Test
    @DisplayName("acquire reports the coordinates and token; status lists the tunnel")
    void acquireAndStatus() throws Exception {
      broker.connected("c1", new Events());

      var lease = broker.acquire("c1", params("host", "192.168.1.118", "consumer", "bb-link"));

      assertThat(lease.get("ula")).isEqualTo("fd00::192.168.1.118");
      assertThat(lease.get("hostUla")).isEqualTo("fd00::host:192.168.1.118");
      assertThat(lease.get("innerIpv4")).isEqualTo("169.254.9.1");
      assertThat(lease.get("token")).isNotNull();
      assertThat(lease.get("refs")).isEqualTo(1L);

      var tunnels = (List<?>) broker.status(params()).get("tunnels");
      assertThat(tunnels).hasSize(1);
      var only = (Map<?, ?>) tunnels.getFirst();
      assertThat(only.get("host")).isEqualTo("192.168.1.118");
      assertThat(only.get("state")).isEqualTo("UP");
      assertThat(only.get("consumers")).isEqualTo(List.of("bb-link"));
    }

    @Test
    @DisplayName("stable pins the constant 169.254.1.1 SMB address")
    void stablePins() throws Exception {
      broker.connected("c1", new Events());
      var lease = broker.acquire("c1", params("host", "192.168.1.118", "stable", true));
      assertThat(lease.get("innerIpv4")).isEqualTo("169.254.1.1");
    }

    @Test
    @DisplayName("a proposed smbHost is honoured and reported in the lease")
    void proposesSmbHost() throws Exception {
      broker.connected("c1", new Events());
      var lease = broker.acquire("c1", params("host", "192.168.1.118", "smbHost", "169.254.7.1"));
      assertThat(lease.get("innerIpv4")).isEqualTo("169.254.7.1");
    }

    @Test
    @DisplayName("two consumers share one tunnel; it stays up until the last releases")
    void sharedRefCount() throws Exception {
      broker.connected("c1", new Events());
      broker.connected("c2", new Events());

      var a = broker.acquire("c1", params("host", "192.168.1.118", "consumer", "bb-link"));
      var b = broker.acquire("c2", params("host", "192.168.1.118", "consumer", "blend"));

      assertThat(b.get("ula")).isEqualTo(a.get("ula"));
      assertThat(b.get("refs")).isEqualTo(2L);
      assertThat(tunnels.isOpen("192.168.1.118")).isTrue();

      assertThat(broker.release(params("token", a.get("token"))).get("refs")).isEqualTo(1L);
      assertThat(tunnels.isOpen("192.168.1.118")).isTrue();

      assertThat(broker.release(params("token", b.get("token"))).get("refs")).isEqualTo(0L);
      assertThat(tunnels.isOpen("192.168.1.118")).isFalse();
      assertThat(((List<?>) broker.status(params()).get("tunnels"))).isEmpty();
    }

    @Test
    @DisplayName("release of an unknown token is a no-op")
    void releaseIsIdempotent() {
      assertThat(broker.release(params("token", "nope")).get("refs")).isEqualTo(0L);
    }

    @Test
    @DisplayName("a connection's leases are released when it disconnects")
    void connectionScopedReclamation() throws Exception {
      broker.connected("c1", new Events());
      broker.acquire("c1", params("host", "192.168.1.118"));
      assertThat(tunnels.isOpen("192.168.1.118")).isTrue();

      broker.disconnected("c1");

      assertThat(tunnels.isOpen("192.168.1.118")).isFalse();
    }
  }

  @Nested
  @DisplayName("identity")
  class Identity {

    @Test
    @DisplayName("identify records the GUID so acquire {guid} rejoins the same tunnel")
    void guidRejoin() throws Exception {
      broker.connected("c1", new Events());
      var first = broker.acquire("c1", params("host", "192.168.1.118"));
      broker.identify(params("host", "192.168.1.118", "guid", "GUID-1", "pin", "2C1E7583"));

      broker.connected("c2", new Events());
      var rejoined = broker.acquire("c2", params("guid", "GUID-1", "consumer", "blend"));

      assertThat(rejoined.get("host")).isEqualTo("192.168.1.118");
      assertThat(rejoined.get("ula")).isEqualTo(first.get("ula"));
      assertThat(rejoined.get("refs")).isEqualTo(2L);
      assertThat(rejoined.get("guid")).isEqualTo("GUID-1");
    }

    @Test
    @DisplayName("acquire by an unknown GUID is refused")
    void unknownGuid() {
      broker.connected("c1", new Events());
      assertThatThrownBy(() -> broker.acquire("c1", params("guid", "GUID-X")))
          .isInstanceOf(BrokerException.class)
          .satisfies(e -> assertThat(((BrokerException) e).code()).isEqualTo("unknown_guid"));
    }

    @Test
    @DisplayName("identify of a host with no tunnel is refused")
    void identifyUnknownHost() {
      assertThatThrownBy(() -> broker.identify(params("host", "10.0.0.1", "guid", "GUID-1")))
          .isInstanceOf(BrokerException.class)
          .satisfies(e -> assertThat(((BrokerException) e).code()).isEqualTo("unknown_host"));
    }

    @Test
    @DisplayName("identify tells the device's other consumers")
    void identifyNotifiesConsumers() throws Exception {
      var one = new Events();
      var two = new Events();
      broker.connected("c1", one);
      broker.connected("c2", two);
      broker.acquire("c1", params("host", "192.168.1.118"));
      broker.acquire("c2", params("host", "192.168.1.118"));

      broker.identify(params("host", "192.168.1.118", "guid", "GUID-1", "pin", "2C1E7583"));

      assertThat(one.received).anySatisfy(e -> assertThat(e.get("event")).isEqualTo("identity"));
      assertThat(two.received).anySatisfy(e -> assertThat(e.get("event")).isEqualTo("identity"));
    }
  }

  @Nested
  @DisplayName("drop events")
  class Drops {

    @Test
    @DisplayName("a spontaneous drop notifies holders and forgets the device")
    void dropNotifiesAndForgets() throws Exception {
      var events = new Events();
      broker.connected("c1", events);
      broker.acquire("c1", params("host", "192.168.1.118"));

      tunnels.drop("192.168.1.118", new IllegalStateException("link lost"));

      assertThat(events.received)
          .anySatisfy(
              e -> {
                assertThat(e.get("event")).isEqualTo("dropped");
                assertThat(e.get("host")).isEqualTo("192.168.1.118");
              });
      assertThat(((List<?>) broker.status(params()).get("tunnels"))).isEmpty();
    }
  }

  @Nested
  @DisplayName("identity resolution")
  class IdentityResolution {

    @Test
    @DisplayName("a missing tunnel certificate is a typed cert error, not a crash")
    void missingCertificate() {
      // A real TunnelManager so the identity is actually loaded; the failure is at the file, before
      // any device I/O, so no device or sudo is needed.
      var real = new TunnelBroker(new com.jbraddockm.tunnel.TunnelManager());
      var request =
          params(
              "host", "192.168.1.118",
              "certificate", "/no/such/cert.pem",
              "key", "/no/such/key.pem");
      assertThatThrownBy(() -> real.acquire("c1", request))
          .isInstanceOf(BrokerException.class)
          .satisfies(e -> assertThat(((BrokerException) e).code()).isEqualTo("cert_not_found"));
    }

    @Test
    @DisplayName("an acquire with no identity and no daemon fallback is refused clearly")
    void noIdentity() {
      var real = new TunnelBroker(new com.jbraddockm.tunnel.TunnelManager());
      assertThatThrownBy(() -> real.acquire("c1", params("host", "192.168.1.118")))
          .isInstanceOf(BrokerException.class)
          .satisfies(e -> assertThat(((BrokerException) e).code()).isEqualTo("identity_missing"));
    }

    @Test
    @DisplayName("passing only one of certificate/key is refused")
    void incompleteIdentity() {
      assertThatThrownBy(
              () -> broker.acquire("c1", params("host", "192.168.1.118", "certificate", "/c.pem")))
          .isInstanceOf(BrokerException.class)
          .satisfies(e -> assertThat(((BrokerException) e).code()).isEqualTo("cert_incomplete"));
    }
  }

  @Nested
  @DisplayName("daemon log")
  class Logging {

    private final List<String> log = new ArrayList<>();
    private final TunnelBroker logged = new TunnelBroker(tunnels, FALLBACK, log::add);

    @Test
    @DisplayName("notes a tunnel coming up with its coordinates")
    void notesTunnelUp() throws Exception {
      logged.connected("c1", new Events());

      logged.acquire("c1", params("host", "192.168.1.118", "consumer", "bb-link"));

      assertThat(log).anyMatch(line -> line.startsWith("192.168.1.118 [bb-link] up"));
    }

    @Test
    @DisplayName("notes an identity learned and the tunnel going down")
    void notesIdentityAndDown() throws Exception {
      logged.connected("c1", new Events());
      var lease = logged.acquire("c1", params("host", "192.168.1.118"));
      logged.identify(params("host", "192.168.1.118", "guid", "GUID-1", "pin", "2C1E7583"));

      logged.release(params("token", lease.get("token")));

      assertThat(log)
          .anyMatch(line -> line.startsWith("2C1E7583 identified") && line.contains("GUID-1"));
      assertThat(log)
          .anyMatch(
              line -> line.startsWith("2C1E7583 [unknown] down") && line.contains("tunnel closed"));
    }

    @Test
    @DisplayName("notes a spontaneous drop with its cause")
    void notesDrop() throws Exception {
      logged.connected("c1", new Events());
      logged.acquire("c1", params("host", "192.168.1.118"));

      tunnels.drop("192.168.1.118", new IllegalStateException("link lost"));

      assertThat(log).anyMatch(line -> line.startsWith("192.168.1.118 dropped"));
    }
  }
}
