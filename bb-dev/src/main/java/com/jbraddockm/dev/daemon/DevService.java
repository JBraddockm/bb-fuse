package com.jbraddockm.dev.daemon;

import com.jbraddockm.core.daemon.CommandSpec;
import com.jbraddockm.core.daemon.Establishment;
import com.jbraddockm.core.daemon.Reply;
import com.jbraddockm.core.daemon.RequestParams;
import com.jbraddockm.core.daemon.Service;
import com.jbraddockm.core.daemon.ServiceInfo;
import com.jbraddockm.dev.deploy.DeployService;
import com.jbraddockm.dev.deploy.DeploySession;
import com.jbraddockm.dev.packaging.PackageDaemonCommands;
import java.util.ArrayList;
import java.util.List;

/**
 * The bb-dev daemon: one newline-JSON loop that offers both namespaces. Deploy's verbs are
 * session-scoped (a {@code connect} opens a device session and logs in); packaging's verbs are
 * standalone (they build and inspect bars with no device). {@code connect} is deliberately not part
 * of it — it holds a long-lived session of its own, not a request/response.
 */
public final class DevService implements Service<DeploySession> {

  static final String VERSION = "0.1.0";

  private final DeployService deploy = new DeployService();

  @Override
  public ServiceInfo info() {
    return new ServiceInfo("bb-dev", VERSION);
  }

  @Override
  public DeploySession open(RequestParams params) {
    return deploy.open(params);
  }

  @Override
  public Establishment establish(DeploySession session, RequestParams params, Reply reply) {
    return deploy.establish(session, params, reply);
  }

  @Override
  public List<CommandSpec<DeploySession>> commands() {
    var commands = new ArrayList<>(deploy.commands());
    commands.addAll(PackageDaemonCommands.commands());
    return List.copyOf(commands);
  }
}
