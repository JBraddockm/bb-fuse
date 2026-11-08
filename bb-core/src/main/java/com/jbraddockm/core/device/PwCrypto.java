package com.jbraddockm.core.device;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * The device web interface's login password hash, in pure JDK. The device answers a login request
 * with a challenge; the client replies with this hash of the password, the device's salt and the
 * challenge, so the password itself never travels. Three algorithms are defined — single-pass SHA-1
 * ({@link #ALGORITHM_SHA1_V1}), salted iterated SHA-1 ({@link #ALGORITHM_SHA1_V2}) and salted
 * iterated SHA-512 ({@link #ALGORITHM_SHA512_V2}) — selected by the challenge the device sends.
 */
final class PwCrypto {

  /** Single-pass SHA-1, no salt or iteration — the legacy algorithm (challenge algorithm id 0). */
  static final int ALGORITHM_SHA1_V1 = 0;

  /** Salted, iterated SHA-1. */
  static final int ALGORITHM_SHA1_V2 = 1;

  /** Salted, iterated SHA-512. */
  static final int ALGORITHM_SHA512_V2 = 2;

  private static final HexFormat UPPER_HEX = HexFormat.of().withUpperCase();

  private PwCrypto() {}

  /**
   * Hashes a login answer from the strings the device's login form supplies: the salt is a
   * hexadecimal number read as a 64-bit value, and the password and challenge are taken in the
   * platform default charset.
   *
   * @param password the device password
   * @param algorithm the challenge algorithm: {@link #ALGORITHM_SHA1_V1}, {@link
   *     #ALGORITHM_SHA1_V2} or {@link #ALGORITHM_SHA512_V2}
   * @param salt the salt, as the device sends it: a hexadecimal number
   * @param iterationCount the number of hashing rounds
   * @param challenge the challenge the device sent
   * @return the hashed answer, uppercase hexadecimal
   */
  static String hashAndEncode(
      String password, int algorithm, String salt, int iterationCount, String challenge) {
    var charset = Charset.defaultCharset();
    return hashAndEncode(
        password.getBytes(charset),
        algorithm,
        salt(salt),
        iterationCount,
        challenge.getBytes(charset));
  }

  /**
   * Hashes a login answer from raw bytes.
   *
   * @param password the password bytes
   * @param algorithm the challenge algorithm
   * @param salt the salt bytes
   * @param iterationCount the number of hashing rounds
   * @param challenge the challenge bytes; empty for none
   * @return the hashed answer, uppercase hexadecimal
   */
  static String hashAndEncode(
      byte[] password, int algorithm, byte[] salt, int iterationCount, byte[] challenge) {
    var digest =
        algorithm == ALGORITHM_SHA1_V1
            ? sha1(password)
            : iterated(algorithm, salt, iterationCount, password);
    if (challenge.length > 0) {
      var mixed = concat(challenge, digest);
      digest =
          algorithm == ALGORITHM_SHA1_V1
              ? sha1(mixed)
              : iterated(algorithm, salt, iterationCount, mixed);
    }
    return UPPER_HEX.formatHex(digest);
  }

  /** The salted, iterated hash: {@code digest = SHA(LE_int32(i) ‖ salt ‖ digest)} each round. */
  private static byte[] iterated(int algorithm, byte[] salt, int iterationCount, byte[] input) {
    var digest = input;
    var round = 0;
    do {
      var buffer =
          ByteBuffer.allocate(Integer.BYTES + salt.length + digest.length)
              .order(ByteOrder.LITTLE_ENDIAN);
      buffer.putInt(round);
      buffer.put(salt);
      buffer.put(digest);
      // SHA-512 for ALGORITHM_SHA512_V2; SHA-1 for the salted, iterated SHA-1 form
      // (ALGORITHM_SHA1_V2) — the two iterated algorithms the device challenge selects.
      digest = algorithm == ALGORITHM_SHA512_V2 ? sha512(buffer.array()) : sha1(buffer.array());
      round++;
    } while (round < iterationCount);
    return digest;
  }

  /** The salt as the eight big-endian bytes of the hexadecimal number the device sends. */
  private static byte[] salt(String salt) {
    var value = new BigInteger(salt, 16).toByteArray();
    var eight = new byte[8];
    var count = Math.min(value.length, eight.length);
    for (var index = 1; index <= count; index++) {
      eight[eight.length - index] = value[value.length - index];
    }
    return eight;
  }

  private static byte[] concat(byte[] first, byte[] second) {
    return ByteBuffer.allocate(first.length + second.length).put(first).put(second).array();
  }

  private static byte[] sha1(byte[] input) {
    return digest("SHA-1", input);
  }

  private static byte[] sha512(byte[] input) {
    return digest("SHA-512", input);
  }

  private static byte[] digest(String algorithm, byte[] input) {
    try {
      return MessageDigest.getInstance(algorithm).digest(input);
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError(algorithm + " is always available", impossible);
    }
  }
}
