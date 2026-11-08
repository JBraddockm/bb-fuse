package com.jbraddockm.dev.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.dev.testing.Resources;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InstalledApps: parsing the device's List response")
class InstalledAppsTest {

  private static InstalledApps golden() {
    var text = Resources.text(InstalledAppsTest.class, "device--list-installed-apps.txt");
    return InstalledApps.parse(List.of(text.split("\n", -1)));
  }

  private static InstalledApps.App find(String id) {
    return golden().apps().stream().filter(app -> app.id().equals(id)).findFirst().orElseThrow();
  }

  @Test
  @DisplayName("reads every listed application and skips the Info and marker lines")
  void readsEveryApp() {
    assertThat(golden().apps()).hasSize(233);
  }

  @Test
  @DisplayName("parses the name, id, version and size of an application")
  void parsesFields() {
    var app = find("gYABgFEqMSpja5S6kL-DWpWmq50");

    assertThat(app.name()).isEqualTo("com.example.Term49.gYABgFEqMSpja5S6kL.DWpWmq50");
    assertThat(app.version()).isEqualTo("1.0.1.1");
    assertThat(app.size()).isEqualTo(6_879_647L);
    assertThat(app.source()).isEmpty();
  }

  @Test
  @DisplayName("keeps the source, stripping the source:: prefix the device uses for some apps")
  void parsesSource() {
    assertThat(find("andrBBCxSkTFXlWLsUUBuXB2ukQ").source()).isEqualTo("apk");
    assertThat(find("gYABgNSvaLtte_snIx7wjRsOcyM").source()).isEqualTo("websl");
  }
}
