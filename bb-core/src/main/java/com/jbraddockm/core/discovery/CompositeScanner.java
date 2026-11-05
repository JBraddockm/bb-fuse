package com.jbraddockm.core.discovery;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Runs several scanners at once and reports what any of them finds. The scanners run on their own
 * threads and a scan returns only when all of them have finished, so a slow transport does not hold
 * back a fast one, and a device on two transports is found by each. One scanner failing does not
 * stop the others; the duplicate a device on two transports produces is folded later by {@link
 * DeviceDiscovery}.
 *
 * <p>{@code found} is handed to every child, so it is called from several threads at once; a caller
 * that is not thread-safe must serialize it, as {@link DeviceDiscovery#discover} does.
 */
public final class CompositeScanner implements DeviceScanner {

  private final List<DeviceScanner> scanners;

  /**
   * Combines scanners.
   *
   * @param scanners the scanners to run together; an empty list finds nothing
   */
  public CompositeScanner(List<DeviceScanner> scanners) {
    this.scanners = List.copyOf(scanners);
  }

  @Override
  public void scan(Consumer<DiscoveredDevice> found) {
    if (scanners.isEmpty()) {
      return;
    }
    try (var pool = Executors.newFixedThreadPool(scanners.size())) {
      var running =
          scanners.stream().map(scanner -> pool.submit(() -> scanner.scan(found))).toList();
      awaitAll(running);
    }
  }

  private static void awaitAll(List<? extends Future<?>> running) {
    for (var task : running) {
      try {
        task.get();
      } catch (ExecutionException _) {
        // One transport failing is not fatal: the others still report what they found.
      } catch (InterruptedException _) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }
}
