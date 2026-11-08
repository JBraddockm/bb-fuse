package com.jbraddockm.dev.deploy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/**
 * Drives the deploy engine against a real device for the read-only verbs over the production
 * transport ({@link AppInstallerEngine#forDevice}), writing one capture file per verb. Usage:
 * {@code DeviceSweepMain <host> <port> <password> [<packageName> <packageId>]}. When a package name
 * and id are given, the app-targeting read-only verbs (verify, isAppRunning, getFile) are captured
 * too.
 */
final class DeviceSweepMain {

  private DeviceSweepMain() {}

  static void main(String[] args) throws Exception {
    var host = args.length > 0 ? args[0] : "169.254.0.1";
    var port = args.length > 1 ? Integer.parseInt(args[1]) : 443;
    var password = args.length > 2 ? args[2] : "";
    var capture = Path.of("bb-dev", "target", "device-capture");
    Files.createDirectories(capture);

    var engine = AppInstallerEngine.forDevice(host, port, password, null);
    try {
      run(engine, capture, "test", new DeployCommand.TestConnection());
      run(engine, capture, "listDeviceInfo", new DeployCommand.ListDeviceInfo());
      run(engine, capture, "listInstalledApps", new DeployCommand.ListInstalledApps());
      if (args.length > 4) {
        var app = new PackageRef.NameAndId(args[3], args[4]);
        run(engine, capture, "verify", new DeployCommand.VerifyApp(app));
        run(engine, capture, "isAppRunning", new DeployCommand.IsAppRunning(app));
        var filePath = args.length > 5 ? args[5] : "logs/devmode_exitcode.txt";
        run(engine, capture, "getFile", new DeployCommand.GetFile(app, filePath, null));
      }
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
      lines.forEach(System.out::println);
    }
  }
}
