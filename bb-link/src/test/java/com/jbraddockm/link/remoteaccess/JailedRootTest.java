package com.jbraddockm.link.remoteaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("The file-share jail")
class JailedRootTest {

  @TempDir Path root;

  @Nested
  @DisplayName("construction")
  class Construction {

    @Test
    @DisplayName("refuses a root that is not a directory")
    void refusesNonDirectory() throws IOException {
      var file = Files.writeString(root.resolve("f.txt"), "x");

      assertThatThrownBy(() -> JailedRoot.of(file)).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("resolving within the jail")
  class WithinJail {

    @Test
    @DisplayName("maps the root path and sub-paths to real paths under the root")
    void resolvesInside() throws IOException {
      Files.createDirectories(root.resolve("sub"));
      var jail = JailedRoot.of(root);

      assertThat(jail.resolve("/")).contains(root.toRealPath());
      assertThat(jail.resolve("/sub/file.txt"))
          .contains(root.toRealPath().resolve("sub").resolve("file.txt"));
    }

    @Test
    @DisplayName("round-trips a resolved path back to its href")
    void href() {
      var jail = JailedRoot.of(root);
      var path = jail.resolve("/a b/c.txt").orElseThrow();

      assertThat(jail.href(path, false)).isEqualTo("/a%20b/c.txt");
    }
  }

  @Nested
  @DisplayName("refusing anything that escapes the jail")
  class Escapes {

    @Test
    @DisplayName("refuses parent traversal, raw and percent-encoded")
    void refusesTraversal() {
      var jail = JailedRoot.of(root);

      assertThat(jail.resolve("/../secret")).isEmpty();
      assertThat(jail.resolve("/a/../../secret")).isEmpty();
      assertThat(jail.resolve("/%2e%2e/secret")).isEmpty();
    }

    @Test
    @DisplayName("refuses a path separator hidden inside a single segment")
    void refusesEncodedSeparator() {
      var jail = JailedRoot.of(root);

      // %2f decodes to '/', which must not be treated as a path separator inside a segment.
      assertThat(jail.resolve("/sub%2f..%2f..%2fetc")).isEmpty();
    }

    @Test
    @DisplayName("refuses a path that reaches outside through a symlink")
    void refusesSymlinkEscape() throws IOException {
      var outside = Files.createTempDirectory("jail-outside");
      Files.writeString(outside.resolve("secret.txt"), "top secret");
      Path link;
      try {
        link = Files.createSymbolicLink(root.resolve("link"), outside);
      } catch (UnsupportedOperationException | IOException unsupported) {
        assumeTrue(false, "symlinks not supported here");
        return;
      }
      assertThat(link).exists();
      var jail = JailedRoot.of(root);

      assertThat(jail.resolve("/link/secret.txt")).isEmpty();
      assertThat(jail.resolve("/link")).isEmpty();
    }
  }
}
