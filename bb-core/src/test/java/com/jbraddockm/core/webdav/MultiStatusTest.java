package com.jbraddockm.core.webdav;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("The DAV:multistatus document")
class MultiStatusTest {

  private static final Instant WHEN = Instant.parse("2026-11-11T22:00:00Z");

  private static final DavResource DIR = new DavResource("/", true, 0, WHEN, "share", null);
  private static final DavResource FILE =
      new DavResource("/a%20b.txt", false, 42, WHEN, "a b.txt", "text/plain");

  @Nested
  @DisplayName("building")
  class Building {

    @Test
    @DisplayName("marks a collection with resourcetype and omits content length")
    void collection() {
      var xml = MultiStatus.build(List.of(DIR));

      assertThat(xml).contains("<D:multistatus xmlns:D=\"DAV:\">");
      assertThat(xml).contains("<D:resourcetype><D:collection/></D:resourcetype>");
      assertThat(xml).doesNotContain("getcontentlength");
      assertThat(xml).contains("<D:href>/</D:href>");
    }

    @Test
    @DisplayName("gives a file its length and content type and escapes XML in names")
    void file() {
      var named = new DavResource("/x.txt", false, 5, WHEN, "a<b>&\"c", "text/plain");

      var xml = MultiStatus.build(List.of(named));

      assertThat(xml).contains("<D:getcontentlength>5</D:getcontentlength>");
      assertThat(xml).contains("<D:getcontenttype>text/plain</D:getcontenttype>");
      assertThat(xml).contains("a&lt;b&gt;&amp;&quot;c");
    }
  }

  @Nested
  @DisplayName("round-tripping (server build -> client parse)")
  class RoundTrip {

    @Test
    @DisplayName("parses back the resources a server built")
    void parsesBack() {
      var xml = MultiStatus.build(List.of(DIR, FILE));

      var parsed = MultiStatus.parse(xml);

      assertThat(parsed).hasSize(2);
      var dir = parsed.getFirst();
      assertThat(dir.collection()).isTrue();
      assertThat(dir.href()).isEqualTo("/");
      var file = parsed.getLast();
      assertThat(file.collection()).isFalse();
      assertThat(file.href()).isEqualTo("/a%20b.txt");
      assertThat(file.displayName()).isEqualTo("a b.txt");
      assertThat(file.contentLength()).isEqualTo(42);
      assertThat(file.contentType()).isEqualTo("text/plain");
      assertThat(file.lastModified()).isEqualTo(WHEN.truncatedTo(ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("is namespace-prefix-agnostic (handles D:, d: and no prefix)")
    void prefixAgnostic() {
      var withD = MultiStatus.build(List.of(FILE));
      var lower = withD.replace("D:", "d:").replace("xmlns:D", "xmlns:d");
      var none = withD.replace("D:", "").replace("xmlns:=\"DAV:\"", "xmlns=\"DAV:\"");

      assertThat(MultiStatus.parse(lower))
          .singleElement()
          .extracting(DavResource::displayName)
          .isEqualTo("a b.txt");
      assertThat(MultiStatus.parse(none))
          .singleElement()
          .extracting(DavResource::displayName)
          .isEqualTo("a b.txt");
    }
  }
}
