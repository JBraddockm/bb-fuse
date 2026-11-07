package com.jbraddockm.link.endpoint.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.jbraddockm.core.error.InvalidArgumentException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for the metadata query enums: their wire names and how a value is parsed. */
@DisplayName("Metadata query enums: wire names and parsing")
class MediaQueryEnumsTest {

  @Nested
  @DisplayName("media type")
  class Type {

    @Test
    @DisplayName("maps each value to its wire name and back")
    void parses() {
      assertThat(MediaType.PICTURE.wireName()).isEqualTo("picture");
      assertThat(MediaType.ALL_PLAYLIST_ENTRIES.wireName()).isEqualTo("allplaylistentries");
      assertThat(MediaType.of("music")).isEqualTo(MediaType.MUSIC);
      assertThat(MediaType.of("summary")).isEqualTo(MediaType.SUMMARY);
    }

    @Test
    @DisplayName("rejects an unknown type")
    void rejectsUnknown() {
      assertThatExceptionOfType(InvalidArgumentException.class)
          .isThrownBy(() -> MediaType.of("tasks"));
    }
  }

  @Nested
  @DisplayName("source, order and collection")
  class Others {

    @Test
    @DisplayName("parse their values and reject unknown ones")
    void parse() {
      assertThat(MediaSource.of("sdcard")).isEqualTo(MediaSource.SDCARD);
      assertThat(SortOrder.of("desc")).isEqualTo(SortOrder.DESC);
      assertThat(MusicCollection.of("genre")).isEqualTo(MusicCollection.GENRE);

      assertThatExceptionOfType(InvalidArgumentException.class)
          .isThrownBy(() -> MediaSource.of("cloud"));
      assertThatExceptionOfType(InvalidArgumentException.class)
          .isThrownBy(() -> SortOrder.of("sideways"));
      assertThatExceptionOfType(InvalidArgumentException.class)
          .isThrownBy(() -> MusicCollection.of("mood"));
    }
  }
}
