package com.jbraddockm.dev.packaging;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Installs or launches a freshly built bar on a device — the bridge from the {@code package}
 * namespace to bb-dev's own {@code deploy} engine. The launcher supplies the deploy-backed
 * implementation, so the packaging feature depends on this interface, not on the deploy feature.
 */
public interface BarDeployer {

  /**
   * Installs the bar, optionally launching it afterwards.
   *
   * @param bar the built bar
   * @param host the device host or address
   * @param port the device HTTPS port
   * @param password the device password
   * @param devMode whether to install in development mode
   * @param launch whether to launch the application after installing it
   * @return a one-line summary of what happened
   * @throws IOException if the device rejects the install
   */
  String install(Path bar, String host, int port, String password, boolean devMode, boolean launch)
      throws IOException;

  /**
   * Launches the application the bar identifies, without installing it.
   *
   * @param bar the built bar, read for its application identity
   * @param host the device host or address
   * @param port the device HTTPS port
   * @param password the device password
   * @return a one-line summary of what happened
   * @throws IOException if the device rejects the launch
   */
  String launch(Path bar, String host, int port, String password) throws IOException;
}
