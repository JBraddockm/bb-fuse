package com.jbraddockm.dev;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Namespace: routing the first command-line word")
class NamespaceTest {

  @Test
  @DisplayName("resolves each known namespace by its wire name")
  void resolvesKnownNamespaces() {
    assertThat(Namespace.byName("connect")).contains(Namespace.CONNECT);
    assertThat(Namespace.byName("deploy")).contains(Namespace.DEPLOY);
    assertThat(Namespace.byName("package")).contains(Namespace.PACKAGE);
    assertThat(Namespace.byName("debug")).contains(Namespace.DEBUG);
    assertThat(Namespace.byName("pull")).contains(Namespace.PULL);
  }

  @Test
  @DisplayName("returns empty for an unknown namespace")
  void rejectsAnUnknownNamespace() {
    assertThat(Namespace.byName("wibble")).isEmpty();
  }
}
