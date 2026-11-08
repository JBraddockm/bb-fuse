package com.jbraddockm.dev.qconn;

import java.nio.charset.StandardCharsets;

/**
 * Throwaway probe: push a file into the device over the qconn file service (the sync write path).
 */
final class FileServiceProbeMain {

  private FileServiceProbeMain() {}

  static void main(String[] args) throws Exception {
    var host = args.length > 0 ? args[0] : "192.168.1.118";
    var path = args.length > 1 ? args[1] : "/tmp/bbdev-p2.txt";
    var content = "hello from bb-dev qconn file service\n".getBytes(StandardCharsets.US_ASCII);
    try (var qconn = QconnSocket.connect(host)) {
      var files = TargetFileService.on(qconn);
      IO.println("service=" + qconn.service());
      files.writeFile(path, content);
      IO.println("wrote " + content.length + " bytes to " + path + " — ok");
    }
  }
}
