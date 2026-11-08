package com.jbraddockm.dev.deploy;

import com.jbraddockm.core.daemon.ServiceSession;

/**
 * A daemon session: one logged-in connection to a device, held open across commands. It wraps the
 * native {@link AppInstallerEngine}, which logs in on the first command and keeps the session; the
 * daemon closes it when the client disconnects.
 */
public final class DeploySession implements ServiceSession {

  private final AppInstallerEngine engine;

  DeploySession(AppInstallerEngine engine) {
    this.engine = engine;
  }

  AppInstallerEngine engine() {
    return engine;
  }

  @Override
  public void close() {
    engine.close();
  }
}
