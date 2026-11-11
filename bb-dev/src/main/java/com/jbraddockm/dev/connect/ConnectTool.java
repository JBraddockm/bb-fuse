package com.jbraddockm.dev.connect;

import com.jbraddockm.core.json.Json;
import com.jbraddockm.dev.DevTool;
import com.jbraddockm.dev.Output;
import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * The {@code connect} namespace: starts the device's SSH daemon over the qconndoor protocol.
 *
 * <p>Usage: {@code bb-dev connect <host> [-sshPublicKey <path>] [-password <password>]}. The
 * command runs the handshake and then stays running to hold the session open, because the device
 * keeps its SSH daemon up only while the session lives.
 *
 * <p>The SSH public key defaults to Momentics' own {@code ~/.rim/bbt_id_rsa.pub}, so an existing
 * Momentics install connects with no key argument; when that key is absent it is generated there
 * once (see {@link SshIdentity}). A key given with {@code -sshPublicKey} is used as-is.
 */
public final class ConnectTool implements DevTool {

  private static final int USAGE_ERROR = 2;
  private static final int CONNECT_ERROR = 1;

  private static String value(List<String> arguments, int index) {
    return index < arguments.size() ? arguments.get(index) : "";
  }

  @Override
  public int run(List<String> arguments, Output output) {
    String host = null;
    String password = "";
    String keyPath = null;
    int index = 0;
    // The host is the leading positional argument.
    if (!arguments.isEmpty() && !arguments.getFirst().startsWith("-")) {
      host = arguments.getFirst();
      index = 1;
    }
    for (; index < arguments.size(); index++) {
      switch (arguments.get(index)) {
        case "-password" -> password = value(arguments, ++index);
        case "-sshPublicKey" -> keyPath = value(arguments, ++index);
        default -> {
          // Unknown options are ignored.
        }
      }
    }

    if (host == null) {
      return output.emit(
          USAGE_ERROR,
          "Usage: bb-dev connect <host> [-sshPublicKey <path>] [-password <password>]",
          Json.object("ok", false, "error", "a host is required"));
    }

    // No key given: use Momentics' own key, generating it there once if it is not present yet. An
    // explicit key is used as-is and never generated (a missing one is a mistake, not a request).
    var usingDefaultKey = keyPath == null;
    var keyFile = usingDefaultKey ? SshIdentity.defaultPublicKey() : Path.of(keyPath);
    SshIdentity.Resolved key;
    try {
      key = SshIdentity.resolve(keyFile, usingDefaultKey);
    } catch (IOException | GeneralSecurityException failure) {
      return output.emit(
          CONNECT_ERROR,
          "bb-dev connect: cannot read the SSH key " + keyFile + ": " + failure.getMessage(),
          Json.object("ok", false, "error", "cannot read the SSH key: " + failure.getMessage()));
    }
    if (key.generated()) {
      output.note(
          "Generated a new SSH key at " + key.privateKeyPath() + " (offering its public half)");
    }

    var resolvedHost = host;
    var generatedKey = key.generated();
    var keyLocation = key.publicKeyPath().toString();
    var client = new QconndoorClient(host, password, key.publicKey(), output::note);
    try {
      client.startDaemon(
          () ->
              output.emit(
                  0,
                  "SSH daemon started on "
                      + resolvedHost
                      + "; holding the session — stop this command to end it",
                  Json.object(
                      "ok",
                      true,
                      "host",
                      resolvedHost,
                      "sshd",
                      true,
                      "sshKey",
                      keyLocation,
                      "generatedKey",
                      generatedKey)));
      return 0;
    } catch (IOException | GeneralSecurityException failure) {
      return output.emit(
          CONNECT_ERROR,
          "bb-dev connect: " + failure.getMessage(),
          Json.object(
              "ok", false, "host", resolvedHost, "error", String.valueOf(failure.getMessage())));
    }
  }
}
