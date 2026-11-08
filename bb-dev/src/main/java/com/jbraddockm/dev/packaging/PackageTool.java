package com.jbraddockm.dev.packaging;

import com.jbraddockm.core.bar.BarArchive;
import com.jbraddockm.core.bar.BarAsset;
import com.jbraddockm.core.bar.InvalidBarException;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.dev.DevTool;
import com.jbraddockm.dev.Output;
import com.jbraddockm.dev.packaging.descriptor.InvalidDescriptorException;
import com.jbraddockm.dev.packaging.internal.Packager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code package} namespace: builds a BlackBerry application archive (.bar), the job of the
 * former bb-packager tool, with the command-line options of {@code blackberry-nativepackager} that
 * still apply.
 *
 * <p>Only unsigned bars are built, so the signing and debug-token options do not apply. {@code
 * -devMode} is a distinct opt-in (off by default) that marks the bar as a development build; it is
 * not required for an unsigned bar. The descriptor's asset paths are read relative to the folder
 * that holds it. Run {@code bb-dev package -help} for the full list.
 */
public final class PackageTool implements DevTool {

  private static final int USAGE_ERROR = 2;
  private static final int PACKAGE_ERROR = 1;
  private static final int DEVICE_PORT = 443;

  /** The tool version reported by {@code -version}. */
  static final String VERSION = "1.0";

  private final BarDeployer deployer;

  /**
   * Creates the tool.
   *
   * @param deployer installs or launches the built bar on a device, for {@code -installApp} /
   *     {@code -launchApp}
   */
  public PackageTool(BarDeployer deployer) {
    this.deployer = deployer;
  }

  @Override
  public int run(List<String> rawArguments, Output output) {
    List<String> arguments;
    try {
      arguments = expandOptionFiles(rawArguments);
    } catch (IOException failure) {
      return output.emit(
          USAGE_ERROR,
          "bb-dev package: cannot read options file: " + failure.getMessage(),
          Json.object("ok", false, "error", String.valueOf(failure.getMessage())));
    }

    boolean help = false;
    boolean version = false;
    String listBar = null;
    String listManifestBar = null;
    String generateXmlFile = null;
    String barPath = null;
    String configuration = null;
    String buildId = null;
    String buildIdFile = null;
    String appVersion = null;
    String descriptorPath = null;
    boolean devMode = false;
    boolean installApp = false;
    boolean launchApp = false;
    String device = null;
    var password = "";
    Path extraRoot = null;
    var extras = new ArrayList<Packager.ExtraFile>();

    for (int index = 0; index < arguments.size(); index++) {
      switch (arguments.get(index)) {
        case "-help", "-help-advanced", "--help", "-h" -> help = true;
        case "-version", "--version" -> version = true;
        case "-list" -> listBar = value(arguments, ++index);
        case "-listManifest" -> listManifestBar = value(arguments, ++index);
        case "-generateXml" -> generateXmlFile = value(arguments, ++index);
        case "-package" -> barPath = value(arguments, ++index);
        case "-configuration" -> configuration = value(arguments, ++index);
        case "-buildId" -> buildId = value(arguments, ++index);
        case "-buildIdFile" -> buildIdFile = value(arguments, ++index);
        case "-appVersion" -> appVersion = value(arguments, ++index);
        case "-C" -> extraRoot = Path.of(value(arguments, ++index));
        case "-e" -> {
          var file = value(arguments, ++index);
          var path = value(arguments, ++index);
          extras.add(new Packager.ExtraFile(path, resolve(extraRoot, file)));
        }
        case "-devMode" -> devMode = true;
        case "-installApp" -> installApp = true;
        case "-launchApp" -> launchApp = true;
        case "-device" -> device = value(arguments, ++index);
        case "-password" -> password = value(arguments, ++index);
        default -> {
          var argument = arguments.get(index);
          // Unknown options are ignored, as blackberry-nativepackager does; a non-option is the
          // descriptor (the first) or an extra file to include (the rest).
          if (!argument.startsWith("-")) {
            if (descriptorPath == null) {
              descriptorPath = argument;
            } else {
              extras.add(new Packager.ExtraFile(argument, resolve(extraRoot, argument)));
            }
          }
        }
      }
    }

    if (help) {
      return output.emit(0, help(), Json.object("ok", true, "help", help()));
    }
    if (version) {
      return output.emit(
          0, "bb-dev package " + VERSION, Json.object("ok", true, "version", VERSION));
    }
    if (generateXmlFile != null) {
      return generateXml(generateXmlFile, output);
    }
    if (listBar != null) {
      return list(listBar, output);
    }
    if (listManifestBar != null) {
      return listManifest(listManifestBar, output);
    }

    if (barPath == null || descriptorPath == null) {
      return output.emit(
          USAGE_ERROR,
          "Usage: bb-dev package [<commands>] [<options>] -package <out.bar> [<app-desc> <files>]"
              + System.lineSeparator()
              + "Run 'bb-dev package -help' for the full list of options.",
          Json.object("ok", false, "error", "-package and a descriptor are required"));
    }
    if ((installApp || launchApp) && device == null) {
      return output.emit(
          USAGE_ERROR,
          "bb-dev package: -installApp and -launchApp require -device <host>",
          Json.object("ok", false, "error", "-installApp and -launchApp require -device <host>"));
    }

    return pack(
        descriptorPath,
        barPath,
        configuration,
        buildId,
        buildIdFile,
        appVersion,
        extras,
        devMode,
        new Deploy(installApp, launchApp, device, password),
        output);
  }

