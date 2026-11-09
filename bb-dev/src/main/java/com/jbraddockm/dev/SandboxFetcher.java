package com.jbraddockm.dev;

import java.io.IOException;

/**
 * Reads a file from an installed application's sandbox, binary-safe. This is the seam that lets a
 * feature which has no device-install transport of its own — the {@code debug} namespace pulling a
 * crash core, say — fetch a sandbox file without reaching into the {@code deploy} feature: {@code
 * deploy} supplies the implementation (over {@code appInstaller.cgi}) and the composition root
 * wires it in.
 */
public interface SandboxFetcher {

  /**
   * Reads the bytes of a file in the application's sandbox.
   *
   * @param app the full application name
   * @param sandboxPath the path within the sandbox, for example {@code logs/App.core}
   * @return the file's bytes
   * @throws IOException if the device cannot be reached, rejects the login, or has no such file
   */
  byte[] fetch(String app, String sandboxPath) throws IOException;

  /** Builds a {@link SandboxFetcher} bound to one device. */
  interface Factory {

    /**
     * Binds a fetcher to a device.
     *
     * @param host the device host or address
     * @param port the HTTPS port
     * @param password the device password
     * @return the fetcher
     */
    SandboxFetcher forDevice(String host, int port, String password);
  }
}
