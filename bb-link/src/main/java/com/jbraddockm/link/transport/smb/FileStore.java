package com.jbraddockm.link.transport.smb;

import java.nio.file.Path;
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
  long pull(SmbLocation source, Path destination, LongConsumer progress)
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
  long push(Path source, SmbLocation destination, LongConsumer progress)
      throws SmbTransferException;
}
