package com.jbraddockm.core.bar;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("The entry-point attribute parser")
class EntryPointParserTest {

  @Nested
  @DisplayName("reading an Entry-Point command's binary")
  class Binary {

    @Test
    @DisplayName("skips the environment assignments and drops the app/ sandbox prefix")
    void realCommand() {
      var binary =
          EntryPointParser.binaryAssetName(
              "LD_LIBRARY_PATH=app/native/lib:/usr/lib/qt4/lib CASCADES_THEME=dark"
                  + " app/native/TLSDeBug");

      assertThat(binary).isEqualTo("native/TLSDeBug");
    }

    @Test
    @DisplayName("takes the launch token from after its first path separator")
    void bareCommand() {
      assertThat(EntryPointParser.binaryAssetName("app/native/App")).isEqualTo("native/App");
    }

    @Test
    @DisplayName("keeps a backslash-escaped space as part of the path")
    void escapedSpace() {
      assertThat(EntryPointParser.binaryAssetName("app/my\\ dir/App")).isEqualTo("my\\ dir/App");
    }

    @Test
    @DisplayName("returns null when the command is only environment assignments")
    void onlyAssignments() {
      assertThat(EntryPointParser.binaryAssetName("A=app/x B=c")).isNull();
    }
  }

  @Nested
  @DisplayName("reading an image attribute's assets")
  class Images {

    @Test
    @DisplayName("returns a single unqualified name unchanged")
    void single() {
      assertThat(EntryPointParser.imageAssetNames("native/icon.png"))
          .containsExactly("native/icon.png");
    }

    @Test
    @DisplayName("splits a colon list and strips each size qualifier")
    void qualifiedList() {
      assertThat(
              EntryPointParser.imageAssetNames(
                  "{144x144}native/icon-144.png:{96x96}native/icon-96.png"))
          .containsExactly("native/icon-144.png", "native/icon-96.png");
    }
  }
}
