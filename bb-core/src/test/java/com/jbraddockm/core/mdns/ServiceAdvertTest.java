package com.jbraddockm.core.mdns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link ServiceAdvert}: a validated DNS-SD service to advertise. */
@DisplayName("ServiceAdvert: a service to advertise")
class ServiceAdvertTest {

  @Nested
  @DisplayName("validation")
  class Validation {

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "  "})
    @DisplayName("a blank type is refused")
    void blankType(String type) {
      assertThatThrownBy(() -> new ServiceAdvert(type, "Inst", "friendly", 4455, Map.of()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("a service type is required");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "  "})
    @DisplayName("a blank instance is refused")
    void blankInstance(String instance) {
      assertThatThrownBy(
              () -> new ServiceAdvert("_bp2p._tcp.local.", instance, "x", 4455, Map.of()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("an instance name is required");
    }

    @ParameterizedTest(name = "port {0}")
    @ValueSource(ints = {-1, 65536})
    @DisplayName("a port out of range is refused")
    void portOutOfRange(int port) {
      assertThatThrownBy(() -> new ServiceAdvert("_bp2p._tcp.local.", "Inst", "x", port, Map.of()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("port out of range");
    }

    @Test
    @DisplayName("keeps its own copy of the TXT entries")
    void copiesTxt() {
      var txt = new java.util.HashMap<String, String>();
      txt.put("fname", "PC");

      var advert = new ServiceAdvert("_bp2p._tcp.local.", "Inst", "x", 4455, txt);
      txt.clear();

      assertThat(advert.txt()).containsEntry("fname", "PC");
    }
  }

  @Test
  @DisplayName("maps to a JmDNS service info with the name, port and TXT")
  void toServiceInfo() {
    var advert =
        new ServiceAdvert(
            "_bp2p._tcp.local.", "Friendly_X_Y", "friendly", 4455, Map.of("fname", "PC"));

    var info = advert.toServiceInfo();

    assertThat(info.getName()).isEqualTo("Friendly_X_Y");
    assertThat(info.getPort()).isEqualTo(4455);
    assertThat(info.getPropertyString("fname")).isEqualTo("PC");
    assertThat(info.getType()).contains("_bp2p._tcp");
  }
}
