package com.jbraddockm.link.endpoint.pairing;

import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.error.ErrorCode;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.endpoint.characteristics.DeviceCharacteristics;
import com.jbraddockm.link.endpoint.characteristics.SessionCharacteristics;
import com.jbraddockm.link.transport.cgi.DeviceCommand;
import com.jbraddockm.link.transport.cgi.Step;
import com.jbraddockm.link.transport.session.DeviceSession;
import java.util.Locale;
import java.util.Map;

/**
 * What the workflows of this package do alike on a session: they confirm the PIN of the device, and
 * then run commands, one after the other, until one fails. A step that fails, or an answer that
 * cannot be used, ends the workflow with a {@link Stopped}, so that a workflow reads from the top
 * to the bottom and answers once, in one place, with the error.
 */
final class DeviceSteps {

  private static final int EXCERPT_LENGTH = 600;

  private final DeviceSession session;
  private final CommandListener listener;

  DeviceSteps(DeviceSession session, CommandListener listener) {
    this.session = session;
    this.listener = listener;
  }

  /**
   * Asks the device for its PIN, which must be the one that the caller typed. The PIN is read from
   * {@code discovery.cgi} through {@link SessionCharacteristics}, the same source {@code connect}
   * uses.
   *
   * @param typedPin what the caller typed
   * @return the PIN of the device, in capitals
   * @throws Stopped if the device cannot be asked, or says another PIN
   */
  String confirmPin(String typedPin) {
    var actual =
        switch (SessionCharacteristics.read(session, listener)) {
          case Step.Finish<DeviceCharacteristics>(var characteristics) -> characteristics.pin();
          case Step.Fail<DeviceCharacteristics>(var error, var ignored) -> throw new Stopped(error);
          case Step.Send<DeviceCharacteristics> ignored ->
              throw new Stopped(
                  new ServiceError(
                      LinkErrorCode.UNEXPECTED_RESPONSE,
                      "The device characteristics could not be read"));
        };
    if (!normalise(actual).equals(normalise(typedPin))) {
      throw new Stopped(
          new ServiceError(
              LinkErrorCode.PIN_MISMATCH,
              "The confirmation PIN does not match the PIN of the device"));
    }
    return actual.strip().toUpperCase(Locale.ROOT);
  }

  private static String normalise(String pin) {
    var upper = pin.strip().toUpperCase(Locale.ROOT);
    return upper.startsWith("0X") ? upper.substring(2) : upper;
  }

  /**
   * Runs a command on the session.
   *
   * @param command what to run
   * @param <R> what the command gives
   * @return the result
   * @throws Stopped if the command failed
   */
  <R extends CommandResult> R run(DeviceCommand<R> command) {
    return switch (session.execute(command, listener)) {
      case CommandOutcome.Succeeded<R>(var result, _) -> result;
      case CommandOutcome.Failed<R>(var error, _, _) -> throw new Stopped(error);
    };
  }

  /**
   * The end of a workflow because the device answered, and the answer lacks what the workflow
   * needs.
   *
   * @param message what is missing
   * @param statusText the {@code Status} of the answer; {@code null} if it has none
   * @param answer the answer, which is shortened
   * @return the exception to throw
   */
  static Stopped unexpected(String message, String statusText, String answer) {
    var shortened =
        answer.length() > EXCERPT_LENGTH ? answer.substring(0, EXCERPT_LENGTH) + "…" : answer;
    return new Stopped(
        LinkErrorCode.UNEXPECTED_RESPONSE,
        message,
        Json.object("status", statusText == null ? "" : statusText, "answer", shortened));
  }

  /** The end of a workflow, with the error that it answers. */
  static final class Stopped extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient ServiceError error;

    Stopped(ServiceError error) {
      super(error.message());
      this.error = error;
    }

    Stopped(ErrorCode code, String message, Map<String, Object> details) {
      this(new ServiceError(code, message, details));
    }

    ServiceError error() {
      return error;
    }
  }
}
