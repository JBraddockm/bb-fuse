package com.jbraddockm.dev.packaging;

import com.jbraddockm.core.json.Json;
import com.jbraddockm.dev.DevTool;
import com.jbraddockm.dev.Output;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * The {@code package} namespace: builds a BlackBerry application archive (.bar), the job of the
 * former bb-packager tool.
 *
 * <p>Usage: {@code bb-dev package -package <out.bar> [-configuration <name>] [-devMode]
 * <bar-descriptor.xml>}, taking the options of {@code blackberry-nativepackager}. Only unsigned
 * development bars are built, so {@code -devMode} is the only mode and is assumed; the descriptor's
 * asset paths are read relative to the folder that holds it.
 */
public final class PackageTool implements DevTool {

  private static final int USAGE_ERROR = 2;
  private static final int PACKAGE_ERROR = 1;

  private static String value(List<String> arguments, int index) {
    return index < arguments.size() ? arguments.get(index) : "";
  }

  @Override
  public int run(List<String> arguments, Output output) {
    String barPath = null;
    String configuration = null;
    String descriptorPath = null;
    for (int index = 0; index < arguments.size(); index++) {
      switch (arguments.get(index)) {
        case "-package" -> barPath = value(arguments, ++index);
        case "-configuration" -> configuration = value(arguments, ++index);
        case "-devMode" -> {
          // Development mode is the only mode; a bar is always unsigned.
        }
        default -> {
          var argument = arguments.get(index);
          if (!argument.startsWith("-") && descriptorPath == null) {
            descriptorPath = argument;
          }
          // Unknown options are ignored for compatibility with blackberry-nativepackager.
        }
      }
    }

    if (barPath == null || descriptorPath == null) {
      return output.emit(
          USAGE_ERROR,
          "Usage: bb-dev package -package <out.bar> [-configuration <name>] [-devMode]"
              + " <bar-descriptor.xml>",
          Json.object("ok", false, "error", "-package and a descriptor are required"));
    }

    var descriptorFile = Path.of(descriptorPath);
    var bar = Path.of(barPath);
    var projectRoot = descriptorFile.toAbsolutePath().getParent();
    try {
      var packed = Packager.pack(descriptorFile, projectRoot, configuration, bar);
      return output.emit(
          0,
          "bb-dev package: wrote " + barPath + " (" + packed.size() + " files)",
          Json.object("ok", true, "bar", barPath, "files", packed.size()));
    } catch (InvalidDescriptorException failure) {
      return output.emit(
          PACKAGE_ERROR,
          "bb-dev package: " + descriptorPath + " is not usable: " + failure.getMessage(),
          Json.object("ok", false, "error", failure.getMessage()));
    } catch (IOException failure) {
      return output.emit(
          PACKAGE_ERROR,
          "bb-dev package: " + failure.getMessage(),
          Json.object("ok", false, "error", String.valueOf(failure.getMessage())));
    }
  }
}
