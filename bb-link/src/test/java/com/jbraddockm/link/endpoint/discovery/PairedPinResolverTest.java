package com.jbraddockm.link.endpoint.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link PairedPinResolver}: recovering a paired device's PIN from its bp2p advert.
 *
 * <p>The fixed vector is the one {@link FriendlyIdentity} verified on the wire: {@code
 * SHA1(33C9D3A93B9DD79F || "2C1E7583")[12:20] = 9B2770A11C414366}, so a device whose PIN is {@code
 * 2C1E7583} advertises {@code Friendly_33C9D3A93B9DD79F_9B2770A11C414366}.
 */
@DisplayName("PairedPinResolver: the local paired-PIN deviceHash match")
class PairedPinResolverTest {

  private static final String ADVERT = "Friendly_33C9D3A93B9DD79F_9B2770A11C414366";
  private static final String MATCHING_PIN = "2C1E7583";
  private static final String OTHER_PIN = "2BD63247";

  private static void pair(Path devices, String pin) throws IOException {
    Files.createDirectories(devices.resolve(pin));
  }

  @Nested
  @DisplayName("when the advertised device is paired")
  class Paired {

    @Test
    @DisplayName("recovers the PIN whose fold matches the advertised hash")
    void recoversTheMatchingPin(@TempDir Path devices) throws IOException {
      pair(devices, MATCHING_PIN);
      pair(devices, OTHER_PIN);

      assertThat(new PairedPinResolver(devices).resolve(ADVERT)).contains(MATCHING_PIN);
    }

    @Test
    @DisplayName("ignores non-PIN entries beside the paired folders")
    void ignoresNonPinEntries(@TempDir Path devices) throws IOException {
      pair(devices, MATCHING_PIN);
      Files.createFile(devices.resolve("README.md"));
      Files.createDirectories(devices.resolve("not-a-pin"));

      assertThat(new PairedPinResolver(devices).resolve(ADVERT)).contains(MATCHING_PIN);
    }
  }

  @Nested
  @DisplayName("when nothing matches")
  class NoMatch {

    @Test
    @DisplayName("is empty when no paired PIN folds to the advertised hash")
    void emptyWhenNoPairedPinMatches(@TempDir Path devices) throws IOException {
      pair(devices, OTHER_PIN);

      assertThat(new PairedPinResolver(devices).resolve(ADVERT)).isEmpty();
    }

    @Test
    @DisplayName("is empty when the devices directory does not exist")
    void emptyWhenNoDirectory(@TempDir Path devices) {
      assertThat(new PairedPinResolver(devices.resolve("absent")).resolve(ADVERT)).isEmpty();
    }

    @Test
    @DisplayName("is empty for an instance name that is not a Friendly advert")
    void emptyForANonFriendlyInstance(@TempDir Path devices) throws IOException {
      pair(devices, MATCHING_PIN);

      assertThat(new PairedPinResolver(devices).resolve("Invoke_33C9D3A93B9DD79F")).isEmpty();
      assertThat(new PairedPinResolver(devices).resolve("Friendly_notHex_9B2770A11C414366"))
          .isEmpty();
      assertThat(new PairedPinResolver(devices).resolve(null)).isEmpty();
    }
  }
}
