package com.jbraddockm.link.transport.cgi;

import com.jbraddockm.core.device.ClientCertificate;
import com.jbraddockm.core.device.ClientKeyManager;
import com.jbraddockm.core.device.DeviceTls;
import com.jbraddockm.core.device.PemIdentity;
import com.jbraddockm.core.error.InvalidArgumentException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import javax.net.ssl.KeyManager;

/**
 * One HTTPS connection to one device with its own cookie jar, on {@code java.net.http}. It is
 * configured like the original tool's client: HTTP/1.1, the same user agent, a ten second connect
 * timeout and TLS that accepts the device's certificate ({@link DeviceTls}).
 *
 * <p>A device presents a self-signed certificate for an address it does not name, so the host
 * cannot be verified against it. {@code java.net.http} verifies the host by default and has no
 * per-client switch for it, so the program that builds this transport starts the JVM with {@code
 * -Djdk.internal.httpclient.disableHostnameVerification=true} (see {@code Launcher}). The trust
 * manager still accepts the certificate; only the name check is turned off.
 */
public final class CgiTransport implements AutoCloseable {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final String USER_AGENT = "QNXWebClient/1.0";
  private static final long MINIMUM_PROGRESS_STEP = 64 * 1024L;

  private final DeviceTarget target;
  private final ExchangeObserver observer;
  private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
  private final HttpClient client;
  private final URI base;
  private boolean closed;

  public CgiTransport(DeviceTarget target, ExchangeObserver observer) throws IOException {
    this.target = target;
    this.observer = observer;
    this.base = URI.create(target.url("/"));
    var keyManagers =
        target.authentication().certificateToOffer().isPresent()
            ? clientKeyManagers(target.authentication().certificateToOffer().get())
            : null;
    this.client =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(CONNECT_TIMEOUT)
            .cookieHandler(cookies)
            .sslContext(DeviceTls.context(keyManagers))
            .build();
  }

  private static KeyManager[] clientKeyManagers(ClientCertificate source) throws IOException {
    return new KeyManager[] {new ClientKeyManager(PemIdentity.load(source))};
  }

  /**
   * Whether a download's answer is the data stream (written to the file) rather than an error
   * envelope (kept in the body). The backup archive comes as {@code octet-stream}; a contact photo
   * comes as an {@code image/…} type, while an error — "not modified", "unauthorised" — comes as
   * JSON or XML and must stay in the body so the conversation can read it.
   */
  private static boolean isStreamedAnswer(String contentType) {
    if (contentType == null) {
      return false;
    }
    var lower = contentType.toLowerCase(Locale.ROOT);
    return lower.contains("octet-stream") || lower.contains("image/");
  }

  public DeviceTarget target() {
    return target;
  }

  /** The cookie jar of this connection, so the session can read and adjust the login cookie. */
  public java.net.CookieStore cookies() {
    return cookies.getCookieStore();
  }

  /**
   * Keeps a cookie in the jar for this device, whatever its expiry. The login uses this to hold the
   * {@code dtmauth} cookie when the device's clock makes it look already over.
   *
   * @param cookie the cookie to keep
   */
  public void keepCookie(HttpCookie cookie) {
    cookies.getCookieStore().add(base, cookie);
  }

  public CgiResponse get(String pathAndQuery) throws IOException {
    var uri = uriOf(pathAndQuery);
    var request = builder(uri, CgiRequest.DEFAULT_READ_TIMEOUT).GET().build();
    return execute(request, "GET " + pathAndQuery, "");
  }

  void send(CgiRequest request) throws IOException {
    send(request, UploadObserver.NONE);
  }

