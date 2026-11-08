package com.jbraddockm.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Keeps bb-dev's packages layered. The feature packages — {@code connect}, {@code deploy} and
 * {@code packaging} — are device-management domains that do not reach into one another; {@code
 * qconn} is the shared device transport they sit on and never reaches back up into a feature; and
 * inside {@code packaging} the dependencies point one way, {@code descriptor} ← {@code internal} ←
 * the front-ends. The root {@code com.jbraddockm.dev} and {@code daemon} are the composition and
 * may depend on several features, so they are exempt. The test reads the sources and is skipped
 * when they are not found.
 */
@DisplayName("Architecture boundaries: features stay isolated and packaging stays layered")
class ArchitectureBoundaryTest {

  private static final String BASE = "com.jbraddockm.dev.";
  private static final Set<String> FEATURES = Set.of("connect", "deploy", "packaging");

  private static Path mainRoot() {
    Optional<Path> root =
        Stream.of(Path.of("src/main/java"), Path.of("bb-dev/src/main/java"))
            .filter(path -> Files.isDirectory(path.resolve("com/jbraddockm/dev")))
            .findFirst();
    assumeTrue(root.isPresent(), "the sources are not here, so the boundaries were not checked");
    return root.orElseThrow();
  }

  private static List<JavaFile> sources() {
    var dev = mainRoot().resolve("com/jbraddockm/dev");
    try (var paths = Files.walk(dev)) {
      return paths
          .filter(path -> path.toString().endsWith(".java"))
          .filter(path -> !path.getFileName().toString().equals("package-info.java"))
          .map(ArchitectureBoundaryTest::read)
          .toList();
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  private static JavaFile read(Path path) {
    var pkg = "";
    var imports = new ArrayList<String>();
    try {
      for (var line : Files.readAllLines(path)) {
        var statement = line.strip();
        if (statement.startsWith("package ")) {
          pkg = statement.substring("package ".length(), statement.length() - 1);
        } else if (statement.startsWith("import ") && !statement.startsWith("import static ")) {
          imports.add(statement.substring("import ".length(), statement.length() - 1));
        }
      }
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
    return new JavaFile(pkg, path.getFileName().toString(), imports);
  }

  /** The feature a package belongs to, or {@code null} for qconn, the daemon and the root. */
  private static String feature(String pkg) {
    if (!pkg.startsWith(BASE)) {
      return null;
    }
    var segment = pkg.substring(BASE.length()).split("\\.", 2)[0];
    return FEATURES.contains(segment) ? segment : null;
  }

  /**
   * The packaging layer of a package or of an imported class: {@code descriptor}=0, {@code
   * internal}=1, the front-ends=2.
   */
  private static int packagingLayer(String packageOrClass) {
    if (inPackage(packageOrClass, BASE + "packaging.descriptor")) {
      return 0;
    }
    if (inPackage(packageOrClass, BASE + "packaging.internal")) {
      return 1;
    }
    return 2;
  }

  private static boolean inPackage(String packageOrClass, String pkg) {
    return packageOrClass.equals(pkg) || packageOrClass.startsWith(pkg + ".");
  }

  @Test
  @DisplayName("a feature package does not import another feature package")
  void featuresDoNotImportEachOther() {
    var violations =
        sources().stream()
            .flatMap(
                file -> {
                  var from = feature(file.pkg());
                  if (from == null) {
                    return Stream.empty();
                  }
                  return file.imports().stream()
                      .filter(
                          imported -> feature(imported) != null && !from.equals(feature(imported)))
                      .map(imported -> file.name() + ": " + from + " -> " + feature(imported));
                })
            .toList();

    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("the qconn transport never imports a feature")
  void qconnStaysBelowFeatures() {
    var violations =
        sources().stream()
            .filter(file -> file.pkg().equals(BASE + "qconn"))
            .flatMap(
                file ->
                    file.imports().stream()
                        .filter(imported -> feature(imported) != null)
                        .map(imported -> file.name() + " imports " + imported))
            .toList();

    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("inside packaging the dependencies point down: descriptor <- internal <- front-ends")
  void packagingLayersPointDownward() {
    var violations =
        sources().stream()
            .filter(file -> "packaging".equals(feature(file.pkg())))
            .flatMap(
                file -> {
                  var here = packagingLayer(file.pkg());
                  return file.imports().stream()
                      .filter(imported -> "packaging".equals(feature(imported)))
                      .filter(imported -> packagingLayer(imported) > here)
                      .map(imported -> file.name() + ": " + file.pkg() + " -> " + imported);
                })
            .toList();

    assertThat(violations).isEmpty();
  }

  /**
   * The deploy layer of a package or imported class: {@code sync}=0, the engine and front-ends=1.
   */
  private static int deployLayer(String packageOrClass) {
    return inPackage(packageOrClass, BASE + "deploy.sync") ? 0 : 1;
  }

  @Test
  @DisplayName("inside deploy the sync sub-feature does not reach up into the engine or front-ends")
  void deployLayersPointDownward() {
    var violations =
        sources().stream()
            .filter(file -> "deploy".equals(feature(file.pkg())))
            .flatMap(
                file -> {
                  var here = deployLayer(file.pkg());
                  return file.imports().stream()
                      .filter(imported -> "deploy".equals(feature(imported)))
                      .filter(imported -> deployLayer(imported) > here)
                      .map(imported -> file.name() + ": " + file.pkg() + " -> " + imported);
                })
            .toList();

    assertThat(violations).isEmpty();
  }

  private record JavaFile(String pkg, String name, List<String> imports) {}
}
