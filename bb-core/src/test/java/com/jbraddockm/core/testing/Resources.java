package com.jbraddockm.core.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads test data from the resource folder that mirrors the package of the test. A missing resource
 * fails the test.
 */
public final class Resources {

  private Resources() {}

  /**
   * The text of a resource, as UTF-8, exactly as the file holds it.
   *
   * @param owner a class in the package whose folder holds the resource
   * @param name the name of the file
   * @return the text
   * @throws AssertionError if there is no such resource
   */
  public static String text(Class<?> owner, String name) {
    try (var stream = owner.getResourceAsStream(name)) {
      if (stream == null) {
        throw new AssertionError("no test resource " + name + " beside " + owner.getName());
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  /**
   * The bytes of a resource, exactly as the file holds them: for data that must not be turned into
   * text and back.
   *
   * @param owner a class in the package whose folder holds the resource
   * @param name the name of the file
   * @return the bytes
   * @throws AssertionError if there is no such resource
   */
  public static byte[] bytes(Class<?> owner, String name) {
    try (var stream = owner.getResourceAsStream(name)) {
      if (stream == null) {
        throw new AssertionError("no test resource " + name + " beside " + owner.getName());
      }
      return stream.readAllBytes();
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  /**
   * Copies a resource into a folder, for a test that needs the file on disk.
   *
   * @param owner the class whose package names the folder
   * @param name the name of the file in that folder
   * @param folder where to put the copy, which keeps the name
   * @return the copy
   */
  public static Path copyTo(Class<?> owner, String name, Path folder) {
    try {
      return Files.write(folder.resolve(name), bytes(owner, name));
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z0-9_.-]+)}}");

  /**
   * The text of a resource that is a template: each {@code {{name}}} in it is replaced by the value
   * that is given for the name. A name that has no value, and a value that has no name to go in,
   * are failures of the test, so that a template and its caller cannot drift apart unseen.
   *
   * @param owner a class in the package whose folder holds the resource
   * @param name the name of the file
   * @param values what goes in each placeholder
   * @return the text
   * @throws AssertionError if the placeholders and the values do not match
   */
  public static String text(Class<?> owner, String name, Map<String, String> values) {
    var template = text(owner, name);
    var found = new HashSet<String>();
    var result = new StringBuilder();
    var matcher = PLACEHOLDER.matcher(template);
    while (matcher.find()) {
      var key = matcher.group(1);
      var value = values.get(key);
      if (value == null) {
        throw new AssertionError("no value for {{" + key + "}} in " + name);
      }
      found.add(key);
      matcher.appendReplacement(result, Matcher.quoteReplacement(value));
    }
    matcher.appendTail(result);
    var unused = new HashSet<>(values.keySet());
    unused.removeAll(found);
    if (!unused.isEmpty()) {
      throw new AssertionError("no placeholder for " + unused + " in " + name);
    }
    return result.toString();
  }
}
