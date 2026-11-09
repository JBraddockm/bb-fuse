package com.jbraddockm.dev.qconn;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A process spawned by {@link LaunchService}. After {@code start} the launcher connection becomes
 * the process's console, so this handle owns that connection: {@link #console()} is the combined
 * stdout/stderr and {@link #stdin()} the standard input, both inline on the socket. When the
 * process ends the device closes the console stream; {@link #exitCode()} then returns its status.
 *
 * <p>A process spawned with {@link LaunchService#SPAWN_HOLD} is stopped at its entry point until
 * {@link #resume()} lets it run — the hook a debugger uses to attach first.
 */
public final class LaunchedProcess {

  private final QconnSocket qconn;
  private final int pid;
  private final boolean held;

  LaunchedProcess(QconnSocket qconn, int pid, boolean held) {
    this.qconn = qconn;
    this.pid = pid;
    this.held = held;
  }

  /**
   * The process id the device assigned.
   *
   * @return the pid
   */
  public int pid() {
    return pid;
  }

  /**
   * Whether the process was spawned held at its entry point (see {@link LaunchService#SPAWN_HOLD}),
   * awaiting {@link #resume()}.
   *
   * @return {@code true} if the process is held
   */
  public boolean isHeld() {
    return held;
  }

  /**
   * The process's combined standard output and error, streamed inline on the connection until the
   * process ends. Read it to completion before calling {@link #exitCode()}.
   *
   * @return the console input stream
   */
  public InputStream console() {
    return qconn.input();
  }

  /**
   * The process's standard input.
   *
   * @return the console output stream
   */
  public OutputStream stdin() {
    return qconn.output();
  }

  /**
   * Resumes a process spawned held at its entry point.
   *
   * @throws IOException if the device rejects the command, or the process is not held
   */
  public void resume() throws IOException {
    LaunchService.reply(qconn, "continue");
  }

  /**
   * The process's exit status, read on this connection after its console stream has ended. In the
   * inline (no-pty) console mode the device closes this connection when the process exits, so this
   * call then fails; query the status instead on a fresh connection with {@link
   * LaunchService#exitCode(int)} and this {@link #pid()}.
   *
   * @return the exit code
   * @throws IOException if the device reports an error, an unexpected reply, or a closed connection
   */
  public int exitCode() throws IOException {
    return LaunchService.exitCode(qconn, pid);
  }
}
