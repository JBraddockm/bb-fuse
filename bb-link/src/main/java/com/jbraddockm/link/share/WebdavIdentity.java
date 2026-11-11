package com.jbraddockm.link.share;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Builds the bp2p <b>webdav</b> DNS-SD service instance name the host advertises so a paired
 * BlackBerry 10 device discovers this workstation's file share and mounts it in the File Manager.
 *
 * <p>The service has type {@code _bp2p._tcp}, and its instance name holds two 8-byte tokens in
 * upper-case hexadecimal:
 *
 * <pre>  webdav_&lt;SEED 16-hex&gt;_&lt;TOKEN 16-hex&gt;</pre>
 *
 * <p>The seed is fresh per advert; the token folds that seed with the identifier the device stored
 * for this workstation at pairing, so the device recomputes the same fold over the identifier it
 * holds for each paired peer to find the match:
 *
 * <pre>
 *   seed  = 8 random bytes
 *   token = SHA1(seed || ASCII(peerIdentifier))[12:20]   // the last 8 bytes of the digest
 *   name  = "webdav_" + HEXUP(seed) + "_" + HEXUP(token)
 * </pre>
 */
final class WebdavIdentity {

  /** The DNS-SD service type the webdav share is registered under. */
  static final String SERVICE_TYPE = "_bp2p._tcp.local.";

  /** The first instance-name token. */
  static final String INSTANCE_PREFIX = "webdav";

  private static final HexFormat UPPER = HexFormat.of().withUpperCase();
  private static final SecureRandom RANDOM = new SecureRandom();

  private WebdavIdentity() {}

  /** A fresh 8-byte seed, published in the instance name so the device can recompute the token. */
  static byte[] newSeed() {
    var seed = new byte[8];
    RANDOM.nextBytes(seed);
    return seed;
  }

  /**
   * The 8-byte identity token: {@code SHA1(seed || ASCII(peerIdentifier))[12:20]}.
   *
   * @param peerIdentifier the identifier the device stored for this workstation at pairing (ASCII)
   * @param seed the 8-byte seed that will be published in the instance name
   * @return the 8-byte token (bytes [12:20] of the SHA-1 digest)
   */
  static byte[] token(String peerIdentifier, byte[] seed) {
    var sha1 = sha1();
    sha1.update(seed);
    sha1.update(peerIdentifier.getBytes(StandardCharsets.US_ASCII));
    var digest = sha1.digest();
    var token = new byte[8];
    System.arraycopy(digest, 12, token, 0, 8);
    return token;
  }

  /** The instance name {@code webdav_<SEED>_<TOKEN>} (upper-case hex, as seen on the wire). */
  static String instanceName(byte[] seed, byte[] token) {
    return INSTANCE_PREFIX + "_" + UPPER.formatHex(seed) + "_" + UPPER.formatHex(token);
  }

  private static MessageDigest sha1() {
    try {
      return MessageDigest.getInstance("SHA-1");
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-1 unavailable", unavailable); // every JRE ships it
    }
  }
}
