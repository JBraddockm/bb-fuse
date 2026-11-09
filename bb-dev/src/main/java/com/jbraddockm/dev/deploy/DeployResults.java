package com.jbraddockm.dev.deploy;

import com.jbraddockm.core.json.Json;
import java.util.List;
import java.util.Map;

/**
 * Maps a command's device reply to the structured {@code data} of a daemon {@code result} event —
 * the same typed fields the {@code --json} CLI prints, without the {@code ok} wrapper (the daemon's
 * {@code done} event carries that).
 */
final class DeployResults {

  private DeployResults() {}

  /**
   * The result data for a command.
   *
   * @param command the command that was run
   * @param lines the device's reply lines
   * @return the data as a JSON-compatible map
   */
  static Map<String, Object> data(DeployCommand command, List<String> lines) {
    return switch (command) {
      case DeployCommand.TestConnection _, DeployCommand.TerminateApp _ -> Map.of();
      case DeployCommand.ListInstalledApps _ ->
          Json.object("apps", InstalledApps.parse(lines).json());
      case DeployCommand.ListDeviceInfo _ ->
          Json.object("properties", DeviceInfo.parse(lines).json());
      case DeployCommand.VerifyApp _, DeployCommand.InstallApp _, DeployCommand.UninstallApp _ ->
          PackageInfo.parse(lines).json();
      case DeployCommand.IsAppRunning _ -> AppRunState.parse(lines).json();
      case DeployCommand.LaunchApp _ -> LaunchResult.parse(lines).json();
      case DeployCommand.PutFile _ -> FileUpload.parse(lines).json();
      case DeployCommand.GetFile _ -> FileDownload.parse(lines).json();
    };
  }
}
