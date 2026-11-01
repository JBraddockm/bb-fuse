package com.jbraddockm.link.endpoint.pimsync;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.transport.cgi.Step;
import java.util.Optional;

/**
 * Classifies a pimsync answer the one way every command shares: a refusal ends the command, any
 * other status is a result. Keeping this in one place means the refusal codes and the shape of the
 * failure (the device's code and bb-link's explanation of it) cannot drift between the JSON
 * commands and the photo commands.
 */
public final class PimOutcome {

  private PimOutcome() {}

  /**
   * Turns a parsed status and the answer built from it into a command's outcome.
   *
   * @param status the status read from the answer, or {@code null} if it had none
   * @param answer the answer to return as the result, and to keep alongside a failure
   * @return {@link Step.Fail} with {@code COMMAND_FAILED}, the device's code and its explanation
   *     for a refusal (1001, 1013, 1015); otherwise {@link Step.Finish} with the answer
   */
  public static <T extends CommandResult> Step<T> resultOrRejection(PimStatus status, T answer) {
    if (status == null || !PimStatusCode.isRejection(status.code())) {
      return new Step.Finish<>(answer);
    }
    var message = status.message();
    return new Step.Fail<>(
        new ServiceError(
            CoreErrorCode.COMMAND_FAILED,
            message == null || message.isBlank() ? "The device rejected the request" : message,
            Json.object(
                "code",
                status.code(),
                "explanation",
                PimStatusCode.explain(status.code()).orElse(null))),
        Optional.of(answer));
  }
}
