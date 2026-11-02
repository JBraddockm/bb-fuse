package com.jbraddockm.tunnel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link InnerAddressPool}: distinct per-session inner-IPv4 pairs, with a proposed octet
 * honoured when free and countered when not.
 */
@DisplayName("InnerAddressPool: per-session inner-IPv4 allocation")
class InnerAddressPoolTest {

  @Nested
  @DisplayName("allocate, for concurrent sessions")
  class Allocate {

    @Test
    @DisplayName("hands out distinct pairs starting at k=2, skipping the reserved blocks")
    void handsOutDistinctPairsFromKTwo() {
      InnerAddressPool pool = new InnerAddressPool();

      assertThat(pool.allocate()).isEqualTo(new InnerIpv4("169.254.2.2", "169.254.2.1"));
      assertThat(pool.allocate()).isEqualTo(new InnerIpv4("169.254.3.2", "169.254.3.1"));
      assertThat(pool.allocate()).isEqualTo(new InnerIpv4("169.254.4.2", "169.254.4.1"));
    }

    @Test
    @DisplayName("with no preference never hands out k=0, k=1, or k=255")
    void neverHandsOutReservedBlocks() {
      InnerAddressPool pool = new InnerAddressPool();

      List<InnerIpv4> all = new ArrayList<>();
      for (int i = 0; i < 253; i++) {
        all.add(pool.allocate());
      }

      assertThat(all)
          .doesNotContain(
              new InnerIpv4("169.254.0.2", "169.254.0.1"),
              new InnerIpv4("169.254.1.2", "169.254.1.1"))
          .doesNotContain(new InnerIpv4("169.254.255.2", "169.254.255.1"))
          .hasSize(253)
          .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("throws once all 253 no-preference blocks are in use")
    void throwsWhenExhausted() {
      InnerAddressPool pool = new InnerAddressPool();
      for (int i = 0; i < 253; i++) {
        pool.allocate();
      }

      assertThatThrownBy(pool::allocate)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("exhausted");
    }
  }

  @Nested
  @DisplayName("allocate(preferredK), proposing a specific octet")
  class Propose {

    @Test
    @DisplayName("honours a free proposed octet and keeps it out of later no-preference allocation")
    void honoursAFreeProposal() {
      InnerAddressPool pool = new InnerAddressPool();

      assertThat(pool.allocate(5)).isEqualTo(new InnerIpv4("169.254.5.2", "169.254.5.1"));
      assertThat(pool.allocate()).isEqualTo(new InnerIpv4("169.254.2.2", "169.254.2.1"));
    }

    @Test
    @DisplayName("grants the constant k=1 only when proposed, never from allocate()")
    void grantsTheConstantOnlyWhenProposed() {
      InnerAddressPool pool = new InnerAddressPool();

      assertThat(pool.allocate()).isEqualTo(new InnerIpv4("169.254.2.2", "169.254.2.1"));
      assertThat(pool.allocate(1)).isEqualTo(new InnerIpv4("169.254.1.2", "169.254.1.1"));
    }

    @Test
    @DisplayName("counter-offers the next free octet when the proposed one is taken")
    void countersWhenTaken() {
      InnerAddressPool pool = new InnerAddressPool();
      pool.allocate(); // takes k=2

      assertThat(pool.allocate(2)).isEqualTo(new InnerIpv4("169.254.3.2", "169.254.3.1"));
    }

    @Test
    @DisplayName("counter-offers for an out-of-range or reserved proposal")
    void countersForInvalidProposal() {
      assertThat(new InnerAddressPool().allocate(0))
          .isEqualTo(new InnerIpv4("169.254.2.2", "169.254.2.1"));
      assertThat(new InnerAddressPool().allocate(255))
          .isEqualTo(new InnerIpv4("169.254.2.2", "169.254.2.1"));
      assertThat(new InnerAddressPool().allocate(999))
          .isEqualTo(new InnerIpv4("169.254.2.2", "169.254.2.1"));
    }
  }

  @Nested
  @DisplayName("release, returning a pair to the pool")
  class Release {

    @Test
    @DisplayName("frees an allocated block for reuse")
    void freesAllocatedBlockForReuse() {
      InnerAddressPool pool = new InnerAddressPool();
      InnerIpv4 first = pool.allocate();
      pool.allocate();

      pool.release(first);

      assertThat(pool.allocate()).isEqualTo(first);
    }

    @Test
    @DisplayName("frees a proposed pair so it can be proposed again")
    void freesAProposedPair() {
      InnerAddressPool pool = new InnerAddressPool();
      InnerIpv4 proposed = pool.allocate(1);

      pool.release(proposed);

      assertThat(pool.allocate(1)).isEqualTo(proposed);
    }

    @Test
    @DisplayName("ignores a pair the pool never handed out")
    void ignoresUnknownPair() {
      InnerAddressPool pool = new InnerAddressPool();

      pool.release(new InnerIpv4("169.254.0.2", "169.254.0.1"));

      assertThat(pool.allocate()).isEqualTo(new InnerIpv4("169.254.2.2", "169.254.2.1"));
    }
  }

  @Nested
  @DisplayName("InnerIpv4 validation")
  class Validation {

    @Test
    @DisplayName("rejects a non-link-local address")
    void rejectsNonLinkLocal() {
      assertThatThrownBy(() -> new InnerIpv4("10.0.0.2", "169.254.1.1"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("169.254");
    }
  }
}
