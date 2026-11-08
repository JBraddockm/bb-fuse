package com.jbraddockm.dev.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.dev.testing.Resources;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PackageInfo: parsing the device's Verify response")
class PackageInfoTest {

  @Test
  @DisplayName("reads the confirmed directory name, id and version")
  void readsIdentity() {
    var text = Resources.text(PackageInfoTest.class, "device--verify.txt");

    var info = PackageInfo.parse(List.of(text.split("\n", -1)));

    assertThat(info.name()).isEqualTo("com.example.TLSDeBug.testDev_le_TLSDeBugc09806e9");
    assertThat(info.id()).isEqualTo("testDev_le_TLSDeBugc09806e9");
    assertThat(info.version()).isEqualTo("1.0.0.1");
  }
}
