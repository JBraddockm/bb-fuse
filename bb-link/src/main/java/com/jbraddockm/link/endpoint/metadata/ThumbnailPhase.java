package com.jbraddockm.link.endpoint.metadata;

import com.jbraddockm.core.command.ProgressPhase;

/** The phase that the progress of a thumbnail download names. */
enum ThumbnailPhase implements ProgressPhase {

  /** Streaming a thumbnail image from the device to a file. */
  FETCHING("fetching");

  private final String wireName;

  ThumbnailPhase(String wireName) {
    this.wireName = wireName;
  }

  @Override
  public String wireName() {
    return wireName;
  }
}
