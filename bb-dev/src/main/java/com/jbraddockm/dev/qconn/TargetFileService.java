package com.jbraddockm.dev.qconn;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * The qconn {@code file} service: opening, writing and closing files on the device, over a {@link
 * QconnSocket} switched to that service. Commands are colon-separated text — {@code o:"<path>":<hex
 * flags>:<hex mode>} to open, {@code w:<fd>:<hex len>} followed by the bytes to write, {@code
 * c:<fd>} to close — and each reply is a line starting with {@code o} (ok) or {@code e:} (error).
 * This is the transport the dev-mode sync uses to push an app's assets into its sandbox and to read
 * the device's installed state back for the diff.
 *
 * <p>The write path pushes assets; the read path ({@link #read} / {@link #readFile}) streams a
 * device file back, and {@link #stat} returns a file's metadata. A read sends {@code r:<fd>:<hex
 * offset>:<hex length>:1} and the device answers {@code o:<hex count>:…} with {@code count} raw
 * bytes following the line; a stat opens a transient handle with {@code oc:"<path>":0}, sends
 * {@code s:<fd>}, and closes it.
 */
public final class TargetFileService {

  /** Open for reading. */
  static final int O_RDONLY = 0;

  /** Open for writing. */
  static final int O_WRONLY = 2;

  /** Create the file if it does not exist. */
  static final int O_CREAT = 256;

  /** Truncate the file to empty. */
  static final int O_TRUNC = 512;

  /** Default permissions for a created file, {@code 0777}. */
  static final int MODE_DEFAULT = 0x1ff;

  /** The {@code S_IFDIR} mode bit — marks an open-create as a directory. */
  private static final int S_IFDIR = 0x4000;

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

  /** The value field of a reply — the token after the {@code o}/{@code e} reply code. */
  private static String field(String reply) throws IOException {
    var parts = reply.split(":");
    if (parts.length < 2) {
      throw new IOException("qconn file service: unexpected reply: " + reply);
    }
    return parts[1];
  }

  private static String hex(int value) {
    return Integer.toHexString(value);
  }

  private static String hex(long value) {
    return Long.toHexString(value);
  }

  private static int fromHexInt(String field) {
    return Integer.parseInt(field, 16);
  }

  private static long fromHexLong(String field) {
    return Long.parseLong(field, 16);
  }

  private static byte[] slice(byte[] data, int offset, int length) {
    return offset == 0 && length == data.length
        ? data
        : Arrays.copyOfRange(data, offset, offset + length);
  }

  /**
   * Opens a file and returns its descriptor.
   *
   * @param path the device path
   * @param flags the open flags (for example {@link #O_WRONLY} | {@link #O_CREAT} | {@link
   *     #O_TRUNC})
   * @param mode the permissions for a created file
   * @return the file descriptor, an opaque token echoed back verbatim on later commands
   * @throws IOException if the device reports an error
   */
  String open(String path, int flags, int mode) throws IOException {
    send("o:\"" + path + "\":" + hex(flags) + ":" + hex(mode), null);
    // o:<fd>:<hex mode>:<?>:"<path>" — the device numbers descriptors in hex (…,9,a,…,f,10,…),
    // so the fd is kept as an opaque string and never reparsed as a decimal int.
    return field(readReply());
  }

  /**
   * Writes bytes to an open descriptor at a device offset.
   *
   * @param fd the descriptor, opened with {@link #O_WRONLY}
   * @param fileOffset the start offset on the device
   * @param data the source bytes
   * @param offset the start in {@code data}
   * @param length how many bytes to write
   * @return the number of bytes the device accepted
   * @throws IOException if the device reports an error
   */
  int write(String fd, long fileOffset, byte[] data, int offset, int length) throws IOException {
    send("w:" + fd + ":" + hex(fileOffset) + ":" + hex(length) + ":1", slice(data, offset, length));
    // o:<hex bytes written>:<hex new offset>
    return fromHexInt(field(readReply()));
  }

  /**
   * Closes a descriptor.
   *
   * @param fd the descriptor
   * @throws IOException if the device reports an error
   */
  void close(String fd) throws IOException {
    send("c:" + fd, null);
    readReply();
  }

  /**
   * Creates a directory (open-create with the directory type bit, then close). Fails if it already
   * exists; use {@link #mkdirs} to create a path idempotently.
   *
   * @param path the device path
   * @throws IOException if the device reports an error
   */
  void mkdir(String path) throws IOException {
    send("o:\"" + path + "\":" + hex(O_CREAT) + ":" + hex(S_IFDIR | MODE_DEFAULT), null);
    close(field(readReply()));
  }

  /**
   * Creates a directory and every missing parent, ignoring components that already exist.
   *
   * @param path the device directory path
   */
  public void mkdirs(String path) {
    var built = new StringBuilder();
    for (var part : path.split("/")) {
      if (part.isEmpty()) {
        continue;
      }
      built.append('/').append(part);
      try {
        mkdir(built.toString());
      } catch (IOException _) {
        // the component exists; keep going down the path
      }
    }
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
        written += write(fd, written, content, written, content.length - written);
      }
    } finally {
      close(fd);
    }
  }

  /**
   * Reads up to {@code length} bytes at {@code offset} from an open descriptor.
   *
   * @param fd the descriptor, opened with {@link #O_RDONLY}
   * @param offset the start offset on the device
   * @param data the destination buffer
   * @param off the start in {@code data}
   * @param length how many bytes to request
   * @return the number of bytes read, or {@code 0} at end of file
   * @throws IOException if the device reports an error
   */
  int read(String fd, long offset, byte[] data, int off, int length) throws IOException {
    send("r:" + fd + ":" + hex(offset) + ":" + hex(length) + ":1", null);
    // o:<hex count>:<hex count>:<hex new offset>, then <count> raw bytes
    var reply = readReply();
    var count = (int) fromHexLong(field(reply));
    if (count > 0) {
      readFully(data, off, count);
    }
    return count;
  }

  /**
   * Opens, reads the whole file, and closes — pulling one device file back.
   *
   * @param path the device path
   * @return the file's bytes
   * @throws IOException if the device reports an error
   */
  public byte[] readFile(String path) throws IOException {
    var fd = open(path, O_RDONLY, MODE_DEFAULT);
    try {
      var out = new ByteArrayOutputStream();
      var block = new byte[65536];
      for (var offset = 0L; ; ) {
        var count = read(fd, offset, block, 0, block.length);
        if (count <= 0) {
          break;
        }
        out.write(block, 0, count);
        offset += count;
      }
      return out.toByteArray();
    } finally {
      close(fd);
    }
  }

  /**
   * Stats a device path: opens a transient handle, reads its metadata, and closes it.
   *
   * @param path the device path
   * @return the file's metadata
   * @throws IOException if the device reports an error (for example a missing path)
   */
  public TargetStat stat(String path) throws IOException {
    var fd = openForStat(path);
    try {
      send("s:" + fd, null);
      var fields = readReply().split(":");
      if (fields.length < 16 || !fields[0].equalsIgnoreCase("o")) {
        throw new IOException("qconn file service: unexpected stat reply");
      }
      return new TargetStat(
          fromHexLong(fields[1]),
          fromHexLong(fields[2]),
          fromHexInt(fields[3]),
          fromHexInt(fields[4]),
          fromHexInt(fields[5]),
          fromHexInt(fields[6]),
          fromHexLong(fields[7]) * 1000,
          fromHexLong(fields[8]) * 1000,
          fromHexLong(fields[9]) * 1000,
          fromHexInt(fields[10]),
          fromHexInt(fields[11]),
          fromHexInt(fields[12]),
          fromHexInt(fields[13]),
          fromHexInt(fields[14]),
          fromHexLong(fields[15]));
    } finally {
      close(fd);
    }
  }

  /** Opens a path for metadata only ({@code oc:"<path>":0}) and returns the descriptor. */
  private String openForStat(String path) throws IOException {
    send("oc:\"" + path + "\":0", null);
    return field(readReply());
  }

  /** Reads exactly {@code length} bytes from the service stream into {@code data}. */
  private void readFully(byte[] data, int off, int length) throws IOException {
    var input = qconn.input();
    for (var read = 0; read < length; ) {
      var n = input.read(data, off + read, length - read);
      if (n < 0) {
        throw new IOException("qconn file service: stream ended after " + read + " of " + length);
      }
      read += n;
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

  /**
   * A device file's metadata, as the {@code s} (stat) reply carries it. Times are milliseconds
   * since the epoch; {@code mode} holds the POSIX type and permission bits.
   *
   * @param inode the inode number
   * @param size the size in bytes
   * @param dev the device id
   * @param rdev the device id for a special file
   * @param uid the owner id
   * @param gid the group id
   * @param modifiedMillis the last-modified time
   * @param accessedMillis the last-accessed time
   * @param changedMillis the last status-change time
   * @param mode the POSIX type and permission bits
   * @param linkCount the number of hard links
   * @param blockSize the preferred block size
   * @param blockCount the number of blocks
   * @param ioBlockSize the I/O block size
   * @param blocks the allocated block count
   */
  public record TargetStat(
      long inode,
      long size,
      int dev,
      int rdev,
      int uid,
      int gid,
      long modifiedMillis,
      long accessedMillis,
      long changedMillis,
      int mode,
      int linkCount,
      int blockSize,
      int blockCount,
      int ioBlockSize,
      long blocks) {

    private static final int S_IFMT = 0xf000;
    private static final int S_IFDIR = 0x4000;
    private static final int S_IFREG = 0x8000;

    /**
     * Whether this is a directory.
     *
     * @return {@code true} for a directory
     */
    public boolean isDirectory() {
      return (mode & S_IFMT) == S_IFDIR;
    }

    /**
     * Whether this is a regular file.
     *
     * @return {@code true} for a regular file
     */
    public boolean isRegularFile() {
      return (mode & S_IFMT) == S_IFREG;
    }

    /**
     * The permission bits, without the file-type bits.
     *
     * @return the permission bits (for example {@code 0x1ff} for {@code 0777})
     */
    public int permissions() {
      return mode & 0x1ff;
    }
  }
}
