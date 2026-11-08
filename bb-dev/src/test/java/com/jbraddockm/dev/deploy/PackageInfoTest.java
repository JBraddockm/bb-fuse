package com.jbraddockm.dev.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.dev.testing.Resources;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("PackageInfo: parsing the device's confirmed package identity")
class PackageInfoTest {

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"device--verify.txt", "device--install.txt", "device--uninstall.txt"})
  @DisplayName("reads the directory name, id and version from verify, install and uninstall")
  void readsIdentity(String resource) {
    var text = Resources.text(PackageInfoTest.class, resource);

    var info = PackageInfo.parse(List.of(text.split("\n", -1)));

    assertThat(info.name()).isEqualTo("com.example.TLSDeBug.testDev_le_TLSDeBugc09806e9");
    assertThat(info.id()).isEqualTo("testDev_le_TLSDeBugc09806e9");
    assertThat(info.version()).isEqualTo("1.0.0.1");
  }
}
