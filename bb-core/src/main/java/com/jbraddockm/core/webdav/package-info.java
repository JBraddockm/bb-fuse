/**
 * The WebDAV protocol vocabulary shared by bb-link's file-share server and bb-blend's device-file
 * client: the method set, the status codes, the {@code DAV:multistatus} document (built by the
 * server, parsed by the client), path-segment encoding, and the {@code Authi-P} auth header. These
 * are the parts of RIM's {@code rimdav} wire protocol that do not depend on <em>who</em> serves —
 * so they live here, while the socket/filesystem server stays in bb-link and the client in
 * bb-blend.
 *
 * <p>Nothing here opens a socket or touches the filesystem.
 */
package com.jbraddockm.core.webdav;
