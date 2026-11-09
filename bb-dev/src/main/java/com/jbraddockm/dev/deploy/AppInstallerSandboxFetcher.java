package com.jbraddockm.dev.deploy;

import com.jbraddockm.core.device.LoginException;
import com.jbraddockm.dev.SandboxFetcher;
import java.io.IOException;

/**
 * A {@link SandboxFetcher} backed by the deploy {@link AppInstallerEngine}: it reads a sandbox file
 * binary-safe over {@code appInstaller.cgi}. The composition root wires {@link #factory()} into the
 * {@code debug} namespace so a crash-core pull can reuse deploy's transport without the {@code
 * debug} feature importing it.
 */
public final class AppInstallerSandboxFetcher implements SandboxFetcher {

  private final String host;
  private final int port;
  private final String password;

  private AppInstallerSandboxFetcher(String host, int port, String password) {
    this.host = host;
    this.port = port;
    this.password = password;
  }

  /**
   * A factory that binds a fetcher to a device.
   *
   * @return the factory
   */
  public static SandboxFetcher.Factory factory() {
    return AppInstallerSandboxFetcher::new;
  }

  @Override
  public byte[] fetch(String app, String sandboxPath) throws IOException {
    var engine = AppInstallerEngine.forDevice(host, port, password, null);
    try {
      return engine.download(
          new DeployCommand.GetFile(new PackageRef.FullName(app), sandboxPath, null));
    } catch (LoginException | DeviceReportException reported) {
      throw new IOException(reported.getMessage(), reported);
    } finally {
      engine.close();
    }
  }
}
