package com.jbraddockm.link.transport.smb;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.transport.internal.Impossible;
import com.jbraddockm.link.transport.login.SmbCredentials;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.LongConsumer;
import org.codelibs.jcifs.smb1.NtlmPasswordAuthentication;
import org.codelibs.jcifs.smb1.SmbException;
import org.codelibs.jcifs.smb1.SmbFile;
import org.codelibs.jcifs.smb1.SmbFileInputStream;
import org.codelibs.jcifs.smb1.SmbFileOutputStream;

/**
 * A {@link FileStore} backed by an embedded SMB1 client (jcifs), talking to one device over the
 * credentials of its latest login. A fresh store is made for each operation from the session's
 * current {@link SmbCredentials}, because the share password changes with every login; it holds no
 * connection of its own between calls.
 *
 * <p>The jcifs-confined behaviour is verified against a real device rather than in unit tests; the
 * host-side path arithmetic (see {@link #parentOf}) is unit-tested.
 */
public final class JcifsFileStore implements FileStore {

  private static final int BUFFER_BYTES = 64 * 1024;

  static {
    SmbRuntime.apply();
  }

  private final String host;
  private final NtlmPasswordAuthentication authentication;

  /**
   * Creates a store for the device the credentials belong to.
   *
   * @param credentials the file-share credentials and host of the latest login
   */
  public JcifsFileStore(SmbCredentials credentials) {
    Objects.requireNonNull(credentials, "credentials");
    this.host = credentials.host();
    var workgroup = credentials.workgroup() != null ? credentials.workgroup() : "";
    this.authentication =
        new NtlmPasswordAuthentication(workgroup, credentials.user(), credentials.password());
  }

  private static long copy(
      InputStream in, OutputStream out, LongConsumer progress, long alreadyDone)
      throws IOException {
    var buffer = new byte[BUFFER_BYTES];
    var total = 0L;
    int read;
    while ((read = in.read(buffer)) != -1) {
      out.write(buffer, 0, read);
      total += read;
      progress.accept(alreadyDone + total);
    }
    return total;
  }

  private static SmbEntry toEntry(SmbFile file) throws SmbException {
    var raw = file.getName();
    var name = raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
    var directory = file.isDirectory();
    return new SmbEntry(
        name, directory, directory ? 0 : file.length(), Instant.ofEpochMilli(file.lastModified()));
  }

  /**
   * The parent directory of a location. A top-level path yields the share root (an empty path).
   *
   * @param location the location whose parent is wanted
   * @return the parent location, on the same share
   */
  static SmbLocation parentOf(SmbLocation location) {
    var path = location.path();
    var cut = path.lastIndexOf('/');
    return new SmbLocation(location.share(), cut < 0 ? "" : path.substring(0, cut));
  }

  @Override
  public List<SmbEntry> list(SmbLocation directory) throws SmbTransferException {
    try {
      var children = file(directory, true).listFiles();
      var entries = new ArrayList<SmbEntry>(children.length);
      for (var child : children) {
        entries.add(toEntry(child));
      }
      return entries;
    } catch (IOException failure) {
      throw SmbErrorMapper.map(failure, "list", directory);
    }
  }

  @Override
  public SmbEntry stat(SmbLocation path) throws SmbTransferException {
    try {
      var file = file(path, false);
      if (!file.exists()) {
        throw notFound(path);
      }
      return toEntry(file);
    } catch (IOException failure) {
      throw SmbErrorMapper.map(failure, "read", path);
    }
  }

