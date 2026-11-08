package com.jbraddockm.dev.deploy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/**
 * Drives the mutating verbs against a real device (putFile, getFile of what was put, launch,
 * isAppRunning, terminate) and writes one capture file per verb. These change the device, so it is
 * run deliberately, never as part of the read-only sweep. Usage: {@code DeviceMutateMain <host>
 * <port> <password> <packageName> <packageId>}.
 */
final class DeviceMutateMain {

  private DeviceMutateMain() {}

  static void main(String[] args) throws Exception {
    var host = args[0];
    var port = Integer.parseInt(args[1]);
    var password = args[2];
    var app = new PackageRef.NameAndId(args[3], args[4]);
    var capture = Path.of("bb-dev", "target", "device-capture");
    Files.createDirectories(capture);

    var local = Files.writeString(Files.createTempFile("bbdev-mutate", ".txt"), "bbdev mutate test\n");
    var sandboxPath = "data/bbdev-mutate-test.txt";

    var engine = AppInstallerEngine.forDevice(host, port, password, null);
    try {
      run(engine, capture, "putFile", new DeployCommand.PutFile(app, local, sandboxPath));
      run(engine, capture, "getFile-success", new DeployCommand.GetFile(app, sandboxPath, null));
      run(engine, capture, "launch", new DeployCommand.LaunchApp(app));
      Thread.sleep(2000);
      run(engine, capture, "isAppRunning-true", new DeployCommand.IsAppRunning(app));
      run(engine, capture, "terminate", new DeployCommand.TerminateApp(app));
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
