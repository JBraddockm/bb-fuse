package com.jbraddockm.dev.deploy;

import com.jbraddockm.core.bar.BarArchive;
import com.jbraddockm.core.bar.InvalidBarException;
import com.jbraddockm.core.device.LoginException;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.dev.DevTool;
import com.jbraddockm.dev.Output;
import com.jbraddockm.dev.deploy.sync.SyncResult;
import com.jbraddockm.dev.deploy.sync.SyncService;
import com.jbraddockm.dev.qconn.LaunchService;
import com.jbraddockm.dev.qconn.QconnSocket;
import com.jbraddockm.dev.qconn.TargetFileService;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.net.ssl.KeyManager;

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
        cores <app>                 pull the newest crash .core from the app's logs

      Options:
        -d, --device <host>         device host or IP address (required)
        -p, --password <password>   device password
            --port <n>              HTTPS port (default 443)
            --json                  print one JSON object instead of text

      install options:
            --launch                launch the application after installing it
            --dev                   install in development mode (uses the fast sync when it can)
            --no-sync               force a full upload instead of the dev-mode sync
            --debug-host <host>     attach a debugger running on this host
            --debug-native          hold a native application at start-up for a debugger

      get options:
            --out <file>            save to this file instead of printing the content

      <app> is the full application name shown by 'apps', for example
      com.example.App.gYABgExampleId.
      """;
  private final Engines engines;

  /** Creates a deploy tool that opens a real connection to the device. */
  public DeployTool() {
    this(AppInstallerEngine::forDevice);
  }

  DeployTool(Engines engines) {
    this.engines = engines;
  }

  private static void installMiniBar(AppInstallerEngine engine, byte[] miniBar) throws IOException {
    var temp = Files.createTempFile("bb-dev-mini", ".bar");
    try {
      Files.write(temp, miniBar);
      engine.run(new DeployCommand.InstallApp(temp, false, true, null, false, true), _ -> {});
    } catch (LoginException | DeviceReportException reported) {
      throw new IOException(reported.getMessage(), reported);
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  private static int emitSync(Output output, SyncResult result) {
    var text =
        result.nothingToSync()
            ? "✓ Already in sync — nothing to upload"
            : "✓ Synced — pushed "
                + result.uploadedAssets()
                + (result.uploadedAssets() == 1 ? " asset" : " assets");
    return output.emit(
        0,
        text,
        Json.object(
            "ok",
            true,
            "strategy",
            "sync",
            "nothingToSync",
            result.nothingToSync(),
            "uploadedAssets",
            result.uploadedAssets()));
  }

  private static int saved(Output output, Path target, byte[] bytes) {
    try {
      Files.write(target, bytes);
    } catch (IOException failure) {
      return fail(output, "cannot write " + target + ": " + failure.getMessage());
    }
    return output.emit(
        0,
        "✓ Saved " + bytes.length + " bytes to " + target,
        Json.object("ok", true, "size", bytes.length, "saved", target.toString()));
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

  private static int fail(Output output, String message) {
    return output.emit(COMMAND_FAILED, "✗ " + message, Json.object("ok", false, "error", message));
  }

  private static boolean isHelp(String argument) {
    return argument.equals("help") || argument.equals("-h") || argument.equals("--help");
  }

  /**
   * Pulls the newest crash {@code .core} from the app's sandbox {@code logs/}: lists the directory
   * over qconn (needs a held {@code connect} session), then downloads the core binary-safe over
   * {@code appInstaller.cgi}.
   */
  private int pullCore(
      DeployCommand.PullCore command, DeployTarget target, AppInstallerEngine engine, Output output)
      throws LoginException, DeviceReportException, IOException {
    if (!(command.app() instanceof PackageRef.FullName(String fullName))) {
      throw new IOException("a core pull needs the full application name");
    }
    var logsDir = "/accounts/1000/appdata/" + fullName + "/logs";
    String core;
    try (var qconn = QconnSocket.connect(target.host())) {
      core = newestCore(LaunchService.on(qconn), logsDir);
    } catch (IOException qconnDown) {
      return fail(
          output,
          "cannot list "
              + logsDir
              + " — is a 'bb-dev connect' session holding the device? ("
              + qconnDown.getMessage()
              + ")");
    }
    if (core == null) {
      return fail(output, "no .core dump found in " + logsDir);
    }
    var bytes = engine.download(new DeployCommand.GetFile(command.app(), "logs/" + core, null));
    var destination =
        command.saveAs() == null
            ? Path.of(core)
            : Files.isDirectory(command.saveAs())
                ? command.saveAs().resolve(core)
                : command.saveAs();
    return saved(output, destination, bytes);
  }

  /**
   * Installs with the dev-mode sync when it applies and qconn is reachable. Returns the exit code
   * when sync handled the install, or {@code null} to fall through to the full upload — because the
   * bar cannot sync, or the device's qconn services are not up.
   */
  private Integer trySync(
      DeployCommand.InstallApp install,
      DeployTarget target,
      AppInstallerEngine engine,
      Output output) {
    try (var bar = BarArchive.open(install.bar())) {
      if (!SyncService.isSyncCompatible(bar.manifest())) {
        return null;
      }
      try (var qconn = QconnSocket.connect(target.host())) {
        var result =
            SyncService.synchronize(
                bar, TargetFileService.on(qconn), miniBar -> installMiniBar(engine, miniBar));
        return emitSync(output, result);
      } catch (IOException qconnUnavailable) {
        // qconn services are not up (or the push failed) — a full upload still installs the bar.
        engine.forgetLogin();
        return null;
      }
    } catch (InvalidBarException | IOException unreadable) {
      return null;
    }
  }

  @Override
  public int run(List<String> arguments, Output output) {
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
    var engine = engines.forDevice(target.host(), target.port(), target.password(), null);
    try {
      if (invocation.command() instanceof DeployCommand.InstallApp install
          && install.devMode()
          && !install.noSync()) {
        var synced = trySync(install, target, engine, output);
        if (synced != null) {
          return synced;
        }
      }
      var command = invocation.command();
      if (command instanceof DeployCommand.GetFile get && get.saveAs() != null) {
        // Save straight to disk from the raw bytes — binary-safe, unlike the text render.
        return saved(output, get.saveAs(), engine.download(get));
      }
      if (command instanceof DeployCommand.PullCore core) {
        return pullCore(core, target, engine, output);
      }
      var lines = engine.run(command, _ -> {});
      var rendered = DeployRenderer.render(command, lines);
      return output.emit(0, rendered.text(), rendered.json());
    } catch (DeviceReportException | IOException reported) {
      return fail(output, reported.getMessage());
    } catch (LoginException denied) {
      return fail(output, "login failed: " + denied.getMessage());
    } finally {
      engine.close();
    }
  }

  /** Opens an installer engine for a device; the production factory is {@code forDevice}. */
  interface Engines {
    AppInstallerEngine forDevice(String host, int port, String password, KeyManager[] identity);
  }
}
