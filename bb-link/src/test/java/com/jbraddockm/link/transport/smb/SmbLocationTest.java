package com.jbraddockm.link.transport.smb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Tests for {@link SmbLocation}: a validated, normalised path on a device share. */
@DisplayName("SmbLocation: a path on a device share")
class SmbLocationTest {

  @Nested
  @DisplayName("normalising the path")
  class Normalising {

    @ParameterizedTest(name = "\"{0}\" becomes \"{1}\"")
    @CsvSource({
      "documents, documents",
      "/documents, documents",
      "/documents/, documents",
      "documents/report.txt, documents/report.txt",
      "a//b, a/b",
    })
    @DisplayName("strips leading, trailing and repeated separators")
    void stripsSeparators(String input, String expected) {
      assertThat(new SmbLocation("media", input).path()).isEqualTo(expected);
    }

    @Test
    @DisplayName("converts backslashes to forward slashes")
    void convertsBackslashes() {
      assertThat(new SmbLocation("media", "a\\b\\c").path()).isEqualTo("a/b/c");
    }

    @Test
    @DisplayName("treats the empty string and a lone separator as the share root")
    void emptyIsRoot() {
      assertThat(new SmbLocation("media", "").path()).isEmpty();
      assertThat(new SmbLocation("media", "/").path()).isEmpty();
    }
  }

  @Nested
  @DisplayName("rejecting bad input")
  class Rejecting {

    @Test
    @DisplayName("rejects a '..' segment so a location cannot escape its share")
    void rejectsParentSegment() {
      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(() -> new SmbLocation("media", "documents/../secrets"));
    }

    @Test
    @DisplayName("rejects a '.' segment")
    void rejectsCurrentSegment() {
      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(() -> new SmbLocation("media", "documents/./report.txt"));
    }

    @Test
    @DisplayName("rejects a blank share")
    void rejectsBlankShare() {
      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(() -> new SmbLocation(" ", "documents"));
    }

    @Test
    @DisplayName("rejects a null share or path")
    void rejectsNull() {
      assertThatNullPointerException().isThrownBy(() -> new SmbLocation(null, "documents"));
      assertThatNullPointerException().isThrownBy(() -> new SmbLocation("media", null));
    }
  }

  @Nested
  @DisplayName("deriving locations")
  class Deriving {

    @Test
    @DisplayName("root is the share with an empty path")
    void root() {
      var root = SmbLocation.root("media");

      assertThat(root.share()).isEqualTo("media");
      assertThat(root.path()).isEmpty();
      assertThat(root.name()).isEmpty();
    }

    @Test
    @DisplayName("child appends a segment, and name is the last segment")
    void child() {
      var location = SmbLocation.root("media").child("documents").child("report.txt");

      assertThat(location.path()).isEqualTo("documents/report.txt");
      assertThat(location.name()).isEqualTo("report.txt");
    }

    @Test
    @DisplayName("child rejects a name that contains a separator")
    void childRejectsSeparator() {
      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(() -> SmbLocation.root("media").child("documents/report.txt"));
    }
  }
}
