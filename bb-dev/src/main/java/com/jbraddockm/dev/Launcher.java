package com.jbraddockm.dev;

import com.jbraddockm.core.daemon.DaemonServer;
import com.jbraddockm.dev.daemon.DevService;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The entry point of bb-dev. The first command-line word chooses a namespace — {@code connect},
 * {@code deploy} or {@code package} — and the rest of the command line is that tool's own, exactly
 * as the separate bb-connect, bb-deploy and bb-packager tools took it.
 *
 * <p>A global {@code --json} flag, anywhere on the command line, switches a tool's result from
 * plain text to one JSON object.
 */
public final class Launcher {

  /** No namespace was given, or the one given is not known. */
  static final int USAGE_ERROR = 2;

  private static final String JSON_FLAG = "--json";

  private Launcher() {}

  /**
   * Runs the tool and exits with its status.
   *
   * @param arguments the command-line arguments
   */
  public static void main(String[] arguments) {
    // The device speaks TLSv1 and presents a certificate that names no host; the deploy namespace
    // talks to it over HTTPS, so turn the host check off and prefer the device protocol before any
    // client is built. (The TLSv1 protocol itself is re-enabled by the java.security file the
    // native
    // build and the test runner pass; see tls.security.)
    System.setProperty("jdk.tls.client.protocols", "TLSv1");
    System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");

    var out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
    var err = new PrintStream(System.err, true, StandardCharsets.UTF_8);
    System.exit(dispatch(arguments, out, err));
  }

  /**
   * Routes the command line to a namespace and returns its exit status, writing the result to
   * {@code out} and usage or routing errors to {@code err}. Separated from {@link #main} so it can
   * be driven in tests without ending the process.
   *
   * @param arguments the command-line arguments
   * @param out where a tool writes its result
   * @param err where usage and routing errors are written
   * @return the exit status
   */
  static int dispatch(String[] arguments, PrintStream out, PrintStream err) {
    var remaining = new ArrayList<String>();
    var json = false;
    for (var argument : arguments) {
      if (argument.equals(JSON_FLAG)) {
        json = true;
      } else {
        remaining.add(argument);
      }
    }

    if (remaining.isEmpty()) {
      err.println(usage());
      return USAGE_ERROR;
    }

    var name = remaining.getFirst();
    if (name.equals("help") || name.equals("-help") || name.equals("--help") || name.equals("-h")) {
      out.println(usage());
      return 0;
    }
    if (name.equals("-daemon")) {
      try {
        DaemonServer.run(new DevService());
        return 0;
      } catch (IOException failure) {
        err.println("bb-dev: the daemon failed: " + failure.getMessage());
        return 1;
      }
    }

    Optional<Namespace> namespace = Namespace.byName(name);
    if (namespace.isEmpty()) {
      err.println("bb-dev: unknown namespace '" + name + "'");
      err.println(usage());
      return USAGE_ERROR;
    }

    var toolArguments = List.copyOf(remaining.subList(1, remaining.size()));
    return namespace.get().run(toolArguments, new Output(out, json));
  }

  private static String usage() {
    return """
    bb-dev - an all-in-one BlackBerry 10 developer tool

    Usage: bb-dev <connect|deploy|package|debug|pull> [--json] [options...]

      connect   start the device's SSH daemon (was bb-connect)
      deploy    install and manage applications on the device (was bb-deploy)
      package   build a BlackBerry application archive (was bb-packager)
      debug     inspect a running or crashed app (run a process, pull a crash core)
      pull      read a device file or directory listing to the host (qconn file)

      -daemon   serve the deploy and package commands as a newline-JSON
                daemon on stdin/stdout (bb-link style)
      --json    emit one JSON object instead of plain text
      help      print this help

    Run 'bb-dev <command> -help' for a command's own options,
    for example 'bb-dev package -help'.\
    """;
  }
}
