package com.jbraddockm.link.endpoint.metadata;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.transport.cgi.WireValue;
import java.util.Arrays;

/**
 * How a music listing is grouped. {@link #NONE} is a flat track list and is not sent; the others
 * are the album / artist / genre browse pivots, valid only with {@link MediaType#MUSIC}.
 */
public enum MusicCollection implements WireValue {

  /** A flat track list — sent as no {@code collection} parameter. */
  NONE("none"),

  /** Grouped by album. */
  ALBUM("album"),

  /** Grouped by artist. */
  ARTIST("artist"),

  /** Grouped by genre. */
  GENRE("genre");

  private final String wireName;

  MusicCollection(String wireName) {
    this.wireName = wireName;
  }

  /**
   * Recognises a collection by its wire name.
   *
   * @param value the collection as written in a request
   * @return the matching collection
   * @throws InvalidArgumentException if the value is not a known collection
   */
  public static MusicCollection of(String value) {
    return Arrays.stream(values())
        .filter(collection -> collection.wireName.equals(value))
        .findFirst()
        .orElseThrow(
            () ->
                new InvalidArgumentException(
                    "collection", "is not album, artist or genre: " + value));
  }

  @Override
  public String wireName() {
    return wireName;
  }
}
