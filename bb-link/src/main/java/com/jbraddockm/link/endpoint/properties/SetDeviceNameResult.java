package com.jbraddockm.link.endpoint.properties;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.json.Json;
import java.util.Map;

/**
 * The outcome of setting the device name: the name the device now carries.
 *
 * @param deviceName the name that was set
 */
public record SetDeviceNameResult(String deviceName) implements CommandResult {

  @Override
  public Map<String, Object> toJson() {
    return Json.object("deviceName", deviceName);
  }
}
