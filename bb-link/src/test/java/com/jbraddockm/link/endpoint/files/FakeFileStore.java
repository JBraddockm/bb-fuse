package com.jbraddockm.link.endpoint.files;

import com.jbraddockm.link.transport.smb.FileStore;
import com.jbraddockm.link.transport.smb.SmbEntry;
import com.jbraddockm.link.transport.smb.SmbLocation;
import com.jbraddockm.link.transport.smb.SmbTransferException;
import java.nio.file.Path;
import java.time.Instant;
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
  boolean pushResume;
  boolean pullResume;
  SmbLocation deleted;
  SmbLocation made;
  SmbLocation movedFrom;
  SmbLocation movedTo;
  SmbLocation copiedFrom;
  SmbLocation copiedTo;
  SmbLocation freeSpaceOf;
  long freeSpace;
  SmbLocation timeSetOn;
  Instant timeSet;

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
  public long pull(SmbLocation source, Path destination, LongConsumer progress, boolean resume)
      throws SmbTransferException {
    pulledSource = source;
    pulledDestination = destination;
    pullResume = resume;
    raise();
    return bytes;
  }

  @Override
  public long push(Path source, SmbLocation destination, LongConsumer progress, boolean resume)
      throws SmbTransferException {
    pushedSource = source;
    pushedDestination = destination;
    pushResume = resume;
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

  @Override
  public void copy(SmbLocation from, SmbLocation to) throws SmbTransferException {
    copiedFrom = from;
    copiedTo = to;
    raise();
  }

  @Override
  public long freeSpace(SmbLocation path) throws SmbTransferException {
    freeSpaceOf = path;
    raise();
    return freeSpace;
  }

  @Override
  public void setModifiedTime(SmbLocation path, Instant time) throws SmbTransferException {
    timeSetOn = path;
    timeSet = time;
    raise();
  }

  FakeFileStore withFreeSpace(long bytes) {
    this.freeSpace = bytes;
    return this;
  }

  private void raise() throws SmbTransferException {
    if (failure != null) {
      throw failure;
    }
  }
}
