package com.jbraddockm.link.endpoint.discovery;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Builds the bp2p <b>Friendly</b> DNS-SD service instance name a peer advertises so a paired device
 * can recognise it, and derives the seed-salted identity token carried inside that name.
 *
 * <p>The service has type {@code _bp2p._tcp} under the {@code _friendly} subtype, and its instance
 * name holds two 8-byte tokens in upper-case hexadecimal:
 *
 * <pre>  Friendly_&lt;SEED 16-hex&gt;_&lt;DEVICEHASH 16-hex&gt;</pre>
 *
 * <p>The human-readable name is not in the instance name; it travels in the TXT record under the
 * key {@code fname}. The device hash folds a per-advert random seed with the advertiser's
 * identifier:
 *
 * <pre>
 *   seed       = 8 random bytes
 *   deviceHash = SHA1(seed || ASCII(identifier))[12:20]   // the last 8 bytes of the digest
 *   instance   = "Friendly_" + HEXUP(seed) + "_" + HEXUP(deviceHash)
 * </pre>
 *
 * <p>The {@code identifier} is the value by which the other side knows this peer: a device's own
 * advert folds its PIN (8 hexadecimal digits); a workstation's advert folds the identifier it
 * registered at pairing. The seed is fresh per advert, so a receiver recomputes the same fold over
 * the identifier it has stored for each paired peer to find the match — the identifier used here
 * must therefore be exactly the one the peer has stored.
 */
final class FriendlyIdentity {

  /**
   * The DNS-SD service type to register the Friendly service under. This is the <b>base</b> type;
   * the {@code _friendly} subtype (below) is passed separately to JmDNS so the {@code
   * _friendly._sub} PTR is actually published. Registering the inline {@code
   * _friendly._sub._bp2p._tcp.local.} form instead publishes only the base type, which the device's
   * subtype browse does not see.
   */
  static final String SERVICE_TYPE = "_bp2p._tcp.local.";

  /**
   * The DNS-SD subtype the device browses ({@code _bp2p._tcp,_friendly}), passed to JmDNS as its
   * own argument. JmDNS prepends the {@code _} and the {@code _sub} label.
   */
  static final String SUBTYPE = "friendly";

  /** The first instance-name token. */
  static final String INSTANCE_PREFIX = "Friendly";

  /** TXT key carrying the displayed name. */
  static final String TXT_FNAME = "fname";

  private static final HexFormat UPPER = HexFormat.of().withUpperCase();
  private static final SecureRandom RANDOM = new SecureRandom();

  private FriendlyIdentity() {}

  /**
   * A fresh 8-byte seed (the {@code random} token, published so the device can recompute the hash).
   */
  static byte[] newSeed() {
    byte[] seed = new byte[8];
    RANDOM.nextBytes(seed);
    return seed;
  }

  /**
   * The instance name {@code Friendly_<SEED>_<DEVICEHASH>} (upper-case hex, as seen on the wire).
   */
  static String instanceName(byte[] seed, byte[] deviceHash) {
    return INSTANCE_PREFIX + "_" + UPPER.formatHex(seed) + "_" + UPPER.formatHex(deviceHash);
  }

  /** A parsed {@code Friendly} instance name: its 8-byte seed and advertised 8-byte device hash. */
  record Parsed(byte[] seed, byte[] deviceHash) {}

  /**
   * Parses a bp2p {@code Friendly} service instance name — {@code Friendly_<SEED>_<DEVICEHASH>},
   * each an 8-byte token in hex — into its seed and device hash. A name that is not this shape (a
   * different bp2p instance, or junk) yields empty rather than throwing.
   *
   * @param instanceName the advertised instance name, or {@code null}
   * @return the seed and device hash, or empty when the name is not a {@code Friendly} instance
   */
  static Optional<Parsed> parse(String instanceName) {
    if (instanceName == null) {
      return Optional.empty();
    }
    var parts = instanceName.split("_");
    if (parts.length != 3 || !parts[0].equals(INSTANCE_PREFIX)) {
      return Optional.empty();
    }
    try {
      var seed = HexFormat.of().parseHex(parts[1]);
      var deviceHash = HexFormat.of().parseHex(parts[2]);
      if (seed.length != 8 || deviceHash.length != 8) {
        return Optional.empty();
      }
      return Optional.of(new Parsed(seed, deviceHash));
    } catch (IllegalArgumentException notHex) {
      return Optional.empty();
    }
  }

  /**
   * Derives the 8-byte {@code deviceHash} token: {@code SHA1(seed ||
   * ASCII(peerIdentifier))[12:20]}. The device recomputes the same over the identifier it stored
   * for our paired cert and matches it.
   *
   * @param peerIdentifier our device identifier as registered at pairing (ASCII; the string the
   *     device stored for our certificate — for a BlackBerry device this is its 8-hex uid)
   * @param seed the 8-byte seed that will be published in the instance name
   * @return the 8-byte device-hash token (bytes [12:20] of the SHA-1 digest)
   */
  static byte[] deviceHash(String peerIdentifier, byte[] seed) {
    MessageDigest sha1 = sha1();
    sha1.update(seed);
    sha1.update(peerIdentifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    byte[] digest = sha1.digest();
    byte[] token = new byte[8];
    System.arraycopy(digest, 12, token, 0, 8); // last 8 bytes of SHA-1
    return token;
  }

  private static MessageDigest sha1() {
    try {
      return MessageDigest.getInstance("SHA-1");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-1 unavailable", e); // every JRE ships it
    }
  }
}
