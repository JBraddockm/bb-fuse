package com.jbraddockm.link.endpoint.metadata;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.transport.cgi.WireValue;
import java.util.Arrays;

/**
 * The storage area a {@code metadata.cgi} query reads. {@link #INTERNAL} is the default and is not
 * sent; the others name the SD card and the work perimeter.
 */
public enum MediaSource implements WireValue {

  /** Internal storage — the default, sent as no {@code source} parameter. */
  INTERNAL("internal"),

  /** The removable SD card. */
  SDCARD("sdcard"),

  /** The BlackBerry Balance work perimeter. */
  WORK("work");

  private final String wireName;

  MediaSource(String wireName) {
    this.wireName = wireName;
  }

  /**
   * Recognises a source by its wire name.
   *
   * @param value the source as written in a request
   * @return the matching source
   * @throws InvalidArgumentException if the value is not a known source
   */
  public static MediaSource of(String value) {
    return Arrays.stream(values())
        .filter(source -> source.wireName.equals(value))
        .findFirst()
        .orElseThrow(() -> new InvalidArgumentException("source", "is not a source: " + value));
  }

  @Override
  public String wireName() {
    return wireName;
  }
}
