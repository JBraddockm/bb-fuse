package com.jbraddockm.link.endpoint.pairing.store;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The one record of a device, {@code <devices>/<PIN>/device.json}: its login identity, its pairing,
 * and its pinned SMB host. This is the single place that reads and writes {@code device.json} — the
 * cert and key files beside it are written by {@link IdentityDirectory} and {@link
 * PairingDirectory}, which keep their metadata here. Each write preserves the other sections, so
 * independent operations (pair, enrol, pin) update the file without clobbering one another.
 *
 * <p>Reads are tolerant: a hand-edited or stale file reads as its parts being absent (see {@link
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
   * The record in a device folder named directly — e.g. the folder beside the tunnel certificate a
   * connect was given, {@code <devices>/<PIN>/}.
   *
   * @param directory the device folder
   * @return the directory; nothing is created yet
   */
  public static DeviceDirectory at(Path directory) {
    return new DeviceDirectory(directory);
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
   * The whole record.
   *
   * @return what the file says, or empty if there is no file
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
   * The device's login identity.
   *
   * @return the identity, or empty when none is enrolled or the record is unreadable
   * @throws IOException if the file cannot be read
   */
  public Optional<StoredIdentity> identity() throws IOException {
    return read().map(StoredDevice::identity);
  }

  /**
   * The device's pairing.
   *
   * @return the pairing, or empty when not paired or the record is unreadable
   * @throws IOException if the file cannot be read
   */
  public Optional<StoredPairing> pairing() throws IOException {
    return read().map(StoredDevice::pairing);
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
   * Writes (or replaces) the login identity, keeping the other sections.
   *
   * @param identity the identity to record
   * @throws IOException if the file cannot be written
   */
  public void writeIdentity(StoredIdentity identity) throws IOException {
    var current = current();
    save(new StoredDevice(identity, current.pairing(), current.smbHost()));
  }

  /**
   * Writes (or replaces) the pairing, keeping the other sections.
   *
   * @param pairing the pairing to record
   * @throws IOException if the file cannot be written
   */
  public void writePairing(StoredPairing pairing) throws IOException {
    var current = current();
    save(new StoredDevice(current.identity(), pairing, current.smbHost()));
  }

  /**
   * Pins an SMB host for the device, keeping the other sections.
   *
   * @param smbHost the device SMB host to keep stable ({@code 169.254.<k>.1}, {@code k} in 1..254)
   * @throws IllegalArgumentException if {@code smbHost} is not such an address
   * @throws IOException if the file cannot be written
   */
  public void pinSmbHost(String smbHost) throws IOException {
    var current = current();
    save(new StoredDevice(current.identity(), current.pairing(), smbHost));
  }

  /**
   * Clears any pinned SMB host, keeping the other sections. The file is removed once it would hold
   * nothing.
   *
   * @throws IOException if the file cannot be written
   */
  public void clearSmbHost() throws IOException {
    var current = current();
    save(new StoredDevice(current.identity(), current.pairing(), null));
  }

  /** The current record, or an empty one when there is no file. */
  private StoredDevice current() throws IOException {
    return read().orElseGet(() -> new StoredDevice(null, null, null));
  }

  /** Writes the record atomically, or removes the file when the record would hold nothing. */
  private void save(StoredDevice device) throws IOException {
    if (device.isEmpty()) {
      Files.deleteIfExists(deviceFile());
      return;
    }
    Files.createDirectories(directory);
    var temporary = SecureFiles.withSuffix(deviceFile());
    Files.writeString(temporary, device.toJsonText(), StandardCharsets.UTF_8);
    SecureFiles.move(temporary, deviceFile());
  }
}
