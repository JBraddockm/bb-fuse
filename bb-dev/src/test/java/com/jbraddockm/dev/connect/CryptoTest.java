package com.jbraddockm.dev.connect;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("The qconndoor cryptography")
class CryptoTest {

  private static final char[] HEX = "0123456789ABCDEF".toCharArray();

  private static String upperHex(byte[] data) {
    var builder = new StringBuilder();
    for (byte value : data) {
      builder.append(HEX[(value >> 4) & 0xF]).append(HEX[value & 0xF]);
    }
    return builder.toString();
  }

  private static byte[] sha(String algorithm, byte[] data) throws Exception {
    return MessageDigest.getInstance(algorithm).digest(data);
  }

  @Nested
  @DisplayName("the challenge-item parser")
  class ChallengeItem {

    // [len 4][type 1][a b c d] [len 16][type 2][16-byte key] then the permissions item.
    private final byte[] key = {9, 9, 9, 9, 9, 9, 9, 9, 8, 8, 8, 8, 8, 8, 8, 8};
    private final byte[] blob =
        concat(
            new byte[] {4, 1, 'a', 'b', 'c', 'd'},
            concat(concat(new byte[] {16, 2}, key), Crypto.QCONNDOOR_PERMISSIONS));

    @Test
    @DisplayName("returns the value of the item with the requested type")
    void returnsMatchingItem() {
      assertThat(Crypto.challengeItem(blob, 2)).containsExactly(key);
      assertThat(Crypto.challengeItem(blob, 1))
          .containsExactly((byte) 'a', (byte) 'b', (byte) 'c', (byte) 'd');
      assertThat(Crypto.challengeItem(blob, 4))
          .containsExactly((byte) 0x76, (byte) 0x83, (byte) 0x01);
    }

    @Test
    @DisplayName("returns an empty array when no item has that type")
    void returnsEmptyWhenAbsent() {
      assertThat(Crypto.challengeItem(blob, 7)).isEmpty();
    }
  }

  @Nested
  @DisplayName("the encrypted-frame builder")
  class EncryptFrame {

    @Test
    @DisplayName("prefixes the ciphertext and plaintext lengths and a 16-byte IV, and round-trips")
    void roundTrips() throws Exception {
      byte[] key = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
      byte[] plaintext = "the quick brown fox".getBytes();

      byte[] payload = Crypto.encryptFrame(key, plaintext);
      var buffer = ByteBuffer.wrap(payload);
      int ciphertextLength = buffer.getShort() & 0xFFFF;
      int plaintextLength = buffer.getShort() & 0xFFFF;
      byte[] iv = new byte[16];
      buffer.get(iv);
      byte[] ciphertext = new byte[buffer.remaining()];
      buffer.get(ciphertext);

      assertThat(plaintextLength).isEqualTo(plaintext.length);
      assertThat(ciphertext).hasSize(ciphertextLength);

      var cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
      assertThat(cipher.doFinal(ciphertext)).containsExactly(plaintext);
    }
  }

  @Nested
  @DisplayName("the password hash")
  class HashAndEncode {

    @Test
    @DisplayName("algorithm 0 is uppercase-hex SHA-1 of the password")
    void algorithmZero() throws Exception {
      assertThat(Crypto.hashAndEncode("secret", 0, new byte[0], 0, new byte[0]))
          .isEqualTo(upperHex(sha("SHA-1", "secret".getBytes())));
    }

    @Test
    @DisplayName("algorithm 0 with a challenge hashes the challenge joined to the password hash")
    void algorithmZeroWithChallenge() throws Exception {
      byte[] challenge = "c".getBytes();
      byte[] inner = sha("SHA-1", "secret".getBytes());
      byte[] expected = sha("SHA-1", concat(challenge, inner));
      assertThat(Crypto.hashAndEncode("secret", 0, new byte[0], 0, challenge))
          .isEqualTo(upperHex(expected));
    }

    @Test
    @DisplayName("algorithm 2 prepends a little-endian round counter and the salt, with SHA-512")
    void algorithmTwoOneRound() throws Exception {
      byte[] salt = {10, 20, 30, 40};
      byte[] round =
          ByteBuffer.allocate(4 + salt.length + "pw".length())
              .order(ByteOrder.LITTLE_ENDIAN)
              .putInt(0)
              .put(salt)
              .put("pw".getBytes())
              .array();
      assertThat(Crypto.hashAndEncode("pw", 2, salt, 1, new byte[0]))
          .isEqualTo(upperHex(sha("SHA-512", round)));
    }
  }

  @Nested
  @DisplayName("the RSA exchange")
  class Rsa {

    @Test
    @DisplayName("exposes the modulus as exactly 128 bytes")
    void modulusIs128Bytes() throws Exception {
      KeyPair pair = Crypto.rsaKeyPair();
      assertThat(Crypto.modulus((RSAPublicKey) pair.getPublic())).hasSize(128);
    }

    @Test
    @DisplayName("signs the SHA-1 DigestInfo of the blob so the public key recovers it")
    void signatureRecoversDigest() throws Exception {
      KeyPair pair = Crypto.rsaKeyPair();
      byte[] blob = {1, 2, 3, 4, 5};
      byte[] signature = Crypto.signBlob(blob, (RSAPrivateKey) pair.getPrivate());

      var cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
      cipher.init(Cipher.DECRYPT_MODE, pair.getPublic());
      byte[] recovered = cipher.doFinal(signature);

      assertThat(signature).hasSize(128);
      assertThat(recovered).hasSize(35); // 15-byte DigestInfo prefix + 20-byte SHA-1
      byte[] digest = sha("SHA-1", blob);
      assertThat(java.util.Arrays.copyOfRange(recovered, 15, 35)).containsExactly(digest);
    }
  }

  private static byte[] concat(byte[] first, byte[] second) {
    byte[] out = java.util.Arrays.copyOf(first, first.length + second.length);
    System.arraycopy(second, 0, out, first.length, second.length);
    return out;
  }
}
