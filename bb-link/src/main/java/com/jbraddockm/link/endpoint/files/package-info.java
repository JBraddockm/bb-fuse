/**
 * The device file-access methods: listing, stat, and copying files to and from the device's share.
 *
 * <p>These are the client-facing primitives over the SMB transport ({@link
 * com.jbraddockm.link.transport.smb}). A client names files by their device-absolute path; the
 * commands translate that to a share location, run one SMB operation, and return a typed result.
 * There is no sync, merge or recursion here — that is a client's concern.
 */
package com.jbraddockm.link.endpoint.files;
