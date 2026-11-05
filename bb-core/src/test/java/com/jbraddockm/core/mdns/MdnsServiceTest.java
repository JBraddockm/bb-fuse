package com.jbraddockm.core.mdns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MdnsService}: one responder, opened on first use and closed when the last ends.
 */
@DisplayName("MdnsService: one shared responder, ref-counted")
// The registrations are closed explicitly, in sequence, to assert the ref-count at each step.
@SuppressWarnings("resource")
class MdnsServiceTest {

  private static final ServiceAdvert ADVERT =
      new ServiceAdvert(
          "_bp2p._tcp.local.", "Friendly_X_Y", "friendly", 4455, Map.of("fname", "PC"));

  private static final ServiceListener NO_OP =
      new ServiceListener() {
        @Override
        public void serviceAdded(ServiceEvent event) {}

        @Override
        public void serviceRemoved(ServiceEvent event) {}

        @Override
        public void serviceResolved(ServiceEvent event) {}
      };

  /** A responder that records what it was asked to do. */
  private static final class FakeResponder implements MdnsService.Responder {
    final List<ServiceInfo> services = new ArrayList<>();
    final List<String> browses = new ArrayList<>();
    boolean closed;
    IOException registerFailure;

    @Override
    public void registerService(ServiceInfo service) throws IOException {
      if (registerFailure != null) {
        throw registerFailure;
      }
      services.add(service);
    }

    @Override
    public void unregisterService(ServiceInfo service) {
      services.remove(service);
    }

    @Override
    public void addServiceListener(String type, ServiceListener listener) {
      browses.add(type);
    }

    @Override
    public void removeServiceListener(String type, ServiceListener listener) {
      browses.remove(type);
    }

    @Override
    public void close() {
      closed = true;
    }
  }

  /** Opens a fresh responder each time, counting the opens. */
  private static final class CountingResponders implements MdnsService.Responders {
    int opens;
    FakeResponder current;
    IOException openFailure;
    IOException registerFailure;

    @Override
    public MdnsService.Responder open() throws IOException {
      if (openFailure != null) {
        throw openFailure;
      }
      opens++;
      current = new FakeResponder();
      current.registerFailure = registerFailure;
      return current;
    }
  }

  @Nested
  @DisplayName("advertising")
  class Advertising {

    @Test
    @DisplayName(
        "opens the responder, registers the service, and closing withdraws it and the responder")
    void opensRegistersAndCloses() throws IOException {
      var responders = new CountingResponders();
      var mdns = new MdnsService(responders);

      var registration = mdns.advertise(ADVERT);

      assertThat(responders.opens).isOne();
      assertThat(responders.current.services).hasSize(1);
      assertThat(responders.current.closed).isFalse();

      registration.close();

      assertThat(responders.current.services).isEmpty();
      assertThat(responders.current.closed).isTrue();
    }

    @Test
    @DisplayName("closing is idempotent: a second close does not withdraw again")
    void closeIsIdempotent() throws IOException {
      var responders = new CountingResponders();
      var registration = new MdnsService(responders).advertise(ADVERT);

      registration.close();
      registration.close();

      assertThat(responders.opens).isOne();
    }

    @Test
    @DisplayName("reopens a fresh responder for a later advert")
    void reopensAfterClose() throws IOException {
      var responders = new CountingResponders();
      var mdns = new MdnsService(responders);

      mdns.advertise(ADVERT).close();
      mdns.advertise(ADVERT);

      assertThat(responders.opens).isEqualTo(2);
    }

    @Test
    @DisplayName("a registration failure propagates and releases the responder it just opened")
    void registerFailureReleases() {
      var responders = new CountingResponders();
      responders.registerFailure = new IOException("no responder");
      var mdns = new MdnsService(responders);

      assertThatIOException().isThrownBy(() -> mdns.advertise(ADVERT));

      assertThat(responders.current.closed).isTrue();
    }

    @Test
    @DisplayName("an open failure propagates")
    void openFailurePropagates() {
      var responders = new CountingResponders();
      responders.openFailure = new IOException("mDNS down");

      assertThatIOException().isThrownBy(() -> new MdnsService(responders).advertise(ADVERT));
    }
  }

  @Nested
  @DisplayName("sharing one responder")
  class Sharing {

    @Test
    @DisplayName(
        "a second advert reuses the open responder; the responder closes only with the last")
    void twoAdvertsShareOneResponder() throws IOException {
      var responders = new CountingResponders();
      var mdns = new MdnsService(responders);

      var first = mdns.advertise(ADVERT);
      var second = mdns.advertise(ADVERT);

      assertThat(responders.opens).isOne();

      first.close();
      assertThat(responders.current.closed).isFalse();

      second.close();
      assertThat(responders.current.closed).isTrue();
    }

    @Test
    @DisplayName("an advert and a browse share the one responder")
    void advertAndBrowseShare() throws IOException {
      var responders = new CountingResponders();
      var mdns = new MdnsService(responders);

      var advert = mdns.advertise(ADVERT);
      var browse = mdns.browse("_bp2p._tcp.local.", NO_OP);

      assertThat(responders.opens).isOne();
      assertThat(responders.current.browses).containsExactly("_bp2p._tcp.local.");

      browse.close();
      assertThat(responders.current.browses).isEmpty();
      assertThat(responders.current.closed).isFalse();

      advert.close();
      assertThat(responders.current.closed).isTrue();
    }
  }
}
