package com.jbraddockm.link.endpoint.files;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.transport.smb.DeviceShares;
import com.jbraddockm.link.transport.smb.SmbEntry;
import com.jbraddockm.link.transport.smb.SmbLocation;
import java.util.List;
import java.util.Map;

/**
 * The listing of a directory on the device's share: the directory's device-absolute path and its
 * entries, each carrying its own device-absolute path.
 *
 * @param directory the directory that was listed
 * @param entries its entries, in the order the device returned them
 */
public record DeviceFileListing(SmbLocation directory, List<SmbEntry> entries)
    implements CommandResult {

  /** Takes an immutable copy of the entries. */
  public DeviceFileListing {
    entries = List.copyOf(entries);
  }

  @Override
  public Map<String, Object> toJson() {
    var items =
        entries.stream()
            .map(
                entry ->
                    FileEntries.toJson(
                        DeviceShares.devicePath(directory.child(entry.name())), entry))
            .toList();
    return Json.object("path", DeviceShares.devicePath(directory), "entries", items);
  }
}
