package com.jbraddockm.core.discovery;

import java.util.Optional;

/**
 * Resolves a device's PIN from its bp2p mDNS advert, without a tunnel. A plain Wi-Fi sighting has
 * no PIN, but its {@code Friendly} instance name folds a seed-salted hash of the device's PIN, so a
 * caller holding the paired PINs can recompute the fold and recover which device it is — a local
 * hash, no network.
 *
 * <p>A seam, so {@code bb-core} need not know where the paired PINs live or how the fold works: a
 * consumer supplies the resolver and {@link MdnsScanner} stamps the PIN onto the sighting. The
 * default {@link #NONE} resolves nothing.
 */
@FunctionalInterface
public interface DevicePinResolver {

  /** A resolver that never matches: Wi-Fi sightings keep their {@code null} PIN. */
  DevicePinResolver NONE = _ -> Optional.empty();

  /**
   * Resolves the PIN of the device whose {@code Friendly} bp2p advert this is.
   *
   * @param bp2pInstanceName the bp2p {@code Friendly} service instance name as advertised (for
   *     example {@code Friendly_<seed>_<deviceHash>}), or {@code null} when the sighting has none
   * @return the device's PIN when a paired device folds to the advertised hash, otherwise empty
   */
  Optional<String> resolve(String bp2pInstanceName);
}
