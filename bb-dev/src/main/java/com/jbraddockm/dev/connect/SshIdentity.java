package com.jbraddockm.dev.connect;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

/**
 * Resolves the SSH public key that {@code connect} offers to the device. It defaults to Momentics'
 * own key at {@code ~/.rim/bbt_id_rsa.pub}, so an existing Momentics install connects with no setup
 * — the device already authorises that key. When the default key is absent, an RSA key pair is
 * written there under the same name: the private half, which bb-dev never uses itself, is what lets
 * the user then SSH in; the public half is what the device is told to trust. A key named explicitly
 * on the command line is used as-is and never generated.
 */
final class SshIdentity {

  /**
   * Overrides the {@code .rim} directory. Mainly a test seam; also serves non-standard installs.
   */
  private static final String RIM_DIR_PROPERTY = "bb.dev.rim.dir";

  private static final String KEY_NAME = "bbt_id_rsa";
  private static final int RSA_BITS = 4096;
  private static final String SSH_RSA = "ssh-rsa";

  private SshIdentity() {}

  /** Momentics' default public-key path, {@code ~/.rim/bbt_id_rsa.pub}. */
  static Path defaultPublicKey() {
    String override = System.getProperty(RIM_DIR_PROPERTY);
    Path rim =
        override != null ? Path.of(override) : Path.of(System.getProperty("user.home"), ".rim");
    return rim.resolve(KEY_NAME + ".pub");
  }

  /**
   * Reads the public key at {@code publicKey}, first generating a fresh RSA key pair there when it
   * is absent and {@code generateIfMissing} is set.
   *
   * @param publicKey the public-key path to read or generate
   * @param generateIfMissing whether a missing key may be generated rather than reported
   * @return the resolved key
   * @throws IOException if the key cannot be read, or is missing and may not be generated
   * @throws GeneralSecurityException if key generation fails
   */
  static Resolved resolve(Path publicKey, boolean generateIfMissing)
      throws IOException, GeneralSecurityException {
    var privateKey = privateKeyFor(publicKey);
    if (Files.exists(publicKey)) {
      return new Resolved(Files.readAllBytes(publicKey), publicKey, privateKey, false);
    }
    if (!generateIfMissing) {
      throw new IOException("no such SSH public key: " + publicKey);
    }
    return new Resolved(generate(publicKey, privateKey), publicKey, privateKey, true);
  }

  /** Writes an RSA key pair and returns the public-key bytes. */
  private static byte[] generate(Path publicKey, Path privateKey)
      throws IOException, GeneralSecurityException {
    var directory = publicKey.toAbsolutePath().getParent();
    if (directory != null) {
      Files.createDirectories(directory);
    }

    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(RSA_BITS);
    var pair = generator.generateKeyPair();

    byte[] publicLine = opensshPublicKey((RSAPublicKey) pair.getPublic());
    write(privateKey, pemPrivateKey(pair), "rw-------");
    write(publicKey, publicLine, "rw-r--r--");
    return publicLine;
  }

  /** The private-key path for a public-key path: the same name without a trailing {@code .pub}. */
  private static Path privateKeyFor(Path publicKey) {
    var name = publicKey.getFileName().toString();
    var stem = name.endsWith(".pub") ? name.substring(0, name.length() - ".pub".length()) : name;
    return publicKey.resolveSibling(stem);
  }

  /**
   * An OpenSSH {@code ssh-rsa} public-key line: the wire blob base64'd, with a {@code user@host}
   * comment. The comment must be in {@code user@host} form: the device's qconn rejects the key
   * during the connect handshake (closing the connection at the send-key step) when its comment is
   * a bare word with no {@code @} — verified on a device, where only the comment distinguished an
   * accepted key from a rejected one.
   */
  private static byte[] opensshPublicKey(RSAPublicKey key) {
    byte[] blob =
        concat(
            sshString(SSH_RSA.getBytes(StandardCharsets.US_ASCII)),
            concat(sshMpint(key.getPublicExponent()), sshMpint(key.getModulus())));
    var comment = System.getProperty("user.name", "bb-dev") + "@bb-dev";
    var line = SSH_RSA + " " + Base64.getEncoder().encodeToString(blob) + " " + comment + "\n";
    return line.getBytes(StandardCharsets.US_ASCII);
  }

  /** The private key as an unencrypted PKCS#8 PEM, which OpenSSH reads with {@code ssh -i}. */
  private static byte[] pemPrivateKey(KeyPair pair) {
    var body =
        Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
            .encodeToString(pair.getPrivate().getEncoded());
    var pem = "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n";
    return pem.getBytes(StandardCharsets.US_ASCII);
  }

  private static void write(Path path, byte[] bytes, String posix) throws IOException {
    Files.write(path, bytes);
    try {
      Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(posix));
    } catch (UnsupportedOperationException notPosix) {
      // A non-POSIX filesystem (e.g. Windows): the bytes are written; permissions stay the OS's.
    }
  }

  /** An SSH {@code string}: the bytes, big-endian, length-prefixed with a u32. */
  private static byte[] sshString(byte[] data) {
    byte[] out = new byte[4 + data.length];
    out[0] = (byte) (data.length >>> 24);
    out[1] = (byte) (data.length >>> 16);
    out[2] = (byte) (data.length >>> 8);
    out[3] = (byte) data.length;
    System.arraycopy(data, 0, out, 4, data.length);
    return out;
  }

  /**
   * An SSH {@code mpint}: {@link BigInteger#toByteArray} is already the right two's-complement
   * form.
   */
  private static byte[] sshMpint(BigInteger value) {
    return sshString(value.toByteArray());
  }

  private static byte[] concat(byte[] first, byte[] second) {
    byte[] out = new byte[first.length + second.length];
    System.arraycopy(first, 0, out, 0, first.length);
    System.arraycopy(second, 0, out, first.length, second.length);
    return out;
  }

  /**
   * The public key bb-dev offers.
   *
   * @param publicKey the public-key bytes (an OpenSSH {@code ssh-rsa} line)
   * @param publicKeyPath where the public key lives
   * @param privateKeyPath where its private half lives
   * @param generated whether this resolution created the key pair
   */
  record Resolved(byte[] publicKey, Path publicKeyPath, Path privateKeyPath, boolean generated) {}
}