  /** The on-device step after a build: install and/or launch, or neither. */
  private record Deploy(boolean installApp, boolean launchApp, String device, String password) {
    boolean requested() {
      return installApp || launchApp;
    }
  }

  private int pack(
      String descriptorPath,
      String barPath,
      String configuration,
      String buildId,
      String buildIdFile,
      String appVersion,
      List<Packager.ExtraFile> extras,
      boolean devMode,
      Deploy deploy,
      Output output) {
    var descriptorFile = Path.of(descriptorPath);
    var bar = Path.of(barPath);
    var projectRoot = descriptorFile.toAbsolutePath().getParent();
    try {
      // -buildIdFile supplies the build id and is bumped for the next build, unless -buildId wins.
      var effectiveBuildId = buildId;
      String current = null;
      if (effectiveBuildId == null && buildIdFile != null) {
        current = readBuildId(Path.of(buildIdFile));
        effectiveBuildId = current;
      }

      var options =
          new Packager.PackageOptions(configuration, effectiveBuildId, appVersion, extras, devMode);
      var packed = Packager.pack(descriptorFile, projectRoot, bar, options);

      if (current != null) {
        writeBuildId(Path.of(buildIdFile), current);
      }
      var wrote = "bb-dev package: wrote " + barPath + " (" + packed.size() + " files)";
      if (!deploy.requested()) {
        return output.emit(
            0, wrote, Json.object("ok", true, "bar", barPath, "files", packed.size()));
      }
      return deploy(bar, barPath, packed.size(), wrote, devMode, deploy, output);
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

  private int deploy(
      Path bar,
      String barPath,
      int files,
      String wrote,
      boolean devMode,
      Deploy deploy,
      Output output) {
    String summary;
    try {
      summary =
          deploy.installApp()
              ? deployer.install(
                  bar, deploy.device(), DEVICE_PORT, deploy.password(), devMode, deploy.launchApp())
              : deployer.launch(bar, deploy.device(), DEVICE_PORT, deploy.password());
    } catch (IOException failure) {
      return output.emit(
          PACKAGE_ERROR,
          wrote + System.lineSeparator() + "✗ deploy failed: " + failure.getMessage(),
          Json.object(
              "ok",
              false,
              "bar",
              barPath,
              "files",
              files,
              "error",
              String.valueOf(failure.getMessage())));
    }
    return output.emit(
        0,
        wrote + System.lineSeparator() + "✓ " + summary,
        Json.object("ok", true, "bar", barPath, "files", files, "deploy", summary));
  }

  private int list(String barPath, Output output) {
    try (var archive = BarArchive.open(Path.of(barPath))) {
      var paths = archive.assets().stream().map(BarAsset::path).sorted().toList();
      return output.emit(
          0,
          String.join(System.lineSeparator(), paths),
          Json.object("ok", true, "bar", barPath, "files", paths));
    } catch (InvalidBarException | IOException failure) {
      return output.emit(
          PACKAGE_ERROR,
          "bb-dev package: cannot read " + barPath + ": " + failure.getMessage(),
          Json.object("ok", false, "error", String.valueOf(failure.getMessage())));
    }
  }

  private int listManifest(String barPath, Output output) {
    try (var archive = BarArchive.open(Path.of(barPath))) {
      var manifest = new String(archive.manifest().toBytes(), StandardCharsets.UTF_8);
      return output.emit(
          0, manifest, Json.object("ok", true, "bar", barPath, "manifest", manifest));
    } catch (InvalidBarException | IOException failure) {
      return output.emit(
          PACKAGE_ERROR,
          "bb-dev package: cannot read " + barPath + ": " + failure.getMessage(),
          Json.object("ok", false, "error", String.valueOf(failure.getMessage())));
    }
  }

  private int generateXml(String path, Output output) {
    var file = Path.of(path);
    if (Files.exists(file)) {
      return output.emit(
          PACKAGE_ERROR,
          "bb-dev package: refusing to overwrite existing " + path,
          Json.object("ok", false, "error", "file already exists"));
    }
    try {
      Files.writeString(file, DescriptorTemplate.xml());
      return output.emit(
          0,
          "bb-dev package: wrote starter descriptor " + path,
          Json.object("ok", true, "generated", path));
    } catch (IOException failure) {
      return output.emit(
          PACKAGE_ERROR,
          "bb-dev package: " + failure.getMessage(),
          Json.object("ok", false, "error", String.valueOf(failure.getMessage())));
    }
  }

  /** The current build id in the file, or {@code 0} when the file does not yet exist. */
  private static String readBuildId(Path file) throws IOException {
    if (!Files.exists(file)) {
      return "0";
    }
    var text = Files.readString(file).strip();
    return text.isEmpty() ? "0" : text;
  }

  /** Writes back the build id incremented by one, for the next build. */
  private static void writeBuildId(Path file, String current) throws IOException {
    long next;
    try {
      next = Long.parseLong(current) + 1;
    } catch (NumberFormatException notANumber) {
      return; // Leave a non-numeric build-id file untouched.
    }
    Files.writeString(file, Long.toString(next));
  }

  /** Resolves a command-line file against the current {@code -C} folder, or against the cwd. */
  private static Path resolve(Path root, String file) {
    return root == null ? Path.of(file) : root.resolve(file);
  }

  /** Expands every {@code @file} argument into the whitespace-separated options it holds. */
  private static List<String> expandOptionFiles(List<String> arguments) throws IOException {
    var expanded = new ArrayList<String>();
    for (var argument : arguments) {
      if (argument.startsWith("@") && argument.length() > 1) {
        var text = Files.readString(Path.of(argument.substring(1)));
        for (var token : text.split("\\s+")) {
          if (!token.isEmpty()) {
            expanded.add(token);
          }
        }
      } else {
        expanded.add(argument);
      }
    }
    return expanded;
  }

  private static String value(List<String> arguments, int index) {
    return index < arguments.size() ? arguments.get(index) : "";
  }

  private static String help() {
    return String.join(
        System.lineSeparator(),
        "bb-dev package - build a BlackBerry application archive (.bar)",
        "Creates an unsigned BAR package from native binary files and a descriptor.",
        "Usage:",
        "  bb-dev package [<commands>] [<options>] -package <out.bar> [<app-desc> <files>]",
        "Where:",
        "  <out.bar>               - a path for the output BAR file",
        "  <app-desc>              - a path to the application descriptor file"
            + " (bar-descriptor.xml)",
        "  <files>                 - paths to files or directories to include in a package.",
        "  These paths can be absolute or relative to a current directory.",
        "  The resulting location in the package would be a tail path of the file, unless"
            + " overridden by -C or -e options:",
        "<commands>: ",
        "  -package             - package assets into a unsigned BAR file (default)",
        "  -list                - list all files in the resulting package (for debugging of"
            + " packaging issues)",
        "  -listManifest        - print BAR manifest (for debugging of packaging issues)",
        "<packaging options>: ",
        "  -buildId <id>        - set the build ID (the fourth segment of the version). Must be a"
            + " number from 0 to 65535",
        "  -buildIdFile <file>  - set the build ID from an existing file, save new incremented"
            + " version to the same file",
        "  -devMode             - package in development mode: grants the on-device development"
            + " sandbox and remote access to application data (deploy sync). Off by default; not"
            + " required for an unsigned bar",
        "  -appVersion <X.Y.Z>  - app version (the first three segments of the version). Each must"
            + " be a number from 0 to 65535. Not all 0.",
        "  -configuration <id-or-name> - specify which configuration to use from the bar"
            + " descriptor",
        "<on-device options> (install/launch the built bar via bb-dev's own deploy): ",
        "  -installApp          - install the built bar on the device (requires -device)",
        "  -launchApp           - launch the application (after install, or on its own)",
        "  -device <host>       - the device host or IP address",
        "  -password <password> - the device password",
        "<path options>: ",
        "  -C <dir>             - use <dir> as a root directory;all files that follow will be used"
            + " with tail paths in the final package",
        "  -e <file> <path>     - save a <file> with the provided <path> in the package",
        "<options>: ",
        "  -generateXml <file>  - generate template bar-descriptor.xml and store it in <file>",
        "  -version             - print version",
        "  -help-advanced       - print advanced options",
        "  -help                - print this help",
        "  @<options_file>      - read options/arguments from a file, with one command-line"
            + " argument per line",
        "",
        "Signing and debug tokens are not supported (bars are unsigned only). On-device",
        "install and launch are also available on their own under 'bb-dev deploy'.",
        "",
        "Examples:",
        "Create a development BAR file with (mybinary,locale) assets:",
        "   bb-dev package -package out.bar -devMode bar-descriptor.xml mybinary -C /tmp"
            + " /tmp/locale/");
  }
}
