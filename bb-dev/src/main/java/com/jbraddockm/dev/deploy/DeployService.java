package com.jbraddockm.dev.deploy;

import com.jbraddockm.core.daemon.CommandSpec;
import com.jbraddockm.core.daemon.Establishment;
import com.jbraddockm.core.daemon.Reply;
import com.jbraddockm.core.daemon.RequestParams;
import com.jbraddockm.core.daemon.Service;
import com.jbraddockm.core.daemon.ServiceInfo;
import com.jbraddockm.core.device.LoginException;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.core.json.Json;
import java.io.IOException;
import java.util.List;
import java.util.function.Function;

/**
 * The deploy daemon service: the native engine behind the bb-link-style newline-JSON protocol. A
 * {@code connect} opens a device session and logs in; each later command runs a deploy verb in that
 * session and replies with the same typed data the {@code --json} CLI prints. The engine, the verbs
 * and the results are shared with the terminal front-end; only the request parsing and the reply
 * shape are its own.
 */
final class DeployService implements Service<DeploySession> {

  static final String VERSION = "0.1.0";

  @Override
  public ServiceInfo info() {
    return new ServiceInfo("bb-dev deploy", VERSION);
  }

  @Override
  public DeploySession open(RequestParams params) {
    var host = params.requireString("host");
    var port = params.integer("port", 443, 1, 65_535);
    var password = params.optionalString("password").orElse("");
    return new DeploySession(AppInstallerEngine.forDevice(host, port, password, null));
  }

  @Override
  public Establishment establish(DeploySession session, RequestParams params, Reply reply) {
    try {
      session.engine().run(new DeployCommand.TestConnection(), _ -> {});
      return new Establishment.Established(Json.object("ok", true));
    } catch (LoginException denied) {
      return new Establishment.Rejected(
          new ServiceError(CoreErrorCode.AUTH_FAILED, "login failed: " + denied.getMessage()));
    } catch (DeviceReportException | IOException failure) {
      return new Establishment.Rejected(
          new ServiceError(CoreErrorCode.CONNECTION_FAILED, failure.getMessage()));
    }
  }

  @Override
  public List<CommandSpec<DeploySession>> commands() {
    return List.of(
        command("test", _ -> new DeployCommand.TestConnection()),
        command("info", _ -> new DeployCommand.ListDeviceInfo()),
        command("apps", _ -> new DeployCommand.ListInstalledApps()),
        command("verify", params -> new DeployCommand.VerifyApp(app(params))),
        command("running", params -> new DeployCommand.IsAppRunning(app(params))),
        command("launch", params -> new DeployCommand.LaunchApp(app(params))),
        command("terminate", params -> new DeployCommand.TerminateApp(app(params))),
        command("uninstall", params -> new DeployCommand.UninstallApp(app(params))),
        command(
            "install",
            params ->
                new DeployCommand.InstallApp(
                    params.requirePath("bar"),
                    params.flag("launch"),
                    params.flag("dev"),
                    params.optionalString("debugHost").orElse(null),
                    params.flag("debugNative"))),
        command(
            "get",
            params ->
                new DeployCommand.GetFile(
                    app(params),
                    params.requireString("path"),
                    params.optionalPath("out").orElse(null))),
        command(
            "put",
            params ->
                new DeployCommand.PutFile(
                    app(params), params.requirePath("local"), params.requireString("path"))));
  }

  private static CommandSpec<DeploySession> command(
      String wireName, Function<RequestParams, DeployCommand> build) {
    return new CommandSpec.SessionScoped<>(
        wireName,
        params -> {
          var command = build.apply(params);
          return (session, reply) -> run(wireName, command, session, reply);
        });
  }

  private static void run(
      String wireName, DeployCommand command, DeploySession session, Reply reply) {
    try {
      var lines = session.engine().run(command, _ -> {});
      reply.result(wireName, DeployResults.data(command, lines), lines);
      reply.succeed();
    } catch (DeviceReportException reported) {
      reply.fail(
          new ServiceError(
              reported.isAuthenticationFailure()
                  ? CoreErrorCode.AUTH_FAILED
                  : CoreErrorCode.COMMAND_FAILED,
              reported.getMessage()));
    } catch (LoginException denied) {
      reply.fail(
          new ServiceError(CoreErrorCode.AUTH_FAILED, "login failed: " + denied.getMessage()));
    } catch (IOException failure) {
      reply.fail(new ServiceError(CoreErrorCode.CONNECTION_FAILED, failure.getMessage()));
    }
  }

  private static PackageRef app(RequestParams params) {
    return new PackageRef.FullName(params.requireString("app"));
  }
}
