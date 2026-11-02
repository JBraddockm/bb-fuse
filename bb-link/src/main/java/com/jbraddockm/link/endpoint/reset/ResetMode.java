package com.jbraddockm.link.endpoint.reset;

import com.jbraddockm.link.transport.cgi.WireValue;

/**
 * The kind of reset {@code reset.cgi} performs, sent as the value of its {@code reset} field.
 *
 * <p>Only the two orderly modes are offered. The device also accepts {@code fast} and {@code
 * extrafast} (an immediate {@code sysmgr_reboot}), which are deliberately not modelled here because
 * their exact behaviour is unverified — see {@code analysis/endpoints/ResetCGI.md}.
 */
public enum ResetMode implements WireValue {

  /**
   * An orderly reboot — the device restarts (the power service's {@code shutdownType} 0). Sent as
   * {@code reset=true} (the device's reboot value is the literal {@code true}, not {@code reset}).
   */
  REBOOT("true"),

  /** An orderly power-off — the device shuts down (the power service's {@code shutdownType} 1). */
  SHUTDOWN("shutdown");

  private final String wireName;

  ResetMode(String wireName) {
    this.wireName = wireName;
  }

  @Override
  public String wireName() {
    return wireName;
  }
}
