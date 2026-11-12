package com.jbraddockm.link.share;

import java.io.IOException;
import java.net.Inet6Address;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The production {@link WebdavAdvertiser.ProxyAdvertiser}: it drives macOS' {@code dns-sd} in proxy
 * mode to advertise a service whose host record resolves to an address this machine does not hold
 * on the LAN interface (the tunnel ULA). This is the only readily available way to publish that
 * cross-interface record — JmDNS cannot — and it is how BlackBerry Link's host advertises the
 * share.
 *
 * <pre>
 *   dns-sd -P &lt;name&gt; _bp2p._tcp,_webdav local &lt;port&gt; &lt;ULAhex&gt;.local &lt;ULA&gt; &lt;txt…&gt;
 * </pre>
 *
 * <p>{@code dns-sd -P} keeps running as the proxy responder for as long as the advert should stand,
 * so the registration holds the process and closing it ends the advert. This is macOS-specific,
 * like the tunnel's own {@code ifconfig}/{@code route} usage; the file share is a macOS feature
 * today.
 */
final class DnsSdProxyAdvertiser implements WebdavAdvertiser.ProxyAdvertiser {

  static final DnsSdProxyAdvertiser INSTANCE = new DnsSdProxyAdvertiser();

  /**
   * How long to wait for {@code dns-sd} to stay up before treating an immediate exit as failure.
   */
  private static final long LIVENESS_MILLIS = 500;

  private DnsSdProxyAdvertiser() {}

  @Override
  public AutoCloseable advertise(
      String serviceName,
      String serviceType,
      int port,
      String hostName,
      Inet6Address address,
      Map<String, String> txt)
      throws IOException {
    var command = new ArrayList<String>();
    command.add("dns-sd");
    command.add("-P");
    command.add(serviceName);
    command.add(serviceType);
    command.add("local");
    command.add(Integer.toString(port));
    command.add(hostName + ".local");
    command.add(address.getHostAddress());
    txt.forEach((key, value) -> command.add(key + "=" + value));

    var process =
        new ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start();
    try {
      if (process.waitFor(LIVENESS_MILLIS, TimeUnit.MILLISECONDS)) {
        throw new IOException(
            "dns-sd -P exited immediately (code "
                + process.exitValue()
                + ") registering "
                + serviceName);
      }
    } catch (InterruptedException interrupted) {
      process.destroy();
      Thread.currentThread().interrupt();
      throw new IOException("interrupted while starting dns-sd -P", interrupted);
    }
    return process::destroy;
  }
}
