package com.jbraddockm.link.endpoint.pairing.store;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The {@code device.json} record in a device folder ({@code <devices>/<PIN>/}) — currently the
 * user's pinned SMB host for the device, the inner address kept stable across reconnects. Written
 * and read directly, like the certificate and the identity/pairing files beside it (same atomic
 * temp-then-move as {@link IdentityDirectory}/{@link PairingDirectory}); a later iteration merges
 * those records into this file.
 *
 * <p>Reads are tolerant: a hand-edited or stale file reads as "no pin" (see {@link
 * StoredDevice#fromJsonText}), so a bad record can never break a connect.
 */
public final class DeviceDirectory {

  /** The name of the device record file. */
  public static final String DEVICE_FILE = "device.json";

  private final Path directory;

  private DeviceDirectory(Path directory) {
    this.directory = directory;
  }

  /**
   * The folder of a device.
   *
   * @param devices the folder that holds one folder for each device
   * @param pin the PIN of the device, the name of the folder
   * @return the directory; nothing is created yet
   * @throws IllegalArgumentException if the PIN is not a safe folder name
   */
  public static DeviceDirectory of(Path devices, String pin) {
    if (!pin.matches("[A-Za-z0-9]{1,16}")) {
      throw new IllegalArgumentException("not a PIN: " + pin);
    }
    return new DeviceDirectory(devices.resolve(pin));
  }

  /**
   * The folder itself.
   *
   * @return its path
   */
  public Path directory() {
    return directory;
  }

  /**
   * The device record file.
   *
   * @return its path
   */
  public Path deviceFile() {
    return directory.resolve(DEVICE_FILE);
  }

  /**
   * Reads the device record.
   *
   * @return what it says, or empty if there is no file
   * @throws IOException if the file cannot be read
   */
  public Optional<StoredDevice> read() throws IOException {
    if (!Files.exists(deviceFile())) {
      return Optional.empty();
    }
    return Optional.of(
        StoredDevice.fromJsonText(Files.readString(deviceFile(), StandardCharsets.UTF_8)));
  }

  /**
   * The device's pinned SMB host, if one is set.
   *
   * @return the pinned {@code 169.254.<k>.1} host, or empty when none is pinned or the record is
   *     unreadable
   * @throws IOException if the file cannot be read
   */
  public Optional<String> pinnedSmbHost() throws IOException {
    return read().map(StoredDevice::smbHost);
  }

  /**
   * Pins an SMB host for the device, writing (or replacing) {@code device.json}.
   *
   * @param smbHost the device SMB host to keep stable ({@code 169.254.<k>.1}, {@code k} in 1..254)
   * @throws IllegalArgumentException if {@code smbHost} is not such an address
   * @throws IOException if the file cannot be written
   */
  public void pinSmbHost(String smbHost) throws IOException {
    var device = new StoredDevice(smbHost);
    Files.createDirectories(directory);
    var temporary = SecureFiles.withSuffix(deviceFile());
    Files.writeString(temporary, device.toJsonText(), StandardCharsets.UTF_8);
    SecureFiles.move(temporary, deviceFile());
  }

  /**
   * Clears any pinned SMB host for the device. While {@code device.json} holds only the pin this
   * removes the file; when the record grows to carry other fields this becomes a field removal.
   *
   * @throws IOException if the file cannot be removed
   */
  public void clearSmbHost() throws IOException {
    Files.deleteIfExists(deviceFile());
  }
}
