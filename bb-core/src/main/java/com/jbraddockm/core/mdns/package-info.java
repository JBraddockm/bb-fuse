/**
 * The shared in-process mDNS / DNS-SD facility.
 *
 * <p>{@link com.jbraddockm.core.mdns.MdnsService} keeps one responder for the whole process and
 * serves both advertising (a {@link com.jbraddockm.core.mdns.ServiceAdvert}) and browsing, so the
 * device-name advertise and the Wi-Fi device browse share a single JmDNS instance rather than
 * contend on UDP 5353. Swift and Blend use native Bonjour directly; this serves the JVM and
 * native-binary consumers.
 */
package com.jbraddockm.core.mdns;
