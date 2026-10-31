package com.jbraddockm.link.transport.cgi;

import java.io.IOException;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.util.EntityUtils;

/**
 * A single unauthenticated plain-HTTP {@code GET}, separate from the device-manager {@link
 * CgiTransport} session. It exists for the one part of the device interface served only on the
 * plain {@code :80} listener — {@code discovery.cgi} over USB — which the TLS session on {@code
 * :8443} cannot reach; everything else goes through the authenticated session.
 */
public final class PlainHttpGet {

  private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
  private static final int READ_TIMEOUT_MILLIS = 10_000;
  private static final String USER_AGENT = "QNXWebClient/1.0";

  private PlainHttpGet() {}

  /**
   * Fetches a URL with a {@code GET}: no authentication, no cookies, no TLS.
   *
   * @param url the absolute {@code http://…} URL
   * @return the answer's status, content type and body (no cookie, no downloaded file)
   * @throws IOException if the request could not be made
   */
  public static CgiResponse fetch(String url) throws IOException {
    var client = new DefaultHttpClient();
    try {
      HttpConnectionParams.setConnectionTimeout(client.getParams(), CONNECT_TIMEOUT_MILLIS);
      HttpConnectionParams.setSoTimeout(client.getParams(), READ_TIMEOUT_MILLIS);
      HttpProtocolParams.setUserAgent(client.getParams(), USER_AGENT);
      var response = client.execute(new HttpGet(url));
      HttpEntity entity = response.getEntity();
      var body = entity == null ? "" : EntityUtils.toString(entity, "UTF-8");
      var contentType = response.getFirstHeader("Content-Type");
      return new CgiResponse(
          response.getStatusLine().getStatusCode(),
          contentType == null ? null : contentType.getValue(),
          body,
          null);
    } finally {
      client.getConnectionManager().shutdown();
    }
  }
}
