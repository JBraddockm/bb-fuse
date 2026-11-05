package com.jbraddockm.core.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link CompositeScanner}: running several scanners together. */
@DisplayName("CompositeScanner: running scanners together")
class CompositeScannerTest {

  private static final DiscoveredDevice A =
      DiscoveredDevice.anonymous("a", null, ConnectionKind.WIFI, "10.0.0.1");
  private static final DiscoveredDevice B =
      DiscoveredDevice.anonymous("b", null, ConnectionKind.USB, "169.254.0.1");

  private static DeviceScanner emitting(DiscoveredDevice... devices) {
    return found -> List.of(devices).forEach(found);
  }

  private static List<DiscoveredDevice> scanWith(DeviceScanner scanner) {
    var found = Collections.synchronizedList(new ArrayList<DiscoveredDevice>());
    scanner.scan(found::add);
    return found;
  }

  @Test
  @DisplayName("reports what every scanner finds")
  void forwardsFromEveryScanner() {
    var composite = new CompositeScanner(List.of(emitting(A), emitting(B)));

    assertThat(scanWith(composite)).containsExactlyInAnyOrder(A, B);
  }

  @Test
  @DisplayName("finds nothing when there are no scanners")
  void emptyFindsNothing() {
    assertThat(scanWith(new CompositeScanner(List.of()))).isEmpty();
  }

  @Test
  @DisplayName("one scanner failing does not stop the others")
  void aFailingScannerDoesNotStopTheRest() {
    DeviceScanner failing =
        _ -> {
          throw new IllegalStateException("that transport broke");
        };

    var composite = new CompositeScanner(List.of(failing, emitting(A)));

    assertThat(scanWith(composite)).containsExactly(A);
  }

  @Test
  @DisplayName("keeps its own copy of the scanners it was given")
  void keepsItsOwnCopy() {
    var given = new ArrayList<>(List.of(emitting(A)));

    var composite = new CompositeScanner(given);
    given.add(emitting(B));

    assertThat(scanWith(composite)).containsExactly(A);
  }

  @Test
  @DisplayName("hands every scanner the same sink")
  void everyScannerGetsTheSink() {
    var sinks = Collections.synchronizedList(new ArrayList<Consumer<DiscoveredDevice>>());
    DeviceScanner recording = sinks::add;

    new CompositeScanner(List.of(recording, recording)).scan(_ -> {});

    assertThat(sinks).hasSize(2);
  }
}