  public CgiResponse send(CgiRequest request, UploadObserver observer) throws IOException {
    return switch (request) {
      case CgiRequest.Get get -> get(get.pathAndQuery());
      case CgiRequest.Form form -> {
        var body = form.encodedBody();
        var request1 =
            builder(uriOf(form.endpoint().path()), form.readTimeout())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.US_ASCII))
                .build();
        yield execute(request1, "POST " + form.endpoint().path(), body);
      }
      case CgiRequest.Body body -> {
        var request1 =
            builder(uriOf(body.pathAndQuery()), body.readTimeout())
                .header("Content-Type", body.contentType())
                .POST(HttpRequest.BodyPublishers.ofString(body.text(), StandardCharsets.UTF_8))
                .build();
        yield execute(request1, "POST " + body.pathAndQuery(), body.text());
      }
      case CgiRequest.Upload upload -> upload(upload, observer);
      case CgiRequest.Download download -> download(download, observer);
      case CgiRequest.StreamingGet streamingGet -> streamingGet(streamingGet, observer);
      case CgiRequest.Multipart multipart -> multipart(multipart, observer);
    };
  }

  /**
   * Sends a GET whose answer is streamed to a file. Like {@link #download}, an octet-stream or
   * image answer is written to the request's destination and the body is left empty, while an XML
   * or JSON status/error stays in the body for the conversation to read. This is the GET
   * counterpart of the POST download: {@code support.cgi}'s DumpLog drives its whole state machine
   * with this one GET.
   */
  private CgiResponse streamingGet(CgiRequest.StreamingGet request, UploadObserver observer)
      throws IOException {
    var http = builder(uriOf(request.pathAndQuery()), request.readTimeout()).GET().build();
    return streamed(
        http,
        "GET " + request.pathAndQuery(),
        "",
        request.destination(),
        request.expectedLength(),
        observer);
  }

  /**
   * Sends a request whose answer is streamed to a file and turns the response into a {@link
   * CgiResponse}: a data stream (octet-stream or image) becomes a {@link CgiResponse#downloaded()}
   * with an empty body, anything else — an XML or JSON error — stays in the body so the
   * conversation can read it. Shared by the POST {@link #download} and the GET {@link
   * #streamingGet}.
   *
   * @param requestBody the request body for the trace, empty for a GET
   */
  private CgiResponse streamed(
      HttpRequest http,
      String requestLine,
      String requestBody,
      Path destination,
      long expectedLength,
      UploadObserver observer)
      throws IOException {
    var cookieNames = cookieNames();
    try {
      var response = client.send(http, streamingHandler(destination, expectedLength, observer));
      var contentType = header(response, "Content-Type");
      var setCookie = header(response, "Set-Cookie");
      var outcome = response.body();
      var result =
          outcome.downloaded() != null
              ? new CgiResponse(
                  response.statusCode(), contentType, "", setCookie, outcome.downloaded())
              : new CgiResponse(response.statusCode(), contentType, outcome.body(), setCookie);
      this.observer.onExchange(requestLine, requestBody, cookieNames, result);
      return result;
    } catch (IOException failure) {
      this.observer.onFailedExchange(requestLine, requestBody, cookieNames, failure);
      throw failure;
    } catch (InterruptedException interrupted) {
      throw interruptedToIo(interrupted, requestLine, requestBody, cookieNames);
    }
  }

  /**
   * Sends one file as the single part of a {@code multipart/form-data} POST, with the rest of the
   * request in the query string: a contact photo put. The file is read into memory (photos are
   * small) and the trace gets the name and size of the file, never its content.
   */
  private CgiResponse multipart(CgiRequest.Multipart request, UploadObserver observer)
      throws IOException {
    var fileBytes = Files.readAllBytes(request.file());
    var fileName = request.file().getFileName().toString();
    var boundary = "----bblinkPhotoBoundary";
    // The device reads the item descriptor from a "data" form field in the body (not the query, the
    // way command/service are read), then wraps it as [{<data>,"filepath":"…"}]. Send it as its own
    // part when present; without it the device sees a malformed [{,"filepath":…}] and answers 404.
    var dataPart =
        request.item().isEmpty()
            ? new byte[0]
            : ("--"
                    + boundary
                    + "\r\nContent-Disposition: form-data; name=\"data\"\r\n\r\n"
                    + request.item()
                    + "\r\n")
                .getBytes(StandardCharsets.UTF_8);
    var preamble =
        ("--"
                + boundary
                + "\r\nContent-Disposition: form-data; name=\""
                + request.fileField()
                + "\"; filename=\""
                + fileName
                + "\"\r\nContent-Type: "
                + request.fileContentType()
                + "\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8);
    var epilogue = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
    var body = new byte[dataPart.length + preamble.length + fileBytes.length + epilogue.length];
    var at = 0;
    System.arraycopy(dataPart, 0, body, at, dataPart.length);
    at += dataPart.length;
    System.arraycopy(preamble, 0, body, at, preamble.length);
    at += preamble.length;
    System.arraycopy(fileBytes, 0, body, at, fileBytes.length);
    at += fileBytes.length;
    System.arraycopy(epilogue, 0, body, at, epilogue.length);

    var http =
        builder(uriOf(request.pathAndQuery()), request.readTimeout())
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();
    observer.sent(fileBytes.length);
    return execute(
        http,
        "POST " + request.pathAndQuery(),
        "(multipart/form-data, "
            + fileBytes.length
            + " bytes: "
            + fileName
            + " as "
            + request.fileField()
            + ")");
  }

  /**
   * Sends a file as the raw body of a POST, streamed from disk with its length known so the body is
   * never chunked. The trace gets the name and the size of the file and never its content.
   */
  private CgiResponse upload(CgiRequest.Upload upload, UploadObserver observer) throws IOException {
    var http =
        builder(uriOf(upload.pathAndQuery()), upload.readTimeout())
            .header("Content-Type", "application/octet-stream")
            .POST(countedFile(upload.file(), upload.length(), observer))
            .build();
    return execute(
        http,
        "POST " + upload.pathAndQuery(),
        "(application/octet-stream, "
            + upload.length()
            + " bytes: "
            + upload.file().getFileName()
            + ")");
  }

  /**
   * A body publisher that streams a file of a known length, reporting bytes as they are read. The
   * known length keeps the request from being sent with chunked transfer encoding, which the device
   * does not accept.
   */
  private static HttpRequest.BodyPublisher countedFile(
      Path file, long length, UploadObserver observer) {
    var delegate =
        HttpRequest.BodyPublishers.ofInputStream(
            () -> {
              try {
                return new CountingInputStream(Files.newInputStream(file), length, observer);
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
   * Sends a form post whose answer is streamed to a file. When the device answers with octet-stream
   * — the archive — or an image — a photo — it is written to the request's destination and the
   * response body is left empty; when it answers with anything else — an XML or JSON error envelope
   * — that is kept in the body as usual, so the conversation can read the error.
   */
  private CgiResponse download(CgiRequest.Download download, UploadObserver observer)
      throws IOException {
    var body = download.encodedBody();
    var http =
        builder(uriOf(download.endpoint().path()), download.readTimeout())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.US_ASCII))
            .build();
    return streamed(
        http,
        "POST " + download.endpoint().path(),
        body,
        download.destination(),
        download.expectedLength(),
        observer);
  }

  /** A data answer that was streamed to a file, or an error envelope kept as text. */
  private record DownloadOutcome(String body, CgiResponse.Downloaded downloaded) {}

  /**
   * A body handler that inspects the content type when the answer arrives: a data stream is written
   * to the file with progress, anything else is kept as a UTF-8 string so an error can be read.
   */
  private HttpResponse.BodyHandler<DownloadOutcome> streamingHandler(
      Path destination, long expectedLength, UploadObserver observer) {
    return info -> {
      var contentType = info.headers().firstValue("Content-Type").orElse(null);
      if (isStreamedAnswer(contentType)) {
        var writer = new StreamingFileWriter(destination, expectedLength, observer);
        return HttpResponse.BodySubscribers.mapping(
            HttpResponse.BodySubscribers.ofByteArrayConsumer(writer),
            _ ->
                new DownloadOutcome(
                    "", new CgiResponse.Downloaded(destination, writer.bytesWritten())));
      }
      return HttpResponse.BodySubscribers.mapping(
          HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8),
          text -> new DownloadOutcome(text, null));
    };
  }

  /**
   * Writes a streamed data answer to a file as its chunks arrive, reporting bytes the way an upload
   * reports them: about once per percent, and at least every 64 KiB.
   */
  private static final class StreamingFileWriter implements Consumer<Optional<byte[]>> {

    private final OutputStream out;
    private final long step;
    private final UploadObserver observer;
    private long total;
    private long lastReported;

    StreamingFileWriter(Path destination, long expectedLength, UploadObserver observer) {
      try {
        this.out = Files.newOutputStream(destination);
      } catch (IOException failure) {
        throw new UncheckedIOException(failure);
      }
      this.step = Math.max(MINIMUM_PROGRESS_STEP, expectedLength / 100);
      this.observer = observer;
    }

    long bytesWritten() {
      return total;
    }

    @Override
    public void accept(Optional<byte[]> chunk) {
      try {
        if (chunk.isPresent()) {
          var bytes = chunk.get();
          out.write(bytes);
          total += bytes.length;
          if (total - lastReported >= step) {
            lastReported = total;
            observer.sent(total);
          }
        } else {
          out.close();
        }
      } catch (IOException failure) {
        closeQuietly();
        throw new UncheckedIOException(failure);
      }
    }

    private void closeQuietly() {
      try {
        out.close();
      } catch (IOException _) {
        // the write already failed; nothing more to do
      }
    }
  }

  private HttpRequest.Builder builder(URI uri, Duration readTimeout) {
    return HttpRequest.newBuilder(uri).timeout(readTimeout).header("User-Agent", USER_AGENT);
  }

  private URI uriOf(String pathAndQuery) {
    var url = target.url(pathAndQuery);
    if (URI.create(url).getHost() == null) {
      throw new InvalidArgumentException("host", "is not a usable host name or address");
    }
    return URI.create(url);
  }

  private List<String> cookieNames() {
    return cookies.getCookieStore().getCookies().stream()
        .map(HttpCookie::getName)
        .sorted()
        .toList();
  }

  private static String header(HttpResponse<?> response, String name) {
    return response.headers().firstValue(name).orElse(null);
  }

  private CgiResponse execute(HttpRequest request, String requestLine, String requestBody)
      throws IOException {
    if (closed) {
      throw new IllegalStateException("the transport is closed");
    }
    var cookieNames = cookieNames();
    try {
      var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
      var result =
          new CgiResponse(
              response.statusCode(),
              header(response, "Content-Type"),
              new String(response.body(), StandardCharsets.UTF_8),
              header(response, "Set-Cookie"));
      observer.onExchange(requestLine, requestBody, cookieNames, result);
      return result;
    } catch (IOException failure) {
      observer.onFailedExchange(requestLine, requestBody, cookieNames, failure);
      throw failure;
    } catch (InterruptedException interrupted) {
      throw interruptedToIo(interrupted, requestLine, requestBody, cookieNames);
    }
  }

  private IOException interruptedToIo(
      InterruptedException interrupted,
      String requestLine,
      String requestBody,
      List<String> cookieNames) {
    Thread.currentThread().interrupt();
    var failure = new IOException("the device request was interrupted", interrupted);
    observer.onFailedExchange(requestLine, requestBody, cookieNames, failure);
    return failure;
  }

  @Override
  public void close() {
    closed = true;
    client.close();
  }
}
