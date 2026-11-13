package com.jbraddockm.link.remoteaccess;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Chooses which host folder remote access serves for a device and makes sure it exists. The
 * device's stored setting may name a folder of its own; when it does not, the <em>global
 * default</em> is used — {@code ~/Documents/BB-Fuse} out of the box. Either way the folder is
 * created if it is not there yet, because the server jails itself to a real directory ({@link
 * JailedRoot}).
 *
 * <p>The default root is injected so tests need no home directory; {@link #withHomeDefault()}
 * builds the production one.
 */
public final class RemoteAccessFolders {

  private final Path defaultRoot;

  /**
   * @param defaultRoot the folder served when a device names none of its own
   */
  RemoteAccessFolders(Path defaultRoot) {
    this.defaultRoot = Objects.requireNonNull(defaultRoot, "defaultRoot");
  }

  /**
   * The production resolver, whose global default is {@code ~/Documents/BB-Fuse}.
   *
   * @return the resolver
   */
  public static RemoteAccessFolders withHomeDefault() {
    return new RemoteAccessFolders(
        Path.of(System.getProperty("user.home"), "Documents", "BB-Fuse"));
  }

  /**
   * The global default folder — served when a device names none of its own.
   *
   * @return the default root
   */
  public Path defaultRoot() {
    return defaultRoot;
  }

  /**
   * Resolves the folder to serve and ensures it exists: the device's own {@code perDeviceFolder}
   * when set, otherwise the global default.
   *
   * @param perDeviceFolder the device's chosen folder, or {@code null} to use the global default
   * @param readOnly whether the device may read but not write
   * @return the configuration, its root an existing directory
   * @throws IOException if the folder cannot be created
   */
  public RemoteAccessConfig resolve(Path perDeviceFolder, boolean readOnly) throws IOException {
    var root = perDeviceFolder != null ? perDeviceFolder : defaultRoot;
    Files.createDirectories(root);
    return new RemoteAccessConfig(root, readOnly);
  }
}
