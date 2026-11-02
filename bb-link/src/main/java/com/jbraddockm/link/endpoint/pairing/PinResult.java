package com.jbraddockm.link.endpoint.pairing;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.json.Json;
import java.util.Map;

/**
 * What pinning or clearing a device's SMB host did: the device and the address now kept for it. A
 * pin reports the address; a clear reports {@code null} and {@code pinned=false}.
 *
 * @param pin the device folder whose {@code device.json} was written
 * @param smbHost the pinned SMB host ({@code 169.254.<k>.1}), or {@code null} after a clear
 */
public record PinResult(String pin, String smbHost) implements CommandResult {

  @Override
  public Map<String, Object> toJson() {
    return Json.object("pin", pin, "smbHost", smbHost, "pinned", smbHost != null);
  }
}
