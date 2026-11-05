package com.jbraddockm.core.mdns;

import java.util.HashMap;
import java.util.Map;
import javax.jmdns.ServiceInfo;

/**
 * A DNS-SD service to advertise over mDNS.
 *
 * @param type the service type, for example {@code _bp2p._tcp.local.}
 * @param instance the instance name within the type
 * @param subtype the DNS-SD subtype to publish under, or {@code null} for none
 * @param port the SRV port
 * @param txt the TXT record entries
 */
public record ServiceAdvert(
    String type, String instance, String subtype, int port, Map<String, String> txt) {

  /** Validates the advert and takes an immutable copy of its TXT entries. */
  public ServiceAdvert {
    if (type == null || type.isBlank()) {
      throw new IllegalArgumentException("a service type is required");
    }
    if (instance == null || instance.isBlank()) {
      throw new IllegalArgumentException("an instance name is required");
    }
    if (port < 0 || port > 65535) {
      throw new IllegalArgumentException("port out of range: " + port);
    }
    txt = Map.copyOf(txt);
  }

  /** The JmDNS service info this advert describes (a fresh, mutable TXT map, as JmDNS expects). */
  ServiceInfo toServiceInfo() {
    return ServiceInfo.create(type, instance, subtype, port, 0, 0, true, new HashMap<>(txt));
  }
}
