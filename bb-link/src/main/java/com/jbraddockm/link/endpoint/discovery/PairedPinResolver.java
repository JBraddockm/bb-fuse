package com.jbraddockm.link.endpoint.discovery;

import com.jbraddockm.core.discovery.DevicePinResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Recovers a Wi-Fi device's PIN from its bp2p {@code Friendly} advert, locally and with no tunnel.
 *
 * <p>The instance name folds {@code SHA1(seed || PIN)[12:20]} over the device's own PIN (see {@link
 * FriendlyIdentity}), and the seed rotates each session. Each paired PIN is a folder under the
 * devices directory ({@code devices/<PIN>/}), so recomputing the fold over the advertised seed for
 * each paired PIN and matching the advertised hash identifies the device — pure local SHA-1.
 *
 * <p>An unpaired device matches nothing and keeps no PIN. The directory is read on each resolve, so
 * a freshly paired device is picked up on the next scan.
 */
public final class PairedPinResolver implements DevicePinResolver {

  private final Path devicesDirectory;

  /**
   * Creates a resolver over the paired PINs under {@code devicesDirectory}.
   *
   * @param devicesDirectory the directory whose subfolders are the paired PINs (for example {@code
   *     devices/}); it need not exist
   */
  public PairedPinResolver(Path devicesDirectory) {
    this.devicesDirectory = devicesDirectory;
  }

  @Override
  public Optional<String> resolve(String bp2pInstanceName) {
    var advert = FriendlyIdentity.parse(bp2pInstanceName);
    if (advert.isEmpty()) {
      return Optional.empty();
    }
    var seed = advert.get().seed();
    var advertisedHash = advert.get().deviceHash();
    for (var pin : pairedPins()) {
      if (Arrays.equals(FriendlyIdentity.deviceHash(pin, seed), advertisedHash)) {
        return Optional.of(pin);
      }
    }
    return Optional.empty();
  }

  /**
   * The paired PINs: the names of the subfolders of the devices directory that look like a PIN (8
   * hexadecimal digits). An absent or unreadable directory yields none.
   */
  private List<String> pairedPins() {
    if (!Files.isDirectory(devicesDirectory)) {
      return List.of();
    }
    try (Stream<Path> entries = Files.list(devicesDirectory)) {
      return entries
          .filter(Files::isDirectory)
          .map(folder -> folder.getFileName().toString())
          .filter(PairedPinResolver::looksLikePin)
          .toList();
    } catch (IOException unreadable) {
      return List.of();
    }
  }

  /** Whether a folder name is a PIN: exactly 8 hexadecimal digits. */
  private static boolean looksLikePin(String name) {
    if (name.length() != 8) {
      return false;
    }
    for (var i = 0; i < name.length(); i++) {
      var c = name.charAt(i);
      var hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
      if (!hex) {
        return false;
      }
    }
    return true;
  }
}
