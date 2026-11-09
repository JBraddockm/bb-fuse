package com.jbraddockm.dev.debug;

import com.jbraddockm.core.json.Json;
import com.jbraddockm.dev.DevTool;
import com.jbraddockm.dev.Output;
import com.jbraddockm.dev.SandboxFetcher;
import com.jbraddockm.dev.qconn.LaunchService;
import com.jbraddockm.dev.qconn.QconnSocket;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code debug} namespace: inspect a running or crashed application on the device. Its commands
 * sit on the qconn {@code launcher} transport — {@code run} spawns an executable and streams its
 * console; {@code core} finds and pulls the newest crash dump. Pulling a core also needs deploy's
 * {@code appInstaller} transport (the qconn file service cannot read a core), so the composition
 * root injects a {@link SandboxFetcher} for that step rather than this feature importing deploy.
 *
 * <p>Usage:
 *
 * <pre>
 *   bb-dev debug run  &lt;host&gt; &lt;command&gt; [args...] [--cwd &lt;dir&gt;] [--env N=V]... [--pty] [--hold] [--port &lt;n&gt;]
 *   bb-dev debug core &lt;host&gt; &lt;app&gt; [-p &lt;pw&gt;] [--out &lt;dir&gt;] [--port &lt;n&gt;]
 * </pre>
 *
 * The qconn agent must be reachable — a {@code bb-dev connect} session holding the device open.
 */
public final class DebugTool implements DevTool {

  private static final int USAGE_ERROR = 2;
  private static final int DEBUG_ERROR = 1;
  private static final int HTTPS_PORT = 443;

  private static final String USAGE =
      """
      Usage: bb-dev debug <command> [arguments]

        run  <host> <command> [args...]   spawn an executable and stream its console
        core <host> <app>                 pull the newest crash .core from the app's logs
        logs <host>                       stream the device log (slog2info) over the launcher

      run options:   --cwd <dir>  --env NAME=VALUE (repeatable)  --pty  --hold  --port <n>
      core options:  -p <password>  --out <dir>  --port <https-port>
      logs options:  --once (dump, don't follow)  --grep <text>  --port <n>
                     -- <slog2info option>...  pass flags straight to slog2info, e.g.
                        -- -b default,bbcascades -S        (one buffer set, named severities)
                        -- -w -s                           (replay history, then follow)\
      """;

  private final SandboxFetcher.Factory fetchers;

  /**
   * Creates the debug tool.
   *
   * @param fetchers the factory for the sandbox fetcher a core pull downloads through
   */
  public DebugTool(SandboxFetcher.Factory fetchers) {
    this.fetchers = fetchers;
  }

  @Override
  public int run(List<String> arguments, Output output) {
    if (arguments.isEmpty()) {
      return output.emit(
          USAGE_ERROR, USAGE, Json.object("ok", false, "error", "a command is required"));
    }
    var command = arguments.getFirst();
    var rest = arguments.subList(1, arguments.size());
    return switch (command) {
      case "run" -> runProcess(rest, output);
      case "core" -> pullCore(rest, output);
      case "logs" -> streamLogs(rest, output);
      case "help", "-h", "--help" -> output.emit(0, USAGE, Json.object("ok", true));
      default ->
          output.emit(
              USAGE_ERROR,
              "bb-dev debug: unknown command '" + command + "'\n\n" + USAGE,
              Json.object("ok", false, "error", "unknown command: " + command));
    };
  }

