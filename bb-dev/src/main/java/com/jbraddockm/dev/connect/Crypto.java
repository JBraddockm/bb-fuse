package com.jbraddockm.dev.connect;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * The qconndoor cryptography, all with the stock JDK providers: the ephemeral RSA key exchange that
 * recovers the AES session key, the SHA-1 blob signature, the AES-CBC frame encryption, and the
 * salted-and-iterated password hash. See {@code analysis/QCONNDOOR-protocol.md} §4, §8.
 */
final class Crypto {

  /** Appended to the decrypted challenge blob; itself a type-4 challenge item. */
  static final byte[] QCONNDOOR_PERMISSIONS = {0x03, 0x04, 0x76, (byte) 0x83, 0x01};

  /** The PKCS#1 v1.5 DigestInfo prefix for a SHA-1 digest. */
  private static final byte[] EMSA_SHA1_PREFIX = {
    0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2B, 0x0E, 0x03, 0x02, 0x1A, 0x05, 0x00, 0x04, 0x14
  };

  private static final int RSA_MODULUS_BYTES = 128;
  private static final int IV_BYTES = 16;
  private static final char[] HEX = "0123456789ABCDEF".toCharArray();
  private static final SecureRandom RANDOM = new SecureRandom();

  private Crypto() {}

  /** Generates the ephemeral RSA-1024 key pair whose public modulus is offered to the device. */
  static KeyPair rsaKeyPair() throws GeneralSecurityException {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(1024, RANDOM);
    return generator.generateKeyPair();
  }

  /**
   * The low 128 bytes of the modulus — the public encryption key as it goes on the wire. The
   * exponent is the JDK default (65537), which the device assumes.
   */
  static byte[] modulus(RSAPublicKey key) {
    byte[] full = key.getModulus().toByteArray();
    byte[] out = new byte[RSA_MODULUS_BYTES];
    System.arraycopy(full, full.length - RSA_MODULUS_BYTES, out, 0, RSA_MODULUS_BYTES);
    return out;
  }

  /** Decrypts the device's RSA-wrapped challenge container with our private key. */
  static byte[] rsaDecrypt(byte[] container, RSAPrivateKey key) throws GeneralSecurityException {
    var cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
    cipher.init(Cipher.DECRYPT_MODE, key);
    return cipher.doFinal(container);
  }

  /**
   * Signs the decrypted blob: a PKCS#1 v1.5 RSA signature over the SHA-1 DigestInfo of the blob,
   * proving we hold the private key.
   */
  static byte[] signBlob(byte[] blob, RSAPrivateKey key) throws GeneralSecurityException {
    byte[] emsa = concat(EMSA_SHA1_PREFIX, digest("SHA-1", blob));
    var cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
    cipher.init(Cipher.ENCRYPT_MODE, key);
    return cipher.doFinal(emsa);
  }

  /**
   * Builds an encrypted-frame payload: {@code [u16 ciphertextLen][u16 plaintextLen][16-byte IV][AES
   * /CBC/PKCS5 ciphertext]}.
   */
  static byte[] encryptFrame(byte[] sessionKey, byte[] plaintext) throws GeneralSecurityException {
    byte[] iv = new byte[IV_BYTES];
    RANDOM.nextBytes(iv);
    var cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
    cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(sessionKey, "AES"), new IvParameterSpec(iv));
    byte[] ciphertext = cipher.doFinal(plaintext);
    return ByteBuffer.allocate(4 + iv.length + ciphertext.length)
        .putShort((short) ciphertext.length)
        .putShort((short) plaintext.length)
        .put(iv)
        .put(ciphertext)
        .array();
  }

  /**
   * Returns the value of the challenge item of the given type. The blob is a sequence of items,
   * each {@code [u8 length][u8 type][length bytes of value]}.
   *
   * @return the value, or an empty array if no item has that type
   */
  static byte[] challengeItem(byte[] blob, int type) {
    int length = 0;
    int itemType = 0;
    int cursor = 0;
    while (cursor < blob.length) {
      if (length == 0) {
        length = blob[cursor] & 0xFF;
      } else if (itemType == 0) {
        itemType = blob[cursor] & 0xFF;
      } else if (itemType == type) {
        return Arrays.copyOfRange(blob, cursor, cursor + length);
      } else {
        cursor += length - 1;
        length = 0;
        itemType = 0;
      }
      cursor++;
    }
    return new byte[0];
  }

  /**
   * Hashes the device password for the authenticate frame: {@code UPPERHEX(H(challenge ‖
   * H(password)))}, where {@code H} is chosen by the device's algorithm (0 plain SHA-1, 1 iterated
   * SHA-1, 2 iterated SHA-512). The second pass is skipped when the challenge is empty.
   */
  static String hashAndEncode(
      String password, int algorithm, byte[] salt, int iterations, byte[] challenge)
      throws GeneralSecurityException {
    byte[] hash =
        algorithm == 0
            ? digest("SHA-1", password.getBytes())
            : iterate(algorithm, salt, iterations, password.getBytes());
    if (challenge.length > 0) {
      byte[] buffer = concat(challenge, hash);
      hash =
          algorithm == 0 ? digest("SHA-1", buffer) : iterate(algorithm, salt, iterations, buffer);
    }
    return hex(hash);
  }

  private static byte[] iterate(int algorithm, byte[] salt, int iterations, byte[] input)
      throws GeneralSecurityException {
    var hashName = algorithm == 2 ? "SHA-512" : "SHA-1";
    byte[] result = input;
    for (int round = 0; round < iterations; round++) {
      byte[] data =
          ByteBuffer.allocate(4 + salt.length + result.length)
              .order(ByteOrder.LITTLE_ENDIAN)
              .putInt(round)
              .put(salt)
              .put(result)
              .array();
      result = digest(hashName, data);
    }
    return result;
  }

  private static byte[] digest(String algorithm, byte[] data) throws GeneralSecurityException {
    return MessageDigest.getInstance(algorithm).digest(data);
  }

  static byte[] concat(byte[] first, byte[] second) {
    byte[] out = Arrays.copyOf(first, first.length + second.length);
    System.arraycopy(second, 0, out, first.length, second.length);
    return out;
  }

  private static String hex(byte[] data) {
    var builder = new StringBuilder(data.length * 2);
    for (byte value : data) {
      builder.append(HEX[(value >> 4) & 0xF]).append(HEX[value & 0xF]);
    }
    return builder.toString();
  }
}
