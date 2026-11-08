package com.jbraddockm.dev.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.dev.testing.Resources;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DeviceInfo: parsing the device's List Device Info response")
class DeviceInfoTest {

  private static DeviceInfo golden() {
    var text = Resources.text(DeviceInfoTest.class, "device--list-device-info.txt");
    return DeviceInfo.parse(List.of(text.split("\n", -1)));
  }

  @Test
  @DisplayName("reads the plain key::value properties")
  void readsPlainProperties() {
    var info = golden();

    assertThat(info.get("device_os")).isEqualTo("BlackBerry 10");
    assertThat(info.get("modelfullname")).isEqualTo("BlackBerry Leap");
    assertThat(info.get("modelname")).isEqualTo("Leap");
    assertThat(info.get("scmbundle")).isEqualTo("10.3.3.3216");
    assertThat(info.get("devicepin")).isEqualTo("0xdeadbeef");
    assertThat(info.get("perimeter")).isEqualTo("personal");
  }

  @Test
  @DisplayName("drops the [n] prefix and the :b:/:n: type tags, keeping the value as text")
  void readsTaggedProperties() {
    var info = golden();

    assertThat(info.get("dev_mode_enabled")).isEqualTo("true");
    assertThat(info.get("debug_token_installed")).isEqualTo("false");
    assertThat(info.get("debug_token_validation_error_code")).isEqualTo("1");
  }

  @Test
  @DisplayName("trims the leading space the device leaves in some values")
  void trimsValues() {
    assertThat(golden().get("build_id")).isEqualTo("911537");
  }
}
