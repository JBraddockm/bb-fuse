package com.jbraddockm.dev;

import java.util.List;

/**
 * One bb-dev namespace tool. Each reads its own command line and writes its result to {@link
 * Output}.
 */
public interface DevTool {

  /**
   * Runs the tool.
   *
   * @param arguments the tool's command line, without the namespace word or the global {@code
   *     --json} flag
   * @param output where to write the result, as text or JSON
   * @return the exit status
   */
  int run(List<String> arguments, Output output);
}
