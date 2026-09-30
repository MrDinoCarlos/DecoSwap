package es.mrdino.decoswap.decoration;

import es.mrdino.decoswap.deployment.DeploymentEngine;
import es.mrdino.decoswap.language.LanguageService;
import es.mrdino.decoswap.selection.PlayerSelection;
import es.mrdino.decoswap.selection.SelectionManager;
import es.mrdino.decoswap.storage.DecorationRepository;
import es.mrdino.decoswap.util.BlockKey;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Detects edits to saved home templates and immediately paints them red for admins. */
public final class ChangeMonitor implements Listener {
  private final JavaPlugin plugin;
  private final LanguageService lang;
  private final DecorationRepository decorations;
  private final DecorationService service;
  private final DeploymentEngine deployments;
  private final SelectionManager selections;
  private final Map<UUID, Long> pending = new LinkedHashMap<>();
  private final Map<String, String> lastReports = new HashMap<>();

  public ChangeMonitor(
      JavaPlugin plugin,
      LanguageService lang,
      DecorationRepository decorations,
      DecorationService service,
      DeploymentEngine deployments,
      SelectionManager selections) {
    this.plugin = plugin;
    this.lang = lang;
    this.decorations = decorations;
    this.service = service;
    this.deployments = deployments;
    this.selections = selections;
    plugin.getServer().getPluginManager().registerEvents(this, plugin);
    plugin
        .getServer()
        .getScheduler()
        .runTaskTimer(plugin, this::flush, 10L, 10L);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void blockBreak(BlockBreakEvent event) {
    mark(event.getBlock().getLocation());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void blockPlace(BlockPlaceEvent event) {
    mark(event.getBlock().getLocation());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void entityDamage(EntityDamageEvent event) {
    mark(event.getEntity());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void entityDeath(EntityDeathEvent event) {
    mark(event.getEntity());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void entityTeleport(EntityTeleportEvent event) {
    mark(event.getEntity());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void entityTransform(EntityTransformEvent event) {
    mark(event.getEntity());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void entityInteraction(PlayerInteractEntityEvent event) {
    mark(event.getRightClicked());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void armorStandManipulation(PlayerArmorStandManipulateEvent event) {
    mark(event.getRightClicked());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void entityChangesBlock(EntityChangeBlockEvent event) {
    mark(event.getBlock().getLocation());
  }

  private void mark(Location location) {
    if (location == null || location.getWorld() == null) return;
    for (Decoration d : decorations.all())
      if (!deployments.isBusy(d.uuid()) && contains(d, location)) pending.put(d.uuid(), System.currentTimeMillis());
  }

  private void mark(Entity entity) {
    if (entity instanceof Player || entity.getPersistentDataContainer().has(es.mrdino.decoswap.DecoSwapPlugin.VISUAL_KEY)) return;
    mark(entity.getLocation());
  }

  private boolean contains(Decoration d, Location location) {
    Anchor anchor = d.homeAnchor();
    if (!anchor.worldId().equals(location.getWorld().getUID())) return false;
    for (BlockRecord block : d.blocks())
      if ((int) Math.floor(anchor.x()) + block.x() == location.getBlockX()
          && (int) Math.floor(anchor.y()) + block.y() == location.getBlockY()
          && (int) Math.floor(anchor.z()) + block.z() == location.getBlockZ()) return true;
    for (EntityRecord entity : d.entities())
      if (Math.abs(anchor.x() + entity.x() - location.getX()) <= 1.5
          && Math.abs(anchor.y() + entity.y() - location.getY()) <= 2.5
          && Math.abs(anchor.z() + entity.z() - location.getZ()) <= 1.5) return true;
    return false;
  }

  private void flush() {
    if (pending.isEmpty()) return;
    UUID[] ids = pending.keySet().toArray(UUID[]::new);
    pending.clear();
    for (UUID id : ids) {
      Decoration d = decorations.findByUuid(id).orElse(null);
      if (d == null || deployments.isBusy(id)) continue;
      for (Player admin : Bukkit.getOnlinePlayers()) {
        if (!admin.hasPermission("decoswap.decoration.changes")
            && !admin.hasPermission("decoswap.admin")) continue;
        try {
          DecorationChanges report = service.detectChanges(admin, d, new PlayerSelection());
          selections.showChanges(admin, report);
          String signature = signature(report);
          String reportKey = admin.getUniqueId() + ":" + id;
          if (report.isEmpty()) {
            lastReports.remove(reportKey);
            continue;
          }
          if (signature.equals(lastReports.get(reportKey))) continue;
          lastReports.put(reportKey, signature);
          lang.send(
              admin,
              "changes.auto-found",
              Map.of(
                  "name", d.displayName(),
                  "total", report.totalChanges(),
                  "blocks", report.changedBlocks().size(),
                  "entities", report.changedEntities().size(),
                  "missing", report.missingEntities(),
                  "additional", report.additionalEntities()));
          report.changedBlocks().stream().limit(8).forEach(key -> sendBlock(admin, key));
          report.entityDetails().stream().limit(8).forEach(change -> sendEntity(admin, change));
          lang.send(admin, "changes.apply", Map.of("name", d.displayName(), "id", d.id()));
        } catch (Exception error) {
          plugin.getLogger().fine("Automatic change scan failed for " + d.id() + ": " + error.getMessage());
        }
      }
    }
  }

  private void sendBlock(Player player, BlockKey key) {
    lang.send(
        player,
        "changes.block",
        Map.of(
            "world",
            Optional.ofNullable(Bukkit.getWorld(key.worldId())).map(World::getName).orElse("unknown"),
            "x",
            key.x(),
            "y",
            key.y(),
            "z",
            key.z()));
  }

  private void sendEntity(Player player, EntityChange change) {
    lang.send(
        player,
        "changes.entity",
        Map.of(
            "name",
            change.name(),
            "type",
            change.type(),
            "world",
            Optional.ofNullable(Bukkit.getWorld(change.location().worldId()))
                .map(World::getName)
                .orElse("unknown"),
            "x",
            change.location().x(),
            "y",
            change.location().y(),
            "z",
            change.location().z(),
            "status",
            lang.component(
                player,
                "changes.status."
                    + (change.missing() ? "missing" : change.additional() ? "additional" : "changed"))));
  }

  private String signature(DecorationChanges report) {
    return report.changedBlocks()
            + "|"
            + report.entityDetails()
            + "|"
            + report.missingEntities()
            + "|"
            + report.additionalEntities();
  }
}
