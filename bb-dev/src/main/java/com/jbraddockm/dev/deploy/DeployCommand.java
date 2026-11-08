package com.jbraddockm.dev.deploy;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A command the deploy engine can run on a device. Each variant carries only the parameters its
 * {@code appInstaller.cgi} request needs and validates them when it is created, so an instance is
 * always ready to run. Unlike the legacy model it holds no command-line arguments and no output
 * parser — the engine maps the variant to a request and parses the device's answer itself.
 */
public sealed interface DeployCommand {

  /** Checks that the device is reachable and accepts the login. */
  record TestConnection() implements DeployCommand {}

  /** Lists the applications installed on the device. */
  record ListInstalledApps() implements DeployCommand {}

  /** Reads the device's properties. */
  record ListDeviceInfo() implements DeployCommand {}

  /**
   * Installs an application from a bar file, optionally launching it. A development-mode install of
   * a native bar uses the incremental sync (mini-BAR + the changed assets over qconn) unless {@code
   * noSync} forces the full upload.
   *
   * @param bar the bar file to install
   * @param launch whether to launch the application after installing it
   * @param devMode whether to install in development mode
   * @param debugHost the host of a debugger to attach, or {@code null}
   * @param debugNative whether to hold a native application at start-up for a native debugger
   * @param noSync whether to force a full upload instead of the dev-mode sync
   */
  record InstallApp(
      Path bar,
      boolean launch,
      boolean devMode,
      String debugHost,
      boolean debugNative,
      boolean noSync)
      implements DeployCommand {

    /**
     * Creates the command.
     *
     * @throws NullPointerException if {@code bar} is {@code null}
     */
    public InstallApp {
      Objects.requireNonNull(bar, "bar");
    }
  }

  /**
   * Launches an installed application.
   *
   * @param app the application; a name-and-id reference must carry the name
   */
  record LaunchApp(PackageRef app) implements DeployCommand {

    /**
     * Creates the command.
     *
     * @throws IllegalArgumentException if the reference lacks the package name
     */
    public LaunchApp {
      app.requireName();
    }
  }

  /**
   * Terminates a running application. Terminating one that is already stopped succeeds.
   *
   * @param app the application; a name-and-id reference must carry the name
   */
  record TerminateApp(PackageRef app) implements DeployCommand {

    /**
     * Creates the command.
     *
     * @throws IllegalArgumentException if the reference lacks the package name
     */
    public TerminateApp {
      app.requireName();
    }
  }

  /**
   * Asks whether an application is running. The device answers {@code false} for an app that is not
   * installed.
   *
   * @param app the application; a name-and-id reference must carry the name
   */
  record IsAppRunning(PackageRef app) implements DeployCommand {

    /**
     * Creates the command.
     *
     * @throws IllegalArgumentException if the reference lacks the package name
     */
    public IsAppRunning {
      app.requireName();
    }
  }

  /**
   * Uninstalls an application. A running one is terminated first.
   *
   * @param app the application
   */
  record UninstallApp(PackageRef app) implements DeployCommand {

    /**
     * Creates the command.
     *
     * @throws NullPointerException if {@code app} is {@code null}
     */
    public UninstallApp {
      Objects.requireNonNull(app, "app");
    }
  }

  /**
   * Verifies an installed application.
   *
   * @param app the application
   */
  record VerifyApp(PackageRef app) implements DeployCommand {

    /**
     * Creates the command.
     *
     * @throws NullPointerException if {@code app} is {@code null}
     */
    public VerifyApp {
      Objects.requireNonNull(app, "app");
    }
  }

  /**
   * Downloads a file from an application's sandbox.
   *
   * @param app the application; a name-and-id reference must carry the name
   * @param path the file, relative to the sandbox
   * @param saveAs where to store it locally, or {@code null} to receive the content in the result
   *     (text files only; a binary file must be saved to a path)
   */
  record GetFile(PackageRef app, String path, Path saveAs) implements DeployCommand {

    /**
     * Creates the command.
     *
     * @throws IllegalArgumentException if the path is blank or the reference lacks the package name
     */
    public GetFile {
      app.requireName();
      if (path == null || path.isBlank()) {
        throw new IllegalArgumentException("a sandbox path is required");
      }
    }
  }

  /**
   * Uploads a file into an application's sandbox.
   *
   * @param app the application, identified by name and id or by full name; a bar file is not
   *     accepted
   * @param localPath the local file to upload
   * @param path the destination file, relative to the sandbox; not a directory
   */
  record PutFile(PackageRef app, Path localPath, String path) implements DeployCommand {

    /**
     * Creates the command.
     *
     * @throws IllegalArgumentException if the path is blank, the reference lacks the package name,
     *     or it is a bar file
     */
    public PutFile {
      if (app instanceof PackageRef.BarFile) {
        throw new IllegalArgumentException(
            "a bar file cannot identify the target of a put; use the package name and id");
      }
      app.requireName();
      Objects.requireNonNull(localPath, "localPath");
      if (path == null || path.isBlank()) {
        throw new IllegalArgumentException("a sandbox path is required");
      }
    }
  }
}