  @Override
  public long pull(SmbLocation source, Path destination, LongConsumer progress, boolean resume)
      throws SmbTransferException {
    var temp = destination.resolveSibling(destination.getFileName() + ".bblink.tmp");
    try {
      var startAt = resume && Files.exists(temp) ? Files.size(temp) : 0L;
      long copied;
      try (InputStream in = new SmbFileInputStream(file(source, false));
          OutputStream out =
              startAt > 0
                  ? Files.newOutputStream(temp, StandardOpenOption.APPEND, StandardOpenOption.WRITE)
                  : Files.newOutputStream(
                      temp,
                      StandardOpenOption.CREATE,
                      StandardOpenOption.TRUNCATE_EXISTING,
                      StandardOpenOption.WRITE)) {
        if (startAt > 0) {
          in.skipNBytes(startAt);
        }
        copied = copy(in, out, progress, startAt);
      }
      Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
      return startAt + copied;
    } catch (IOException failure) {
      // A resumable pull keeps the partial file so a later call can continue it; otherwise clean
      // up.
      if (!resume) {
        deleteQuietly(temp);
      }
      throw SmbErrorMapper.map(failure, "read", source);
    }
  }

  @Override
  public long push(Path source, SmbLocation destination, LongConsumer progress, boolean resume)
      throws SmbTransferException {
    if (!Files.isRegularFile(source)) {
      throw new SmbTransferException(
          CoreErrorCode.INVALID_ARGUMENT,
          "The local file to send does not exist: " + source,
          Json.object("source", source.toString()));
    }
    var temp = parentOf(destination).child("." + destination.name() + ".tmp");
    try {
      var parent = file(parentOf(destination), true);
      if (!parent.exists()) {
        parent.mkdirs();
      }
      var tempFile = file(temp, false);
      var startAt = resume && tempFile.exists() ? tempFile.length() : 0L;
      long copied;
      try (InputStream in = Files.newInputStream(source);
          OutputStream out = new SmbFileOutputStream(tempFile, startAt > 0)) {
        if (startAt > 0) {
          in.skipNBytes(startAt);
        }
        copied = copy(in, out, progress, startAt);
      }
      var target = file(destination, false);
      if (target.exists()) {
        if (target.isDirectory()) {
          // Never let a file overwrite a directory: jcifs delete() would recurse and wipe its
          // contents. Refuse instead, as delete/move/copy already refuse a directory.
          throw notADirectory(destination, "is a directory; a file cannot replace it");
        }
        target.delete();
      }
      tempFile.renameTo(target);
      return startAt + copied;
    } catch (IOException failure) {
      // A resumable push keeps the partial temp so a later call can continue it; otherwise clean
      // up.
      if (!resume) {
        deleteQuietly(temp);
      }
      throw SmbErrorMapper.map(failure, "write", destination);
    }
  }

  @Override
  public void delete(SmbLocation path) throws SmbTransferException {
    try {
      var file = file(path, false);
      if (!file.exists()) {
        throw notFound(path);
      }
      if (file.isDirectory()) {
        throw notADirectory(path, "is a directory; delete removes files only");
      }
      file.delete();
    } catch (IOException failure) {
      throw SmbErrorMapper.map(failure, "delete", path);
    }
  }

  @Override
  public void removeDirectory(SmbLocation path) throws SmbTransferException {
    try {
      var node = file(path, false);
      if (!node.exists()) {
        throw notFound(path);
      }
      if (!node.isDirectory()) {
        throw notADirectory(path, "is a file; use delete to remove a file");
      }
      // Confined to an empty directory: jcifs SmbFile.delete() recurses, so an emptiness check is
      // what keeps this from wiping a tree. Listing uses the directory (trailing-slash) form.
      var directory = file(path, true);
      var children = directory.listFiles();
      if (children.length > 0) {
        throw new SmbTransferException(
            LinkErrorCode.DIRECTORY_NOT_EMPTY,
            path.share() + "/" + path.path() + " is not empty; remove its contents first",
            Json.object("share", path.share(), "path", path.path(), "entries", children.length));
      }
      directory.delete();
    } catch (IOException failure) {
      throw SmbErrorMapper.map(failure, "delete", path);
    }
  }

  @Override
  public void makeDirectory(SmbLocation path) throws SmbTransferException {
    try {
      var directory = file(path, true);
      if (directory.exists()) {
        throw alreadyExists(path);
      }
      directory.mkdirs();
    } catch (IOException failure) {
      throw SmbErrorMapper.map(failure, "create", path);
    }
  }

