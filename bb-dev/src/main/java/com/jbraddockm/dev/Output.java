package com.jbraddockm.dev;

import com.jbraddockm.core.json.Json;
import java.io.PrintStream;

/**
 * Where a tool writes its one result — plain text by default, or a single JSON object when the
 * caller passed {@code --json}.
 */
public final class Output {

  /** The namespace is known but its tool is not built yet. */
  public static final int NOT_IMPLEMENTED = 69;

  private final PrintStream stream;
  private final boolean json;

  Output(PrintStream stream, boolean json) {
    this.stream = stream;
    this.json = json;
  }

  /**
   * Tells whether JSON output was requested.
   *
   * @return {@code true} if the result should be a JSON object
   */
  public boolean isJson() {
    return json;
  }

  /**
   * Reports that a namespace's tool is not built yet.
   *
   * @param namespace the namespace that was asked for
   * @return {@link #NOT_IMPLEMENTED}
   */
  public int notImplemented(String namespace) {
    if (json) {
      stream.println(
          Json.write(Json.object("ok", false, "namespace", namespace, "error", "not implemented")));
    } else {
      stream.println("bb-dev " + namespace + ": not implemented yet");
    }
    return NOT_IMPLEMENTED;
  }
}
