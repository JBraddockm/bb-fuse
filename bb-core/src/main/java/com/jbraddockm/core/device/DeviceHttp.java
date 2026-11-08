package com.jbraddockm.core.device;

import java.io.IOException;

/**
 * One device HTTPS connection, enough to log in: GET a path, and tell whether the connection now
 * holds the login cookie or offers a client certificate. Keeping the login logic to this small seam
 * lets it be driven in tests without a network; {@link JdkDeviceHttp} is the real connection.
 */
public interface DeviceHttp {

  /**
   * GETs a path on the device and returns the response body.
   *
   * @param pathAndQuery the absolute path and query, for example {@code /cgi-bin/login.cgi?...}
   * @return the response body
   * @throws IOException if the request fails
   */
  String get(String pathAndQuery) throws IOException;

  /** Whether the connection now holds the device's login ({@code dtmauth}) session cookie. */
  boolean hasLoginCookie();

  /**
   * Whether this connection offers a client certificate (a passwordless login is then possible).
   */
  boolean offersClientCertificate();
}