  @Override
  public void move(SmbLocation from, SmbLocation to) throws SmbTransferException {
    relocate(from, to, "move", SmbFile::renameTo);
  }

  @Override
  public void copy(SmbLocation from, SmbLocation to) throws SmbTransferException {
    relocate(from, to, "copy", SmbFile::copyTo);
  }

  /**
   * A terminal device-side relocation of one share node onto another: a rename, or a server copy.
   */
  @FunctionalInterface
  private interface Relocation {
    void apply(SmbFile source, SmbFile target) throws IOException;
  }

  /**
   * Relocates {@code from} to {@code to} on the share with a single terminal operation shared by
   * {@link #move} and {@link #copy}: guards that the source exists and the destination does not,
   * ensures the destination's parent directory, then applies {@code relocation}. {@code verb} names
   * the operation for error mapping.
   */
  private void relocate(SmbLocation from, SmbLocation to, String verb, Relocation relocation)
      throws SmbTransferException {
    try {
      var source = file(from, false);
      if (!source.exists()) {
        throw notFound(from);
      }
      if (file(to, false).exists()) {
        throw alreadyExists(to);
      }
      var parent = file(parentOf(to), true);
      if (!parent.exists()) {
        parent.mkdirs();
      }
      relocation.apply(source, file(to, false));
    } catch (IOException failure) {
      throw SmbErrorMapper.map(failure, verb, from);
    }
  }

  @Override
  public long freeSpace(SmbLocation path) throws SmbTransferException {
    try {
      return file(SmbLocation.root(path.share()), true).getDiskFreeSpace();
    } catch (IOException failure) {
      throw SmbErrorMapper.map(failure, "read", path);
    }
  }

  @Override
  public void setModifiedTime(SmbLocation path, Instant time) throws SmbTransferException {
    try {
      var file = file(path, false);
      if (!file.exists()) {
        throw notFound(path);
      }
      file.setLastModified(time.toEpochMilli());
    } catch (IOException failure) {
      throw SmbErrorMapper.map(failure, "update", path);
    }
  }

  private static SmbTransferException notFound(SmbLocation path) {
    return new SmbTransferException(
        LinkErrorCode.NO_SUCH_PATH,
        path.share() + "/" + path.path() + " does not exist on the device",
        Json.object("share", path.share(), "path", path.path()));
  }

  private static SmbTransferException alreadyExists(SmbLocation path) {
    return new SmbTransferException(
        LinkErrorCode.ALREADY_EXISTS,
        path.share() + "/" + path.path() + " already exists on the device",
        Json.object("share", path.share(), "path", path.path()));
  }

  private static SmbTransferException notADirectory(SmbLocation path, String explanation) {
    return new SmbTransferException(
        LinkErrorCode.NOT_A_DIRECTORY,
        path.share() + "/" + path.path() + " " + explanation,
        Json.object("share", path.share(), "path", path.path()));
  }

  private void deleteQuietly(SmbLocation location) {
    try {
      var file = file(location, false);
      if (file.exists()) {
        file.delete();
      }
    } catch (IOException _) {
      // Best-effort removal of a leftover temporary file; a failure here must not mask the
      // original.
    }
  }

  private void deleteQuietly(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException _) {
      // Best-effort removal of a leftover temporary file.
    }
  }

  private SmbFile file(SmbLocation location, boolean directory) {
    return Impossible.call(
        () -> {
          // Traverse the share root one segment at a time. jcifs encodes child names for the wire,
          // preserving spaces and reserved characters; encoding the full URL would store "%20"
          // literally. Trailing slashes mark intermediate segments as directories.
          var node = new SmbFile("smb://" + host + "/" + location.share() + "/", authentication);
          if (!location.path().isEmpty()) {
            var segments = location.path().split("/");
            for (var index = 0; index < segments.length; index++) {
              var asDirectory = directory || index < segments.length - 1;
              node = new SmbFile(node, asDirectory ? segments[index] + "/" : segments[index]);
            }
          }
          return node;
        },
        "the SMB file is built from a validated share and path");
  }
}
