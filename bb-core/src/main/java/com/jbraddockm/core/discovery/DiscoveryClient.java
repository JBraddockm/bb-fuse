package com.jbraddockm.core.discovery;

import com.jbraddockm.core.xml.MalformedXmlException;
import com.jbraddockm.core.xml.XmlElement;
import com.jbraddockm.core.xml.XmlParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Reads a device's identity from the unauthenticated {@code discovery.cgi} on its plain-HTTP port
 * 80. That endpoint answers pre-auth — for an unpaired device too — so it is the identity step both
 * the USB and the Wi-Fi scanners use: given a candidate address, this returns the device's {@link
 * DeviceCharacteristics} (with the PIN and GUID) or nothing when the address is not an answering
 * device. A scanner skips an address that does not identify.
 *
 * <p>The HTTP {@code GET} is a seam ({@link HttpGet}) so a test drives the client without a server.
 */
public final class DiscoveryClient {

  private static final int PORT = 80;
  private static final String PATH = "/cgi-bin/discovery.cgi";
  private static final String CHARACTERISTICS = "DeviceCharacteristics";
  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final String USER_AGENT = "QNXWebClient/1.0";

  private final HttpGet http;

  /** Creates a client that reads over the JDK HTTP client. */
  public DiscoveryClient() {
    this(DiscoveryClient::fetch);
  }

  /**
   * Creates a client that reads over the given transport.
   *
   * @param http performs the one plain-HTTP {@code GET}
   */
  DiscoveryClient(HttpGet http) {
    this.http = http;
  }

  /**
   * Reads the characteristics of the device at a host. An answer identifies a device only when it
   * carries the PIN; an unreachable host, a non-success status, an answer that is not well-formed,
   * or one without a usable PIN (an error response, or empty characteristics) all identify nothing.
   *
   * @param host the candidate address (a USB link-local, or a Wi-Fi address learned from mDNS)
   * @return the characteristics, or empty when the host did not answer as an identifiable device
   */
  public Optional<DeviceCharacteristics> read(String host) {
    Body body;
    try {
      body = http.get(URI.create("http://" + host + ":" + PORT + PATH));
    } catch (IOException _) {
      return Optional.empty();
    }
    if (body.status() < 200 || body.status() >= 300) {
      return Optional.empty();
    }
    XmlElement root;
    try {
      root = XmlParser.parse(body.text());
    } catch (MalformedXmlException _) {
      return Optional.empty();
    }
    return characteristicsElement(root)
        .flatMap(DeviceCharacteristicsParser::parse)
        .filter(characteristics -> characteristics.pin() != null);
  }

  private static Optional<XmlElement> characteristicsElement(XmlElement root) {
    return root.name().equals(CHARACTERISTICS)
        ? Optional.of(root)
        : root.descendant(CHARACTERISTICS);
  }

  private static Body fetch(URI uri) throws IOException {
    var client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    var request =
        HttpRequest.newBuilder(uri).timeout(TIMEOUT).header("User-Agent", USER_AGENT).GET().build();
    try {
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      return new Body(response.statusCode(), response.body());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IOException("interrupted while reading discovery.cgi", interrupted);
    }
  }

  /** A single plain-HTTP {@code GET}, so a test supplies the answer without a server. */
  @FunctionalInterface
  public interface HttpGet {

    /**
     * Fetches a URL with a {@code GET}.
     *
     * @param uri the absolute {@code http://…} URL
     * @return the status and body of the answer
     * @throws IOException if the request could not be made
     */
    Body get(URI uri) throws IOException;
  }

  /**
   * The answer to a {@code GET}.
   *
   * @param status the HTTP status code
   * @param text the response body as text
   */
  public record Body(int status, String text) {}
}
