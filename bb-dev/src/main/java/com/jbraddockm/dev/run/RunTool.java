package com.jbraddockm.dev.run;

import com.jbraddockm.core.json.Json;
import com.jbraddockm.dev.DevTool;
import com.jbraddockm.dev.Output;
import com.jbraddockm.dev.qconn.LaunchService;
import com.jbraddockm.dev.qconn.QconnSocket;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code run} namespace: spawns an arbitrary executable on the device through the qconn {@code
 * launcher} service and streams its console. Unlike {@code deploy ... --launch}, which starts an
 * installed, packaged app, this runs any binary and captures its stdout/stderr — the primitive live
 * debugging and device tooling need.
 *
 * <p>Usage: {@code bb-dev run <host> <command> [args...] [--port <n>] [--cwd <dir>] [--env
 * NAME=VALUE]... [--pty] [--hold]}. The qconn agent must be reachable, which means a {@code bb-dev
 * connect} session is holding the device open.
 */
public final class RunTool implements DevTool {

  private static final int USAGE_ERROR = 2;
  private static final int RUN_ERROR = 1;

  @Override
  public int run(List<String> arguments, Output output) {
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
          "Usage: bb-dev run <host> <command> [args...] [--port <n>] [--cwd <dir>]"
              + " [--env NAME=VALUE]... [--pty] [--hold]",
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
          exitCode == 0 ? 0 : RUN_ERROR,
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
      return output.emit(
          RUN_ERROR,
          "bb-dev run: " + failure.getMessage(),
          Json.object("ok", false, "error", String.valueOf(failure.getMessage())));
    }
  }

  private static String value(List<String> arguments, int index) {
    return index < arguments.size() ? arguments.get(index) : "";
  }
}
