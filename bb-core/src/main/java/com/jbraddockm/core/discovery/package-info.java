/**
 * Finding BlackBerry 10 devices and their identity, shared by every client.
 *
 * <p>The {@link com.jbraddockm.core.discovery.DeviceScanner} seam reports a {@link
 * com.jbraddockm.core.discovery.DiscoveredDevice} as each is found; {@link
 * com.jbraddockm.core.discovery.DeviceDiscovery} gathers a scan into a {@link
 * com.jbraddockm.core.discovery.DeviceList}. A platform supplies its own scanners (USB, Wi-Fi); the
 * legacy library-backed scanner lives in the consumer that still depends on it.
 */
package com.jbraddockm.core.discovery;
