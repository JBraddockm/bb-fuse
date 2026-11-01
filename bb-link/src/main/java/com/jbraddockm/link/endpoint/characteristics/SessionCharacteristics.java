package com.jbraddockm.link.endpoint.characteristics;

import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.transport.cgi.ConnectionPath;
import com.jbraddockm.link.transport.cgi.Step;
import com.jbraddockm.link.transport.session.DeviceSession;
import java.io.IOException;
import java.util.Optional;

/**
 * Reads the device characteristics on an open session, choosing the path the same way {@code
 * connect} does, so it is the one PIN source for the flows that need the PIN after a connect (the
 * pair/renew confirm-PIN guard, and naming a backup).
 *
 * <p>Over the tunnel or a LAN it reads {@code discovery.cgi} over the session. Over USB the {@code
 * :8443} management listener does not serve {@code discovery.cgi} — it lives only on the
 * unauthenticated {@code :80} listener — so it reads {@code :80} directly instead (see {@link
 * UsbDiscovery}), the way {@code LinkService} reads it during connect.
 */
public final class SessionCharacteristics {

  private SessionCharacteristics() {}

  /**
   * Reads the characteristics on the session.
   *
   * @param session the open session
   * @param listener receives the events of a read made over the session
   * @return the characteristics as a {@link Step.Finish}, or why they could not be read as a {@link
   *     Step.Fail}
   */
  public static Step<DeviceCharacteristics> read(DeviceSession session, CommandListener listener) {
    if (session.connectionPath() == ConnectionPath.USB) {
      try {
        return UsbDiscovery.read(session.target().host());
      } catch (IOException failure) {
        return new Step.Fail<>(
            new ServiceError(
                LinkErrorCode.UNEXPECTED_RESPONSE,
                "Could not read the device characteristics from port 80: " + failure.getMessage()),
            Optional.empty());
      }
    }
    return switch (session.execute(new GetDeviceCharacteristics(), listener)) {
      case CommandOutcome.Succeeded<DeviceCharacteristics>(var characteristics, var ignored) ->
          new Step.Finish<>(characteristics);
      case CommandOutcome.Failed<DeviceCharacteristics>(var error, var body, var raw) ->
          new Step.Fail<>(error, Optional.empty());
    };
  }
}
