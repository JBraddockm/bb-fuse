package com.jbraddockm.dev.qconn;

/** Throwaway probe: connect to a device's qconn agent and print its endianness and services. */
final class QconnProbeMain {

  private QconnProbeMain() {}

  static void main(String[] args) throws Exception {
    var host = args.length > 0 ? args[0] : "192.168.1.118";
    try (var qconn = QconnSocket.connect(host)) {
      IO.println("connected to qconn at " + host + ":" + QconnSocket.PORT);
      IO.println("little-endian: " + qconn.isLittleEndian());
      IO.println("services: " + qconn.services());
    }
  }
}
