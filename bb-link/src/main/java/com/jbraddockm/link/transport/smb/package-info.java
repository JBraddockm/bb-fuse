/**
 * The in-process SMB1 client for the device's file share.
 *
 * <p>The device serves its media folders over SMB1/CIFS (Samba). This package reaches that share
 * directly with an embedded SMB1 client, so the host operating system never has to enable its own
 * SMB1 support. The share credentials and host come from a {@link
 * com.jbraddockm.link.transport.login.SmbCredentials} handed out at login.
 *
 * <p>{@link com.jbraddockm.link.transport.smb.FileStore} is the capability — list, stat, pull and
 * push over neutral types ({@link com.jbraddockm.link.transport.smb.SmbLocation}, {@link
 * com.jbraddockm.link.transport.smb.SmbEntry}). The jcifs library is confined to this package: its
 * types never appear in the API, and failures are translated to {@link
 * com.jbraddockm.link.transport.smb.SmbTransferException}.
 */
package com.jbraddockm.link.transport.smb;
