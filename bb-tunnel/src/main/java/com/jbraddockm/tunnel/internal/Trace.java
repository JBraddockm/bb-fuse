package com.jbraddockm.tunnel.internal;

/**
 * Optional diagnostic trace for the tunnel bring-up and data plane. It is off unless enabled with
 * the {@code bb.tunnel.trace} system property or the {@code BB_TUNNEL_TRACE} environment variable,
 * so normal runs and tests stay quiet; when enabled it writes to standard error.
 */
public final class Trace {

  private static final boolean ENABLED =
      Boolean.getBoolean("bb.tunnel.trace") || "1".equals(System.getenv("BB_TUNNEL_TRACE"));

  private Trace() {}

  /**
   * Writes a diagnostic line when tracing is enabled; otherwise does nothing.
   *
   * @param message the line to write
   */
  public static void line(String message) {
    if (ENABLED) {
      System.err.println(message);
    }
  }
}
