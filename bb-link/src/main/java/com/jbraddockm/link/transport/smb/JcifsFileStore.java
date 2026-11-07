package com.jbraddockm.link.transport.smb;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.transport.internal.Impossible;
import com.jbraddockm.link.transport.login.SmbCredentials;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
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
        throw new SmbTransferException(
            LinkErrorCode.NO_SUCH_PATH,
            path.share() + "/" + path.path() + " does not exist on the device",
            Json.object("share", path.share(), "path", path.path()));
      }
      return toEntry(file);
    } catch (IOException failure) {
      throw SmbErrorMapper.map(failure, "read", path);
    }
  }

  @Override
  public long pull(SmbLocation source, Path destination, LongConsumer progress)
      throws SmbTransferException {
    var temp = destination.resolveSibling(destination.getFileName() + ".bblink.tmp");
    try {
      long copied;
      try (InputStream in = new SmbFileInputStream(file(source, false));
          OutputStream out =
              Files.newOutputStream(
                  temp,
                  StandardOpenOption.CREATE,
                  StandardOpenOption.TRUNCATE_EXISTING,
                  StandardOpenOption.WRITE)) {
        copied = copy(in, out, progress);
      }
      Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
      return copied;
    } catch (IOException failure) {
      deleteQuietly(temp);
      throw SmbErrorMapper.map(failure, "read", source);
    }
  }

  @Override
  public long push(Path source, SmbLocation destination, LongConsumer progress)
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
      long copied;
      try (InputStream in = Files.newInputStream(source);
          OutputStream out = new SmbFileOutputStream(tempFile)) {
        copied = copy(in, out, progress);
      }
      var target = file(destination, false);
      if (target.exists()) {
        target.delete();
      }
      tempFile.renameTo(target);
      return copied;
    } catch (IOException failure) {
      deleteQuietly(temp);
      throw SmbErrorMapper.map(failure, "write", destination);
    }
  }

  private static long copy(InputStream in, OutputStream out, LongConsumer progress)
      throws IOException {
    var buffer = new byte[BUFFER_BYTES];
    var total = 0L;
    int read;
    while ((read = in.read(buffer)) != -1) {
      out.write(buffer, 0, read);
      total += read;
      progress.accept(total);
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

  private static SmbLocation parentOf(SmbLocation location) {
    var path = location.path();
    var cut = path.lastIndexOf('/');
    return new SmbLocation(location.share(), cut < 0 ? "" : path.substring(0, cut));
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
    var path =
        "/"
            + location.share()
            + (location.path().isEmpty() ? "" : "/" + location.path())
            + (directory ? "/" : "");
    return Impossible.call(
        () -> new SmbFile(new URI("smb", host, path, null, null).toString(), authentication),
        "the SMB URL is built from a validated share and path");
  }
}
