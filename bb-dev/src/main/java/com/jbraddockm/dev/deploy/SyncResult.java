package com.jbraddockm.dev.deploy;

/**
 * The outcome of a dev-mode sync install.
 *
 * @param nothingToSync whether the device was already up to date, so no asset was uploaded
 * @param uploadedAssets how many assets were pushed
 */
record SyncResult(boolean nothingToSync, int uploadedAssets) {}
