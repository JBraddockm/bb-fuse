package com.jbraddockm.link.endpoint.characteristics;

import com.jbraddockm.core.discovery.DeviceCharacteristics;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.transport.cgi.PlainHttpGet;
import com.jbraddockm.link.transport.cgi.ResponseEnvelope;
import com.jbraddockm.link.transport.cgi.Step;
import java.io.IOException;

/**
 * Reads the device characteristics from the unauthenticated {@code discovery.cgi} on the device's
 * plain-HTTP port 80.
 *
 * <p>Over USB the management listener on {@code :8443} does <em>not</em> serve {@code
 * discovery.cgi} — on that transport it lives only on the separate, unauthenticated {@code :80}
 * listener — so the session cannot read it there: a GET to {@code :8443/cgi-bin/discovery.cgi} is
 * answered by the web server closing the connection with no reply, which also costs a needless
 * re-login. This reads it from {@code :80} directly, outside the TLS session, and feeds the answer
 * to the same parser the session path uses, so the characteristics (the device PIN, GUID, developer
 * mode, flash size, SD card, Blend policy and fuse state) are still available over USB. Over the
 * tunnel the session's own listener serves {@code discovery.cgi}, so this is only for the USB path.
 */
public final class UsbDiscovery {

  /** The plain-HTTP port the device serves {@code discovery.cgi} on over USB. */
  static final int HTTP_PORT = 80;

  private static final String PATH = "/cgi-bin/discovery.cgi";

  private UsbDiscovery() {}

  /**
   * Reads the characteristics from {@code http://<host>:80/cgi-bin/discovery.cgi}.
   *
   * @param host the device's USB address
   * @return the characteristics as a {@link Step.Finish}, or why they could not be read as a {@link
   *     Step.Fail}
   * @throws IOException if the request could not be made
   */
  public static Step<DeviceCharacteristics> read(String host) throws IOException {
    return readUrl("http://" + host + ":" + HTTP_PORT + PATH);
  }

  /**
   * Reads and interprets {@code discovery.cgi} at an absolute URL (its own method, for testing).
   */
  static Step<DeviceCharacteristics> readUrl(String url) throws IOException {
    var response = PlainHttpGet.fetch(url);
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      return new Step.Fail<>(
          new ServiceError(
              LinkErrorCode.UNEXPECTED_RESPONSE,
              "discovery.cgi on port " + HTTP_PORT + " returned HTTP " + response.statusCode()));
    }
    return GetDeviceCharacteristics.interpret(ResponseEnvelope.parse(response.body()));
  }
}
