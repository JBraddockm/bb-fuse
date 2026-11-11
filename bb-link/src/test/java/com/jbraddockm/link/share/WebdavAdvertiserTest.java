package com.jbraddockm.link.share;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

import com.jbraddockm.core.mdns.MdnsService;
import com.jbraddockm.core.mdns.ServiceAdvert;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link WebdavAdvertiser}: what it advertises, and that it withdraws on close. */
@DisplayName("WebdavAdvertiser: advertising the bp2p webdav file share")
// The advertiser's close is exercised where it is under test; elsewhere the fake holds no resource.
@SuppressWarnings("resource")
class WebdavAdvertiserTest {

  @BeforeAll
  static void quietAdvertiserLog() {
    Logger.getLogger(WebdavAdvertiser.class.getName()).setLevel(Level.WARNING);
  }

  /** Captures the advert and hands back a registration that counts its closes. */
  private static final class CapturingAdvertiser implements WebdavAdvertiser.Advertiser {
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
      "advertises the webdav bp2p service on the rimdav port with the identity in the instance")
  void advertisesTheWebdavService() throws IOException {
    var advertiser = new CapturingAdvertiser();

    new WebdavAdvertiser(advertiser).start("2C1E7583");

    assertThat(advertiser.captured.type()).isEqualTo(WebdavIdentity.SERVICE_TYPE);
    assertThat(advertiser.captured.port()).isEqualTo(WebDavServer.RIMDAV_PORT);
    assertThat(advertiser.captured.subtype()).isNull();
    assertThat(advertiser.captured.txt())
        .containsEntry("txtvers", "1")
        .containsEntry("protovers", "2");
    assertThat(advertiser.captured.instance()).matches("webdav_[0-9A-F]{16}_[0-9A-F]{16}");
  }

  @Test
  @DisplayName("folds a fresh seed each time, so two adverts for one peer differ")
  void freshSeedPerAdvert() throws IOException {
    var first = new CapturingAdvertiser();
    var second = new CapturingAdvertiser();

    new WebdavAdvertiser(first).start("2C1E7583");
    new WebdavAdvertiser(second).start("2C1E7583");

    assertThat(first.captured.instance()).isNotEqualTo(second.captured.instance());
  }

  @Test
  @DisplayName("withdraws the advert on close, once, even when closed more than once")
  void withdrawsOnCloseOnce() throws IOException {
    var advertiser = new CapturingAdvertiser();
    var webdav = new WebdavAdvertiser(advertiser);
    webdav.start("2C1E7583");

    webdav.close();
    webdav.close();

    assertThat(advertiser.closes).hasValue(1);
  }

  @Test
  @DisplayName("closing before a start withdraws nothing")
  void closeBeforeStart() {
    new WebdavAdvertiser(new CapturingAdvertiser()).close();
  }

  @Test
  @DisplayName("a registration failure propagates from start")
  void startPropagatesFailure() {
    WebdavAdvertiser.Advertiser failing =
        _ -> {
          throw new IOException("mDNS registration failed");
        };

    assertThatIOException().isThrownBy(() -> new WebdavAdvertiser(failing).start("2C1E7583"));
  }
}
