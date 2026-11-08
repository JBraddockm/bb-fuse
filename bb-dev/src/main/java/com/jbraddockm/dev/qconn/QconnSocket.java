package com.jbraddockm.dev.qconn;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A connection to a device's qconn agent (plain TCP on {@value #PORT}). qconn speaks a
 * line-oriented, Telnet-flavoured control protocol: the agent greets with {@code QCONN}, answers
 * {@code info} with its properties (endianness, service count), lists its services for {@code
 * service ?}, and switches the connection to a named service with {@code service <name>}. After a
 * service is selected the same socket carries that service's own protocol (for deploy, the {@code
 * file} service).
 *
 * <p>Responses are read a line at a time up to {@code \n}; control characters and Telnet option
 * sequences (IAC …) are skipped, and the client never negotiates options back.
 */
public final class QconnSocket implements AutoCloseable {

  /** The qconn agent's TCP port. */
  public static final int PORT = 8000;

  private static final int IAC = 255;
  private static final int WILL = 251;
  private static final int DONT = 254;
  private static final int SB = 250;
  private static final int SE = 240;
  private static final int NEWLINE = '\n';
  private static final int CONNECT_TIMEOUT_MILLIS = 10_000;

  private final Socket socket;
  private final InputStream input;
  private final OutputStream output;
  private final Map<String, String> info = new LinkedHashMap<>();
  private String service;

  private QconnSocket(Socket socket, InputStream input, OutputStream output) {
    this.socket = socket;
    this.input = input;
    this.output = output;
  }

  /** For tests: a connection over supplied streams, with no socket. */
  QconnSocket(InputStream input, OutputStream output) {
    this(null, input, output);
  }

  /**
   * Connects to the device's qconn agent on the default port and reads its greeting and info.
   *
   * @param host the device host or address
   * @return the connected agent
   * @throws IOException if the connection fails or the agent does not greet with {@code QCONN}
   */
  public static QconnSocket connect(String host) throws IOException {
    return connect(host, PORT);
  }

  /**
   * Connects to the device's qconn agent and reads its greeting and info.
   *
   * @param host the device host or address
   * @param port the qconn port
   * @return the connected agent
   * @throws IOException if the connection fails or the agent does not greet with {@code QCONN}
   */
  public static QconnSocket connect(String host, int port) throws IOException {
    var socket = new Socket();
    socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
    socket.setTcpNoDelay(true);
    socket.setKeepAlive(true);
    var agent = new QconnSocket(socket, socket.getInputStream(), socket.getOutputStream());
    var banner = agent.receiveResponse();
    if (!banner.equals("QCONN")) {
      socket.close();
      throw new IOException(
          "no qconn agent at " + host + ":" + port + " — greeting was \"" + banner + "\"");
    }
    agent.readInfo();
    return agent;
  }

  /** Reads the agent's {@code info} properties (endianness, service count, …). */
  void readInfo() throws IOException {
    sendCommand("info");
    for (var pair : receiveResponse().split("\\s+")) {
      var equals = pair.indexOf('=');
      if (equals > 0) {
        info.put(pair.substring(0, equals), pair.substring(equals + 1));
      }
    }
  }

  /**
   * Whether the target is little-endian, from the {@code info} {@code ENDIAN} property.
   *
   * @return {@code true} for a little-endian target
   */
  public boolean isLittleEndian() {
    return "l".equalsIgnoreCase(info.getOrDefault("ENDIAN", ""));
  }

  /**
   * The services the agent offers, for example {@code file} and {@code launch}.
   *
   * @return the service names
   * @throws IOException if the agent refuses the request
   */
  public List<String> services() throws IOException {
    sendCommand("service ?");
    var response = receiveResponse();
    if (response.toLowerCase().startsWith("error")) {
      throw new IOException("qconn refused the service list: " + response);
    }
    var colon = response.indexOf(':');
    var names = (colon < 0 ? response : response.substring(colon + 1)).trim();
    return names.isEmpty() ? List.of() : List.of(names.split("\\s+"));
  }

  /**
   * Switches the connection to a service; afterwards the socket carries that service's own
   * protocol.
   *
   * @param name the service name, for example {@code file}
   * @throws IOException if the agent does not accept the service
   */
  public void setService(String name) throws IOException {
    command("service " + name);
    this.service = name;
  }

  /** The service this connection is switched to, or {@code null}. */
  public String service() {
    return service;
  }

  /** The agent's raw input, for a selected service's protocol. */
  InputStream input() {
    return input;
  }

  /** The agent's raw output, for a selected service's protocol. */
  OutputStream output() {
    return output;
  }

  /** Writes one {@code \r\n}-terminated command line. */
  void sendCommand(String command) throws IOException {
    output.write((command + "\r\n").getBytes(StandardCharsets.US_ASCII));
    output.flush();
  }

  /** Reads one response line, skipping control characters and Telnet option sequences. */
  String receiveResponse() throws IOException {
    var line = new StringBuilder();
    for (var b = input.read(); b != -1 && b != NEWLINE; b = input.read()) {
      if (b == IAC) {
        skipTelnetOption();
      } else if (!Character.isISOControl((char) b)) {
        line.append((char) b);
      }
    }
    return line.toString();
  }

  /**
   * Consumes a Telnet option after an IAC: a sub-negotiation up to IAC SE, or a single option byte.
   */
  private void skipTelnetOption() throws IOException {
    var command = input.read();
    if (command == SB) {
      var previous = -1;
      for (var b = input.read(); b != -1; b = input.read()) {
        if (previous == IAC && b == SE) {
          return;
        }
        previous = b;
      }
    } else if (command >= WILL && command <= DONT) {
      input.read();
    }
  }

  /** Sends a command and requires an {@code ok} response. */
  void command(String command) throws IOException {
    sendCommand(command);
    var response = receiveResponse();
    var head = response.length() >= 2 ? response.substring(0, 2) : response;
    if (!head.equalsIgnoreCase("ok")) {
      throw new IOException("qconn command failed: " + command + " → " + response);
    }
  }

  @Override
  public void close() throws IOException {
    if (socket != null) {
      socket.close();
    }
  }
}
