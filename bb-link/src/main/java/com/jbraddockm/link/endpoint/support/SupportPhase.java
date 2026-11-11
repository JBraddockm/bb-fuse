package com.jbraddockm.link.endpoint.support;

import com.jbraddockm.core.command.ProgressPhase;

/** The phases that the progress of a device-log collection names. */
enum SupportPhase implements ProgressPhase {

  /** The device gathering its logs with {@code quiplog} before the archive can be downloaded. */
  GATHERING("gathering"),

  /** Streaming the gathered archive from the device; the progress has the bytes. */
  DOWNLOADING("downloading");

  private final String wireName;

  SupportPhase(String wireName) {
    this.wireName = wireName;
  }

  @Override
  public String wireName() {
    return wireName;
  }
}
