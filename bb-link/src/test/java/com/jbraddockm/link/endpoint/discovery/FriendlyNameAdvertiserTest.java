package com.jbraddockm.link.endpoint.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

import com.jbraddockm.core.mdns.MdnsService;
import com.jbraddockm.core.mdns.ServiceAdvert;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link FriendlyNameAdvertiser}: what it advertises, and that it withdraws on close. */
@DisplayName("FriendlyNameAdvertiser: advertising the bp2p Friendly service")
// The advertiser's close is exercised where it is under test; elsewhere the fake holds no resource.
@SuppressWarnings("resource")
class FriendlyNameAdvertiserTest {

  /** Captures the advert and hands back a registration that counts its closes. */
  private static final class CapturingAdvertiser implements FriendlyNameAdvertiser.Advertiser {
    ServiceAdvert captured;
    final AtomicInteger closes = new AtomicInteger();

    @Override
    public MdnsService.Registration advertise(ServiceAdvert advert) {
      captured = advert;
      return closes::incrementAndGet;
    }
  }

  @Test
  @DisplayName(
      "advertises the Friendly service with the name in the TXT and the identity in the instance")
  void advertisesTheFriendlyService() throws IOException {
    var advertiser = new CapturingAdvertiser();

    new FriendlyNameAdvertiser(advertiser).start("My Laptop", "2C1E7583");

    assertThat(advertiser.captured.type()).isEqualTo(FriendlyIdentity.SERVICE_TYPE);
    assertThat(advertiser.captured.subtype()).isEqualTo(FriendlyIdentity.SUBTYPE);
    assertThat(advertiser.captured.txt())
        .containsEntry(FriendlyIdentity.TXT_FNAME, "My Laptop")
        .containsEntry("txtvers", "1")
        .containsEntry("protovers", "2");
    assertThat(advertiser.captured.instance()).matches("Friendly_[0-9A-F]{16}_[0-9A-F]{16}");
  }

  @Test
  @DisplayName("withdraws the advert on close, once, even when closed more than once")
  void withdrawsOnCloseOnce() throws IOException {
    var advertiser = new CapturingAdvertiser();
    var friendly = new FriendlyNameAdvertiser(advertiser);
    friendly.start("My Laptop", "2C1E7583");

    friendly.close();
    friendly.close();

    assertThat(advertiser.closes).hasValue(1);
  }

  @Test
  @DisplayName("closing before a start withdraws nothing")
  void closeBeforeStart() {
    new FriendlyNameAdvertiser(new CapturingAdvertiser()).close();
  }

  @Test
  @DisplayName("a registration failure propagates from start")
  void startPropagatesFailure() {
    FriendlyNameAdvertiser.Advertiser failing =
        _ -> {
          throw new IOException("mDNS registration failed");
        };

    assertThatIOException()
        .isThrownBy(() -> new FriendlyNameAdvertiser(failing).start("My Laptop", "2C1E7583"));
  }
}
