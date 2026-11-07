package com.jbraddockm.dev.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

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
}
