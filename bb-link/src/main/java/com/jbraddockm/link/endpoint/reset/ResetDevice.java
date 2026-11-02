package com.jbraddockm.link.endpoint.reset;

import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.transport.cgi.Conversation;
import com.jbraddockm.link.transport.cgi.DeviceCommand;
import com.jbraddockm.link.transport.cgi.Endpoint;
import com.jbraddockm.link.transport.cgi.FormField;
import com.jbraddockm.link.transport.cgi.ResponseElement;
import com.jbraddockm.link.transport.cgi.ResponseEnvelope;
import com.jbraddockm.link.transport.cgi.ResponseFailures;
import com.jbraddockm.link.transport.cgi.Step;
import com.jbraddockm.link.transport.cgi.XmlConversation;
import java.util.Objects;

/**
 * Reboots or powers off the device through {@code reset.cgi}.
 *
 * <p>The request carries one field, {@code reset=<mode>} ({@link ResetMode}). The device answers
 * the {@code Reset} envelope with a {@code Success} status and only then goes down — it forks a
 * child that waits for the request to finish before it reboots — so the reply arrives cleanly and
 * the connection drops afterwards. Changes the device, so it is never repeated after a dropped
 * connection.
 *
 * <p>When the device rejects the request it answers an error envelope whose {@code ErrorIdentifier}
 * is surfaced as a {@code COMMAND_FAILED} error carrying the device's number and message: {@code 3}
 * {@code Request not supported} (an unrecognised mode), {@code 4} {@code Failed to reset the
 * device} (the device could not start the reset). See {@code analysis/endpoints/ResetCGI.md}.
 *
 * @param mode the kind of reset to perform
 */
public record ResetDevice(ResetMode mode) implements DeviceCommand<ResetResult> {

  /**
   * Checks the mode.
   *
   * @throws NullPointerException if {@code mode} is {@code null}
   */
  public ResetDevice {
    Objects.requireNonNull(mode, "mode");
  }

  /**
   * Returns the request that performs the reset.
   *
   * @param mode the kind of reset
   * @return the request
   */
  public static CgiRequest.Form request(ResetMode mode) {
    return CgiRequest.Form.to(Endpoint.RESET)
        .with(FormField.RESET, mode)
        .describedAs(
            mode == ResetMode.SHUTDOWN ? "Powering the device off" : "Rebooting the device");
  }

  /**
   * Reads the device's answer to {@link #request(ResetMode)}.
   *
   * @param response the answer
   * @param mode the mode that was requested
   * @return the outcome as a {@link Step.Finish}, or why it failed as a {@link Step.Fail}
   */
  public static Step<ResetResult> interpret(ResponseEnvelope response, ResetMode mode) {
    var failure = ResponseFailures.failureFor(response, ResponseElement.RESET);
    if (failure.isPresent()) {
      return new Step.Fail<>(failure.get());
    }
    return new Step.Finish<>(new ResetResult(mode));
  }

  @Override
  public Conversation<ResetResult> conversation() {
    return new XmlConversation<>() {
      @Override
      public Step<ResetResult> start() {
        return new Step.Send<>(request(mode));
      }

      @Override
      protected Step<ResetResult> onResponse(ResponseEnvelope response) {
        return interpret(response, mode);
      }
    };
  }

  @Override
  public boolean readOnly() {
    return false;
  }
}
