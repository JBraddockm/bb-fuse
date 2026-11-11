package com.jbraddockm.link.share;

import com.jbraddockm.core.mdns.MdnsService;
import com.jbraddockm.core.mdns.ServiceAdvert;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Advertises this workstation's bp2p <b>webdav</b> file share over mDNS so that a paired BlackBerry
 * 10 device discovers it and mounts it in the File Manager. It is the discovery half of the host
 * file server (tracker N1): {@link WebDavServer} serves the share on the host's tunnel ULA, and
 * this advert is how the device learns the share is there.
 *
 * <p>It registers a service of type {@link WebdavIdentity#SERVICE_TYPE} with the instance name
 * {@code webdav_<seed>_<token>} (see {@link WebdavIdentity}) on {@link WebDavServer#RIMDAV_PORT}
 * and a TXT record carrying the DNS-SD envelope. The advert rides the shared {@link MdnsService},
 * so it and the Wi-Fi device browse share one JmDNS instance.
 *
 * <p>Keep an instance alive for as long as the share should be reachable; {@link #close()}
 * withdraws the advert.
 */
public final class WebdavAdvertiser implements AutoCloseable {

  private static final System.Logger LOG = System.getLogger(WebdavAdvertiser.class.getName());

  private final Advertiser advertiser;
  private MdnsService.Registration advert;

  /** Advertises over the shared mDNS facility. */
  public WebdavAdvertiser() {
    this(MdnsService.shared()::advertise);
  }

  /**
   * Advertises over the given facility.
   *
   * @param advertiser registers the service
   */
  WebdavAdvertiser(Advertiser advertiser) {
    this.advertiser = advertiser;
  }

  /**
   * Registers the webdav service and begins advertising. Blocks only for the initial mDNS
   * registration.
   *
   * @param peerIdentifier the identifier the device stored for this workstation at pairing, folded
   *     into the instance name so the device recognises the share as this computer's
   * @throws IOException if mDNS registration fails
   */
  public void start(String peerIdentifier) throws IOException {
    var seed = WebdavIdentity.newSeed();
    var token = WebdavIdentity.token(peerIdentifier, seed);
    var instance = WebdavIdentity.instanceName(seed, token);

    Map<String, String> txt = new HashMap<>();
    txt.put("txtvers", "1");
    txt.put("protovers", "2");

    advert =
        advertiser.advertise(
            new ServiceAdvert(
                WebdavIdentity.SERVICE_TYPE, instance, null, WebDavServer.RIMDAV_PORT, txt));
    LOG.log(
        System.Logger.Level.INFO,
        () ->
            "Advertising bp2p webdav service " + instance + " on port " + WebDavServer.RIMDAV_PORT);
  }

  /**
   * Best-effort {@link #start}: returns the running advertiser, or {@link Optional#empty()} (with a
   * warning) if it cannot start. Never throws — discovery is a courtesy, so nothing it does may
   * break the share it rides along with. This catches more than the {@link IOException} {@link
   * #start} declares: in a native image mDNS can fail with a runtime exception or a linkage error,
   * and those must be swallowed too.
   *
   * @param peerIdentifier the identifier the device stored for this workstation at pairing
   * @return the running advertiser, or empty when it could not start
   */
  public static Optional<WebdavAdvertiser> tryStart(String peerIdentifier) {
    var advertiser = new WebdavAdvertiser();
    try {
      advertiser.start(peerIdentifier);
      return Optional.of(advertiser);
    } catch (Exception | LinkageError failure) {
      LOG.log(System.Logger.Level.WARNING, "webdav advertising failed to start", failure);
    }
    advertiser.close();
    return Optional.empty();
  }

  /** Withdraws the advert. Safe to call more than once. */
  @Override
  public void close() {
    var local = advert;
    advert = null;
    if (local != null) {
      local.close();
    }
  }

  /** Registers a service over mDNS; a seam, so a test needs no mDNS responder. */
  @FunctionalInterface
  interface Advertiser {

    /**
     * Advertises a service.
     *
     * @param advert the service to advertise
     * @return the registration that withdraws it
     * @throws IOException if the service could not be registered
     */
    MdnsService.Registration advertise(ServiceAdvert advert) throws IOException;
  }
}