  private int runProcess(List<String> arguments, Output output) {
    String host = null;
    String command = null;
    var commandArgs = new ArrayList<String>();
    var environment = new ArrayList<String[]>();
    String workingDirectory = null;
    var pty = false;
    var hold = false;
    var port = QconnSocket.PORT;

    for (var index = 0; index < arguments.size(); index++) {
      var argument = arguments.get(index);
      if (host == null && !argument.startsWith("-")) {
        host = argument;
      } else if (command == null && !argument.startsWith("-")) {
        command = argument;
      } else if (command != null) {
        // Everything after the command is the process's own argument list, verbatim.
        commandArgs.add(argument);
      } else {
        switch (argument) {
          case "--port" -> port = Integer.parseInt(value(arguments, ++index));
          case "--cwd" -> workingDirectory = value(arguments, ++index);
          case "--pty" -> pty = true;
          case "--hold" -> hold = true;
          case "--env" -> {
            var pair = value(arguments, ++index).split("=", 2);
            environment.add(new String[] {pair[0], pair.length > 1 ? pair[1] : ""});
          }
          default -> {
            // Unknown options are ignored.
          }
        }
      }
    }

    if (host == null || command == null) {
      return output.emit(
          USAGE_ERROR,
          "Usage: bb-dev debug run <host> <command> [args...] [--cwd <dir>]"
              + " [--env NAME=VALUE]... [--pty] [--hold] [--port <n>]",
          Json.object("ok", false, "error", "a host and a command are required"));
    }

    try (var qconn = QconnSocket.connect(host, port)) {
      var launcher = LaunchService.on(qconn);
      for (var variable : environment) {
        launcher.setEnvironment(variable[0], variable[1]);
      }
      if (workingDirectory != null) {
        launcher.setWorkingDirectory(workingDirectory);
      }
      if (!pty) {
        launcher.disablePseudoTerminal();
      }

      var flags = hold ? LaunchService.SPAWN_HOLD : 0;
      var process = launcher.start(command, List.copyOf(commandArgs), flags);
      output.note("[pid " + process.pid() + "]");
      if (hold) {
        process.resume();
      }

      var console = new ArrayList<String>();
      try (var reader =
          new BufferedReader(new InputStreamReader(process.console(), StandardCharsets.UTF_8))) {
        for (var line = reader.readLine(); line != null; line = reader.readLine()) {
          output.note(line);
          console.add(line);
        }
      }

      // In the inline (no-pty) console mode the device closes this connection when the process
      // ends, so the status is read on a fresh launcher connection by pid.
      int exitCode;
      try (var status = QconnSocket.connect(host, port)) {
        exitCode = LaunchService.on(status).exitCode(process.pid());
      } catch (IOException failure) {
        output.note("[exit code unavailable: " + failure.getMessage() + "]");
        exitCode = -1;
      }
      return output.emit(
          exitCode == 0 ? 0 : DEBUG_ERROR,
          "[exit " + exitCode + "]",
          Json.object(
              "ok",
              exitCode == 0,
              "pid",
              process.pid(),
              "exitCode",
              exitCode,
              "output",
              String.join("\n", console)));
    } catch (IOException | NumberFormatException failure) {
      return fail(output, "run: " + failure.getMessage());
    }
  }

  private int pullCore(List<String> arguments, Output output) {
    String host = null;
    String app = null;
    var password = "";
    String out = null;
    var port = HTTPS_PORT;

    for (var index = 0; index < arguments.size(); index++) {
      var argument = arguments.get(index);
      switch (argument) {
        case "-p", "--password" -> password = value(arguments, ++index);
        case "--out" -> out = value(arguments, ++index);
        case "--port" -> port = Integer.parseInt(value(arguments, ++index));
        default -> {
          // The two positionals are the host then the app; unknown options are ignored.
          if (!argument.startsWith("-")) {
            if (host == null) {
              host = argument;
            } else if (app == null) {
              app = argument;
            }
          }
        }
      }
    }

    if (host == null || app == null) {
      return output.emit(
          USAGE_ERROR,
          "Usage: bb-dev debug core <host> <app> [-p <pw>] [--out <dir>] [--port <https-port>]",
          Json.object("ok", false, "error", "a host and an application are required"));
    }

    var logsDir = "/accounts/1000/appdata/" + app + "/logs";
    String core;
    try (var qconn = QconnSocket.connect(host)) {
      core = newestCore(LaunchService.on(qconn), logsDir);
    } catch (IOException qconnDown) {
      return fail(
          output,
          "core: cannot list "
              + logsDir
              + " — is a 'bb-dev connect' session holding the device? ("
              + qconnDown.getMessage()
              + ")");
    }
    if (core == null) {
      return fail(output, "core: no .core dump found in " + logsDir);
    }

    byte[] bytes;
    try {
      bytes = fetchers.forDevice(host, port, password).fetch(app, "logs/" + core);
    } catch (IOException | NumberFormatException failure) {
      return fail(output, "core: " + failure.getMessage());
    }

    var target = localTarget(out, core);
    try {
      Files.write(target, bytes);
    } catch (IOException failure) {
      return fail(output, "core: cannot write " + target + ": " + failure.getMessage());
    }
    return output.emit(
        0,
        "✓ Pulled " + core + " (" + bytes.length + " bytes) to " + target,
        Json.object("ok", true, "core", core, "bytes", bytes.length, "saved", target.toString()));
  }

