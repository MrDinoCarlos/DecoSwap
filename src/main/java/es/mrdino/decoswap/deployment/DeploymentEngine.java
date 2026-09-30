package es.mrdino.decoswap.deployment;

import es.mrdino.decoswap.api.event.*;
import es.mrdino.decoswap.config.PluginConfig;
import es.mrdino.decoswap.decoration.*;
import es.mrdino.decoswap.language.LanguageService;
import es.mrdino.decoswap.serialization.block.BlockSerializerRegistry;
import es.mrdino.decoswap.serialization.entity.EntitySerializerRegistry;
import es.mrdino.decoswap.transaction.*;
import es.mrdino.decoswap.util.*;
import es.mrdino.decoswap.util.Rotation;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.*;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class DeploymentEngine {
  private final JavaPlugin plugin;
  private PluginConfig config;
  private final LanguageService lang;
  private final BlockSerializerRegistry blockCodec;
  private final EntitySerializerRegistry entityCodec;
  private final SnapshotStore snapshots;
  private final TransactionJournal journal;
  private final Map<UUID, Deployment> active = new LinkedHashMap<>();
  private final Map<BlockKey, Deque<UUID>> owners = new HashMap<>();
  private final Set<UUID> busyDecorations = new HashSet<>();
  private final Map<UUID, TransactionJournal.Entry> recovery = new LinkedHashMap<>();

  public DeploymentEngine(
      JavaPlugin plugin,
      PluginConfig config,
      LanguageService lang,
      BlockSerializerRegistry blocks,
      EntitySerializerRegistry entities,
      SnapshotStore snapshots,
      TransactionJournal journal) {
    this.plugin = plugin;
    this.config = config;
    this.lang = lang;
    this.blockCodec = blocks;
    this.entityCodec = entities;
    this.snapshots = snapshots;
    this.journal = journal;
  }

  public void config(PluginConfig c) {
    config = c;
  }

  public void load() {
    List<TransactionJournal.Entry> entries = journal.scan();
    entries.stream()
        .filter(e -> e.state() == TransactionState.ACTIVE)
        .sorted(Comparator.comparing(e -> e.deployment().createdAt()))
        .forEach(
            e -> {
              Deployment d = e.deployment();
              active.put(d.id(), d);
              d.expectedBlocks()
                  .keySet()
                  .forEach(
                      k ->
                          owners.computeIfAbsent(k, ignored -> new ArrayDeque<>()).addLast(d.id()));
            });
    for (TransactionJournal.Entry e : entries)
      if (e.state() != TransactionState.ACTIVE && e.state() != TransactionState.COMMITTED) {
        Deployment d = e.deployment();
        d.state(TransactionState.RECOVERY_REQUIRED);
        try {
          journal.persist(d, e.type(), "Incomplete transaction detected during startup");
        } catch (IOException ex) {
          plugin.getLogger().log(Level.SEVERE, "Could not mark recovery transaction", ex);
        }
        recovery.put(
            d.id(),
            new TransactionJournal.Entry(
                e.id(),
                e.type(),
                TransactionState.RECOVERY_REQUIRED,
                d,
                e.updated(),
                "Incomplete transaction detected during startup"));
        plugin.getLogger().severe("DecoSwap detected an incomplete transaction: " + d.id());
      }
  }

  public Collection<Deployment> active() {
    return List.copyOf(active.values());
  }

  public Collection<TransactionJournal.Entry> recovery() {
    return List.copyOf(recovery.values());
  }

  public boolean isBusy(UUID decoration) {
    return busyDecorations.contains(decoration);
  }

  public List<Deployment> forDecoration(UUID id) {
    return active.values().stream().filter(d -> d.decorationId().equals(id)).toList();
  }

  public CompletableFuture<Deployment> deploy(
      Decoration decoration,
      Anchor target,
      Rotation rotation,
      Player actor,
      String groupId,
      boolean forceOverlap) {
    CompletableFuture<Deployment> future = new CompletableFuture<>();
    if (!Bukkit.isPrimaryThread()) {
      sync(
          () ->
              deploy(decoration, target, rotation, actor, groupId, forceOverlap)
                  .whenComplete(copy(future)));
      return future;
    }
    if (!busyDecorations.add(decoration.uuid()))
      return CompletableFuture.failedFuture(new IllegalStateException("busy"));
    World world = Bukkit.getWorld(target.worldId());
    if (world == null) {
      busyDecorations.remove(decoration.uuid());
      return CompletableFuture.failedFuture(
          new IllegalStateException("world-missing:" + target.worldName()));
    }
    DecorationPreDeployEvent event = new DecorationPreDeployEvent(decoration, target);
    Bukkit.getPluginManager().callEvent(event);
    if (event.isCancelled()) {
      busyDecorations.remove(decoration.uuid());
      return CompletableFuture.failedFuture(new CancellationException("Cancelled by event"));
    }
    List<TargetBlock> targets = targets(decoration, target, rotation, world);
    long overlap = targets.stream().map(TargetBlock::key).filter(owners::containsKey).count();
    if (overlap > 0 && !config.allowOverlap() && !forceOverlap) {
      busyDecorations.remove(decoration.uuid());
      return CompletableFuture.failedFuture(new OverlapException((int) overlap));
    }
    if (!config.allowDuplicateHome()
        && sameHome(decoration, target)
        && forDecoration(decoration.uuid()).stream()
            .anyMatch(d -> sameAnchor(d.target(), target))) {
      busyDecorations.remove(decoration.uuid());
      return CompletableFuture.failedFuture(new IllegalStateException("active"));
    }
    Set<Chunk> tickets = loadChunks(world, targets.stream().map(TargetBlock::key).toList());
    UUID id = UUID.randomUUID();
    Deployment deployment =
        new Deployment(
            id,
            decoration.uuid(),
            decoration.displayName(),
            actor == null ? null : actor.getUniqueId(),
            groupId,
            Instant.now(),
            target,
            rotation,
            TransactionState.PREPARED);
    List<BlockRecord> before = new ArrayList<>(), expected = new ArrayList<>();
    try {
      for (TargetBlock t : targets) {
        world.getChunkAt(t.x() >> 4, t.z() >> 4);
        BlockRecord old = blockCodec.capture(world.getBlockAt(t.x(), t.y(), t.z()), target);
        before.add(old);
        org.bukkit.block.data.BlockData data = Bukkit.createBlockData(t.record().blockData());
        data.rotate(structure(rotation));
        String finalData = data.getAsString(true);
        BlockRecord expectedRecord =
            new BlockRecord(
                t.x() - (int) Math.floor(target.x()),
                t.y() - (int) Math.floor(target.y()),
                t.z() - (int) Math.floor(target.z()),
                finalData,
                t.record().pdc(),
                t.record().properties(),
                t.record().items());
        deployment.expect(t.key(), Fingerprint.block(expectedRecord));
        expected.add(expectedRecord);
      }
    } catch (Exception e) {
      release(tickets);
      busyDecorations.remove(decoration.uuid());
      return CompletableFuture.failedFuture(e);
    }
    if (actor != null)
      lang.send(actor, "deploy.preparing", Map.of("name", decoration.displayName()));
    CompletableFuture.runAsync(
            () -> {
              try {
                snapshots.writeSnapshot(id, before);
                snapshots.writeExpected(id, expected);
                journal.persist(deployment, "DEPLOY", "");
              } catch (Exception e) {
                throw new CompletionException(e);
              }
            })
        .whenComplete(
            (unused, error) ->
                sync(
                    () -> {
                      if (error != null) {
                        release(tickets);
                        busyDecorations.remove(decoration.uuid());
                        future.completeExceptionally(unwrap(error));
                        return;
                      }
                      deployment.state(TransactionState.APPLYING);
                      persistQuiet(deployment, "DEPLOY", "");
                      applyDeployment(
                          decoration, deployment, targets, world, actor, tickets, future);
                    }));
    return future;
  }

  private void applyDeployment(
      Decoration decoration,
      Deployment deployment,
      List<TargetBlock> targets,
      World world,
      Player actor,
      Set<Chunk> tickets,
      CompletableFuture<Deployment> future) {
    BossBar bar =
        bar(
            actor,
            "deploy.progress",
            decoration.displayName(),
            0,
            Math.max(1, targets.size() + decoration.entityCount()));
    new BukkitRunnable() {
      int bi, ei;
      final Map<Integer, Entity> spawned = new HashMap<>();

      public void run() {
        try {
          int budget = config.blocksPerTick();
          while (bi < targets.size() && budget-- > 0) {
            TargetBlock t = targets.get(bi++);
            blockCodec.apply(
                world.getBlockAt(t.x(), t.y(), t.z()),
                t.record(),
                structure(deployment.rotation()));
            progress(
                bar,
                actor,
                "deploy.progress",
                decoration.displayName(),
                bi,
                targets.size() + decoration.entityCount());
          }
          if (bi < targets.size()) return;
          int entities = config.entitiesPerTick(), entityStart = ei;
          while (ei < decoration.entities().size() && entities-- > 0) {
            EntityRecord record = decoration.entities().get(ei++);
            Entity entity =
                entityCodec.spawn(
                    record,
                    world,
                    deployment.target(),
                    deployment.rotation(),
                    deployment.decorationId(),
                    deployment.id());
            spawned.put(record.localId(), entity);
            deployment.spawned(record.localId(), entity.getUniqueId());
            progress(
                bar,
                actor,
                "deploy.progress",
                decoration.displayName(),
                bi + ei,
                targets.size() + decoration.entityCount());
          }
          if (ei > entityStart) persistQuiet(deployment, "DEPLOY", "");
          if (ei < decoration.entities().size()) return;
          for (EntityRecord r : decoration.entities()) {
            Entity parent = spawned.get(r.localId());
            if (parent == null) continue;
            for (int child : r.passengers()) {
              Entity passenger = spawned.get(child);
              if (passenger != null) parent.addPassenger(passenger);
            }
            if (r.leashHolder() != null && parent instanceof LivingEntity leashable) {
              Entity holder = spawned.get(r.leashHolder());
              if (holder != null) leashable.setLeashHolder(holder);
            }
          }
          deployment.state(TransactionState.ACTIVE);
          active.put(deployment.id(), deployment);
          deployment
              .expectedBlocks()
              .keySet()
              .forEach(
                  k ->
                      owners
                          .computeIfAbsent(k, ignored -> new ArrayDeque<>())
                          .addLast(deployment.id()));
          persistQuiet(deployment, "DEPLOY", "");
          busyDecorations.remove(decoration.uuid());
          release(tickets);
          hide(bar, actor);
          Bukkit.getPluginManager().callEvent(new DecorationDeployEvent(decoration, deployment));
          future.complete(deployment);
          cancel();
        } catch (Exception e) {
          deployment.state(TransactionState.RECOVERY_REQUIRED);
          persistQuiet(deployment, "DEPLOY", e.toString());
          recovery.put(
              deployment.id(),
              new TransactionJournal.Entry(
                  deployment.id(),
                  "DEPLOY",
                  deployment.state(),
                  deployment,
                  Instant.now().toString(),
                  e.toString()));
          busyDecorations.remove(decoration.uuid());
          release(tickets);
          hide(bar, actor);
          future.completeExceptionally(e);
          cancel();
        }
      }
    }.runTaskTimer(plugin, 1, 1);
  }

  public CompletableFuture<RestoreResult> restore(
      Deployment deployment, Player actor, boolean force) {
    CompletableFuture<RestoreResult> future = new CompletableFuture<>();
    if (!Bukkit.isPrimaryThread()) {
      sync(() -> restore(deployment, actor, force).whenComplete(copy(future)));
      return future;
    }
    if (!busyDecorations.add(deployment.decorationId()))
      return CompletableFuture.failedFuture(new IllegalStateException("busy"));
    DecorationPreRestoreEvent event = new DecorationPreRestoreEvent(deployment);
    Bukkit.getPluginManager().callEvent(event);
    if (event.isCancelled()) {
      busyDecorations.remove(deployment.decorationId());
      return CompletableFuture.failedFuture(new CancellationException("Cancelled by event"));
    }
    World world = Bukkit.getWorld(deployment.target().worldId());
    long covered =
        deployment.expectedBlocks().keySet().stream()
            .filter(
                k -> {
                  Deque<UUID> stack = owners.get(k);
                  return stack != null
                      && !stack.isEmpty()
                      && !stack.peekLast().equals(deployment.id());
                })
            .count();
    if (covered > 0) {
      busyDecorations.remove(deployment.decorationId());
      return CompletableFuture.failedFuture(new OverlapException((int) covered));
    }
    if (world == null) {
      busyDecorations.remove(deployment.decorationId());
      return CompletableFuture.failedFuture(
          new IllegalStateException("world-missing:" + deployment.target().worldName()));
    }
    CompletableFuture.supplyAsync(
            () -> {
              try {
                return snapshots.readSnapshot(deployment.id());
              } catch (Exception e) {
                throw new CompletionException(e);
              }
            })
        .whenComplete(
            (before, error) ->
                sync(
                    () -> {
                      if (error != null) {
                        busyDecorations.remove(deployment.decorationId());
                        future.completeExceptionally(unwrap(error));
                        return;
                      }
                      Set<Chunk> tickets = loadChunks(world, deployment.expectedBlocks().keySet());
                      List<BlockKey> conflicts = new ArrayList<>();
                      for (var e : deployment.expectedBlocks().entrySet()) {
                        BlockKey k = e.getKey();
                        if (!currentFingerprint(world, k, deployment.target()).equals(e.getValue()))
                          conflicts.add(k);
                      }
                      ConflictPolicy policy =
                          force ? ConflictPolicy.FORCE : config.conflictPolicy();
                      if (!conflicts.isEmpty() && policy == ConflictPolicy.ABORT) {
                        release(tickets);
                        busyDecorations.remove(deployment.decorationId());
                        deployment.state(TransactionState.ACTIVE);
                        persistQuiet(deployment, "RESTORE", "Conflicts: " + conflicts.size());
                        future.completeExceptionally(
                            new ConflictException(deployment.id(), conflicts.size()));
                        return;
                      }
                      deployment.state(TransactionState.RESTORING);
                      persistQuiet(deployment, "RESTORE", "");
                      Set<BlockKey> skip =
                          policy == ConflictPolicy.WARN_AND_SKIP ? Set.copyOf(conflicts) : Set.of();
                      int missing = 0;
                      for (UUID id : deployment.spawnedEntities().values()) {
                        Entity entity = Bukkit.getEntity(id);
                        if (entity != null) entity.remove();
                        else missing++;
                      }
                      for (Entity entity : new ArrayList<>(world.getEntities())) {
                        String owner =
                            entity
                                .getPersistentDataContainer()
                                .get(
                                    es.mrdino.decoswap.DecoSwapPlugin.DEPLOYMENT_ID_KEY,
                                    org.bukkit.persistence.PersistentDataType.STRING);
                        if (deployment.id().toString().equals(owner)) entity.remove();
                      }
                      applyRestore(
                          deployment, before, world, actor, skip, missing, tickets, future);
                    }));
    return future;
  }

  private void applyRestore(
      Deployment d,
      List<BlockRecord> before,
      World world,
      Player actor,
      Set<BlockKey> skip,
      int missing,
      Set<Chunk> tickets,
      CompletableFuture<RestoreResult> future) {
    BossBar bar = bar(actor, "restore.progress", d.decorationName(), 0, Math.max(1, before.size()));
    new BukkitRunnable() {
      int index;

      public void run() {
        try {
          int budget = config.blocksPerTick();
          while (index < before.size() && budget-- > 0) {
            BlockRecord r = before.get(index++);
            int x = (int) Math.floor(d.target().x()) + r.x(),
                y = (int) Math.floor(d.target().y()) + r.y(),
                z = (int) Math.floor(d.target().z()) + r.z();
            BlockKey key = new BlockKey(world.getUID(), x, y, z);
            if (!skip.contains(key))
              blockCodec.apply(world.getBlockAt(x, y, z), r, StructureRotation.NONE);
            progress(bar, actor, "restore.progress", d.decorationName(), index, before.size());
          }
          if (index < before.size()) return;
          d.state(TransactionState.COMMITTED);
          persistQuiet(d, "RESTORE", "");
          active.remove(d.id());
          recovery.remove(d.id());
          d.expectedBlocks()
              .keySet()
              .forEach(
                  k -> {
                    Deque<UUID> stack = owners.get(k);
                    if (stack != null) {
                      stack.removeLastOccurrence(d.id());
                      if (stack.isEmpty()) owners.remove(k);
                    }
                  });
          busyDecorations.remove(d.decorationId());
          release(tickets);
          try {
            snapshots.complete(
                d.id(), plugin.getConfig().getBoolean("snapshots.archive-completed", true));
            journal.archive(d.id());
          } catch (IOException e) {
            plugin
                .getLogger()
                .log(Level.WARNING, "Could not archive completed snapshot " + d.id(), e);
          }
          hide(bar, actor);
          RestoreResult result = new RestoreResult(d, missing, skip.size());
          Bukkit.getPluginManager().callEvent(new DecorationRestoreEvent(d, missing, skip.size()));
          future.complete(result);
          cancel();
        } catch (Exception e) {
          d.state(TransactionState.RECOVERY_REQUIRED);
          persistQuiet(d, "RESTORE", e.toString());
          recovery.put(
              d.id(),
              new TransactionJournal.Entry(
                  d.id(), "RESTORE", d.state(), d, Instant.now().toString(), e.toString()));
          busyDecorations.remove(d.decorationId());
          release(tickets);
          hide(bar, actor);
          future.completeExceptionally(e);
          cancel();
        }
      }
    }.runTaskTimer(plugin, 1, 1);
  }

  public CompletableFuture<RestoreResult> recoveryRollback(UUID id, Player actor) {
    TransactionJournal.Entry e = recovery.get(id);
    if (e == null)
      return CompletableFuture.failedFuture(new IllegalArgumentException("not-active"));
    return restore(e.deployment(), actor, true);
  }

  public boolean recoveryAccept(UUID id) {
    TransactionJournal.Entry e = recovery.remove(id);
    if (e == null) return false;
    try {
      snapshots.complete(id, true);
      journal.archive(id);
      Deployment d = e.deployment();
      active.remove(id);
      d.expectedBlocks()
          .keySet()
          .forEach(
              k -> {
                Deque<UUID> stack = owners.get(k);
                if (stack != null) {
                  stack.removeLastOccurrence(id);
                  if (stack.isEmpty()) owners.remove(k);
                }
              });
      busyDecorations.remove(d.decorationId());
      return true;
    } catch (IOException ex) {
      recovery.put(id, e);
      return false;
    }
  }

  private List<TargetBlock> targets(Decoration d, Anchor target, Rotation rotation, World world) {
    List<TargetBlock> result = new ArrayList<>(d.blockCount());
    for (BlockRecord b : d.blocks()) {
      double[] r = rotation.rotate(b.x(), b.z());
      int x = (int) Math.floor(target.x()) + (int) Math.round(r[0]),
          y = (int) Math.floor(target.y()) + b.y(),
          z = (int) Math.floor(target.z()) + (int) Math.round(r[1]);
      result.add(new TargetBlock(b, x, y, z, new BlockKey(world.getUID(), x, y, z)));
    }
    return result;
  }

  private StructureRotation structure(Rotation r) {
    return switch (r) {
      case NONE -> StructureRotation.NONE;
      case CLOCKWISE_90 -> StructureRotation.CLOCKWISE_90;
      case CLOCKWISE_180 -> StructureRotation.CLOCKWISE_180;
      case COUNTERCLOCKWISE_90 -> StructureRotation.COUNTERCLOCKWISE_90;
    };
  }

  private boolean sameHome(Decoration d, Anchor t) {
    return sameAnchor(d.homeAnchor(), t);
  }

  private boolean sameAnchor(Anchor a, Anchor b) {
    return a.worldId().equals(b.worldId())
        && Math.abs(a.x() - b.x()) < .01
        && Math.abs(a.y() - b.y()) < .01
        && Math.abs(a.z() - b.z()) < .01;
  }

  private void persistQuiet(Deployment d, String type, String error) {
    try {
      journal.persist(d, type, error);
    } catch (IOException e) {
      plugin.getLogger().log(Level.SEVERE, "Could not persist transaction " + d.id(), e);
    }
  }

  private Set<Chunk> loadChunks(World world, Collection<BlockKey> keys) {
    Set<Chunk> chunks = new HashSet<>();
    for (BlockKey key : keys) {
      Chunk chunk = world.getChunkAt(key.x() >> 4, key.z() >> 4);
      if (chunks.add(chunk)) chunk.addPluginChunkTicket(plugin);
    }
    return chunks;
  }

  private void release(Collection<Chunk> chunks) {
    chunks.forEach(chunk -> chunk.removePluginChunkTicket(plugin));
  }

  private String currentFingerprint(World world, BlockKey key, Anchor target) {
    try {
      return Fingerprint.block(
          blockCodec.capture(world.getBlockAt(key.x(), key.y(), key.z()), target));
    } catch (Exception e) {
      return "capture-error:" + e.getClass().getName();
    }
  }

  private BossBar bar(Player p, String key, String name, int done, int total) {
    BossBar b =
        BossBar.bossBar(
            p == null
                ? net.kyori.adventure.text.Component.text(name)
                : lang.component(
                    p, key, Map.of("name", name, "percent", 0, "done", done, "total", total)),
            0,
            BossBar.Color.BLUE,
            BossBar.Overlay.PROGRESS);
    if (p != null) p.showBossBar(b);
    return b;
  }

  private void progress(BossBar b, Player p, String key, String name, int done, int total) {
    if (p == null) return;
    float ratio = Math.min(1f, (float) done / Math.max(1, total));
    b.progress(ratio);
    b.name(
        lang.component(
            p,
            key,
            Map.of(
                "name", name, "percent", Math.round(ratio * 100), "done", done, "total", total)));
  }

  private void hide(BossBar b, Player p) {
    if (b != null && p != null) p.hideBossBar(b);
  }

  private void sync(Runnable r) {
    Bukkit.getScheduler().runTask(plugin, r);
  }

  private Throwable unwrap(Throwable e) {
    return e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
  }

  private <T> java.util.function.BiConsumer<T, Throwable> copy(CompletableFuture<T> target) {
    return (v, e) -> {
      if (e != null) target.completeExceptionally(e);
      else target.complete(v);
    };
  }

  private record TargetBlock(BlockRecord record, int x, int y, int z, BlockKey key) {}

  public record RestoreResult(Deployment deployment, int missingEntities, int skippedConflicts) {}

  public static final class OverlapException extends IllegalStateException {
    private final int count;

    public OverlapException(int count) {
      super("overlap");
      this.count = count;
    }

    public int count() {
      return count;
    }
  }

  public static final class ConflictException extends IllegalStateException {
    private final UUID deploymentId;
    private final int count;

    public ConflictException(UUID id, int count) {
      super("conflicts");
      deploymentId = id;
      this.count = count;
    }

    public UUID deploymentId() {
      return deploymentId;
    }

    public int count() {
      return count;
    }
  }
}
