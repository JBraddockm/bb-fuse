package com.jbraddockm.link.endpoint.metadata;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.transport.cgi.WireValue;
import java.util.Arrays;

/**
 * The media collection a {@code metadata.cgi} query enumerates. {@link #SUMMARY} is the
 * whole-library summary, which the device selects by a {@code summary} flag rather than a {@code
 * type} value.
 */
public enum MediaType implements WireValue {

  /** Pictures. */
  PICTURE("picture"),

  /** Videos. */
  VIDEO("video"),

  /** Music tracks. */
  MUSIC("music"),

  /** Documents. */
  DOCUMENT("document"),

  /** Playlists. */
  PLAYLIST("playlist"),

  /** The entries of one playlist. */
  PLAYLIST_ENTRIES("playlistentries"),

  /** The entries across all playlists. */
  ALL_PLAYLIST_ENTRIES("allplaylistentries"),

  /** The whole-library summary. */
  SUMMARY("summary");

  private final String wireName;

  MediaType(String wireName) {
    this.wireName = wireName;
  }

  /**
   * Recognises a media type by its wire name.
   *
   * @param value the type as written in a request
   * @return the matching type
   * @throws InvalidArgumentException if the value is not a known type
   */
  public static MediaType of(String value) {
    return Arrays.stream(values())
        .filter(type -> type.wireName.equals(value))
        .findFirst()
        .orElseThrow(() -> new InvalidArgumentException("type", "is not a media type: " + value));
  }

  @Override
  public String wireName() {
    return wireName;
  }
}
