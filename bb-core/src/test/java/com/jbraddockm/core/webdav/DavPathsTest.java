package com.jbraddockm.core.webdav;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("WebDAV path encoding")
class DavPathsTest {

  @ParameterizedTest(name = "round-trips \"{0}\"")
  @ValueSource(strings = {"plain.txt", "a b.txt", "n  spaces", "café.pdf", "a+b&c=d", "100% done"})
  @DisplayName("encodes a segment and decodes it back to the original")
  void roundTripsSegments(String name) {
    var encoded = DavPaths.encodeSegment(name);

    assertThat(DavPaths.decode(encoded)).isEqualTo(name);
  }

  @Test
  @DisplayName("keeps unreserved characters and percent-encodes the rest as upper-case UTF-8")
  void encodesTheReservedSet() {
    assertThat(DavPaths.encodeSegment("a-b_c.d~e")).isEqualTo("a-b_c.d~e");
    assertThat(DavPaths.encodeSegment("a b")).isEqualTo("a%20b");
    assertThat(DavPaths.encodeSegment("/")).isEqualTo("%2F");
  }

  @Test
  @DisplayName("splits a path into decoded, non-empty segments and drops a trailing slash")
  void splitsSegments() {
    assertThat(DavPaths.segments("/a%20b/c.txt")).containsExactly("a b", "c.txt");
    assertThat(DavPaths.segments("/")).isEmpty();
    assertThat(DavPaths.segments("/dir/")).containsExactly("dir");
  }

  @Test
  @DisplayName("builds a collection href with a trailing slash and a file href without")
  void buildsHrefs() {
    assertThat(DavPaths.href(List.of("a b", "c.txt"), false)).isEqualTo("/a%20b/c.txt");
    assertThat(DavPaths.href(List.of("sub"), true)).isEqualTo("/sub/");
    assertThat(DavPaths.href(List.of(), true)).isEqualTo("/");
  }
}
