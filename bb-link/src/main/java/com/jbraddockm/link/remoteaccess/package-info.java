/**
 * Host remote access — the computer shown as a WebDAV drive in the device's File Manager (RIM's
 * {@code rimdav}). {@link com.jbraddockm.link.remoteaccess.RemoteAccess} is the on/off control; it
 * speaks the {@link com.jbraddockm.core.webdav} protocol over a plain blocking-socket HTTP/1.1
 * server, confined to one selectable root directory by {@link
 * com.jbraddockm.link.remoteaccess.JailedRoot}, and is advertised and bound onto the bp2p tunnel
 * for the paired device. The connect session owns it as a {@link
 * com.jbraddockm.link.transport.session.TunnelScopedServer}, draining it before the tunnel drops.
 *
 * <p>Only this package serves files; the WebDAV <em>protocol</em> it uses is shared with bb-blend's
 * device-file client and lives in bb-core.
 */
package com.jbraddockm.link.remoteaccess;