  private int streamLogs(List<String> arguments, Output output) {
    String host = null;
    var port = QconnSocket.PORT;
    var follow = true;
    String grep = null;
    var slogOptions = new ArrayList<String>();
    var passthrough = false;

    for (var index = 0; index < arguments.size(); index++) {
      var argument = arguments.get(index);
      if (passthrough) {
        // Everything after `--` is passed to slog2info verbatim.
        slogOptions.add(argument);
      } else {
        switch (argument) {
          case "--" -> passthrough = true;
          case "--once" -> follow = false;
          case "--grep" -> grep = value(arguments, ++index);
          case "--port" -> port = Integer.parseInt(value(arguments, ++index));
          default -> {
            if (!argument.startsWith("-") && host == null) {
              host = argument;
            }
          }
        }
      }
    }

    if (host == null) {
      return output.emit(
          USAGE_ERROR,
          "Usage: bb-dev debug logs <host> [--once] [--grep <text>] [--port <n>]"
              + " [-- <slog2info option>...]",
          Json.object("ok", false, "error", "a host is required"));
    }

    // slog2info reads the device log. The default follows new entries with the severity shown — the
    // flags Momentics' own Slog2Service runs over this same launcher service; `--once` dumps the
    // current log and exits. Options after `--` replace the defaults entirely (e.g. `-- -b default
    // -S`
    // for one buffer set with named severities, or `-- -w -s` to replay history first); see
    // `debug run <host> /bin/slog2info --help`.
    var slog =
        !slogOptions.isEmpty()
            ? List.copyOf(slogOptions)
            : follow ? List.of("-w", "-W", "-s") : List.of("-s");
    try (var qconn = QconnSocket.connect(host, port)) {
      var launcher = LaunchService.on(qconn);
      launcher.disablePseudoTerminal();
      var process = launcher.start("/bin/slog2info", slog);
      var lines = 0;
      try (var reader =
          new BufferedReader(new InputStreamReader(process.console(), StandardCharsets.UTF_8))) {
        for (var line = reader.readLine(); line != null; line = reader.readLine()) {
          if (grep == null || line.contains(grep)) {
            output.note(line);
            lines++;
          }
        }
      }
      // Reached only in --once mode; -w streams until the connection drops (Ctrl-C).
      return output.emit(0, "[" + lines + " log lines]", Json.object("ok", true, "lines", lines));
    } catch (IOException | NumberFormatException failure) {
      return fail(output, "logs: " + failure.getMessage());
    }
  }

  /** The newest {@code .core} entry in a device directory, by running {@code ls -1 -t} on it. */
  private static String newestCore(LaunchService launcher, String directory) throws IOException {
    launcher.disablePseudoTerminal();
    var process = launcher.start("/bin/ls", List.of("-1", "-t", directory));
    try (var reader =
        new BufferedReader(new InputStreamReader(process.console(), StandardCharsets.UTF_8))) {
      for (var line = reader.readLine(); line != null; line = reader.readLine()) {
        var name = line.trim();
        if (name.endsWith(".core")) {
          return name;
        }
      }
    }
    return null;
  }

  private static Path localTarget(String out, String core) {
    if (out == null) {
      return Path.of(core);
    }
    var target = Path.of(out);
    return Files.isDirectory(target) ? target.resolve(core) : target;
  }

  private static int fail(Output output, String message) {
    return output.emit(
        DEBUG_ERROR, "bb-dev debug " + message, Json.object("ok", false, "error", message));
  }

  private static String value(List<String> arguments, int index) {
    return index < arguments.size() ? arguments.get(index) : "";
  }
}
