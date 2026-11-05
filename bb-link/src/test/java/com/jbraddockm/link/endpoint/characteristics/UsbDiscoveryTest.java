package com.jbraddockm.link.endpoint.characteristics;


import static com.jbraddockm.link.testing.Steps.failed;
import static com.jbraddockm.link.testing.Steps.finished;
import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.link.testing.Resources;
import com.jbraddockm.link.testing.ScriptedServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link UsbDiscovery}: reading discovery.cgi from the plain :80 listener over USB. */
@DisplayName("UsbDiscovery: reading discovery.cgi over plain :80")
class UsbDiscoveryTest {

  private static String urlOf(ScriptedServer server) {
    return "http://127.0.0.1:" + server.port() + "/cgi-bin/discovery.cgi";
  }

  @Test
  @DisplayName("GETs /cgi-bin/discovery.cgi and parses the characteristics, including the PIN")
  void readsCharacteristics() throws Exception {
    try (var server = new ScriptedServer()) {
      server.answer(
          200,
          Resources.text(UsbDiscoveryTest.class, "discovery--real.xml"),
          "Content-Type",
          "text/xml");

      var characteristics = finished(UsbDiscovery.readUrl(urlOf(server)));

      assertThat(characteristics.pin()).isEqualTo("2C1E7583");
      assertThat(characteristics.guid()).isEqualTo("EDunxOMuoNpdOlvSIBPJD1dfcMs=");
      assertThat(characteristics.modelName()).isEqualTo("STR100-1");
      var request = server.requests().getFirst();
      assertThat(request.method()).isEqualTo("GET");
      assertThat(request.target()).isEqualTo("/cgi-bin/discovery.cgi");
    }
  }

  @Test
  @DisplayName("reports a failure, naming port 80, when the device answers a non-2xx status")
  void nonSuccessStatus() throws Exception {
    try (var server = new ScriptedServer()) {
      server.answer(500, "");

      var error = failed(UsbDiscovery.readUrl(urlOf(server)));

      assertThat(error.message()).contains("port 80").contains("500");
    }
  }
}
