package com.jbraddockm.dev;

import com.jbraddockm.core.bar.BarInspector;
import com.jbraddockm.core.bar.InvalidBarException;
import com.jbraddockm.core.device.LoginException;
import com.jbraddockm.dev.deploy.AppInstallerEngine;
import com.jbraddockm.dev.deploy.DeployCommand;
import com.jbraddockm.dev.deploy.DeviceReportException;
import com.jbraddockm.dev.deploy.PackageRef;
import com.jbraddockm.dev.packaging.BarDeployer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The {@link BarDeployer} the launcher wires into the {@code package} namespace: it drives bb-dev's
 * own {@code deploy} engine in process. As part of the top-level composition it may depend on both
 * the packaging and deploy features; the features themselves do not depend on each other.
 */
final class DeployBarDeployer implements BarDeployer {

  private static final Pattern PID = Pattern.compile("result::(\\d+)");

  @Override
  public String install(
      Path bar, String host, int port, String password, boolean devMode, boolean launch)
      throws IOException {
    var engine = AppInstallerEngine.forDevice(host, port, password, null);
    try {
      engine.run(new DeployCommand.InstallApp(bar, launch, devMode, null, false, false), _ -> {});
      return "Installed " + appName(bar) + (launch ? " and launched it" : "");
    } catch (LoginException | DeviceReportException reported) {
      throw new IOException(reported.getMessage(), reported);
    } finally {
      engine.close();
    }
  }

  @Override
  public String launch(Path bar, String host, int port, String password) throws IOException {
    var engine = AppInstallerEngine.forDevice(host, port, password, null);
    try {
      var lines = engine.run(new DeployCommand.LaunchApp(new PackageRef.BarFile(bar)), _ -> {});
      return "Launched " + appName(bar) + pid(lines);
    } catch (LoginException | DeviceReportException reported) {
      throw new IOException(reported.getMessage(), reported);
    } finally {
      engine.close();
    }
  }

  private static String appName(Path bar) throws IOException {
    try {
      var info = BarInspector.inspect(bar);
      return info.packageName() + "." + info.packageId();
    } catch (InvalidBarException invalid) {
      throw new IOException("the bar manifest could not be read: " + invalid.getMessage(), invalid);
    }
  }

  static String pid(List<String> lines) {
    for (var line : lines) {
      var matcher = PID.matcher(line);
      if (matcher.find()) {
        return " (pid " + matcher.group(1) + ")";
      }
    }
    return "";
  }
}
