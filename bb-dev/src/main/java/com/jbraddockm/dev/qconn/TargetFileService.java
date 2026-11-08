package com.jbraddockm.dev.qconn;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * The qconn {@code file} service: opening, writing and closing files on the device, over a {@link
 * QconnSocket} switched to that service. Commands are colon-separated text — {@code o:"<path>":<hex
 * flags>:<hex mode>} to open, {@code w:<fd>:<hex len>} followed by the bytes to write, {@code
 * c:<fd>} to close — and each reply is a line starting with {@code o} (ok) or {@code e:} (error).
 * This is the transport the dev-mode sync uses to push an app's assets into its sandbox.
 *
 * <p>The write path (open-create, write, close) is implemented and live-verified; reading and stat
 * use a different argument layout and are not needed to push assets.
 */
public final class TargetFileService {

  /** Open for writing. */
  public static final int O_WRONLY = 2;

  /** Create the file if it does not exist. */
  public static final int O_CREAT = 256;

  /** Truncate the file to empty. */
  public static final int O_TRUNC = 512;

  /** Default permissions for a created file, {@code 0777}. */
  public static final int MODE_DEFAULT = 0x1ff;

  private final QconnSocket qconn;

  TargetFileService(QconnSocket qconn) {
    this.qconn = qconn;
  }

  /**
   * Switches the connection to the file service.
   *
   * @param qconn a connected qconn agent
   * @return the file service
   * @throws IOException if the service cannot be selected
   */
  public static TargetFileService on(QconnSocket qconn) throws IOException {
    qconn.setService("file");
    return new TargetFileService(qconn);
  }

  /**
   * Opens a file and returns its descriptor.
   *
   * @param path the device path
   * @param flags the open flags (for example {@link #O_WRONLY} | {@link #O_CREAT} | {@link
   *     #O_TRUNC})
   * @param mode the permissions for a created file
   * @return the file descriptor
   * @throws IOException if the device reports an error
   */
  public int open(String path, int flags, int mode) throws IOException {
    send(
        "o:\"" + path + "\":" + Integer.toHexString(flags) + ":" + Integer.toHexString(mode), null);
    // o:<fd>:<hex mode>:<?>:"<path>"
    return Integer.parseInt(field(readReply(), 1));
  }

  /**
   * Writes bytes to an open descriptor.
   *
   * @param fd the descriptor
   * @param data the source bytes
   * @param offset the start in {@code data}
   * @param length how many bytes to write
   * @return the number of bytes the device accepted
   * @throws IOException if the device reports an error
   */
  public int write(int fd, byte[] data, int offset, int length) throws IOException {
    send("w:" + fd + ":" + Integer.toHexString(length), slice(data, offset, length));
    // o:<fd>:<hex bytes written>
    return Integer.parseInt(field(readReply(), 2), 16);
  }

  /**
   * Closes a descriptor.
   *
   * @param fd the descriptor
   * @throws IOException if the device reports an error
   */
  public void close(int fd) throws IOException {
    send("c:" + fd, null);
    readReply();
  }

  /**
   * Opens (creating and truncating), writes the whole content, and closes — pushing one asset into
   * the sandbox.
   *
   * @param path the device path
   * @param content the bytes to write
   * @throws IOException if the device reports an error
   */
  public void writeFile(String path, byte[] content) throws IOException {
    var fd = open(path, O_WRONLY | O_CREAT | O_TRUNC, MODE_DEFAULT);
    try {
      for (var written = 0; written < content.length; ) {
        written += write(fd, content, written, content.length - written);
      }
    } finally {
      close(fd);
    }
  }

  private void send(String command, byte[] payload) throws IOException {
    var output = qconn.output();
    output.write((command + "\r\n").getBytes(StandardCharsets.US_ASCII));
    if (payload != null) {
      output.write(payload);
    }
    output.flush();
  }

  /** Reads one reply line; throws for an {@code e:<message>} error reply. */
  private String readReply() throws IOException {
    var line = new StringBuilder();
    for (var b = qconn.input().read(); b != -1 && b != '\n'; b = qconn.input().read()) {
      if (b != '\r') {
        line.append((char) b);
      }
    }
    var reply = line.toString();
    if (reply.startsWith("e:")) {
      throw new IOException("qconn file service error: " + reply.substring(2));
    }
    return reply;
  }

  private static String field(String reply, int index) {
    var parts = reply.split(":");
    if (index >= parts.length) {
      throw new NumberFormatException("unexpected file reply: " + reply);
    }
    return parts[index];
  }

  private static byte[] slice(byte[] data, int offset, int length) {
    if (offset == 0 && length == data.length) {
      return data;
    }
    var slice = new byte[length];
    System.arraycopy(data, offset, slice, 0, length);
    return slice;
  }
}
