package com.jbraddockm.link.endpoint.files;

import com.jbraddockm.link.transport.smb.FileStore;
import com.jbraddockm.link.transport.smb.SmbEntry;
import com.jbraddockm.link.transport.smb.SmbLocation;
import com.jbraddockm.link.transport.smb.SmbTransferException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongConsumer;

/**
 * An in-memory {@link FileStore} for exercising the file commands without a device. Listings and
 * stats are seeded; transfers report a fixed byte count and record their arguments; a seeded
 * failure makes every operation throw.
 */
final class FakeFileStore implements FileStore {

  private final Map<SmbLocation, List<SmbEntry>> listings = new HashMap<>();
  private final Map<SmbLocation, SmbEntry> stats = new HashMap<>();
  private SmbTransferException failure;
  private long bytes;

  SmbLocation listed;
  SmbLocation statted;
  Path pushedSource;
  SmbLocation pushedDestination;
  SmbLocation pulledSource;
  Path pulledDestination;
  SmbLocation deleted;
  SmbLocation made;
  SmbLocation movedFrom;
  SmbLocation movedTo;

  FakeFileStore withListing(SmbLocation directory, List<SmbEntry> entries) {
    listings.put(directory, entries);
    return this;
  }

  FakeFileStore withStat(SmbLocation path, SmbEntry entry) {
    stats.put(path, entry);
    return this;
  }

  FakeFileStore returning(long transferred) {
    this.bytes = transferred;
    return this;
  }

  FakeFileStore failingWith(SmbTransferException seeded) {
    this.failure = seeded;
    return this;
  }

  @Override
  public List<SmbEntry> list(SmbLocation directory) throws SmbTransferException {
    listed = directory;
    raise();
    return listings.getOrDefault(directory, List.of());
  }

  @Override
  public SmbEntry stat(SmbLocation path) throws SmbTransferException {
    statted = path;
    raise();
    return stats.get(path);
  }

  @Override
  public long pull(SmbLocation source, Path destination, LongConsumer progress)
      throws SmbTransferException {
    pulledSource = source;
    pulledDestination = destination;
    raise();
    return bytes;
  }

  @Override
  public long push(Path source, SmbLocation destination, LongConsumer progress)
      throws SmbTransferException {
    pushedSource = source;
    pushedDestination = destination;
    raise();
    return bytes;
  }

  @Override
  public void delete(SmbLocation path) throws SmbTransferException {
    deleted = path;
    raise();
  }

  @Override
  public void makeDirectory(SmbLocation path) throws SmbTransferException {
    made = path;
    raise();
  }

  @Override
  public void move(SmbLocation from, SmbLocation to) throws SmbTransferException {
    movedFrom = from;
    movedTo = to;
    raise();
  }

  private void raise() throws SmbTransferException {
    if (failure != null) {
      throw failure;
    }
  }
}
