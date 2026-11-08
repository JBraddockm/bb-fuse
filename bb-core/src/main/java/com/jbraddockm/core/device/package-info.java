/**
 * The shared, pure-JDK client stack for talking to a BlackBerry 10 device over its HTTPS surface:
 * the web-interface login (challenge-response with {@link com.jbraddockm.core.device.PwCrypto}),
 * the client TLS identity and the session that bb-link (CGI), bb-dev (deploy), bb-tunnel and
 * bb-blend share rather than each reimplement.
 *
 * <p>It owns the device-facing <em>mechanism</em> only. The endpoints and command verbs stay with
 * each consumer (pimsync in bb-link, {@code appInstaller.cgi} in bb-dev), and the
 * client-certificate identity is supplied by the caller, so this package never learns about another
 * module. It is the HTTPS login surface, not the qconndoor/SSH-daemon handshake.
 */
package com.jbraddockm.core.device;
