package com.jbraddockm.dev.connect;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Speaks the qconndoor SecureTarget protocol to a device on TCP 4455 to start its SSH daemon: the
 * RSA session-key exchange, the password proof, the SSH-key push and the start-services request,
 * then holds the session open with keepalives (the daemon stays up only while the session is held).
 */
final class QconndoorClient {

  /** The type of the challenge item that carries the AES session key. */
  private static final int SESSION_KEY_TYPE = 2;

  private static final int PORT = 4455;
  private static final long KEEPALIVE_INTERVAL_MILLIS = 5000;
  private static final byte[] EMPTY = new byte[0];

  private final String host;
  private final String password;
  private final byte[] sshPublicKey;
  private final Progress progress;

  /** Receives human-readable progress notes during the handshake. */
  interface Progress {
    void note(String message);
  }

  QconndoorClient(String host, String password, byte[] sshPublicKey, Progress progress) {
    this.host = host;
    this.password = password;
    this.sshPublicKey = sshPublicKey.clone();
    this.progress = progress;
  }

  /**
   * Runs the handshake, calls {@code onConnected} once the daemon is up, then holds the session
   * with keepalives until the connection ends (for example when the process is stopped).
   *
   * @param onConnected run after the daemon has started, before the session is held
   * @throws IOException if the device connection fails
   * @throws GeneralSecurityException if a cryptographic step fails
   */
  void startDaemon(Runnable onConnected) throws IOException, GeneralSecurityException {
    try (var socket = new Socket(host, PORT)) {
      var in = new BufferedInputStream(socket.getInputStream());
      var out = socket.getOutputStream();
      handshake(in, out);
      onConnected.run();
      holdSession(in, out);
    }
  }

  private void handshake(InputStream in, OutputStream out)
      throws IOException, GeneralSecurityException {
    progress.note("Connecting to " + host + ":" + PORT);
    SecureTarget.write(out, SecureTarget.HELLO, EMPTY);
    expect(in, SecureTarget.FEEDBACK);

    progress.note("Exchanging encryption keys");
    var keyPair = Crypto.rsaKeyPair();
    SecureTarget.write(
        out, SecureTarget.CHALLENGE_REQUEST, publicKey((RSAPublicKey) keyPair.getPublic()));
    var challenge =
        EncryptionChallenge.parse(expect(in, SecureTarget.ENCRYPTED_CHALLENGE_RESPONSE));

    var privateKey = (RSAPrivateKey) keyPair.getPrivate();
    byte[] blob =
        Crypto.concat(
            Crypto.rsaDecrypt(challenge.container(), privateKey), Crypto.QCONNDOOR_PERMISSIONS);
    byte[] sessionKey = Crypto.challengeItem(blob, SESSION_KEY_TYPE);
    if (sessionKey.length == 0) {
      throw new IOException("the device did not return a session key");
    }
    byte[] signature = Crypto.signBlob(blob, privateKey);
    SecureTarget.write(
        out,
        SecureTarget.DECRYPTED_CHALLENGE_RESPONSE,
        Crypto.encryptFrame(sessionKey, decryptedResponse(blob, signature)));
    expect(in, SecureTarget.FEEDBACK);

    progress.note("Authenticating");
    SecureTarget.write(out, SecureTarget.AUTH_CHALLENGE_REQUEST, EMPTY);
    var params = AuthParameters.parse(expect(in, SecureTarget.AUTH_CHALLENGE_RESPONSE));
    String hashed =
        Crypto.hashAndEncode(
            password, params.algorithm(), params.salt(), params.iterations(), params.challenge());
    SecureTarget.write(
        out,
        SecureTarget.AUTHENTICATE,
        Crypto.encryptFrame(
            sessionKey, lengthPrefixed(hashed.getBytes(StandardCharsets.US_ASCII))));
    expect(in, SecureTarget.FEEDBACK);

    progress.note("Sending SSH key");
    SecureTarget.write(
        out,
        SecureTarget.SEND_SSH_KEY,
        Crypto.encryptFrame(sessionKey, lengthPrefixed(trimmedKey())));
    expect(in, SecureTarget.FEEDBACK);

    SecureTarget.write(out, SecureTarget.START_SERVICES, EMPTY);
    expect(in, SecureTarget.FEEDBACK);
  }

  /**
   * Holds the session open until the device closes it: a background thread sends a keepalive every
   * {@link #KEEPALIVE_INTERVAL_MILLIS} while this thread blocks draining each keepalive's feedback.
   */
  private void holdSession(InputStream in, OutputStream out) {
    try (var keepAlives =
        Executors.newSingleThreadScheduledExecutor(QconndoorClient::keepAliveThread)) {
      keepAlives.scheduleWithFixedDelay(
          () -> sendKeepAlive(out),
          KEEPALIVE_INTERVAL_MILLIS,
          KEEPALIVE_INTERVAL_MILLIS,
          TimeUnit.MILLISECONDS);
      boolean held = true;
      while (held) {
        try {
          SecureTarget.read(in);
        } catch (IOException _) {
          held = false; // the device closed the session, which ends the hold
        }
      }
    }
  }

  private static Thread keepAliveThread(Runnable runnable) {
    var thread = new Thread(runnable, "qconndoor-keepalive");
    thread.setDaemon(true);
    return thread;
  }

  private static void sendKeepAlive(OutputStream out) {
    try {
      SecureTarget.write(out, SecureTarget.KEEPALIVE, EMPTY);
    } catch (IOException _) {
      // the connection is gone; the read loop will end on its own
    }
  }

  private static byte[] publicKey(RSAPublicKey key) {
    return lengthPrefixed(Crypto.modulus(key));
  }

  /**
   * The DecryptedChallengeResponse plaintext: a 6-byte length header, then the blob and signature.
   */
  private static byte[] decryptedResponse(byte[] blob, byte[] signature) {
    return ByteBuffer.allocate(6 + blob.length + signature.length)
        .putShort((short) (4 + blob.length + signature.length))
        .putShort((short) blob.length)
        .putShort((short) signature.length)
        .put(blob)
        .put(signature)
        .array();
  }

  private static byte[] lengthPrefixed(byte[] data) {
    return ByteBuffer.allocate(2 + data.length).putShort((short) data.length).put(data).array();
  }

  private byte[] trimmedKey() {
    return new String(sshPublicKey, StandardCharsets.US_ASCII)
        .trim()
        .getBytes(StandardCharsets.US_ASCII);
  }

  private static byte[] expect(InputStream in, int code) throws IOException {
    var frame = SecureTarget.read(in);
    if (frame.code() != code) {
      throw new IOException(
          "the device sent frame " + frame.code() + " where " + code + " was expected");
    }
    return frame.payload();
  }

  /**
   * The device's password-hashing parameters from an AuthenticateChallengeResponse (big-endian).
   */
  private record AuthParameters(int algorithm, int iterations, byte[] salt, byte[] challenge) {

    static AuthParameters parse(byte[] payload) {
      var buffer = ByteBuffer.wrap(payload);
      int algorithm = buffer.getInt();
      int iterations = buffer.getInt();
      int saltLength = buffer.getShort() & 0xFFFF;
      int challengeLength = buffer.getShort() & 0xFFFF;
      byte[] salt = new byte[saltLength];
      buffer.get(salt);
      byte[] challenge = new byte[challengeLength];
      buffer.get(challenge);
      return new AuthParameters(algorithm, iterations, salt, challenge);
    }
  }
}
