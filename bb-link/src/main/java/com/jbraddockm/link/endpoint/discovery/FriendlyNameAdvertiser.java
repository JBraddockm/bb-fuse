package com.jbraddockm.link.endpoint.discovery;

import com.jbraddockm.core.mdns.MdnsService;
import com.jbraddockm.core.mdns.ServiceAdvert;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Advertises this workstation's bp2p <b>Friendly</b> service over mDNS so that a paired BlackBerry
 * 10 device learns our name and shows it in <i>Settings &gt; Device Connections</i> (instead of the
 * {@code Desktop-XXXX} fallback). This is the piece the real BlackBerry Link / PeerManager does and
 * that bb-link was missing: pairing only enrols the certificate; the display name reaches the
 * device only via this DNS-SD advert.
 *
 * <p>It registers a service of type {@link FriendlyIdentity#SERVICE_TYPE} with the instance name
 * {@code Friendly_<seed>_<deviceHash>} (see {@link FriendlyIdentity}) and a TXT record carrying
 * {@code fname=<name>}. The device resolves it, reads {@code fname}, matches the {@code deviceHash}
 * to our paired certificate, and writes {@code /pps/services/bp2p/devices/<id>/devicename}.
 *
 * <p>The advert rides the shared {@link MdnsService}, so it and the Wi-Fi device browse share one
 * JmDNS instance. Keep an instance alive for as long as the device should see the name (the device
 * re-browses and the official stack re-publishes periodically); {@link #close()} withdraws it.
 *
 * <p><b>Wiring:</b> start one after a pairing has persisted (and on daemon start while a pairing
 * exists), passing the workstation name — reuse {@code pairing.ClientNames.localWorkstation()} —
 * and the {@code peerIdentifier} we registered at pairing (the {@code p2p_client_id} the device
 * stored for our certificate). <b>Live-verified:</b> after pairing, the device shows this
 * workstation's name, confirming the fold ({@link FriendlyIdentity#deviceHash}) and the identifier
 * the device stores for a <i>workstation</i> peer.
 */
public final class FriendlyNameAdvertiser implements AutoCloseable {

  private static final System.Logger LOG = System.getLogger(FriendlyNameAdvertiser.class.getName());

  /**
   * Nominal SRV port. The device's observer only needs the resolve + TXT, but a DNS-SD service
   * still needs a port; this value is not otherwise meaningful.
   */
  private static final int NOMINAL_PORT = 4455;

  private final Advertiser advertiser;
  private MdnsService.Registration advert;

  /** Advertises over the shared mDNS facility. */
  public FriendlyNameAdvertiser() {
    this(MdnsService.shared()::advertise);
  }

  /**
   * Advertises over the given facility.
   *
   * @param advertiser registers the service
   */
  FriendlyNameAdvertiser(Advertiser advertiser) {
    this.advertiser = advertiser;
  }

  /**
   * Registers the Friendly service and begins advertising. Blocks only for the initial mDNS
   * registration.
   *
   * @param friendlyName the name to show on the device (the TXT {@code fname})
   * @param peerIdentifier our device identifier as registered at pairing — the string the device
   *     stored for our certificate, which it re-hashes to match our advertised {@code deviceHash}
   *     (see {@link FriendlyIdentity#deviceHash})
   * @throws IOException if mDNS registration fails
   */
  public void start(String friendlyName, String peerIdentifier) throws IOException {
    byte[] seed = FriendlyIdentity.newSeed();
    byte[] deviceHash = FriendlyIdentity.deviceHash(peerIdentifier, seed);
    String instance = FriendlyIdentity.instanceName(seed, deviceHash);

    Map<String, String> txt = new HashMap<>();
    txt.put("txtvers", "1");
    txt.put("protovers", "2");
    txt.put(FriendlyIdentity.TXT_FNAME, friendlyName);

    advert =
        advertiser.advertise(
            new ServiceAdvert(
                FriendlyIdentity.SERVICE_TYPE,
                instance,
                FriendlyIdentity.SUBTYPE,
                NOMINAL_PORT,
                txt));
    LOG.log(
        System.Logger.Level.INFO,
        () ->
            "Advertising bp2p Friendly service "
                + instance
                + " ("
                + FriendlyIdentity.TXT_FNAME
                + "="
                + friendlyName
                + ")");
  }

  /**
   * Best-effort {@link #start}: returns the running advertiser, or {@link Optional#empty()} (with a
   * warning) if it cannot start. Never throws — the friendly name is a courtesy, so nothing it does
   * may break the connection it rides along with. This catches more than the {@link IOException}
   * {@link #start} declares: in a native image mDNS can fail with a runtime exception or a linkage
   * error, and those must be swallowed too.
   *
   * @param friendlyName the name to show on the device
   * @param peerIdentifier our device identifier as registered at pairing
   * @return the running advertiser, or empty when it could not start
   */
  public static Optional<FriendlyNameAdvertiser> tryStart(
      String friendlyName, String peerIdentifier) {
    FriendlyNameAdvertiser advertiser = new FriendlyNameAdvertiser();
    try {
      advertiser.start(friendlyName, peerIdentifier);
      return Optional.of(advertiser);
    } catch (Exception | LinkageError e) {
      LOG.log(System.Logger.Level.WARNING, "Friendly-name advertising failed to start", e);
    }
    advertiser.close();
    return Optional.empty();
  }

  /** Withdraws the advert. Safe to call more than once. */
  @Override
  public void close() {
    MdnsService.Registration local = advert;
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
