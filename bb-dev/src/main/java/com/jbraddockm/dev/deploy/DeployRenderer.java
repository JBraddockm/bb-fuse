package com.jbraddockm.dev.deploy;

import com.jbraddockm.core.json.Json;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Renders a deploy command's device reply as readable text and as a JSON object. The text form is a
 * short, human-friendly summary; the JSON form (for {@code --json} and the daemon) is {@code
 * {"ok":true, …}} with the command's typed fields.
 */
final class DeployRenderer {

  private static final String TICK = "✓ ";

  private DeployRenderer() {}

  /** A rendered result: the text to print, and the object to serialise for {@code --json}. */
  record Rendered(String text, Object json) {}

  /**
   * Renders the reply of a command.
   *
   * @param command the command that was run
   * @param lines the device's reply lines
   * @return the text and JSON renderings
   */
  static Rendered render(DeployCommand command, List<String> lines) {
    return switch (command) {
      case DeployCommand.TestConnection _ -> new Rendered(TICK + "Connected.", ok());
      case DeployCommand.ListDeviceInfo _ -> deviceInfo(DeviceInfo.parse(lines));
      case DeployCommand.ListInstalledApps _ -> installedApps(InstalledApps.parse(lines));
      case DeployCommand.InstallApp _ -> packageAction("Installed", PackageInfo.parse(lines));
      case DeployCommand.VerifyApp _ -> packageAction("Verified", PackageInfo.parse(lines));
      case DeployCommand.UninstallApp _ -> packageAction("Uninstalled", PackageInfo.parse(lines));
      case DeployCommand.LaunchApp _ -> launched(LaunchResult.parse(lines));
      case DeployCommand.TerminateApp _ -> new Rendered(TICK + "Terminated.", ok());
      case DeployCommand.IsAppRunning _ -> running(AppRunState.parse(lines));
      case DeployCommand.GetFile _ -> downloaded(FileDownload.parse(lines));
      case DeployCommand.PutFile _ -> uploaded(FileUpload.parse(lines));
    };
  }

  private static Rendered packageAction(String verb, PackageInfo info) {
    var version = info.version() == null || info.version().isBlank() ? "" : " " + info.version();
    return new Rendered(
        TICK + verb + " " + info.name() + version,
        Json.object("ok", true, "name", info.name(), "id", info.id(), "version", info.version()));
  }

  private static Rendered launched(LaunchResult launch) {
    return new Rendered(
        TICK + "Launched (pid " + launch.pid() + ")", Json.object("ok", true, "pid", launch.pid()));
  }

  private static Rendered running(AppRunState state) {
    return new Rendered(
        state.running() ? "Running." : "Not running.",
        Json.object("ok", true, "running", state.running()));
  }

  private static Rendered uploaded(FileUpload upload) {
    return new Rendered(
        TICK + "Uploaded " + upload.size() + " bytes",
        Json.object("ok", true, "size", upload.size()));
  }

  private static Rendered downloaded(FileDownload download) {
    return new Rendered(
        download.content(),
        Json.object("ok", true, "size", download.size(), "content", download.content()));
  }

  private static Rendered deviceInfo(DeviceInfo info) {
    var width = info.properties().keySet().stream().mapToInt(String::length).max().orElse(0);
    var text = new StringBuilder();
    info.properties()
        .forEach(
            (key, value) -> text.append(pad(key, width)).append("  ").append(value).append('\n'));
    return new Rendered(
        text.toString().stripTrailing(), Json.object("ok", true, "properties", info.json()));
  }

  private static Rendered installedApps(InstalledApps apps) {
    var nameWidth = Math.min(60, width(apps, app -> app.name().length()));
    var versionWidth = width(apps, app -> app.version().length());
    var text = new StringBuilder();
    for (var app : apps.apps()) {
      text.append(pad(app.name(), nameWidth))
          .append("  ")
          .append(pad(app.version(), versionWidth))
          .append("  ")
          .append(size(app.size()))
          .append('\n');
    }
    text.append(apps.apps().size()).append(" applications");
    return new Rendered(text.toString(), Json.object("ok", true, "apps", apps.json()));
  }

  private static int width(InstalledApps apps, ToIntFunction<InstalledApps.App> of) {
    return apps.apps().stream().mapToInt(of).max().orElse(0);
  }

  private static Object ok() {
    return Json.object("ok", true);
  }

  private static String pad(String text, int width) {
    return text.length() >= width ? text : text + " ".repeat(width - text.length());
  }

  private static String size(long bytes) {
    if (bytes >= 1_000_000) {
      return String.format("%.1f MB", bytes / 1_000_000.0);
    }
    if (bytes >= 1_000) {
      return bytes / 1_000 + " KB";
    }
    return bytes + " B";
  }
}
