package com.jbraddockm.tunnel;

import java.util.BitSet;

/**
 * Hands out distinct inner-IPv4 pairs so several paired devices can hold <b>concurrent</b> tunnels
 * without colliding. The device binds whatever valid {@code 169.254.0.0/16} address it is sent, so
 * the pair is ours to choose per session; this pool is how we choose it.
 *
 * <p>Each pair takes a distinct third octet {@code k}: device {@code 169.254.k.1}, pc {@code
 * 169.254.k.2}. {@code k = 0} ({@code 169.254.0.0/24} — the USB NCM interface and RFC-3927's
 * reserved first {@code /24}) and {@code k = 255} (RFC-3927's reserved last {@code /24}) are never
 * handed out. {@link #allocate()} (no preference) hands out {@code k = 2, 3, …}, leaving {@code k =
 * 1} ({@code 169.254.1.1/.2}) free for a caller that {@linkplain #allocate(int) proposes} it — e.g.
 * an external-app mount that wants the constant {@code smb://169.254.1.1/} address.
 *
 * <p>Thread-safe; the session manager holds one pool and {@link #release(InnerIpv4) releases} a
 * pair when its tunnel tears down.
 */
final class InnerAddressPool {

  /** The lowest third octet {@link #allocate()} hands out; {@code k = 1} is left for a proposal. */
  private static final int MIN_AUTO_K = 2;

  /**
   * The range a proposal may request ({@code k = 1..254}); {@code k = 0} and {@code 255} stay
   * reserved.
   */
  private static final int MIN_K = 1;

  private static final int MAX_K = 254;

  private final BitSet used = new BitSet(256);

  InnerAddressPool() {
    used.set(0); // 169.254.0.0/24 — USB NCM + RFC-3927 reserved first /24
    used.set(255); // 169.254.255.0/24 — RFC-3927 reserved last /24
  }

  private static InnerIpv4 pair(int k) {
    return new InnerIpv4("169.254." + k + ".2", "169.254." + k + ".1");
  }

  private static int thirdOctet(String ipv4) {
    return Integer.parseInt(ipv4.split("\\.")[2]);
  }

  /** The next free pair with no preference ({@code k = 2…254}). Throws when all 253 are in use. */
  synchronized InnerIpv4 allocate() {
    int k = used.nextClearBit(MIN_AUTO_K);
    if (k > MAX_K) {
      throw new IllegalStateException("inner-IPv4 pool exhausted (253 concurrent sessions)");
    }
    used.set(k);
    return pair(k);
  }

  /**
   * The pair for {@code preferredK} when it is a valid, free third octet ({@code k = 1…254});
   * otherwise the next free pair, as a counter-offer. This is how a proposed SMB host is honoured
   * when it can be and replaced when it cannot.
   *
   * @param preferredK the proposed device third octet (its SMB host is {@code 169.254.<k>.1})
   * @return the granted pair — the proposed one when free, else a counter-offer
   */
  synchronized InnerIpv4 allocate(int preferredK) {
    if (preferredK >= MIN_K && preferredK <= MAX_K && !used.get(preferredK)) {
      used.set(preferredK);
      return pair(preferredK);
    }
    return allocate();
  }

  /** Returns a pair to the pool; ignored if it was not one this pool handed out. */
  synchronized void release(InnerIpv4 pair) {
    int k = thirdOctet(pair.deviceIpv4());
    if (k >= MIN_K && k <= MAX_K) {
      used.clear(k);
    }
  }
}
