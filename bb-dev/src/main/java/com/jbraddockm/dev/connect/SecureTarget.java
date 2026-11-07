package com.jbraddockm.dev.connect;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * The SecureTarget wire framing of the qconndoor protocol: a 6-byte big-endian header — total
 * length, protocol version, message code — followed by the payload. See {@code
 * analysis/QCONNDOOR-protocol.md}.
 */
final class SecureTarget {

  static final int VERSION = 2;

  static final int HELLO = 1;
  static final int FEEDBACK = 2;
  static final int CHALLENGE_REQUEST = 3;
  static final int ENCRYPTED_CHALLENGE_RESPONSE = 4;
  static final int DECRYPTED_CHALLENGE_RESPONSE = 5;
  static final int KEEPALIVE = 6;
  static final int SEND_SSH_KEY = 7;
  static final int AUTH_CHALLENGE_REQUEST = 8;
  static final int AUTH_CHALLENGE_RESPONSE = 9;
  static final int AUTHENTICATE = 10;
  static final int START_SERVICES = 11;

  private static final int HEADER = 6;
  private static final int MAX_FRAME = 4096;

  private SecureTarget() {}

  /** One SecureTarget frame: a message code and its already-decrypted-or-plain payload. */
  record Frame(int code, byte[] payload) {

    byte[] encode() {
      return ByteBuffer.allocate(HEADER + payload.length)
          .putShort((short) (HEADER + payload.length))
          .putShort((short) VERSION)
          .putShort((short) code)
          .put(payload)
          .array();
    }
  }

  /**
   * Writes one frame and flushes it.
   *
   * @param out the device stream
   * @param code the message code
   * @param payload the payload, empty for a control frame
   * @throws IOException if the stream cannot be written
   */
  static void write(OutputStream out, int code, byte[] payload) throws IOException {
    out.write(new Frame(code, payload).encode());
    out.flush();
  }

  /**
   * Reads one frame: the 6-byte header in full, then the payload from a single read. The frame's
   * own length field is not trusted for the payload — the device over-states it on at least one
   * frame — so, like the original tool, the payload is whatever arrives in one read after the
   * header (each response arrives in one segment). The payload's own inner length fields bound the
   * fields that matter.
   *
   * @param in the device stream
   * @return the frame
   * @throws IOException if the connection ends before the header is read
   */
  static Frame read(InputStream in) throws IOException {
    byte[] header = readHeader(in);
    int code = ((header[4] & 0xFF) << 8) | (header[5] & 0xFF);
    byte[] buffer = new byte[MAX_FRAME];
    int read = in.read(buffer);
    byte[] payload = read > 0 ? Arrays.copyOf(buffer, read) : new byte[0];
    return new Frame(code, payload);
  }

  private static byte[] readHeader(InputStream in) throws IOException {
    byte[] header = new byte[HEADER];
    int offset = 0;
    while (offset < HEADER) {
      int read = in.read(header, offset, HEADER - offset);
      if (read < 0) {
        throw new IOException("the device closed the connection during the handshake");
      }
      offset += read;
    }
    return header;
  }
}
