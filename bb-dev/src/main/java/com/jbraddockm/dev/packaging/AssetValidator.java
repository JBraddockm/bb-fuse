package com.jbraddockm.dev.packaging;

import com.jbraddockm.core.bar.BarValidation.Message;
import com.jbraddockm.core.bar.BarValidation.Severity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Checks a descriptor's asset paths against BlackBerry's {@code AttributeValidator} rules: a path
 * must be relative, use {@code /} as its separator with no {@code \\}, {@code :} or {@code ;}, be
 * no longer than {@value #MAXIMUM_PATH_LENGTH} characters, name a file in the set being packaged,
 * and that file must exist. Each broken rule is a warning, as in the original.
 */
final class AssetValidator {

  /** The longest an asset path may be. */
  static final int MAXIMUM_PATH_LENGTH = 255;

  /** The one asset that need not be in the packaged set, because the packager supplies it. */
  static final String DEFAULT_ICON = "blackberry-tablet-default-icon.png";

  private AssetValidator() {}

  /**
   * Checks every asset of the descriptor, the top-level assets and each configuration's.
   *
   * @param descriptor the descriptor whose assets to check
   * @param packagedFiles the files being packaged, each asset path mapped to the file on the host
   * @return what the rules found
   */
  static AssetValidation validate(BarDescriptor descriptor, Map<String, Path> packagedFiles) {
    var messages = new ArrayList<Message>();
    Stream.concat(
            descriptor.assets().stream(),
            descriptor.configurations().stream()
                .flatMap(configuration -> configuration.assets().stream()))
        .map(DescriptorAsset::path)
        .filter(Objects::nonNull)
        .forEach(path -> check(path, packagedFiles, messages));
    return new AssetValidation(messages);
  }

  private static void check(String path, Map<String, Path> packagedFiles, List<Message> messages) {
    if (path.startsWith("/")) {
      messages.add(warning("asset path cannot be absolute: " + path));
    }
    if (path.contains("\\") || path.contains(":") || path.contains(";")) {
      messages.add(
          warning("asset path must use '/' as its separator, with no '\\', ':' or ';': " + path));
    }
    if (path.length() > MAXIMUM_PATH_LENGTH) {
      messages.add(
          warning("asset path is longer than " + MAXIMUM_PATH_LENGTH + " characters: " + path));
    }
    if (!packagedFiles.containsKey(path) && !path.equals(DEFAULT_ICON)) {
      messages.add(warning("asset is not among the packaged files: " + path));
      return;
    }
    var file = packagedFiles.get(path);
    if (file != null && !Files.exists(file)) {
      messages.add(warning("asset file does not exist: " + path));
    }
  }

  private static Message warning(String text) {
    return new Message(Severity.WARNING, text);
  }
}
