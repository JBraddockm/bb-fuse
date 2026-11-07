package com.jbraddockm.link.transport.smb;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.function.LongConsumer;

/**
 * Read and write access to the device's file share: the transport primitives on which the file API
 * is built. Each method is a single operation and carries no sync, merge or recursion — those are a
 * client's concern.
 */
public interface FileStore {

  /**
   * Lists the entries of a directory on the share.
   *
   * @param directory the directory to list
   * @return its entries, in no guaranteed order; empty for an empty directory
   * @throws SmbTransferException if the directory cannot be read
   */
  List<SmbEntry> list(SmbLocation directory) throws SmbTransferException;

  /**
   * Reads the metadata of a single file or directory.
   *
   * @param path the file or directory
   * @return its entry
   * @throws SmbTransferException if the path does not exist or cannot be read
   */
  SmbEntry stat(SmbLocation path) throws SmbTransferException;

  /**
   * Copies a file from the device to the host.
   *
   * @param source the file on the share
   * @param destination the local file to write; an existing file is replaced
   * @param progress receives the running byte count as the copy proceeds
   * @return the number of bytes copied
   * @throws SmbTransferException if the source cannot be read or the destination cannot be written
   */
  default long pull(SmbLocation source, Path destination, LongConsumer progress)
      throws SmbTransferException {
    return pull(source, destination, progress, false);
  }

  /**
   * Copies a file from the device to the host, optionally resuming a partial transfer.
   *
   * @param source the file on the share
   * @param destination the local file to write
   * @param progress receives the running byte count
   * @param resume when {@code true} and a partial download is present, continue from where it
   *     stopped instead of starting over
   * @return the number of bytes in the finished file
   * @throws SmbTransferException if the source cannot be read or the destination cannot be written
   */
  long pull(SmbLocation source, Path destination, LongConsumer progress, boolean resume)
      throws SmbTransferException;

  /**
   * Copies a file from the host to the device. The bytes are written to a temporary name and then
   * renamed into place, so a reader never sees a half-written file and the device's media scanner
   * indexes only the finished file.
   *
   * @param source the local file to read
   * @param destination the file on the share to write; its parent directories are created, and an
   *     existing file is replaced
   * @param progress receives the running byte count as the copy proceeds
   * @return the number of bytes copied
   * @throws SmbTransferException if the source cannot be read or the destination cannot be written
   */
  default long push(Path source, SmbLocation destination, LongConsumer progress)
      throws SmbTransferException {
    return push(source, destination, progress, false);
  }

  /**
   * Copies a host file onto the device, optionally resuming a partial transfer. The bytes are
   * written to a temporary name and then renamed into place.
   *
   * @param source the local file to read
   * @param destination the file on the share to write
   * @param progress receives the running byte count
   * @param resume when {@code true} and a partial upload is present, continue from where it stopped
   *     instead of starting over
   * @return the number of bytes in the finished file
   * @throws SmbTransferException if the source cannot be read or the destination cannot be written
   */
  long push(Path source, SmbLocation destination, LongConsumer progress, boolean resume)
      throws SmbTransferException;

  /**
   * Deletes a single file. Directories are refused — removing a directory tree is a client's
   * concern, not a primitive.
   *
   * @param path the file to delete
   * @throws SmbTransferException if the path does not exist, is a directory, or cannot be removed
   */
  void delete(SmbLocation path) throws SmbTransferException;

  /**
   * Creates a directory, and any missing parent directories.
   *
   * @param path the directory to create
   * @throws SmbTransferException if the directory already exists or cannot be created
   */
  void makeDirectory(SmbLocation path) throws SmbTransferException;

  /**
   * Renames or moves a file or directory on the device. The destination's parent directories are
   * created if missing.
   *
   * @param from the existing path
   * @param to the new path
   * @throws SmbTransferException if the source does not exist, the destination already exists, or
   *     the move cannot be made
   */
  void move(SmbLocation from, SmbLocation to) throws SmbTransferException;

  /**
   * Copies a file to another path on the device, server-side — the bytes never travel to the host.
   * The destination's parent directories are created if missing.
   *
   * @param from the existing file
   * @param to the new file
   * @throws SmbTransferException if the source does not exist, the destination already exists, or
   *     the copy cannot be made
   */
  void copy(SmbLocation from, SmbLocation to) throws SmbTransferException;

  /**
   * Returns the free space, in bytes, of the share the path is on.
   *
   * @param path any path on the share to measure
   * @return the free space in bytes
   * @throws SmbTransferException if the share cannot be reached
   */
  long freeSpace(SmbLocation path) throws SmbTransferException;

  /**
   * Sets a file's last-modified time.
   *
   * @param path the file
   * @param time the new last-modified time
   * @throws SmbTransferException if the path does not exist or the time cannot be set
   */
  void setModifiedTime(SmbLocation path, Instant time) throws SmbTransferException;
}
