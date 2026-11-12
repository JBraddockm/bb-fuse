package com.jbraddockm.link.share;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

import com.jbraddockm.link.transport.tunnel.TunnelBinding;
import com.jbraddockm.link.transport.tunnel.TunnelConnector;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ShareRunner: acquire, start, park, tear down")
// The fake tunnel hold holds no real resource; the runner's own close is what is under test.
@SuppressWarnings("resource")
class ShareRunnerTest {

  /** A tunnel hold that records its close and reports a fixed host ULA. */
  private static final class FakeBinding implements TunnelBinding {
    final List<String> events;
    boolean closed;

    FakeBinding(List<String> events) {
      this.events = events;
    }

    @Override
    public String ula() {
      return "fd00::dev";
    }

    @Override
    public String hostUla() {
      return "fd00::host";
    }

    @Override
    public String smbHost() {
      return "169.254.1.1";
    }

    @Override
    public boolean isDown() {
      return false;
    }

    @Override
    public void close() {
      closed = true;
      events.add("binding-closed");
    }
  }

  @Test
  @DisplayName("starts the share on the hold's host ULA, parks, then closes share then tunnel")
  void happyPath() throws Exception {
    var events = new ArrayList<String>();
    var binding = new FakeBinding(events);
    TunnelConnector connector = () -> binding;
    var seenHostUla = new String[1];
    ShareRunner.ShareStarter starter =
        hold -> {
          seenHostUla[0] = hold.hostUla();
          events.add("share-started");
          return () -> events.add("share-closed");
        };
    ShareRunner.Parker parker = () -> events.add("parked");

    ShareRunner.run(connector, starter, parker);

    assertThat(seenHostUla[0]).isEqualTo("fd00::host");
    assertThat(events).containsExactly("share-started", "parked", "share-closed", "binding-closed");
    assertThat(binding.closed).isTrue();
  }

  @Test
  @DisplayName("releases the tunnel hold even when the share fails to start")
  void startFailureReleasesTunnel() {
    var events = new ArrayList<String>();
    var binding = new FakeBinding(events);
    TunnelConnector connector = () -> binding;
    ShareRunner.ShareStarter starter =
        _ -> {
          throw new IOException("bind refused");
        };

    assertThatIOException()
        .isThrownBy(() -> ShareRunner.run(connector, starter, () -> events.add("parked")));

    assertThat(binding.closed).isTrue();
    assertThat(events).containsExactly("binding-closed");
  }

  @Test
  @DisplayName("closes the share even when the park is interrupted")
  void parkInterruptedStillTearsDown() {
    var events = new ArrayList<String>();
    var binding = new FakeBinding(events);
    TunnelConnector connector = () -> binding;
    ShareRunner.ShareStarter starter =
        _ -> {
          events.add("share-started");
          return () -> events.add("share-closed");
        };

    try {
      ShareRunner.run(
          connector,
          starter,
          () -> {
            throw new InterruptedException("stop");
          });
    } catch (IOException | InterruptedException expected) {
      // the interrupt propagates after teardown
    }

    assertThat(events).containsExactly("share-started", "share-closed", "binding-closed");
  }
}
