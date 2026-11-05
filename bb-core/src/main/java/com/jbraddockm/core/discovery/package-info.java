/**
 * Finding BlackBerry 10 devices and their identity, shared by every client.
 *
 * <p>The {@link com.jbraddockm.core.discovery.DeviceScanner} seam reports a {@link
 * com.jbraddockm.core.discovery.DiscoveredDevice} as each is found; {@link
 * com.jbraddockm.core.discovery.DeviceDiscovery} gathers a scan into a {@link
 * com.jbraddockm.core.discovery.DeviceList}, folding a device found more than once into one entry.
 * {@link com.jbraddockm.core.discovery.PlatformScanners} supplies the native scanners for the host
 * — so far the {@link com.jbraddockm.core.discovery.UsbScanner}, which identifies a device over
 * {@link com.jbraddockm.core.discovery.DiscoveryClient} — and a {@link
 * com.jbraddockm.core.discovery.CompositeScanner} runs several at once. The legacy library-backed
 * scanner lives in the consumer that still depends on it.
 */
package com.jbraddockm.core.discovery;
