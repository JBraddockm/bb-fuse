package com.jbraddockm.dev.packaging;

import com.jbraddockm.core.bar.BarArchive;
import com.jbraddockm.core.bar.BarAsset;
import com.jbraddockm.core.bar.InvalidBarException;
import com.jbraddockm.core.daemon.CommandSpec;
import com.jbraddockm.core.daemon.Reply;
import com.jbraddockm.core.daemon.ServiceSession;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.dev.packaging.descriptor.InvalidDescriptorException;
import com.jbraddockm.dev.packaging.internal.Packager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * The packaging namespace's daemon commands: build a bar, list its files, read its manifest. They
 * are <em>standalone</em> — they need no device and no session — so a daemon can offer them
 * alongside the session-scoped deploy commands over one loop.
 */
public final class PackageDaemonCommands {

  private PackageDaemonCommands() {}

  /**
   * The standalone packaging commands, usable in a service of any session type.
   *
   * @param <S> the daemon's session type (unused by these commands)
   * @return the command specs
   */
  public static <S extends ServiceSession> List<CommandSpec<S>> commands() {
    return List.of(
        new CommandSpec.Standalone<>(
            "package",
            params -> {
              var descriptor = params.requirePath("descriptor");
              var bar = params.requirePath("bar");
              var options =
                  new Packager.PackageOptions(
                      params.optionalString("configuration").orElse(null),
                      params.optionalString("buildId").orElse(null),
                      params.optionalString("appVersion").orElse(null),
                      List.of(),
                      params.flag("devMode"));
              return reply -> pack(descriptor, bar, options, reply);
            }),
        new CommandSpec.Standalone<>(
            "listBar",
            params -> {
              var bar = params.requirePath("bar");
              return reply -> listBar(bar, reply);
            }),
        new CommandSpec.Standalone<>(
            "manifest",
            params -> {
              var bar = params.requirePath("bar");
              return reply -> manifest(bar, reply);
            }));
  }

  private static void pack(
      Path descriptor, Path bar, Packager.PackageOptions options, Reply reply) {
    try {
      var packed = Packager.pack(descriptor, descriptor.toAbsolutePath().getParent(), bar, options);
      reply.result("package", Json.object("bar", bar.toString(), "files", packed.size()), null);
      reply.succeed();
    } catch (InvalidDescriptorException invalid) {
      reply.fail(new ServiceError(CoreErrorCode.INVALID_ARGUMENT, invalid.getMessage()));
    } catch (IOException failure) {
      reply.fail(new ServiceError(CoreErrorCode.IO_ERROR, String.valueOf(failure.getMessage())));
    }
  }

  private static void listBar(Path bar, Reply reply) {
    try (var archive = BarArchive.open(bar)) {
      var paths = archive.assets().stream().map(BarAsset::path).sorted().toList();
      reply.result("listBar", Json.object("bar", bar.toString(), "files", paths), null);
      reply.succeed();
    } catch (InvalidBarException | IOException failure) {
      reply.fail(new ServiceError(CoreErrorCode.IO_ERROR, String.valueOf(failure.getMessage())));
    }
  }

  private static void manifest(Path bar, Reply reply) {
    try (var archive = BarArchive.open(bar)) {
      var manifest = new String(archive.manifest().toBytes(), StandardCharsets.UTF_8);
      reply.result("manifest", Json.object("bar", bar.toString(), "manifest", manifest), null);
      reply.succeed();
    } catch (InvalidBarException | IOException failure) {
      reply.fail(new ServiceError(CoreErrorCode.IO_ERROR, String.valueOf(failure.getMessage())));
    }
  }
}
