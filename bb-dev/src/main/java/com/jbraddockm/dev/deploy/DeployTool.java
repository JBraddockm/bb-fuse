package com.jbraddockm.dev.deploy;

import com.jbraddockm.dev.DevTool;
import com.jbraddockm.dev.Output;
import java.util.List;

/**
 * The {@code deploy} namespace: installs and manages applications on the device, the job of the
 * former bb-deploy tool.
 */
public final class DeployTool implements DevTool {

  @Override
  public int run(List<String> arguments, Output output) {
    return output.notImplemented("deploy");
  }
}
