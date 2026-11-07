package com.jbraddockm.dev.connect;

import com.jbraddockm.dev.DevTool;
import com.jbraddockm.dev.Output;
import java.util.List;

/**
 * The {@code connect} namespace: starts the device's SSH daemon over the qconndoor protocol, the
 * job of the former bb-connect tool.
 */
public final class ConnectTool implements DevTool {

  @Override
  public int run(List<String> arguments, Output output) {
    return output.notImplemented("connect");
  }
}
