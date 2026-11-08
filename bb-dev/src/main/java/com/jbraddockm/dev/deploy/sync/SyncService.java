package com.jbraddockm.dev.deploy.sync;

import com.jbraddockm.core.bar.BarArchive;
import com.jbraddockm.core.bar.BarAsset;
import com.jbraddockm.core.bar.InvalidBarException;
import com.jbraddockm.core.bar.Manifest;
import com.jbraddockm.core.bar.ManifestAsset;
import com.jbraddockm.dev.qconn.TargetFileService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * The dev-mode fast install: install a tiny {@linkplain MiniBarGenerator mini-BAR} so the app's
 * sandbox exists, read back the device's installed manifest over qconn, and stream in only the
 * assets whose SHA-512 digest changed (or that the device does not yet have), then write the full
 * manifest so the next install diffs clean. A re-install of an unchanged bar uploads nothing.
 *
 * <p>Sync applies only to a development-mode bar with a native entry point; otherwise the caller
 * does a full install. See {@code analysis/DEPLOY-sync-plan.md}.
 */
public final class SyncService {

  private static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";

  private SyncService() {}

  /**
   * Installs a bar with sync, pushing the mini-BAR through {@code installer} and the asset delta
   * through {@code files}.
   *
   * @param bar the full bar
   * @param files the device's qconn file service
   * @param installer installs the mini-BAR (the appInstaller.cgi path)
   * @return the sync outcome
   * @throws IOException if the device transport fails
   * @throws InvalidBarException if the bar has no manifest that can be understood
   */
  public static SyncResult synchronize(BarArchive bar, TargetFileService files, Installer installer)
      throws IOException, InvalidBarException {
    var host = bar.manifest();
    var base = deviceAppBasePath(host);

    // Read the installed manifest first — installing the mini-BAR would replace it. An empty
    // manifest means the app is not installed yet, so create its sandbox with the mini-BAR; an
    // already-installed app keeps its manifest and we push only what changed against it.
    var device = readDeviceManifest(files, base);
    if (device.sections().isEmpty()) {
      installer.install(MiniBarGenerator.generate(bar));
    }

    var toUpload = assetsToUpload(host, device);
    for (var asset : toUpload) {
      var path = base + asset.name();
      files.mkdirs(parentOf(path));
      files.writeFile(path, read(bar, asset.name()));
    }
    if (!toUpload.isEmpty()) {
      files.mkdirs(parentOf(base + MANIFEST_PATH));
      files.writeFile(base + MANIFEST_PATH, host.toBytes());
    }
    return new SyncResult(toUpload.isEmpty(), toUpload.size());
  }

  /**
   * Whether a bar can be installed with sync: it must be built for development mode and have a
   * native entry point (sync does not apply to an Android runtime app).
   *
   * @param manifest the bar's manifest
   * @return {@code true} if sync applies
   */
  public static boolean isSyncCompatible(Manifest manifest) {
    var developmentMode =
        manifest.value("Application-Development-Mode").map("true"::equalsIgnoreCase).orElse(false);
    var entryPointType = manifest.value("Entry-Point-Type").orElse("");
    return developmentMode && !entryPointType.equalsIgnoreCase("Qnx/Android");
  }

  /**
   * The device sandbox base path of an app, as the deploy tool computes it for the personal
   * perimeter: {@code /apps/<Package-Name>.<Package-Id>/}, with any {@code -} in the id replaced by
   * {@code .}.
   *
   * @param manifest the bar's manifest
   * @return the base path, ending with {@code /}
   */
  static String deviceAppBasePath(Manifest manifest) {
    var name =
        manifest
            .value("Package-Name")
            .orElseThrow(() -> new IllegalArgumentException("the manifest has no Package-Name"));
    var id =
        manifest
            .value("Package-Id")
            .orElseThrow(() -> new IllegalArgumentException("the manifest has no Package-Id"));
    return "/apps/" + name + "." + id.replace('-', '.') + "/";
  }

  /**
   * The assets to push: every asset the host bar has that the device is missing or holds at a
   * different SHA-512 digest.
   *
   * @param host the bar being installed
   * @param device the device's installed manifest
   * @return the assets to upload, in host order
   */
  static List<ManifestAsset> assetsToUpload(Manifest host, Manifest device) {
    var deviceDigests = new HashMap<String, String>();
    for (var asset : device.assets()) {
      deviceDigests.put(asset.name(), asset.sha512Digest());
    }
    var toUpload = new ArrayList<ManifestAsset>();
    for (var asset : host.assets()) {
      if (!asset.sha512Digest().equals(deviceDigests.get(asset.name()))) {
        toUpload.add(asset);
      }
    }
    return toUpload;
  }

  /** Reads the device's installed manifest, or an empty manifest if none can be read. */
  private static Manifest readDeviceManifest(TargetFileService files, String base) {
    try {
      return Manifest.parse(files.readFile(base + MANIFEST_PATH));
    } catch (IOException | InvalidBarException absent) {
      return new Manifest(List.of());
    }
  }

  private static byte[] read(BarArchive bar, String name) throws IOException {
    try (var stream = bar.open(new BarAsset(name, -1))) {
      return stream.readAllBytes();
    }
  }

  private static String parentOf(String path) {
    var slash = path.lastIndexOf('/');
    return slash <= 0 ? "/" : path.substring(0, slash);
  }

  /** Installs the mini-BAR — the appInstaller.cgi install the sync rides on. */
  @FunctionalInterface
  public interface Installer {
    void install(byte[] miniBar) throws IOException;
  }
}
