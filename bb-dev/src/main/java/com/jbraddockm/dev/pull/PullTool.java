package com.jbraddockm.dev.pull;

import com.jbraddockm.core.json.Json;
import com.jbraddockm.dev.DevTool;
import com.jbraddockm.dev.Output;
import com.jbraddockm.dev.qconn.LaunchService;
import com.jbraddockm.dev.qconn.QconnSocket;
import com.jbraddockm.dev.qconn.TargetFileService;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code pull} namespace: read device files to the host over the qconn {@code file} service —
 * no SSH. It pulls a named file, or lists a directory. Listing runs {@code /bin/ls} through the
 * qconn {@code launcher} service; the file transfer rides {@link TargetFileService#readFile}.
 *
 * <p>Usage: {@code bb-dev pull <host> <device-path> [-o <local>]} or {@code bb-dev pull <host>
 * --list <dir>}, with an optional {@code --port <n>}. The qconn agent must be reachable, i.e. a
 * {@code bb-dev connect} session is holding the device open.
 *
 * <p>Crash cores are pulled with {@code bb-dev deploy cores <app>} instead: a core is written
 * {@code 0600} and the qconn file service cannot read it, so the deploy namespace downloads it
 * binary-safe over {@code appInstaller.cgi}.
 */
public final class PullTool implements DevTool {

  private static final int USAGE_ERROR = 2;
  private static final int PULL_ERROR = 1;

  @Override
  public int run(List<String> arguments, Output output) {
    String host = null;
    String devicePath = null;
    String listDir = null;
    String out = null;
    var port = QconnSocket.PORT;

    for (var index = 0; index < arguments.size(); index++) {
      var argument = arguments.get(index);
      switch (argument) {
        case "--list" -> listDir = value(arguments, ++index);
        case "-o", "--out" -> out = value(arguments, ++index);
        case "--port" -> port = Integer.parseInt(value(arguments, ++index));
        default -> {
          // A positional is the host, then the device path; unknown options are ignored.
          if (!argument.startsWith("-")) {
            if (host == null) {
              host = argument;
            } else if (devicePath == null) {
              devicePath = argument;
            }
          }
        }
      }
    }

    if (host == null || (devicePath == null && listDir == null)) {
      return output.emit(
          USAGE_ERROR,
          "Usage: bb-dev pull <host> <device-path> [-o <local>]\n"
              + "       bb-dev pull <host> --list <dir>",
          Json.object("ok", false, "error", "a host and a path or --list are required"));
    }

    try (var qconn = QconnSocket.connect(host, port)) {
      if (listDir != null) {
        var names = listDirectory(qconn, listDir);
        return output.emit(
            0, String.join("\n", names), Json.object("ok", true, "dir", listDir, "entries", names));
      }
      var local = localTarget(out, devicePath);
      output.note("pulling " + devicePath + " -> " + local);
      var bytes = TargetFileService.on(qconn).readFile(devicePath);
      Files.write(local, bytes);
      return output.emit(
          0,
          "pulled " + bytes.length + " bytes to " + local,
          Json.object(
              "ok", true, "device", devicePath, "local", local.toString(), "bytes", bytes.length));
    } catch (IOException | NumberFormatException failure) {
      return output.emit(
          PULL_ERROR,
          "bb-dev pull: " + failure.getMessage(),
          Json.object("ok", false, "error", String.valueOf(failure.getMessage())));
    }
  }

  /** Lists a device directory by running {@code /bin/ls -1} through the launcher service. */
  private static List<String> listDirectory(QconnSocket qconn, String dir) throws IOException {
    var launcher = LaunchService.on(qconn);
    launcher.disablePseudoTerminal();
    var process = launcher.start("/bin/ls", List.of("-1", dir));
    var names = new ArrayList<String>();
    try (var reader =
        new BufferedReader(new InputStreamReader(process.console(), StandardCharsets.UTF_8))) {
      for (var line = reader.readLine(); line != null; line = reader.readLine()) {
        var name = line.trim();
        if (!name.isEmpty() && !name.startsWith("ls:")) {
          names.add(name);
        }
      }
    }
    return names;
  }

  /**
   * Resolves the local destination: {@code out} as given, a name inside it if it is a directory.
   */
  private static Path localTarget(String out, String devicePath) {
    var name = devicePath.substring(devicePath.lastIndexOf('/') + 1);
    if (out == null) {
      return Path.of(name);
    }
    var target = Path.of(out);
    return Files.isDirectory(target) ? target.resolve(name) : target;
  }

  private static String value(List<String> arguments, int index) {
    return index < arguments.size() ? arguments.get(index) : "";
  }
}
