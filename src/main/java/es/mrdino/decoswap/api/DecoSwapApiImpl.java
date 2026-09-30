package es.mrdino.decoswap.api;

import es.mrdino.decoswap.decoration.*;
import es.mrdino.decoswap.deployment.*;
import es.mrdino.decoswap.group.*;
import es.mrdino.decoswap.storage.DecorationRepository;
import es.mrdino.decoswap.util.Rotation;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.bukkit.entity.Player;

public final class DecoSwapApiImpl implements DecoSwapAPI {
  private final DecorationRepository decorations;
  private final GroupRepository groups;
  private final DeploymentEngine engine;

  public DecoSwapApiImpl(DecorationRepository d, GroupRepository g, DeploymentEngine e) {
    decorations = d;
    groups = g;
    engine = e;
  }

  public Optional<Decoration> getDecoration(String n) {
    return decorations.find(n);
  }

  public Collection<Decoration> getDecorations() {
    return List.copyOf(decorations.all());
  }

  public Optional<DecorationGroup> getGroup(String n) {
    return groups.find(n);
  }

  public Collection<DecorationGroup> getGroups() {
    return List.copyOf(groups.all());
  }

  public CompletableFuture<Deployment> deployDecoration(
      Decoration d, Anchor a, Rotation r, Player p) {
    return engine.deploy(d, a, r, p, null, false);
  }

  public CompletableFuture<DeploymentEngine.RestoreResult> restoreDeployment(
      UUID id, Player p, boolean force) {
    Deployment d = engine.active().stream().filter(x -> x.id().equals(id)).findFirst().orElse(null);
    return d == null
        ? CompletableFuture.failedFuture(new IllegalArgumentException("Unknown deployment"))
        : engine.restore(d, p, force);
  }

  public boolean isDecorationActive(UUID id) {
    return !engine.forDecoration(id).isEmpty();
  }

  public Collection<Deployment> getActiveDeployments() {
    return List.copyOf(engine.active());
  }
}
