package com.jbraddockm.dev.qconn;

import java.io.IOException;
import java.util.List;

/**
 * The qconn {@code launcher} service: spawning and controlling an arbitrary executable on the
 * device, over a {@link QconnSocket} switched to that service. This is the QNX process launcher —
 * lower-level than deploy's app launch ({@code appInstaller.cgi}, which starts an installed,
 * packaged app): it runs any binary and streams its console, which is what live debugging ({@code
 * pdebug}) and non-app tooling ({@code slog2info}) build on.
 *
 * <p>Commands are line-oriented text ({@code <cmd>\r\n}, the same framing as the control channel);
 * each reply is {@code ok[ <value>]} or {@code error[ <message>]}. The order is <em>configure, then
 * launch</em>: set the environment ({@code setenv}), the working directory ({@code chdir}) and the
 * terminal mode ({@code set nopty}) first, then {@code start} the process. {@code start} replies
 * {@code ok <pid>}; after it, the same connection becomes the process's combined stdout/stderr and
 * stdin (see {@link LaunchedProcess}), so one launcher connection runs one process.
 *
 * <p>Without a pseudo-terminal the device streams the console inline; {@link
 * #disablePseudoTerminal()} selects that mode, and is required unless a pty device (such as {@code
 * devc-pty}) is running on the target. The protocol was recovered from the QNX Momentics {@code
 * com.qnx.tools.ide.utils} target plugin.
 */
public final class LaunchService {

  /** The qconn service name for the process launcher. */
  static final String SERVICE = "launcher";

  /** Spawn flag: hold the process at its entry point so a debugger can attach before it runs. */
  public static final int SPAWN_HOLD = 0x8000;

  private final QconnSocket qconn;

  LaunchService(QconnSocket qconn) {
    this.qconn = qconn;
  }

  /**
   * Switches the connection to the launcher service.
   *
   * @param qconn a connected qconn agent
   * @return the launcher service
   * @throws IOException if the service cannot be selected
   */
  public static LaunchService on(QconnSocket qconn) throws IOException {
    qconn.setService(SERVICE);
    return new LaunchService(qconn);
  }

  /**
   * Sets one environment variable for the process. Must be called before {@link #start}.
   *
   * @param name the variable name
   * @param value the value, or empty to set it to the empty string
   * @throws IOException if the device rejects the command
   */
  public void setEnvironment(String name, String value) throws IOException {
    if (name.isBlank()) {
      throw new IllegalArgumentException("environment variable name must not be blank");
    }
    reply(
        qconn, value.isEmpty() ? "setenv " + name + "=" : "setenv " + name + "=\"" + value + "\"");
  }

  /**
   * Sets the process's working directory. Must be called before {@link #start}.
   *
   * @param directory the device directory path
   * @throws IOException if the device rejects the command
   */
  public void setWorkingDirectory(String directory) throws IOException {
    if (directory.isBlank()) {
      throw new IllegalArgumentException("working directory must not be blank");
    }
    reply(qconn, "chdir " + directory);
  }

  /**
   * Runs the process without a pseudo-terminal, streaming its console inline on this connection.
   * Must be called before {@link #start}. Without it — and without a pty device on the target — the
   * launcher reports "no pty device".
   *
   * @throws IOException if the device rejects the command
   */
  public void disablePseudoTerminal() throws IOException {
    reply(qconn, "set nopty");
  }

  /**
   * Spawns the executable and returns the running process. After this the connection carries the
   * process's console, so the returned handle owns it until the process ends.
   *
   * @param command the executable path on the device
   * @param arguments the arguments to pass, each sent verbatim (quoted on the wire)
   * @return the running process
   * @throws IOException if the device cannot spawn the process
   */
  public LaunchedProcess start(String command, List<String> arguments) throws IOException {
    return start(command, arguments, 0);
  }

