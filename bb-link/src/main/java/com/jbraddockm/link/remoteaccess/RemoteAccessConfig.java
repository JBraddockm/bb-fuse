package com.jbraddockm.link.remoteaccess;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * The host remote-access configuration: which single host directory is exposed, and whether it is
 * read-only. This is bb-link's equivalent of RIM's rimdav {@code folder_config_file} — the control
 * that names what the device may reach. It is deliberately one <em>selectable</em> root (served
 * jailed by {@link JailedRoot}), never the whole filesystem.
 *
 * @param root the host directory to serve; the device sees only this tree
 * @param readOnly when {@code true}, the device may read but not write
 */
public record RemoteAccessConfig(Path root, boolean readOnly) {

  private static final String ROOT_KEY = "root";
  private static final String READ_ONLY_KEY = "readOnly";

  /** Validates the configuration. */
  public RemoteAccessConfig {
    if (root == null) {
      throw new IllegalArgumentException("a remote-access root is required");
    }
  }

  /**
   * Read-write remote access to {@code root}.
   *
   * @param root the host directory to serve
   * @return the configuration
   */
  public static RemoteAccessConfig of(Path root) {
    return new RemoteAccessConfig(root, false);
  }

  /**
   * Reads a remote-access configuration from a properties file: {@code root=<path>} and an optional
   * {@code readOnly=true|false} (default {@code false}).
   *
   * @param file the configuration file
   * @return the configuration
   * @throws IOException if the file cannot be read
   * @throws IllegalArgumentException if it names no root
   */
  public static RemoteAccessConfig load(Path file) throws IOException {
    var properties = new Properties();
    try (var in = Files.newInputStream(file)) {
      properties.load(in);
    }
    var root = properties.getProperty(ROOT_KEY);
    if (root == null || root.isBlank()) {
      throw new IllegalArgumentException("the remote-access configuration names no root: " + file);
    }
    return new RemoteAccessConfig(
        Path.of(root.strip()),
        Boolean.parseBoolean(properties.getProperty(READ_ONLY_KEY, "false")));
  }

  /**
   * Writes this configuration to a properties file.
   *
   * @param file the configuration file to write
   * @throws IOException if the file cannot be written
   */
  public void save(Path file) throws IOException {
    var properties = new Properties();
    properties.setProperty(ROOT_KEY, root.toString());
    properties.setProperty(READ_ONLY_KEY, Boolean.toString(readOnly));
    try (var out = Files.newOutputStream(file)) {
      properties.store(out, "bb-link host remote access");
    }
  }
}
