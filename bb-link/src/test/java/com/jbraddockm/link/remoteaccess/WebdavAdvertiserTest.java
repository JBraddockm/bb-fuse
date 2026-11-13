package com.jbraddockm.link.remoteaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link WebdavAdvertiser}: the proxy advert it builds, and that it withdraws on close.
 */
@DisplayName("WebdavAdvertiser: advertising the bp2p webdav share as a LAN proxy for the host ULA")
// The capturing registrar holds no real resource; the advertiser's own close is what is under test.
@SuppressWarnings("resource")
class WebdavAdvertiserTest {

  private static final Inet6Address ULA = ula();

  private static Inet6Address ula() {
    try {
      return (Inet6Address) InetAddress.getByName("fd00::7e72:a35d:65af:2df0:9966");
    } catch (IOException malformed) {
      throw new IllegalStateException(malformed);
    }
  }

  @BeforeAll
  static void quietAdvertiserLog() {
    Logger.getLogger(WebdavAdvertiser.class.getName()).setLevel(Level.WARNING);
  }

  @Test
  @DisplayName("advertises _bp2p._tcp,_webdav on the rimdav port, proxied to the ULA host label")
  void advertisesTheWebdavService() throws IOException {
    var advertiser = new CapturingAdvertiser();

    new WebdavAdvertiser(advertiser).start("2C1E7583", ULA);

    assertThat(advertiser.serviceType).isEqualTo("_bp2p._tcp,_webdav");
    assertThat(advertiser.port).isEqualTo(WebDavServer.RIMDAV_PORT);
    assertThat(advertiser.address).isEqualTo(ULA);
    // The host label is the ULA's 16 bytes without the leading 0xfd, upper-case hex (30 chars).
    assertThat(advertiser.hostName).isEqualTo("00000000007E72A35D65AF2DF09966");
    // Both version fields are 0; the device ignores a webdav advert whose txtvers or protovers
    // differs from its own.
    assertThat(advertiser.txt).containsEntry("txtvers", "0").containsEntry("protovers", "0");
    assertThat(advertiser.serviceName).matches("webdav_[0-9A-F]{16}_[0-9A-F]{16}");
  }

  @Test
  @DisplayName("folds a fresh seed each time, so two adverts for one peer differ")
  void freshSeedPerAdvert() throws IOException {
    var first = new CapturingAdvertiser();
    var second = new CapturingAdvertiser();

    new WebdavAdvertiser(first).start("2C1E7583", ULA);
    new WebdavAdvertiser(second).start("2C1E7583", ULA);

    assertThat(first.serviceName).isNotEqualTo(second.serviceName);
  }

  @Test
  @DisplayName("withdraws the advert on close, once, even when closed more than once")
  void withdrawsOnCloseOnce() throws IOException {
    var advertiser = new CapturingAdvertiser();
    var webdav = new WebdavAdvertiser(advertiser);
    webdav.start("2C1E7583", ULA);

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
    WebdavAdvertiser.ProxyAdvertiser failing =
        (_, _, _, _, _, _) -> {
          throw new IOException("dns-sd registration failed");
        };

    assertThatIOException().isThrownBy(() -> new WebdavAdvertiser(failing).start("2C1E7583", ULA));
  }

  /** Captures the registration arguments and hands back a registration that counts its closes. */
  private static final class CapturingAdvertiser implements WebdavAdvertiser.ProxyAdvertiser {
    final AtomicInteger closes = new AtomicInteger();
    String serviceName;
    String serviceType;
    int port;
    String hostName;
    Inet6Address address;
    Map<String, String> txt;

    @Override
    public AutoCloseable advertise(
        String serviceName,
        String serviceType,
        int port,
        String hostName,
        Inet6Address address,
        Map<String, String> txt) {
      this.serviceName = serviceName;
      this.serviceType = serviceType;
      this.port = port;
      this.hostName = hostName;
      this.address = address;
      this.txt = txt;
      return closes::incrementAndGet;
    }
  }
}