  /**
   * Spawns the executable with spawn flags (for example {@link #SPAWN_HOLD}) and returns the
   * running process.
   *
   * @param command the executable path on the device
   * @param arguments the arguments to pass, each sent verbatim (quoted on the wire)
   * @param flags the spawn flags, or {@code 0} for none
   * @return the running process, held at entry when {@link #SPAWN_HOLD} is set
   * @throws IOException if the device cannot spawn the process
   */
  public LaunchedProcess start(String command, List<String> arguments, int flags)
      throws IOException {
    if (command.isBlank()) {
      throw new IllegalArgumentException("command must not be blank");
    }
    var head = flags == 0 ? "start " : "start/flags " + Integer.toHexString(flags) + " ";
    var pid = Integer.parseInt(reply(qconn, head + command + encodeArgv(command, arguments)));
    return new LaunchedProcess(qconn, pid, (flags & SPAWN_HOLD) != 0);
  }

  /**
   * Encodes the process's argument vector as the space-separated, double-quoted list the launcher
   * expects. The launcher takes the whole vector verbatim, so {@code command} is passed as {@code
   * argv[0]} ahead of the caller's arguments — the convention {@link ProcessBuilder} uses.
   */
  private static String encodeArgv(String command, List<String> arguments) {
    var line = new StringBuilder();
    line.append(" \"").append(quote(command)).append('"');
    for (var argument : arguments) {
      line.append(" \"").append(quote(argument)).append('"');
    }
    return line.toString();
  }

  private static String quote(String value) {
    return value.replace("\n", "\\n").replace("\"", "\\\"");
  }

  /**
   * Queries the exit status of a process started earlier on the launcher service. In the inline
   * (no-pty) console mode the process's own connection closes when it ends, so the status is read
   * on a fresh launcher connection by pid — the device retains it for a short time after exit.
   *
   * @param pid the process id returned by {@link #start}
   * @return the exit code
   * @throws IOException if the device reports an error or an unexpected reply
   */
  public int exitCode(int pid) throws IOException {
    return exitCode(qconn, pid);
  }

  /**
   * Reads a {@code getexitcode} reply ({@code e:<status>}) and decodes it; shared with {@link
   * LaunchedProcess}. The device returns the raw POSIX wait-status word — the exit code in the high
   * byte for a normal exit, or a terminating signal in the low seven bits — so this returns the
   * exit code for a normal exit and {@code 128 + signal} (the shell convention) for a signalled
   * one.
   */
  static int exitCode(QconnSocket qconn, int pid) throws IOException {
    qconn.sendCommand("getexitcode " + pid);
    var response = qconn.receiveResponse();
    if (response.regionMatches(true, 0, "error", 0, 5)) {
      throw new IOException("qconn launcher: " + response);
    }
    if (response.startsWith("e:")) {
      var status = Integer.parseInt(response.substring(2).trim());
      var signal = status & 0x7f;
      return signal == 0 ? (status >> 8) & 0xff : 128 + signal;
    }
    throw new IOException("qconn launcher: unexpected getexitcode reply: " + response);
  }

  /**
   * Sends a launcher command and returns the value of an {@code ok} reply, raising the device's
   * message for an {@code error} reply. Shared with {@link LaunchedProcess}.
   */
  static String reply(QconnSocket qconn, String command) throws IOException {
    qconn.sendCommand(command);
    var response = qconn.receiveResponse();
    if (response.length() >= 2 && response.regionMatches(true, 0, "ok", 0, 2)) {
      return response.substring(2).trim();
    }
    if (response.regionMatches(true, 0, "error", 0, 5)) {
      var message = response.substring(5).replaceFirst("^[:\\s]+", "").trim();
      if (message.contains("Inappropriate I/O control operation")) {
        message =
            "no pty device on the target — run one (e.g. devc-pty) or call disablePseudoTerminal()";
      }
      throw new IOException("qconn launcher: " + (message.isEmpty() ? response : message));
    }
    throw new IOException("qconn launcher: unexpected reply: " + response);
  }
}
