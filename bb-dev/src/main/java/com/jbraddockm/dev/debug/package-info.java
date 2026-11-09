/**
 * The {@code debug} namespace: inspect a running or crashed application. {@code debug run} spawns
 * an executable and streams its console over the qconn {@code launcher}; {@code debug core} finds
 * the newest crash dump over the launcher and downloads it through an injected {@link
 * com.jbraddockm.dev.SandboxFetcher} (deploy's {@code appInstaller} transport), since the qconn
 * file service cannot read a core. This feature sits on {@code qconn} and never imports another
 * feature.
 */
package com.jbraddockm.dev.debug;
