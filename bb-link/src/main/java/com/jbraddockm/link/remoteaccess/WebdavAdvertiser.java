package com.jbraddockm.link.remoteaccess;

import java.io.IOException;
import java.net.Inet6Address;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Advertises this workstation's bp2p <b>webdav</b> file share over mDNS so that a paired BlackBerry
 * 10 device discovers it and mounts it in the File Manager. It is the discovery half of the host
 * file server: {@link WebDavServer} serves the share on the host's tunnel ULA, and this advert —
 * matching what BlackBerry Link's {@code RimRegisterServicePassthrough} puts on the wire — is how
 * the device learns the share is there.
 *
 * <p><b>Why a system-responder proxy advert, not JmDNS.</b> The device browses {@code
 * _bp2p._tcp,_webdav} on the <b>LAN</b>, and the service must resolve to the host's tunnel
 * <b>ULA</b> (so the device dials it over the tunnel), published as {@code <ULAhex>.local → ULA} —
 * a host record for an address that lives on the utun, not the LAN interface. JmDNS advertises only
 * the address of the interface it is bound to, so it cannot publish that cross-interface record.
 * The system mDNS responder can, through a <i>proxy</i> registration ({@code dns-sd -P}), which is
 * how BlackBerry Link's host does it. See the {@link ProxyAdvertiser} seam and {@link
 * DnsSdProxyAdvertiser}.
 *
 * <p>Keep an instance alive for as long as the share should be reachable; {@link #close()}
 * withdraws the advert.
 */
public final class WebdavAdvertiser implements AutoCloseable {

  private static final System.Logger LOG = System.getLogger(WebdavAdvertiser.class.getName());

  private final ProxyAdvertiser advertiser;
  private AutoCloseable registration;

  /** Advertises through the system mDNS responder. */
  public WebdavAdvertiser() {
    this(DnsSdProxyAdvertiser.INSTANCE);
  }

  /**
   * Advertises through the given registrar.
   *
   * @param advertiser registers the service
   */
  WebdavAdvertiser(ProxyAdvertiser advertiser) {
    this.advertiser = advertiser;
  }

  /**
   * Registers the webdav service and begins advertising it on the LAN, resolving to the host ULA.
   *
   * @param peerIdentifier the identifier the device stored for this workstation at pairing, folded
   *     into the instance name so the device recognises the share as this computer's
   * @param hostUla the host's bp2p tunnel ULA — the address the service resolves to
   * @throws IOException if the advert cannot be registered
   */
  public void start(String peerIdentifier, Inet6Address hostUla) throws IOException {
    var seed = WebdavIdentity.newSeed();
    var token = WebdavIdentity.token(peerIdentifier, seed);
    var instance = WebdavIdentity.instanceName(seed, token);
    var hostLabel = WebdavIdentity.hostLabel(hostUla);

    // Both TXT version fields must be 0: the device compares the advertised txtvers and protovers
    // against its own (both 0) and ignores any webdav service whose values differ, so a non-zero
    // value here leaves the share undiscoverable.
    Map<String, String> txt = new LinkedHashMap<>();
    txt.put("txtvers", "0");
    txt.put("protovers", "0");

    registration =
        advertiser.advertise(
            instance, WebdavIdentity.DNSSD_TYPE, WebDavServer.RIMDAV_PORT, hostLabel, hostUla, txt);
    LOG.log(
        System.Logger.Level.INFO,
        () ->
            "Advertising bp2p webdav service "
                + instance
                + " as ["
                + hostUla.getHostAddress()
                + "]:"
                + WebDavServer.RIMDAV_PORT
                + " (host "
                + hostLabel
                + ".local)");
  }

  /**
   * Best-effort {@link #start}: returns the running advertiser, or {@link Optional#empty()} (with a
   * warning) if it cannot start. Never throws — discovery is a courtesy, so nothing it does may
   * break the share it rides along with.
   *
   * @param peerIdentifier the identifier the device stored for this workstation at pairing
   * @param hostUla the host's bp2p tunnel ULA
   * @return the running advertiser, or empty when it could not start
   */
  public static Optional<WebdavAdvertiser> tryStart(String peerIdentifier, Inet6Address hostUla) {
    var advertiser = new WebdavAdvertiser();
    try {
      advertiser.start(peerIdentifier, hostUla);
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
    var local = registration;
    registration = null;
    if (local != null) {
      try {
        local.close();
      } catch (Exception ignored) {
        // withdrawing the advert is best-effort
      }
    }
  }

  /**
   * Registers a DNS-SD service through the system mDNS responder's <b>proxy</b> mode — advertising
   * a host record for an address this machine does not hold on the advertising interface. A seam,
   * so a test needs no responder.
   */
  @FunctionalInterface
  interface ProxyAdvertiser {

    /**
     * Advertises a service with a proxy host record.
     *
     * @param serviceName the DNS-SD instance name
     * @param serviceType the service type, including any subtype (e.g. {@code _bp2p._tcp,_webdav})
     * @param port the SRV port
     * @param hostName the host label the SRV targets (the {@code .local} domain is added)
     * @param address the address the host record resolves to
     * @param txt the TXT entries
     * @return the registration that withdraws the advert
     * @throws IOException if the service could not be registered
     */
    AutoCloseable advertise(
        String serviceName,
        String serviceType,
        int port,
        String hostName,
        Inet6Address address,
        Map<String, String> txt)
        throws IOException;
  }
}
