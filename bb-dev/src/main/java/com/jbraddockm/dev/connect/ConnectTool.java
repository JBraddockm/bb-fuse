package com.jbraddockm.dev.connect;

import com.jbraddockm.core.json.Json;
import com.jbraddockm.dev.DevTool;
import com.jbraddockm.dev.Output;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * The {@code connect} namespace: starts the device's SSH daemon over the qconndoor protocol, the
 * job of the former bb-connect tool.
 *
 * <p>Usage: {@code bb-dev connect <host> -sshPublicKey <path> [-password <password>]}. The command
 * runs the handshake and then stays running to hold the session open, because the device keeps its
 * SSH daemon up only while the session lives.
 */
public final class ConnectTool implements DevTool {

  private static final int USAGE_ERROR = 2;
  private static final int CONNECT_ERROR = 1;

  @Override
  public int run(List<String> arguments, Output output) {
    String host = null;
    String password = "";
    String keyPath = null;
    for (int index = 0; index < arguments.size(); index++) {
      var argument = arguments.get(index);
      switch (argument) {
        case "-password" -> password = value(arguments, ++index);
        case "-sshPublicKey" -> keyPath = value(arguments, ++index);
        default -> {
          if (host == null && !argument.startsWith("-")) {
            host = argument;
          }
        }
      }
    }

    if (host == null || keyPath == null) {
      return output.emit(
          USAGE_ERROR,
          "Usage: bb-dev connect <host> -sshPublicKey <path> [-password <password>]",
          Json.object("ok", false, "error", "a host and -sshPublicKey are required"));
    }

    byte[] sshPublicKey;
    try {
      sshPublicKey = Files.readAllBytes(Path.of(keyPath));
    } catch (IOException failure) {
      return output.emit(
          CONNECT_ERROR,
          "bb-dev connect: cannot read the SSH key " + keyPath + ": " + failure.getMessage(),
          Json.object("ok", false, "error", "cannot read the SSH key: " + failure.getMessage()));
    }

    var resolvedHost = host;
    var client = new QconndoorClient(host, password, sshPublicKey, output::note);
    try {
      client.startDaemon(
          () ->
              output.emit(
                  0,
                  "SSH daemon started on "
                      + resolvedHost
                      + "; holding the session — stop this command to end it",
                  Json.object("ok", true, "host", resolvedHost, "sshd", true)));
      return 0;
    } catch (IOException | GeneralSecurityException failure) {
      return output.emit(
          CONNECT_ERROR,
          "bb-dev connect: " + failure.getMessage(),
          Json.object(
              "ok", false, "host", resolvedHost, "error", String.valueOf(failure.getMessage())));
    }
  }

  private static String value(List<String> arguments, int index) {
    return index < arguments.size() ? arguments.get(index) : "";
  }
}
