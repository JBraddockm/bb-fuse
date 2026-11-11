/**
 * The host's file-share server — the computer shown as a WebDAV drive in the device's File Manager
 * (RIM's {@code rimdav}). The server speaks the {@link com.jbraddockm.core.webdav} protocol over a
 * plain JDK HTTP server, confined to one selectable root directory by {@link
 * com.jbraddockm.link.share.JailedRoot}; the file share is advertised and bound onto the bp2p
 * tunnel by the daemon (tracker N1).
 *
 * <p>Only this package serves files; the WebDAV <em>protocol</em> it uses is shared with bb-blend's
 * device-file client and lives in bb-core.
 */
package com.jbraddockm.link.share;
