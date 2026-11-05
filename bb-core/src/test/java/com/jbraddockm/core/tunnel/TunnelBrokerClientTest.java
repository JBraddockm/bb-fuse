package com.jbraddockm.core.tunnel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.json.Json;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TunnelBrokerClient}: request/reply correlation, lease and status parsing, error
 * replies and pushed events — driven against a fake broker over a real Unix-domain socket (local
 * and deterministic: no device, no network port).
 */
@DisplayName("TunnelBrokerClient: talking to the broker over a Unix-domain socket")
class TunnelBrokerClientTest {

  private FakeBroker broker;

  @BeforeEach
  void startBroker() throws IOException {
    broker = new FakeBroker();
  }

  @AfterEach
  void stopBroker() throws IOException {
    broker.close();
  }

  private TunnelBrokerClient connect() throws IOException {
    var client = TunnelBrokerClient.connect(broker.socket(), Duration.ofSeconds(5));
    broker.awaitConnected();
    return client;
  }

  private static BrokerEvent poll(LinkedBlockingQueue<BrokerEvent> events, String type)
      throws InterruptedException {
    for (int attempt = 0; attempt < 50; attempt++) {
      var event = events.poll(100, TimeUnit.MILLISECONDS);
      if (event != null && type.equals(event.type())) {
        return event;
      }
    }
    throw new IllegalStateException("no '" + type + "' event arrived");
  }

  @Nested
  @DisplayName("acquire and release")
  class AcquireAndRelease {

    @Test
    @DisplayName("acquire sends the host and consumer and returns the granted lease")
    void acquire() throws IOException {
      try (var client = connect()) {
        var lease = client.acquire("192.168.1.118", "169.254.1.1", "bb-link");

        assertThat(lease.ula()).isEqualTo("fd00::192.168.1.118");
        assertThat(lease.innerIpv4()).isEqualTo("169.254.1.1");
        assertThat(lease.token()).isNotBlank();
        assertThat(lease.refs()).isEqualTo(1);

        var sent = broker.lastParams("acquire");
        assertThat(sent)
            .containsEntry("host", "192.168.1.118")
            .containsEntry("consumer", "bb-link")
            .doesNotContainKey("certificate");
      }
    }

    @Test
    @DisplayName("acquire with a cert and key tells the daemon which identity to present")
    void acquireWithIdentity() throws IOException {
      try (var client = connect()) {
        client.acquire(
            "192.168.1.118", null, "bb-link", Path.of("/d/cert.pem"), Path.of("/d/key.pem"));

        var sent = broker.lastParams("acquire");
        assertThat(sent)
            .containsEntry("certificate", "/d/cert.pem")
            .containsEntry("key", "/d/key.pem")
            .doesNotContainKey("smbHost");
      }
    }

    @Test
    @DisplayName("acquireByGuid rejoins a device by its recorded GUID")
    void acquireByGuid() throws IOException {
      try (var client = connect()) {
        var lease = client.acquireByGuid("GUID-1", "blend");

        assertThat(lease.token()).isNotBlank();
        assertThat(broker.lastParams("acquire")).containsEntry("guid", "GUID-1");
      }
    }

    @Test
    @DisplayName("release sends the token")
    void release() throws IOException {
      try (var client = connect()) {
        client.release("T-1");

        assertThat(broker.lastParams("release")).containsEntry("token", "T-1");
      }
    }
  }

  @Nested
  @DisplayName("status and identify")
  class StatusAndIdentify {

    @Test
    @DisplayName("status parses each tunnel row, including the consumer list")
    void status() throws IOException {
      try (var client = connect()) {
        var tunnels = client.status();

        assertThat(tunnels)
            .singleElement()
            .satisfies(
                tunnel -> {
                  assertThat(tunnel.host()).isEqualTo("192.168.1.118");
                  assertThat(tunnel.state()).isEqualTo("UP");
                  assertThat(tunnel.refs()).isEqualTo(2);
                  assertThat(tunnel.consumers()).containsExactly("bb-link", "blend");
                });
      }
    }

    @Test
    @DisplayName("identify reports the device's host, GUID and PIN")
    void identify() throws IOException {
      try (var client = connect()) {
        client.identify("192.168.1.118", "GUID-1", "2C1E7583");

        assertThat(broker.lastParams("identify"))
            .containsEntry("host", "192.168.1.118")
            .containsEntry("guid", "GUID-1")
            .containsEntry("pin", "2C1E7583");
      }
    }
  }

  @Nested
  @DisplayName("errors and events")
  class ErrorsAndEvents {

    @Test
    @DisplayName("a refusal becomes a BrokerClientException carrying the broker's code")
    void refusal() throws IOException {
      broker.failWith("unknown_guid");
      try (var client = connect()) {
        assertThatThrownBy(() -> client.acquireByGuid("nope", "blend"))
            .isInstanceOfSatisfying(
                BrokerClientException.class, e -> assertThat(e.code()).isEqualTo("unknown_guid"));
      }
    }

