package com.jbraddockm.core.bar;

import java.util.ArrayList;
import java.util.List;

/**
 * Pulls asset names out of the launch-related manifest attributes, the way the dev-mode sync's
 * mini-BAR does. An {@code Entry-Point} is a shell-style command whose binary is one token; an
 * {@code Entry-Point-Icon} / {@code Entry-Point-Splash-Screen} / {@code Invoke-Target-Icon} is a
 * colon-separated list of image assets, each optionally carrying a {@code {width x height}} size
 * qualifier. The names returned are archive-relative, matching {@link ManifestAsset#name()}.
 */
final class EntryPointParser {

  private EntryPointParser() {}

  /**
   * The binary asset named by an {@code Entry-Point} command: its first token that is not an {@code
   * ENV=value} assignment, taken from after that token's first path separator (dropping the {@code
   * app/} sandbox prefix). Spaces escaped with a backslash are part of the path.
   *
   * @param command the {@code Entry-Point} value
   * @return the binary's archive-relative name, or {@code null} if the command names none
   */
  static String binaryAssetName(String command) {
    var firstSlash = -1;
    var assignment = false;
    for (var index = 0; index < command.length(); index++) {
      var character = command.charAt(index);
      if (character == '=') {
        assignment = true;
      } else if (character == '/') {
        if (firstSlash == -1) {
          firstSlash = index;
        }
      } else if (character == ' ' && !(index > 0 && command.charAt(index - 1) == '\\')) {
        if (assignment) {
          assignment = false;
          firstSlash = -1;
        } else {
          return firstSlash == -1 ? null : command.substring(firstSlash + 1, index);
        }
      }
    }
    return !assignment && firstSlash != -1 ? command.substring(firstSlash + 1) : null;
  }

  /**
   * The image asset names in a colon-separated image attribute, each with its leading {@code
   * {size}} qualifier stripped.
   *
   * @param value the attribute value
   * @return the asset names, in order
   */
  static List<String> imageAssetNames(String value) {
    var names = new ArrayList<String>();
    for (var entry : value.split(":")) {
      var brace = entry.indexOf('}');
      names.add(brace == -1 ? entry : entry.substring(brace + 1));
    }
    return names;
  }
}
