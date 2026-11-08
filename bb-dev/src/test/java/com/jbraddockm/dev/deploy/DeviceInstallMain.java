package com.jbraddockm.dev.deploy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/**
 * Drives uninstall then install of a bar against a real device (both change the device) and writes
 * one capture file per verb. Uninstall first, then install, so the app is left installed. Usage:
 * {@code DeviceInstallMain <host> <port> <password> <packageName> <packageId> <barPath>}.
 */
final class DeviceInstallMain {

  private DeviceInstallMain() {}

  static void main(String[] args) throws Exception {
    var host = args[0];
    var port = Integer.parseInt(args[1]);
    var password = args[2];
    var app = new PackageRef.NameAndId(args[3], args[4]);
    var bar = Path.of(args[5]);
    var capture = Path.of("bb-dev", "target", "device-capture");
    Files.createDirectories(capture);

    var engine = AppInstallerEngine.forDevice(host, port, password, null);
    try {
      run(engine, capture, "uninstall", new DeployCommand.UninstallApp(app));
      run(engine, capture, "install", new DeployCommand.InstallApp(bar, false, true, null, false, false));
    } finally {
      engine.close();
    }
  }

  private static void run(
      AppInstallerEngine engine, Path capture, String name, DeployCommand command)
      throws Exception {
    var lines = new ArrayList<String>();
    try {
      engine.run(command, lines::add);
    } catch (DeviceReportException reported) {
      lines.add("[reported-failure] " + reported.getMessage());
    } catch (Exception failure) {
      lines.add("[exception] " + failure.getClass().getName() + ": " + failure.getMessage());
    } finally {
      Files.writeString(capture.resolve(name + ".txt"), String.join("\n", lines));
      IO.println("---- " + name + " (" + lines.size() + " lines) ----");
      lines.forEach(IO::println);
    }
  }
}
