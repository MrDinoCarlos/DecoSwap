package es.mrdino.decoswap.visual;

import es.mrdino.decoswap.DecoSwapPlugin;
import es.mrdino.decoswap.config.PluginConfig;
import es.mrdino.decoswap.decoration.Anchor;
import es.mrdino.decoswap.decoration.DecorationChanges;
import es.mrdino.decoswap.language.LanguageService;
import es.mrdino.decoswap.selection.*;
import es.mrdino.decoswap.util.BlockKey;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class VisualService {
  private static final float THICKNESS = .025f;
  private final JavaPlugin plugin;
  private PluginConfig config;
  private final SelectionManager selections;
  private final SelectorTool selector;
  private final LanguageService lang;
  private final Map<UUID, List<Entity>> permanent = new HashMap<>(), targets = new HashMap<>();
  private final Map<UUID, DecorationChanges> changes = new HashMap<>();
  private final Map<UUID, String> targetKeys = new HashMap<>();
  private BukkitTask task;

  public VisualService(
      JavaPlugin plugin,
      PluginConfig config,
      SelectionManager selections,
      SelectorTool selector,
      LanguageService lang) {
    this.plugin = plugin;
    this.config = config;
    this.selections = selections;
    this.selector = selector;
    this.lang = lang;
  }

  public void start() {
    cleanupOrphans();
    task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickTargets, 5, 5);
  }

  public void config(PluginConfig c) {
    config = c;
    for (Player p : Bukkit.getOnlinePlayers())
      selections.find(p.getUniqueId()).ifPresent(s -> rebuild(p, s));
  }

  public void stop() {
    if (task != null) task.cancel();
    permanent.values().forEach(this::remove);
    targets.values().forEach(this::remove);
    permanent.clear();
    targets.clear();
    changes.clear();
  }

  public void clear(UUID owner) {
    remove(permanent.remove(owner));
    remove(targets.remove(owner));
    targetKeys.remove(owner);
    changes.remove(owner);
  }

  public void showChanges(Player owner, DecorationChanges report) {
    changes.put(owner.getUniqueId(), report);
    rebuild(owner, selections.get(owner));
  }

  public void clearChanges(Player owner) {
    if (changes.remove(owner.getUniqueId()) != null) rebuild(owner, selections.get(owner));
  }

  public void rebuild(Player owner, PlayerSelection selection) {
    remove(permanent.remove(owner.getUniqueId()));
    if (!config.visuals()) return;
    List<Entity> list = new ArrayList<>();
    World world = owner.getWorld();
    List<BlockKey> sameWorld =
        selection.blocks().stream().filter(k -> k.worldId().equals(world.getUID())).toList();
    if (sameWorld.size() <= config.blockOutlineLimit()) {
      for (BlockKey key : sameWorld)
        box(
            owner,
            world,
            new BoundingBox(key.x(), key.y(), key.z(), key.x() + 1, key.y() + 1, key.z() + 1),
            config.lineMaterial(),
            list);
    } else if (!sameWorld.isEmpty())
      box(owner, world, bounds(sameWorld), config.lineMaterial(), list);
    int shown = 0;
    for (UUID id : selection.entities()) {
      if (shown++ >= config.entityOutlineLimit()) break;
      Entity entity = Bukkit.getEntity(id);
      if (entity != null && entity.getWorld() == world)
        box(owner, world, entity.getBoundingBox(), config.entityLineMaterial(), list);
    }
    if (selection.mode() == SelectionMode.REGION
        && selection.pos1() != null
        && selection.pos2() != null
        && selection.pos1().getWorld() == world) {
      Location a = selection.pos1(), b = selection.pos2();
      box(
          owner,
          world,
          new BoundingBox(
              Math.min(a.getBlockX(), b.getBlockX()),
              Math.min(a.getBlockY(), b.getBlockY()),
              Math.min(a.getBlockZ(), b.getBlockZ()),
              Math.max(a.getBlockX(), b.getBlockX()) + 1,
              Math.max(a.getBlockY(), b.getBlockY()) + 1,
              Math.max(a.getBlockZ(), b.getBlockZ()) + 1),
          config.lineMaterial(),
          list);
    }
    if (selection.mode() == SelectionMode.ANCHOR
        && selection.anchor() != null
        && selection.anchor().worldId().equals(world.getUID()))
      anchor(owner, selection.anchor(), list);
    DecorationChanges report = changes.get(owner.getUniqueId());
    if (report != null) {
      for (BlockKey key : report.changedBlocks())
        if (key.worldId().equals(world.getUID()))
          box(
              owner,
              world,
              new BoundingBox(key.x(), key.y(), key.z(), key.x() + 1, key.y() + 1, key.z() + 1),
              config.changedLineMaterial(),
              list);
      for (BlockKey key : report.changedEntityLocations())
        if (key.worldId().equals(world.getUID()))
          box(
              owner,
              world,
              new BoundingBox(
                  key.x() + .2,
                  key.y() + .2,
                  key.z() + .2,
                  key.x() + .8,
                  key.y() + .8,
                  key.z() + .8),
              config.changedLineMaterial(),
              list);
      for (UUID id : report.changedEntities()) {
        Entity entity = Bukkit.getEntity(id);
        if (entity != null && entity.getWorld() == world)
          box(owner, world, entity.getBoundingBox(), config.changedLineMaterial(), list);
      }
    }
    permanent.put(owner.getUniqueId(), list);
  }

  private void tickTargets() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (!selector.isSelector(player.getInventory().getItemInMainHand())) {
        clearTarget(player);
        continue;
      }
      PlayerSelection selection = selections.get(player);
      if (selection.mode() != SelectionMode.OBJECT) {
        clearTarget(player);
        continue;
      }
      var block = player.rayTraceBlocks(8);
      var entities =
          player
              .getWorld()
              .rayTraceEntities(
                  player.getEyeLocation(),
                  player.getEyeLocation().getDirection(),
                  8,
                  e ->
                      !(e instanceof Player)
                          && !e.getPersistentDataContainer().has(DecoSwapPlugin.VISUAL_KEY));
      double
          bd =
              block == null
                  ? Double.MAX_VALUE
                  : block.getHitPosition().distance(player.getEyeLocation().toVector()),
          ed =
              entities == null
                  ? Double.MAX_VALUE
                  : entities.getHitPosition().distance(player.getEyeLocation().toVector());
      String key;
      BoundingBox box;
      if (ed < bd) {
        Entity e = entities.getHitEntity();
        if (e == null) {
          clearTarget(player);
          continue;
        }
        key = "e:" + e.getUniqueId();
        box = e.getBoundingBox();
      } else if (block != null && block.getHitBlock() != null) {
        var b = block.getHitBlock();
        key = "b:" + b.getX() + ":" + b.getY() + ":" + b.getZ();
        box =
            new BoundingBox(b.getX(), b.getY(), b.getZ(), b.getX() + 1, b.getY() + 1, b.getZ() + 1);
      } else {
        clearTarget(player);
        continue;
      }
      if (key.equals(targetKeys.get(player.getUniqueId()))) continue;
      clearTarget(player);
      List<Entity> list = new ArrayList<>();
      box(player, player.getWorld(), box, Material.WHITE_STAINED_GLASS, list);
      targets.put(player.getUniqueId(), list);
      targetKeys.put(player.getUniqueId(), key);
      selections.showStatus(player);
    }
  }

  private void clearTarget(Player p) {
    remove(targets.remove(p.getUniqueId()));
    targetKeys.remove(p.getUniqueId());
  }

  private BoundingBox bounds(List<BlockKey> keys) {
    int minX = Integer.MAX_VALUE,
        minY = Integer.MAX_VALUE,
        minZ = Integer.MAX_VALUE,
        maxX = Integer.MIN_VALUE,
        maxY = Integer.MIN_VALUE,
        maxZ = Integer.MIN_VALUE;
    for (BlockKey k : keys) {
      minX = Math.min(minX, k.x());
      minY = Math.min(minY, k.y());
      minZ = Math.min(minZ, k.z());
      maxX = Math.max(maxX, k.x() + 1);
      maxY = Math.max(maxY, k.y() + 1);
      maxZ = Math.max(maxZ, k.z() + 1);
    }
    return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
  }

  private void box(Player owner, World world, BoundingBox b, Material material, List<Entity> out) {
    double minX = b.getMinX(),
        minY = b.getMinY(),
        minZ = b.getMinZ(),
        maxX = b.getMaxX(),
        maxY = b.getMaxY(),
        maxZ = b.getMaxZ();
    double lx = Math.max(.01, maxX - minX),
        ly = Math.max(.01, maxY - minY),
        lz = Math.max(.01, maxZ - minZ);
    for (double y : new double[] {minY, maxY})
      for (double z : new double[] {minZ, maxZ})
        line(owner, world, minX, y, z, (float) lx, THICKNESS, THICKNESS, material, out);
    for (double x : new double[] {minX, maxX})
      for (double z : new double[] {minZ, maxZ})
        line(owner, world, x, minY, z, THICKNESS, (float) ly, THICKNESS, material, out);
    for (double x : new double[] {minX, maxX})
      for (double y : new double[] {minY, maxY})
        line(owner, world, x, y, minZ, THICKNESS, THICKNESS, (float) lz, material, out);
  }

  private void anchor(Player owner, Anchor a, List<Entity> out) {
    World world = owner.getWorld();
    double size = .16;
    line(
        owner,
        world,
        a.x() - size,
        a.y() - size,
        a.z() - size,
        (float) (size * 2),
        (float) (size * 2),
        (float) (size * 2),
        config.anchorMaterial(),
        out);
    var f = a.forward();
    line(
        owner,
        world,
        a.x(),
        a.y() + .05,
        a.z(),
        (float) (Math.abs(f.getX()) * 1.6 + THICKNESS),
        THICKNESS,
        (float) (Math.abs(f.getZ()) * 1.6 + THICKNESS),
        config.anchorMaterial(),
        out);
    TextDisplay label =
        world.spawn(
            new Location(world, a.x(), a.y() + .45, a.z()),
            TextDisplay.class,
            e -> {
              e.text(lang.component(owner, "anchor.label"));
              e.setBillboard(Display.Billboard.CENTER);
              e.setSeeThrough(true);
              e.setShadowed(true);
              prepare(owner, e);
            });
    out.add(label);
  }

  private void line(
      Player owner,
      World world,
      double x,
      double y,
      double z,
      float sx,
      float sy,
      float sz,
      Material material,
      List<Entity> out) {
    BlockDisplay display =
        world.spawn(
            new Location(world, x, y, z),
            BlockDisplay.class,
            e -> {
              e.setBlock(material.createBlockData());
              e.setTransformation(
                  new Transformation(
                      new Vector3f(),
                      new Quaternionf(),
                      new Vector3f(sx, sy, sz),
                      new Quaternionf()));
              e.setGlowing(true);
              e.setGlowColorOverride(
                  material == Material.LIME_STAINED_GLASS
                      ? Color.LIME
                      : material == Material.RED_STAINED_GLASS ? Color.RED : Color.AQUA);
              e.setBrightness(new Display.Brightness(15, 15));
              e.setViewRange(1.0f);
              prepare(owner, e);
            });
    out.add(display);
  }

  private void prepare(Player owner, Entity e) {
    e.setPersistent(false);
    e.setInvulnerable(true);
    e.setGravity(false);
    e.setSilent(true);
    e.setVisibleByDefault(false);
    e.getPersistentDataContainer()
        .set(DecoSwapPlugin.VISUAL_KEY, PersistentDataType.STRING, owner.getUniqueId().toString());
    owner.showEntity(plugin, e);
  }

  private void remove(Collection<Entity> entities) {
    if (entities != null)
      entities.forEach(
          e -> {
            if (e != null && e.isValid()) e.remove();
          });
  }

  public void hideFromJoiner(Player player) {
    permanent.values().forEach(l -> l.forEach(e -> player.hideEntity(plugin, e)));
    targets.values().forEach(l -> l.forEach(e -> player.hideEntity(plugin, e)));
  }

  public void cleanupOrphans() {
    for (World world : Bukkit.getWorlds())
      for (Entity entity : world.getEntities())
        if (entity.getPersistentDataContainer().has(DecoSwapPlugin.VISUAL_KEY)) entity.remove();
  }
}
