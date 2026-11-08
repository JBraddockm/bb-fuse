package com.jbraddockm.dev.deploy;

import com.jbraddockm.core.bar.BarInspector;
import com.jbraddockm.core.bar.InvalidBarException;
import com.jbraddockm.core.device.DeviceHttp;
import com.jbraddockm.core.device.DeviceLogin;
import com.jbraddockm.core.device.JdkDeviceHttp;
import com.jbraddockm.core.device.LoginException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.net.ssl.KeyManager;

/**
 * Speaks the device's {@code appInstaller.cgi} protocol directly: it logs in the way the device's
 * web tools do ({@link DeviceLogin}), posts each command as a {@code multipart/form-data} request
 * ({@link MultipartBody}), and reads the answer line by line. It logs in on the first command and
 * keeps the session for the next; the caller asks it to log in again when the device stops
 * accepting the login.
 *
 * <p>The connection and the login are shared with bb-core, so the only thing here is the deploy
 * protocol: the verbs, their parts, and the line-oriented answer with its {@code result::success}
 * and {@code result::failure} sentinels.
 */
public final class AppInstallerEngine {

  static final String ENDPOINT = "/cgi-bin/appInstaller.cgi";
  private static final String SENDING_REQUEST = "Info: Sending request: ";

  private final DeviceHttp http;
  private final Poster poster;
  private final BarIdentityReader bars;
  private final String password;
  private final Runnable closer;
  private boolean loggedIn;

  AppInstallerEngine(
      DeviceHttp http, Poster poster, BarIdentityReader bars, String password, Runnable closer) {
    this.http = http;
    this.poster = poster;
    this.bars = bars;
    this.password = password;
    this.closer = closer;
  }

  /**
   * Opens an engine for a device over a password login.
   *
   * @param host the device host or address
   * @param port the HTTPS port (8443 over the tunnel, 443 direct)
   * @param password the device password
   * @param identity the client identity to offer, or {@code null} for a password login
   * @return the engine; nothing is sent until the first command
   */
  public static AppInstallerEngine forDevice(
      String host, int port, String password, KeyManager[] identity) {
    var connection = new JdkDeviceHttp(host, port, identity);
    Poster poster =
        body -> {
          var response =
              connection.post(
                  ENDPOINT,
                  body.contentType(),
                  body.publisher(),
                  HttpResponse.BodyHandlers.ofInputStream());
          return new Poster.Answer(
              response.statusCode(),
              response.headers().firstValue("Content-Type").orElse(null),
              response.body());
        };
    BarIdentityReader bars =
        bar -> {
          try {
            var info = BarInspector.inspect(bar);
            return new Identity(info.packageId(), info.packageName());
          } catch (InvalidBarException invalid) {
            throw new IOException("the bar manifest could not be read: " + invalid.getMessage());
          }
        };
    return new AppInstallerEngine(connection, poster, bars, password, connection::close);
  }

  /**
   * Runs a command and returns every line the device sent, in order, the announced request line
   * first. Each line is also handed to {@code lineHandler} as it arrives.
   *
   * @param command the command to run
   * @param lineHandler receives each line as it is read
   * @return the lines, the announced request line first
   * @throws LoginException if the device rejects the login
   * @throws DeviceReportException if the device reports the command failed
   * @throws IOException if the request or the response fails
   */
  public List<String> run(DeployCommand command, Consumer<String> lineHandler)
      throws LoginException, DeviceReportException, IOException {
    ensureLoggedIn();
    var request = requestFor(command);
    var lines = new ArrayList<String>();
    emit(SENDING_REQUEST + request.logName(), lineHandler, lines);

    var answer = poster.post(request.body());
    try (var body = answer.body()) {
      if (answer.status() < 200 || answer.status() >= 300) {
        throw new IOException("the device answered HTTP " + answer.status());
      }
      var reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
      for (var line = reader.readLine(); line != null; line = reader.readLine()) {
        emit(line, lineHandler, lines);
      }
    }

    var error = toolErrorLine(lines);
    if (error.isPresent()) {
      throw new DeviceReportException(error.get());
    }
    return List.copyOf(lines);
  }

  /** Forgets the login, so the next command logs in again. */
  public void forgetLogin() {
    loggedIn = false;
  }

  /** Closes the connection to the device. */
  public void close() {
    closer.run();
  }

