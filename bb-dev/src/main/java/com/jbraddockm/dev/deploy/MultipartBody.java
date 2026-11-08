package com.jbraddockm.dev.deploy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;

/**
 * The {@code multipart/form-data} body of one {@code appInstaller.cgi} command, built part by part
 * in the order the device expects: {@code command}, the debug and mode flags, the uploaded {@code
 * file}, then {@code package_id}, {@code package_name} and {@code asset_path}. The layout mirrors
 * the original tool's Apache {@code MultipartEntity} in strict mode, so each part carries the same
 * disposition, content type and transfer-encoding headers.
 *
 * <p>A command that uploads a file streams it from disk with the length known in advance, so the
 * request is never sent with chunked transfer-encoding, which the device does not accept. A command
 * with no file is small and is held in memory.
 */
final class MultipartBody {

  private static final String BOUNDARY = "----bbdevDeployBoundary";
  private static final String FILE_PART = "file";
  private static final String FILE_CONTENT_TYPE = "application/zip";

  private final List<StringPart> beforeFile;
  private final FilePart file;
  private final List<StringPart> afterFile;

  private MultipartBody(List<StringPart> beforeFile, FilePart file, List<StringPart> afterFile) {
    this.beforeFile = List.copyOf(beforeFile);
    this.file = file;
    this.afterFile = List.copyOf(afterFile);
  }

  /**
   * Starts a body for a command.
   *
   * @param command the wire verb, the value of the {@code command} part (for example {@code
   *     Install})
   * @return a builder
   */
  static Builder command(String command) {
    return new Builder(command);
  }

  /** The {@code Content-Type} header value, with the boundary this body uses. */
  String contentType() {
    return "multipart/form-data; boundary=" + BOUNDARY;
  }

  /** The number of bytes the body will send. */
  long contentLength() {
    var length = (long) head().length + tail().length;
    return file == null ? length : length + fileLength();
  }

  /** A publisher that sends the body, streaming the file from disk when there is one. */
  HttpRequest.BodyPublisher publisher() {
    if (file == null) {
      return HttpRequest.BodyPublishers.ofByteArray(head());
    }
    var length = contentLength();
    var delegate =
        HttpRequest.BodyPublishers.ofInputStream(
            () -> {
              try {
                return new SequenceInputStream(
                    new SequenceInputStream(
                        new ByteArrayInputStream(head()), Files.newInputStream(file.file())),
                    new ByteArrayInputStream(tail()));
              } catch (IOException failure) {
                throw new UncheckedIOException(failure);
              }
            });
    return new HttpRequest.BodyPublisher() {
      @Override
      public long contentLength() {
        return length;
      }

      @Override
      public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        delegate.subscribe(subscriber);
      }
    };
  }

  /**
   * The bytes before the file content: every part up to and including the file part header, or the
   * whole body (bar the closing boundary) when there is no file. Exposed so a test can read the
   * part layout without a network.
   */
  byte[] head() {
    var out = new ByteArrayOutputStream();
    beforeFile.forEach(part -> write(out, part.encoded()));
    if (file != null) {
      write(out, file.header());
    } else {
      afterFile.forEach(part -> write(out, part.encoded()));
      write(out, closing());
    }
    return out.toByteArray();
  }

  /**
   * The bytes after the file content: the parts that follow the file and the closing boundary.
   * Never called when there is no file.
   */
  byte[] tail() {
    if (file == null) {
      return new byte[0];
    }
    var out = new ByteArrayOutputStream();
    write(out, "\r\n".getBytes(StandardCharsets.US_ASCII));
    afterFile.forEach(part -> write(out, part.encoded()));
    write(out, closing());
    return out.toByteArray();
  }

  private static byte[] closing() {
    return ("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.US_ASCII);
  }

  private long fileLength() {
    try {
      return Files.size(file.file());
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  private static void write(ByteArrayOutputStream out, byte[] bytes) {
    out.writeBytes(bytes);
  }

  /** Assembles the parts of one body. */
  static final class Builder {

    private final List<StringPart> beforeFile = new ArrayList<>();
    private final List<StringPart> afterFile = new ArrayList<>();
    private FilePart file;

    private Builder(String command) {
      beforeFile.add(new StringPart("command", command));
    }

    /** Adds {@code debug_mode=on} when debugging is requested. */
    Builder debugMode(boolean on) {
      return flag("debug_mode", on);
    }

    /** Adds {@code debug_host=<host>} when a debugger host is given. */
    Builder debugHost(String host) {
      return option("debug_host", host);
    }

    /** Adds {@code dev_mode=on} when development mode is requested. */
    Builder devMode(boolean on) {
      return flag("dev_mode", on);
    }

    /** Adds {@code debug_native=on} when a native application is to be held at start-up. */
    Builder debugNative(boolean on) {
      return flag("debug_native", on);
    }

    /** Adds the uploaded {@code file} part: the package to install or the file to put. */
    Builder file(Path path) {
      if (path != null) {
        this.file = new FilePart(path);
      }
      return this;
    }

    /** Adds {@code package_id=<id>} when the command identifies a package. */
    Builder packageId(String id) {
      return afterOption("package_id", id);
    }

    /** Adds {@code package_name=<name>} alongside the id. */
    Builder packageName(String name) {
      return afterOption("package_name", name);
    }

    /** Adds {@code asset_path=<path>} for a file transfer. */
    Builder assetPath(String path) {
      return afterOption("asset_path", path);
    }

    MultipartBody build() {
      return new MultipartBody(beforeFile, file, afterFile);
    }

    private Builder flag(String name, boolean on) {
      if (on) {
        beforeFile.add(new StringPart(name, "on"));
      }
      return this;
    }

    private Builder option(String name, String value) {
      if (value != null) {
        beforeFile.add(new StringPart(name, value));
      }
      return this;
    }

    private Builder afterOption(String name, String value) {
      if (value != null) {
        afterFile.add(new StringPart(name, value));
      }
      return this;
    }
  }

  /** A text part, encoded as the original tool's strict-mode {@code MultipartEntity} would. */
  private record StringPart(String name, String value) {
    byte[] encoded() {
      return ("--"
              + BOUNDARY
              + "\r\nContent-Disposition: form-data; name=\""
              + name
              + "\"\r\nContent-Type: text/plain; charset=US-ASCII\r\nContent-Transfer-Encoding:"
              + " 8bit\r\n\r\n"
              + value
              + "\r\n")
          .getBytes(StandardCharsets.US_ASCII);
    }
  }

  /** The uploaded file part; its content is streamed, not held here. */
  private record FilePart(Path file) {
    byte[] header() {
      return ("--"
              + BOUNDARY
              + "\r\nContent-Disposition: form-data; name=\""
              + FILE_PART
              + "\"; filename=\""
              + file.getFileName()
              + "\"\r\nContent-Type: "
              + FILE_CONTENT_TYPE
              + "\r\nContent-Transfer-Encoding: binary\r\n\r\n")
          .getBytes(StandardCharsets.US_ASCII);
    }
  }

  /**
   * A view of the whole body as bytes, for a command with no file: for tests and small requests.
   *
   * @return the body bytes
   * @throws IOException if the file cannot be read
   */
  byte[] inMemory() throws IOException {
    if (file == null) {
      return head();
    }
    try (InputStream stream = toStream()) {
      return stream.readAllBytes();
    }
  }

  private InputStream toStream() throws IOException {
    return new SequenceInputStream(
        new SequenceInputStream(
            new ByteArrayInputStream(head()), Files.newInputStream(file.file())),
        new ByteArrayInputStream(tail()));
  }
}
