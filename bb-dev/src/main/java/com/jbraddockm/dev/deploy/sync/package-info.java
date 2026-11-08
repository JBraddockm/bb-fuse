/**
 * The dev-mode fast install: a tiny mini-BAR to create the app sandbox on the device, then only the
 * changed assets streamed in over qconn. {@link com.jbraddockm.dev.deploy.sync.SyncService} drives
 * it; the deploy front-ends call it for a development-mode install.
 */
package com.jbraddockm.dev.deploy.sync;
