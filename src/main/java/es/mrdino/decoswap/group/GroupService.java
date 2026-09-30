package es.mrdino.decoswap.group;

import es.mrdino.decoswap.api.event.*;
import es.mrdino.decoswap.decoration.Decoration;
import es.mrdino.decoswap.deployment.*;
import es.mrdino.decoswap.storage.DecorationRepository;
import es.mrdino.decoswap.util.Rotation;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class GroupService {
  private final DecorationRepository decorations;
  private final DeploymentEngine engine;

  public GroupService(DecorationRepository decorations, DeploymentEngine engine) {
    this.decorations = decorations;
    this.engine = engine;
  }

  public CompletableFuture<List<Deployment>> deploy(DecorationGroup group, Player actor) {
    List<Deployment> done = new ArrayList<>();
    CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
    for (String id : group.members()) {
      chain =
          chain.thenCompose(
              v -> {
                Decoration d =
                    decorations
                        .find(id)
                        .orElseThrow(
                            () -> new IllegalArgumentException("Missing decoration " + id));
                return engine
                    .deploy(d, d.homeAnchor(), Rotation.NONE, actor, group.id(), false)
                    .thenAccept(done::add);
              });
    }
    CompletableFuture<List<Deployment>> result =
        chain.thenApply(
            v -> {
              Bukkit.getPluginManager().callEvent(new GroupDeployEvent(group));
              return List.copyOf(done);
            });
    return result.exceptionallyCompose(
        error ->
            rollback(done, actor)
                .handle(
                    (v, rollbackError) -> {
                      throw new CompletionException(error);
                    }));
  }

  private CompletableFuture<Void> rollback(List<Deployment> done, Player actor) {
    CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
    List<Deployment> reverse = new ArrayList<>(done);
    Collections.reverse(reverse);
    for (Deployment d : reverse)
      chain = chain.thenCompose(v -> engine.restore(d, actor, true).thenAccept(r -> {}));
    return chain;
  }

  public CompletableFuture<Void> restore(DecorationGroup group, Player actor) {
    Set<String> members = new HashSet<>(group.members());
    List<Deployment> targets =
        engine.active().stream()
            .filter(
                d ->
                    members.contains(
                        decorations.all().stream()
                            .filter(x -> x.uuid().equals(d.decorationId()))
                            .map(Decoration::id)
                            .findFirst()
                            .orElse("")))
            .sorted(Comparator.comparing(Deployment::createdAt).reversed())
            .toList();
    CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
    for (Deployment d : targets)
      chain = chain.thenCompose(v -> engine.restore(d, actor, false).thenAccept(r -> {}));
    return chain.thenRun(() -> Bukkit.getPluginManager().callEvent(new GroupRestoreEvent(group)));
  }
}
