package com.jbraddockm.dev;

import com.jbraddockm.dev.connect.ConnectTool;
import com.jbraddockm.dev.deploy.DeployTool;
import com.jbraddockm.dev.packaging.PackageTool;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** The namespaces bb-dev dispatches to. The wire name is the first command-line word. */
enum Namespace {
  CONNECT("connect", new ConnectTool()),
  DEPLOY("deploy", new DeployTool()),
  PACKAGE("package", new PackageTool(new DeployBarDeployer()));

  private final String wireName;
  private final DevTool tool;

  Namespace(String wireName, DevTool tool) {
    this.wireName = wireName;
    this.tool = tool;
  }

  static Optional<Namespace> byName(String name) {
    return Arrays.stream(values()).filter(namespace -> namespace.wireName.equals(name)).findFirst();
  }

  int run(List<String> arguments, Output output) {
    return tool.run(arguments, output);
  }
}