    @Test
    @DisplayName("a request that goes unanswered times out as an IOException")
    void timeout() throws IOException {
      broker.dropRequests();
      try (var client = TunnelBrokerClient.connect(broker.socket(), Duration.ofMillis(500))) {
        assertThatThrownBy(client::status)
            .isInstanceOf(IOException.class)
            .hasMessageContaining("did not reply");
      }
    }

    @Test
    @DisplayName("pushed events reach the registered listener")
    void events() throws IOException, InterruptedException {
      try (var client = connect()) {
        var events = new LinkedBlockingQueue<BrokerEvent>();
        client.onEvent(events::add);

        broker.push(Json.object("event", "dropped", "host", "192.168.1.118", "cause", "link lost"));
        broker.push(Json.object("event", "identity", "guid", "GUID-1", "pin", "2C1E7583"));

        var dropped = poll(events, "dropped");
        assertThat(dropped.host()).isEqualTo("192.168.1.118");
        assertThat(dropped.cause()).isEqualTo("link lost");

        var identity = poll(events, "identity");
        assertThat(identity.guid()).isEqualTo("GUID-1");
        assertThat(identity.pin()).isEqualTo("2C1E7583");
      }
    }
  }

  /**
   * A minimal broker over a Unix-domain socket: it records each request's params, replies with
   * canned data, and can force an error reply, drop a request (to make the client time out), or
   * push an unsolicited event to the connected client.
   */
  private static final class FakeBroker implements AutoCloseable {

    private final Path socket =
        Path.of("/tmp", "bb-core-broker-test-" + System.nanoTime() + ".sock");
    private final ServerSocketChannel server;
    private final Map<String, Map<String, Object>> lastParams = new ConcurrentHashMap<>();
    private volatile PrintStream out;
    private volatile String errorCode;
    private volatile boolean drop;

    FakeBroker() throws IOException {
      server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
      server.bind(UnixDomainSocketAddress.of(socket));
      Thread.ofPlatform().daemon().name("fake-broker").start(this::serve);
    }

    Path socket() {
      return socket;
    }

    void failWith(String code) {
      errorCode = code;
    }

    void dropRequests() {
      drop = true;
    }

    Map<String, Object> lastParams(String op) {
      return lastParams.get(op);
    }

    /** Waits until a client has connected and the reply stream is ready. */
    void awaitConnected() {
      for (int attempt = 0; attempt < 200 && out == null; attempt++) {
        try {
          Thread.sleep(5);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          return;
        }
      }
    }

    void push(Map<String, Object> message) {
      var stream = out;
      if (stream == null) {
        return;
      }
      var line = (Json.write(message) + "\n").getBytes(StandardCharsets.UTF_8);
      synchronized (this) {
        stream.write(line, 0, line.length);
        stream.flush();
      }
    }

    private void serve() {
      try (var channel = server.accept()) {
        out = new PrintStream(Channels.newOutputStream(channel), false, StandardCharsets.UTF_8);
        var reader =
            new BufferedReader(
                new InputStreamReader(Channels.newInputStream(channel), StandardCharsets.UTF_8));
        push(Json.object("event", "hello")); // the real broker greets on connect
        String line;
        while ((line = reader.readLine()) != null) {
          if (!line.isBlank()) {
            handle(line);
          }
        }
      } catch (IOException closed) {
        // the client or the test closed the socket
      }
    }

    @SuppressWarnings("unchecked")
    private void handle(String line) {
      var request = (Map<String, Object>) Json.parse(line);
      var op = (String) request.get("op");
      var params = (Map<String, Object>) request.getOrDefault("params", Map.of());
      lastParams.put(op, params);
      if (drop) {
        return;
      }
      var id = request.get("id");
      if (errorCode != null) {
        push(
            Json.object(
                "ok",
                false,
                "id",
                id,
                "error",
                Json.object("code", errorCode, "message", "refused")));
        return;
      }
      push(reply(op, id, params));
    }

    private static Map<String, Object> reply(String op, Object id, Map<String, Object> params) {
      return switch (op) {
        case "acquire" -> {
          var host = params.getOrDefault("host", params.get("guid"));
          var smbHost = params.getOrDefault("smbHost", "169.254.9.1");
          yield Json.object(
              "ok",
              true,
              "id",
              id,
              "token",
              "T-" + host,
              "ula",
              "fd00::" + host,
              "innerIpv4",
              smbHost,
              "refs",
              1L);
        }
        case "status" ->
            Json.object(
                "ok",
                true,
                "id",
                id,
                "tunnels",
                List.of(
                    Json.object(
                        "host",
                        "192.168.1.118",
                        "state",
                        "UP",
                        "ula",
                        "fd00::192.168.1.118",
                        "innerIpv4",
                        "169.254.1.1",
                        "guid",
                        "GUID-1",
                        "pin",
                        "2C1E7583",
                        "refs",
                        2L,
                        "consumers",
                        List.of("bb-link", "blend"))));
        default -> Json.object("ok", true, "id", id);
      };
    }

    @Override
    public void close() throws IOException {
      server.close();
      Files.deleteIfExists(socket);
    }
  }
}
