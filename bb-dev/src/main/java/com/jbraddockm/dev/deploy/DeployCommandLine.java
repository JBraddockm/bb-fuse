package com.jbraddockm.dev.deploy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a {@code bb-dev deploy} command line into a {@link DeployTarget} and a typed {@link
 * DeployCommand}. The grammar is subcommand-first — {@code <command> [arguments] [options]} — with
 * the device given by {@code --device}/{@code -d} and the password by {@code --password}/{@code
 * -p}. An application argument is its full name as {@code apps} lists it, e.g. {@code
 * com.example.App.id}.
 */
final class DeployCommandLine {

  private DeployCommandLine() {}

  /** A parsed command line: the device to talk to and the command to run. */
  record Invocation(DeployTarget target, DeployCommand command) {}

  /**
   * Parses the command line.
   *
   * @param arguments the words after {@code bb-dev deploy}, without the global {@code --json} flag
   * @return the device target and the command
   * @throws UsageException if the command line cannot be understood
   */
  static Invocation parse(List<String> arguments) throws UsageException {
    String device = null;
    var password = "";
    var port = 443;
    String debugHost = null;
    String out = null;
    var launch = false;
    var dev = false;
    var debugNative = false;
    var noSync = false;
    var positionals = new ArrayList<String>();

    for (var i = 0; i < arguments.size(); i++) {
      var argument = arguments.get(i);
      switch (argument) {
        case "-d", "--device" -> device = value(arguments, ++i, argument);
        case "-p", "--password" -> password = value(arguments, ++i, argument);
        case "--port" -> port = port(value(arguments, ++i, argument));
        case "--debug-host" -> debugHost = value(arguments, ++i, argument);
        case "--out" -> out = value(arguments, ++i, argument);
        case "--launch" -> launch = true;
        case "--dev" -> dev = true;
        case "--debug-native" -> debugNative = true;
        case "--no-sync" -> noSync = true;
        default -> {
          if (argument.startsWith("-")) {
            throw new UsageException("unknown option: " + argument);
          }
          positionals.add(argument);
        }
      }
    }

    if (positionals.isEmpty()) {
      throw new UsageException("a command is required");
    }
    if (device == null) {
      throw new UsageException("a device is required: --device <host>");
    }

    var verb = positionals.getFirst();
    var rest = positionals.subList(1, positionals.size());
    var command = command(verb, rest, launch, dev, debugHost, debugNative, noSync, out);
    return new Invocation(new DeployTarget(device, port, password), command);
  }

  private static DeployCommand command(
      String verb,
      List<String> args,
      boolean launch,
      boolean dev,
      String debugHost,
      boolean debugNative,
      boolean noSync,
      String out)
      throws UsageException {
    return switch (verb) {
      case "test" -> {
        expect(verb, args, 0);
        yield new DeployCommand.TestConnection();
      }
      case "info" -> {
        expect(verb, args, 0);
        yield new DeployCommand.ListDeviceInfo();
      }
      case "apps" -> {
        expect(verb, args, 0);
        yield new DeployCommand.ListInstalledApps();
      }
      case "install" -> {
        expect(verb, args, 1);
        yield new DeployCommand.InstallApp(
            Path.of(args.getFirst()), launch, dev, debugHost, debugNative, noSync);
      }
      case "launch" -> {
        expect(verb, args, 1);
        yield new DeployCommand.LaunchApp(app(args.getFirst()));
      }
      case "terminate" -> {
        expect(verb, args, 1);
        yield new DeployCommand.TerminateApp(app(args.getFirst()));
      }
      case "running" -> {
        expect(verb, args, 1);
        yield new DeployCommand.IsAppRunning(app(args.getFirst()));
      }
      case "verify" -> {
        expect(verb, args, 1);
        yield new DeployCommand.VerifyApp(app(args.getFirst()));
      }
      case "uninstall" -> {
        expect(verb, args, 1);
        yield new DeployCommand.UninstallApp(app(args.getFirst()));
      }
      case "get" -> {
        expect(verb, args, 2);
        yield new DeployCommand.GetFile(
            app(args.get(0)), args.get(1), out == null ? null : Path.of(out));
      }
      case "put" -> {
        expect(verb, args, 3);
        yield new DeployCommand.PutFile(app(args.get(0)), Path.of(args.get(1)), args.get(2));
      }
      case "cores" -> {
        expect(verb, args, 1);
        yield new DeployCommand.PullCore(app(args.getFirst()), out == null ? null : Path.of(out));
      }
      default -> throw new UsageException("unknown command: " + verb);
    };
  }

  private static PackageRef app(String fullName) {
    return new PackageRef.FullName(fullName);
  }

  private static void expect(String verb, List<String> args, int count) throws UsageException {
    if (args.size() != count) {
      throw new UsageException(
          verb
              + " takes "
              + count
              + (count == 1 ? " argument" : " arguments")
              + ", got "
              + args.size());
    }
  }

  private static String value(List<String> arguments, int index, String option)
      throws UsageException {
    if (index >= arguments.size()) {
      throw new UsageException("missing value for " + option);
    }
    return arguments.get(index);
  }

  private static int port(String value) throws UsageException {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException notANumber) {
      throw new UsageException("invalid port: " + value);
    }
  }
}
