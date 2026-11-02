package com.jbraddockm.link.endpoint.pairing;

import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.link.endpoint.pairing.store.DeviceDirectory;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Pins or clears a device's SMB host in {@code device.json}, so a later tunnel connect proposes it.
 * Purely local — it writes the device folder on this computer and sends nothing to the device.
 * Pinning is an explicit, deliberate choice (the only "make it sticky" decision); the connect path
 * only reads what is pinned here.
 */
public final class PinSmbHost {

  private PinSmbHost() {}

  /**
   * Pins {@code smbHost} for {@code pin}, writing (or replacing) its {@code device.json}.
   *
   * @param devices the folder that holds one folder per device
   * @param pin the device folder to pin the address for
   * @param smbHost the device SMB host to keep stable ({@code 169.254.<k>.1}, {@code k} in 1..254)
   * @return the pin, or a failure if the PIN or the address is rejected, or the write fails
   */
  public static CommandOutcome<PinResult> pin(Path devices, String pin, String smbHost) {
    DeviceDirectory directory;
    try {
      directory = DeviceDirectory.of(devices, pin);
    } catch (IllegalArgumentException badPin) {
      return CommandOutcome.failed(
          new ServiceError(CoreErrorCode.INVALID_ARGUMENT, badPin.getMessage()));
    }
    try {
      directory.pinSmbHost(smbHost);
    } catch (IllegalArgumentException badHost) {
      return CommandOutcome.failed(
          new ServiceError(CoreErrorCode.INVALID_ARGUMENT, badHost.getMessage()));
    } catch (IOException failure) {
      return CommandOutcome.failed(
          new ServiceError(
              CoreErrorCode.IO_ERROR,
              "could not write " + directory.deviceFile() + ": " + failure.getMessage()));
    }
    return CommandOutcome.succeeded(new PinResult(pin, smbHost));
  }

  /**
   * Clears any pinned SMB host for {@code pin}.
   *
   * @param devices the folder that holds one folder per device
   * @param pin the device folder to clear the pin for
   * @return the clear, or a failure if the PIN is rejected or the write fails
   */
  public static CommandOutcome<PinResult> unpin(Path devices, String pin) {
    DeviceDirectory directory;
    try {
      directory = DeviceDirectory.of(devices, pin);
    } catch (IllegalArgumentException badPin) {
      return CommandOutcome.failed(
          new ServiceError(CoreErrorCode.INVALID_ARGUMENT, badPin.getMessage()));
    }
    try {
      directory.clearSmbHost();
    } catch (IOException failure) {
      return CommandOutcome.failed(
          new ServiceError(
              CoreErrorCode.IO_ERROR,
              "could not remove " + directory.deviceFile() + ": " + failure.getMessage()));
    }
    return CommandOutcome.succeeded(new PinResult(pin, null));
  }
}
