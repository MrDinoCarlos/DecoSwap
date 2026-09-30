package es.mrdino.decoswap.selection;

import es.mrdino.decoswap.config.PluginConfig;
import es.mrdino.decoswap.decoration.DecorationChanges;
import es.mrdino.decoswap.language.LanguageService;
import es.mrdino.decoswap.util.BlockKey;
import es.mrdino.decoswap.visual.VisualService;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class SelectionManager {
  private final JavaPlugin plugin;
  private final LanguageService lang;
  private PluginConfig config;
  private final Map<UUID, PlayerSelection> selections = new HashMap<>();
  private final Map<UUID, RegionScan> scans = new HashMap<>();
  private VisualService visuals;

  public SelectionManager(JavaPlugin plugin, LanguageService lang, PluginConfig config) {
    this.plugin = plugin;
    this.lang = lang;
    this.config = config;
  }

  public void visuals(VisualService visuals) {
    this.visuals = visuals;
  }

  public void config(PluginConfig c) {
    config = c;
  }

  public PlayerSelection get(Player player) {
    return selections.computeIfAbsent(player.getUniqueId(), id -> new PlayerSelection());
  }

  public Optional<PlayerSelection> find(UUID id) {
    return Optional.ofNullable(selections.get(id));
  }

  public void changed(Player player) {
    if (visuals != null) visuals.rebuild(player, get(player));
    showStatus(player);
  }

  public void showStatus(Player p) {
    PlayerSelection s = get(p);
    p.sendActionBar(
        lang.component(
            p,
            "selection.actionbar",
            Map.of(
                "mode",
                lang.component(p, "mode." + s.mode().name().toLowerCase()),
                "blocks",
                s.blocks().size(),
                "entities",
                s.entities().size())));
  }

  public void showChanges(Player player, DecorationChanges report) {
    if (visuals != null) visuals.showChanges(player, report);
  }

  public void clearChanges(Player player) {
    if (visuals != null) visuals.clearChanges(player);
  }

  public CompletableFuture<ImportResult> importRegion(Player player, String type) {
    PlayerSelection s = get(player);
    Location a = s.pos1(), b = s.pos2();
    if (a == null || b == null || a.getWorld() != b.getWorld())
      throw new IllegalStateException("region-incomplete");
    long sx = Math.abs(a.getBlockX() - b.getBlockX()) + 1L,
        sy = Math.abs(a.getBlockY() - b.getBlockY()) + 1L,
        sz = Math.abs(a.getBlockZ() - b.getBlockZ()) + 1L,
        volume = sx * sy * sz;
    if (volume > config.maxRegionVolume())
      throw new IllegalArgumentException("region-too-large:" + volume);
    if (scans.containsKey(player.getUniqueId())) throw new IllegalStateException("busy");
    RegionScan scan = new RegionScan(player, s, type, a, b, volume);
    scans.put(player.getUniqueId(), scan);
    scan.runTaskTimer(plugin, 1, 1);
    return scan.future;
  }

  public boolean cancelRegion(Player player) {
    RegionScan scan = scans.remove(player.getUniqueId());
    if (scan == null) return false;
    scan.cancel();
    scan.future.completeExceptionally(new CancellationException("Region scan cancelled"));
    return true;
  }

  public void remove(Player player) {
    cancelRegion(player);
    if (visuals != null) visuals.clear(player.getUniqueId());
    selections.remove(player.getUniqueId());
  }

  public Collection<PlayerSelection> all() {
    return Collections.unmodifiableCollection(selections.values());
  }

  public record ImportResult(int blocks, int entities, long volume) {}

  private final class RegionScan extends BukkitRunnable {
    private final Player player;
    private final PlayerSelection selection;
    private final String type;
    private final World world;
    private final int minX, minY, minZ, sizeY, sizeZ;
    private final long volume;
    private final CompletableFuture<ImportResult> future = new CompletableFuture<>();
    private long index;
    private int blocks, entities;

    private RegionScan(
        Player player,
        PlayerSelection selection,
        String type,
        Location a,
        Location b,
        long volume) {
      this.player = player;
      this.selection = selection;
      this.type = type;
      this.world = a.getWorld();
      minX = Math.min(a.getBlockX(), b.getBlockX());
      minY = Math.min(a.getBlockY(), b.getBlockY());
      minZ = Math.min(a.getBlockZ(), b.getBlockZ());
      sizeY = Math.abs(a.getBlockY() - b.getBlockY()) + 1;
      sizeZ = Math.abs(a.getBlockZ() - b.getBlockZ()) + 1;
      this.volume = volume;
      selection.beginBulk();
    }

    public void run() {
      try {
        int budget = config.blocksPerTick();
        if (type.equals("entities")) index = volume;
        while (index < volume && budget-- > 0) {
          long current = index++;
          int y = minY + (int) (current % sizeY);
          long column = current / sizeY;
          int z = minZ + (int) (column % sizeZ), x = minX + (int) (column / sizeZ);
          world.getChunkAt(x >> 4, z >> 4);
          var block = world.getBlockAt(x, y, z);
          if (!block.getType().isAir()
              && selection.add(BlockKey.of(block.getLocation()), config.maxBlocks())) blocks++;
          if (selection.blocks().size() >= config.maxBlocks()) index = volume;
        }
        if (index < volume) {
          int percent = (int) Math.min(100, index * 100 / volume);
          player.sendActionBar(
              lang.component(
                  player,
                  "selection.region-progress",
                  Map.of("percent", percent, "done", index, "total", volume)));
          return;
        }
        if (!type.equals("blocks")) {
          Location a = selection.pos1(), b = selection.pos2();
          var box =
              new org.bukkit.util.BoundingBox(
                  Math.min(a.getBlockX(), b.getBlockX()),
                  Math.min(a.getBlockY(), b.getBlockY()),
                  Math.min(a.getBlockZ(), b.getBlockZ()),
                  Math.max(a.getBlockX(), b.getBlockX()) + 1,
                  Math.max(a.getBlockY(), b.getBlockY()) + 1,
                  Math.max(a.getBlockZ(), b.getBlockZ()) + 1);
          for (Entity e : world.getNearbyEntities(box)) {
            if (e instanceof Player
                || e.getPersistentDataContainer().has(es.mrdino.decoswap.DecoSwapPlugin.VISUAL_KEY)
                || (!config.captureNonPersistent() && !e.isPersistent())) continue;
            if (selection.add(e.getUniqueId(), config.maxEntities())) entities++;
          }
        }
        scans.remove(player.getUniqueId());
        changed(player);
        future.complete(new ImportResult(blocks, entities, volume));
        cancel();
      } catch (Exception e) {
        scans.remove(player.getUniqueId());
        future.completeExceptionally(e);
        cancel();
      }
    }
  }
}
