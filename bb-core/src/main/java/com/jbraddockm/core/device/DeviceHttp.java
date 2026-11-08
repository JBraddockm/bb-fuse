package com.jbraddockm.core.device;

import java.io.IOException;

/**
 * One device HTTPS connection, enough to log in: GET a path, and tell whether the connection now
 * holds the login cookie or offers a client certificate. {@link JdkDeviceHttp} is the real
 * connection over {@code java.net.http}.
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
