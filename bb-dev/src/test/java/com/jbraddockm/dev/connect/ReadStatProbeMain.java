package com.jbraddockm.dev.connect;

import com.jbraddockm.dev.qconn.QconnSocket;
import com.jbraddockm.dev.qconn.TargetFileService;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Throwaway probe: brings the device services up over qconndoor, then round-trips a file through
 * the qconn file service — write, stat, read back — to live-verify the read/stat path.
 */
final class ReadStatProbeMain {

  private ReadStatProbeMain() {}

  static void main(String[] args) throws Exception {
    var host = args.length > 0 ? args[0] : "192.168.1.118";
    var password = args.length > 1 ? args[1] : "12345";
    var keyPath = args.length > 2 ? args[2] : "/Users/m/.ssh/bb10_rsa.pub";
    var path = "/tmp/bbdev-readstat.txt";
    var content =
        ("round-trip " + System.currentTimeMillis() + "\n").getBytes(StandardCharsets.US_ASCII);

    var key = Files.readAllBytes(Path.of(keyPath));
    var connected = new CountDownLatch(1);
    var client = new QconndoorClient(host, password, key, IO::println);
    var session =
        new Thread(
            () -> {
              try {
                client.startDaemon(connected::countDown);
              } catch (Exception failure) {
                failure.printStackTrace();
              }
            });
    session.setDaemon(true);
    session.start();

    if (!connected.await(20, TimeUnit.SECONDS)) {
      IO.println("services did not come up");
      return;
    }
    waitForPort(host, 8000);

    try (var qconn = QconnSocket.connect(host)) {
      var files = TargetFileService.on(qconn);
      IO.println("service=" + qconn.service() + " littleEndian=" + qconn.isLittleEndian());

      files.writeFile(path, content);
      IO.println("wrote " + content.length + " bytes to " + path);

      var stat = files.stat(path);
      IO.println(
          "stat size="
              + stat.size()
              + " perms="
              + Integer.toOctalString(stat.permissions())
              + " regular="
              + stat.isRegularFile()
              + " dir="
              + stat.isDirectory()
              + " mtimeMillis="
              + stat.modifiedMillis());

      var back = files.readFile(path);
      IO.println(
          "read " + back.length + " bytes: " + new String(back, StandardCharsets.US_ASCII).trim());
      IO.println(
          "size-matches-stat="
              + (stat.size() == content.length)
              + " bytes-match="
              + Arrays.equals(content, back));
    }
  }

  private static void waitForPort(String host, int port) throws InterruptedException {
    for (var attempt = 0; attempt < 40; attempt++) {
      try (var socket = new Socket()) {
        socket.connect(new InetSocketAddress(host, port), 500);
        return;
      } catch (Exception retry) {
        Thread.sleep(250);
      }
    }
  }
}
