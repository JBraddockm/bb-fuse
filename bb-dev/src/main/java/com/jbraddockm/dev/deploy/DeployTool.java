package com.jbraddockm.dev.deploy;

import com.jbraddockm.core.daemon.DaemonServer;
import com.jbraddockm.core.device.LoginException;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.dev.DevTool;
import com.jbraddockm.dev.Output;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/**
 * The {@code deploy} namespace: installs and manages applications on the device. It parses the
 * command line ({@link DeployCommandLine}), runs the command over the native {@link
 * AppInstallerEngine}, and renders the result ({@link DeployRenderer}) as text or, with {@code
 * --json}, as one JSON object.
 */
public final class DeployTool implements DevTool {

  private static final int USAGE_ERROR = 2;
  private static final int COMMAND_FAILED = 1;
  private static final String HELP =
      """
      bb-dev deploy — install and manage applications on a BlackBerry 10 device

      Usage: bb-dev deploy <command> [arguments] --device <host> [options]

      Commands:
        test                        check the connection and login
        info                        show the device's properties
        apps                        list the installed applications
        install <bar>               install an application
        launch <app>                launch an installed application
        terminate <app>             stop a running application
        running <app>               report whether an application is running
        verify <app>                verify an installed application
        uninstall <app>             uninstall an application
        get <app> <path>            download a file from the application's sandbox
        put <app> <local> <path>    upload a file into the application's sandbox

      Options:
        -d, --device <host>         device host or IP address (required)
        -p, --password <password>   device password
            --port <n>              HTTPS port (default 443)
            --json                  print one JSON object instead of text

      install options:
            --launch                launch the application after installing it
            --dev                   install in development mode
            --debug-host <host>     attach a debugger running on this host
            --debug-native          hold a native application at start-up for a debugger

      get options:
            --out <file>            save to this file instead of printing the content

      <app> is the full application name shown by 'apps', for example
      com.example.App.gYABgExampleId.
      """;

  private static int save(DeployCommand.GetFile get, List<String> lines, Output output) {
    var download = FileDownload.parse(lines);
    try {
      Files.writeString(get.saveAs(), download.content());
    } catch (IOException failure) {
      return fail(output, "cannot write " + get.saveAs() + ": " + failure.getMessage());
    }
    return output.emit(
        0,
        "✓ Saved " + download.size() + " bytes to " + get.saveAs(),
        Json.object("ok", true, "size", download.size(), "saved", get.saveAs().toString()));
  }

  private static int fail(Output output, String message) {
    return output.emit(COMMAND_FAILED, "✗ " + message, Json.object("ok", false, "error", message));
  }

  private static boolean isHelp(String argument) {
    return argument.equals("help") || argument.equals("-h") || argument.equals("--help");
  }

  /** Serves the newline-JSON daemon protocol on standard input and output, until the input ends. */
  private static int daemon() {
    try {
      DaemonServer.run(new DeployService());
      return 0;
    } catch (IOException failure) {
      System.err.println("bb-dev deploy: the daemon failed: " + failure.getMessage());
      return 1;
    }
  }

  @Override
  public int run(List<String> arguments, Output output) {
    if (!arguments.isEmpty() && arguments.getFirst().equals("-daemon")) {
      return daemon();
    }
    if (arguments.isEmpty()) {
      return output.emit(
          USAGE_ERROR, HELP, Json.object("ok", false, "error", "a command is required"));
    }
    if (isHelp(arguments.getFirst())) {
      return output.emit(0, HELP, Json.object("ok", true));
    }
    DeployCommandLine.Invocation invocation;
    try {
      invocation = DeployCommandLine.parse(arguments);
    } catch (UsageException invalid) {
      return output.emit(
          USAGE_ERROR,
          "bb-dev deploy: " + invalid.getMessage() + "\n\n" + HELP,
          Json.object("ok", false, "error", invalid.getMessage()));
    }
    return execute(invocation, output);
  }

  private int execute(DeployCommandLine.Invocation invocation, Output output) {
    var target = invocation.target();
    var engine =
        AppInstallerEngine.forDevice(target.host(), target.port(), target.password(), null);
    try {
      var lines = engine.run(invocation.command(), _ -> {});
      if (invocation.command() instanceof DeployCommand.GetFile get && get.saveAs() != null) {
        return save(get, lines, output);
      }
      var rendered = DeployRenderer.render(invocation.command(), lines);
      return output.emit(0, rendered.text(), rendered.json());
    } catch (DeviceReportException | IOException reported) {
      return fail(output, reported.getMessage());
    } catch (LoginException denied) {
      return fail(output, "login failed: " + denied.getMessage());
    } finally {
      engine.close();
    }
  }
}
