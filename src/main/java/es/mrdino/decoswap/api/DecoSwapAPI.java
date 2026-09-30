package es.mrdino.decoswap.api;

import es.mrdino.decoswap.decoration.*;
import es.mrdino.decoswap.deployment.*;
import es.mrdino.decoswap.group.DecorationGroup;
import es.mrdino.decoswap.util.Rotation;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.bukkit.entity.Player;

public interface DecoSwapAPI {
  Optional<Decoration> getDecoration(String name);

  Collection<Decoration> getDecorations();

  Optional<DecorationGroup> getGroup(String name);

  Collection<DecorationGroup> getGroups();

  CompletableFuture<Deployment> deployDecoration(
      Decoration decoration, Anchor target, Rotation rotation, Player actor);

  CompletableFuture<DeploymentEngine.RestoreResult> restoreDeployment(
      UUID deploymentId, Player actor, boolean force);

  boolean isDecorationActive(UUID decorationId);

  Collection<Deployment> getActiveDeployments();
}
