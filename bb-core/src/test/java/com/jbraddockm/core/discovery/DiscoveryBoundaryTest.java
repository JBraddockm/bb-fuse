package com.jbraddockm.core.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Holds the line below device discovery. {@code core.xml} is pure JDK, and {@code core.discovery}
 * rests on the JDK and a few leaf packages of the core only — never bb-link, never a networking or
 * XML library — so the discovery surface stays something a standalone tool could depend on. The
 * test reads the sources and is skipped when they are not found.
 */
@DisplayName("The boundary of discovery: it rests on the JDK and the core's leaves")
class DiscoveryBoundaryTest {

  private static final String CORE = "com/jbraddockm/core/";

  private record Scan(int files, List<String> violations) {}

  private static Path sources() {
    Optional<Path> root =
        Stream.of(Path.of("src/main/java"), Path.of("bb-core/src/main/java"))
            .filter(path -> Files.isDirectory(path.resolve(CORE + "discovery")))
            .findFirst();
    assumeTrue(root.isPresent(), "the sources are not here, so the boundary was not checked");
    return root.orElseThrow();
  }

  private static Scan importsOutside(Path folder, List<String> allowed, String name)
      throws IOException {
    var violations = new ArrayList<String>();
    var files = 0;
    try (var found = Files.list(folder)) {
      for (var source : found.filter(path -> path.toString().endsWith(".java")).toList()) {
        files++;
        for (var line : Files.readAllLines(source)) {
          var statement = line.strip();
          if (statement.startsWith("import ") && !statement.startsWith("import static ")) {
            var imported = statement.substring("import ".length(), statement.length() - 1);
            if (allowed.stream().noneMatch(imported::startsWith)) {
              violations.add(name + "/" + source.getFileName() + " imports " + imported);
            }
          }
        }
      }
    }
    return new Scan(files, violations);
  }

  @Test
  @DisplayName("xml uses the JDK and nothing else, and the test saw its files")
  void xmlUsesTheJdkOnly() throws IOException {
    var scan = importsOutside(sources().resolve(CORE + "xml"), List.of("java.", "javax."), "xml");

    assertThat(scan.violations()).isEmpty();
    assertThat(scan.files()).as("the files of xml that were read").isGreaterThanOrEqualTo(4);
  }

  @Test
  @DisplayName("discovery uses the JDK, the core's json, command, xml and mdns, and nothing more")
  void discoveryRestsOnLeavesOnly() throws IOException {
    var allowed =
        List.of(
            "java.",
            "javax.",
            "com.jbraddockm.core.json.",
            "com.jbraddockm.core.command.",
            "com.jbraddockm.core.xml.",
            "com.jbraddockm.core.mdns.",
            "com.jbraddockm.core.discovery.");

    var scan = importsOutside(sources().resolve(CORE + "discovery"), allowed, "discovery");

    assertThat(scan.violations()).isEmpty();
    assertThat(scan.files()).as("the files of discovery that were read").isGreaterThanOrEqualTo(10);
  }
}
