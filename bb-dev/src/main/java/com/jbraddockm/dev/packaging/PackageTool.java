package com.jbraddockm.dev.packaging;

import com.jbraddockm.dev.DevTool;
import com.jbraddockm.dev.Output;
import java.util.List;

/**
 * The {@code package} namespace: builds a BlackBerry application archive (.bar), the job of the
 * former bb-packager tool.
 */
public final class PackageTool implements DevTool {

  @Override
  public int run(List<String> arguments, Output output) {
    return output.notImplemented("package");
  }
}
