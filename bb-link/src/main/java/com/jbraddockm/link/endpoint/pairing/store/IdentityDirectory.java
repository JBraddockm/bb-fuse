package com.jbraddockm.link.endpoint.pairing.store;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The login certificate of one device: {@code <devices>/<PIN>/}, with the certificate and its key.
 * The identity metadata lives in the folder's {@link DeviceDirectory device.json} (the {@code
 * identity} section), which this class writes through — the files are its own concern, the record
 * is shared.
 *
 * <p>An identity is written in two steps, so that a failed enrolment never destroys one that works:
 * {@link #prepare} puts the key next to its place under another name, and {@link Pending#commit}
 * writes the certificate, moves everything into place, and records the metadata. A {@code Pending}
 * that is closed without a commit takes back what it wrote.
 */
public final class IdentityDirectory {

  /** The name of the certificate file; the name that the scripts look for. */
  public static final String CERTIFICATE_FILE = "client_cert.pem";

  /** The name of the key file. */
  public static final String KEY_FILE = "client_key.pem";

  private final Path directory;
  private final DeviceDirectory device;

  private IdentityDirectory(Path directory, DeviceDirectory device) {
    this.directory = directory;
    this.device = device;
  }

  /**
   * The folder of a device.
   *
   * @param devices the folder that holds one folder for each device
   * @param pin the PIN of the device, the name of the folder
   * @return the directory; nothing is created yet
   * @throws IllegalArgumentException if the PIN is not a safe folder name
   */
  public static IdentityDirectory of(Path devices, String pin) {
    var device = DeviceDirectory.of(devices, pin);
    return new IdentityDirectory(device.directory(), device);
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
   * The certificate file.
   *
   * @return its path
   */
  public Path certificateFile() {
    return directory.resolve(CERTIFICATE_FILE);
  }

  /**
   * The key file.
   *
   * @return its path
   */
  public Path keyFile() {
    return directory.resolve(KEY_FILE);
  }

  /**
   * The certificate and key of an identity that are there.
   *
   * @return the paths, certificate then key
   */
  public List<Path> existingFiles() {
    return Stream.of(certificateFile(), keyFile()).filter(Files::exists).toList();
  }

  /**
   * The identity recorded for this device.
   *
   * @return what the record says, or empty if there is none
   * @throws IOException if the record cannot be read
   */
  public Optional<StoredIdentity> metadata() throws IOException {
    return device.identity();
  }

  /**
   * Starts writing an identity: the folder is made and the key is written under a temporary name.
   *
   * @param keys the key pair; only the private key is written
   * @param identity what the identity record will say
   * @param replace whether an identity that is there may be replaced
   * @return the pending identity; close it, and commit it before that if all went well
   * @throws FilesExistException if the certificate and key are there and {@code replace} is not set
   * @throws IOException if a file or the folder cannot be written
   */
  public Pending prepare(KeyPair keys, StoredIdentity identity, boolean replace)
      throws IOException {
    var existing = existingFiles();
    if (!existing.isEmpty() && !replace) {
      throw new FilesExistException("an identity for this device", directory, existing);
    }
    SecureFiles.createPrivateDirectory(directory);
    SecureFiles.writePrivateKey(SecureFiles.withSuffix(keyFile()), keys.getPrivate());
    return new Pending(identity);
  }

  /** An identity that has its key on disk and waits for its certificate. */
  public final class Pending implements AutoCloseable {

    private final StoredIdentity identity;
    private boolean committed;

    private Pending(StoredIdentity identity) {
      this.identity = identity;
    }

    /**
     * Writes the certificate, moves the key and certificate into place, and records the identity.
     * The key goes first, so that a certificate is never without its key.
     *
     * @param certificatePem the certificate, as PEM
     * @throws IOException if a file cannot be written or moved
     */
    public void commit(String certificatePem) throws IOException {
      commit(certificatePem, identity);
    }

    /**
     * As {@link #commit(String)}, but records an identity other than the one prepared with.
     *
     * @param certificatePem the certificate, as PEM
     * @param written what the identity record will say
     * @throws IOException if a file cannot be written or moved
     */
    public void commit(String certificatePem, StoredIdentity written) throws IOException {
      var certificateTemporary = SecureFiles.withSuffix(certificateFile());
      Files.writeString(certificateTemporary, certificatePem, StandardCharsets.UTF_8);
      SecureFiles.move(SecureFiles.withSuffix(keyFile()), keyFile());
      SecureFiles.move(certificateTemporary, certificateFile());
      device.writeIdentity(written);
      committed = true;
    }

    /** Takes back the temporary files, unless the identity was committed. */
    @Override
    public void close() {
      if (committed) {
        return;
      }
      SecureFiles.deleteTemporaries(List.of(keyFile(), certificateFile()));
    }
  }
}