  private void ensureLoggedIn() throws LoginException, IOException {
    if (!loggedIn) {
      DeviceLogin.authenticate(http, password);
      loggedIn = true;
    }
  }

  private static void emit(String line, Consumer<String> lineHandler, List<String> lines) {
    lines.add(line);
    lineHandler.accept(line);
  }

  private Request requestFor(DeployCommand command) throws IOException {
    return switch (command) {
      case DeployCommand.TestConnection _ ->
          new Request("TEST", MultipartBody.command("test").build());
      case DeployCommand.ListInstalledApps _ ->
          new Request("LIST", MultipartBody.command("List").build());
      case DeployCommand.ListDeviceInfo _ ->
          new Request("DEVICE_INFO", MultipartBody.command("List Device Info").build());
      case DeployCommand.InstallApp install -> installRequest(install);
      case DeployCommand.LaunchApp(var app) ->
          new Request("LAUNCH", packaged(MultipartBody.command("Launch"), app).build());
      case DeployCommand.TerminateApp(var app) ->
          new Request("TERMINATE", packaged(MultipartBody.command("Terminate"), app).build());
      case DeployCommand.IsAppRunning(var app) ->
          new Request("IS_RUNNING", packaged(MultipartBody.command("Is Running"), app).build());
      case DeployCommand.UninstallApp(var app) ->
          new Request("UNINSTALL", packaged(MultipartBody.command("Uninstall"), app).build());
      case DeployCommand.VerifyApp(var app) ->
          new Request("VERIFY", packaged(MultipartBody.command("Verify"), app).build());
      case DeployCommand.GetFile(var app, var path, var _) ->
          new Request(
              "GET_FILE", packaged(MultipartBody.command("Get File"), app).assetPath(path).build());
      case DeployCommand.PutFile(var app, var local, var path) ->
          new Request(
              "PUT_FILE",
              packaged(MultipartBody.command("Put File").file(local), app).assetPath(path).build());
    };
  }

  private Request installRequest(DeployCommand.InstallApp install) throws IOException {
    var builder =
        MultipartBody.command(install.launch() ? "Install and Launch" : "Install")
            .debugMode(install.debugHost() != null || install.debugNative())
            .debugHost(install.debugHost())
            .devMode(install.devMode())
            .debugNative(install.debugNative())
            .file(install.bar());
    if (install.launch()) {
      var identity = bars.read(install.bar());
      builder.packageId(identity.id()).packageName(identity.name());
    }
    return new Request(install.launch() ? "INSTALL_AND_LAUNCH" : "INSTALL", builder.build());
  }

  private MultipartBody.Builder packaged(MultipartBody.Builder builder, PackageRef app)
      throws IOException {
    var identity = identityOf(app);
    return builder.packageId(identity.id()).packageName(identity.name());
  }

  private Identity identityOf(PackageRef app) throws IOException {
    return switch (app) {
      case PackageRef.NameAndId(var name, var id) -> new Identity(id, name);
      case PackageRef.FullName(var fullName) -> fromFullName(fullName);
      case PackageRef.BarFile(var bar) -> bars.read(bar);
    };
  }

  private static Identity fromFullName(String fullName) {
    var dot = fullName.lastIndexOf('.');
    return dot < 0
        ? new Identity(fullName, null)
        : new Identity(fullName.substring(dot + 1), fullName.substring(0, dot));
  }

  /**
   * The first {@code Error:}/{@code result::failure} line that a later success did not cancel,
   * which is where the original tool raised its exception ("the last outcome wins", so a retried
   * install that finally worked has none).
   */
  private static Optional<String> toolErrorLine(List<String> lines) {
    String errorLine = null;
    for (var line : lines) {
      if (line.startsWith("Error:") || line.startsWith("result::failure")) {
        errorLine = errorLine == null ? line : errorLine;
      } else if (line.startsWith("result::")) {
        errorLine = null;
      }
    }
    return Optional.ofNullable(errorLine);
  }

  /** The device's package id and name for a command that identifies one. */
  record Identity(String id, String name) {}

  /** Reads the package identity from a bar manifest. */
  @FunctionalInterface
  interface BarIdentityReader {

    /**
     * Reads the identity.
     *
     * @param bar the bar file
     * @return its package id and name
     * @throws IOException if the bar cannot be read
     */
    Identity read(Path bar) throws IOException;
  }

  /** A command ready to send: the log name the tool announces, and the body of the request. */
  private record Request(String logName, MultipartBody body) {}
}
