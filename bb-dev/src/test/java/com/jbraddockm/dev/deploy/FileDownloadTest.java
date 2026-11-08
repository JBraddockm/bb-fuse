package com.jbraddockm.dev.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.dev.testing.Resources;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FileDownload: parsing the device's Get File response")
class FileDownloadTest {

  @Test
  @DisplayName("reads the announced byte count and the file content")
  void readsContent() {
    var text = Resources.text(FileDownloadTest.class, "device--get-file.txt");

    var download = FileDownload.parse(List.of(text.split("\n", -1)));

    assertThat(download.size()).isEqualTo(18L);
    assertThat(download.content()).isEqualTo("bbdev mutate test");
  }
}
