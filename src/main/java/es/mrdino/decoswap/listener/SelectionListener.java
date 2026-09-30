package es.mrdino.decoswap.listener;

import es.mrdino.decoswap.DecoSwapPlugin;
import es.mrdino.decoswap.config.PluginConfig;
import es.mrdino.decoswap.decoration.Anchor;
import es.mrdino.decoswap.language.LanguageService;
import es.mrdino.decoswap.selection.*;
import es.mrdino.decoswap.util.BlockKey;
import es.mrdino.decoswap.visual.VisualService;
import java.util.Map;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

public final class SelectionListener implements Listener {
  private final JavaPlugin plugin;
  private PluginConfig config;
  private final SelectionManager selections;
  private final SelectorTool tool;
  private final VisualService visuals;
  private final LanguageService lang;

  public SelectionListener(
      JavaPlugin plugin,
      PluginConfig config,
      SelectionManager selections,
      SelectorTool tool,
      VisualService visuals,
      LanguageService lang) {
    this.plugin = plugin;
    this.config = config;
    this.selections = selections;
    this.tool = tool;
    this.visuals = visuals;
    this.lang = lang;
  }

  public void config(PluginConfig c) {
    config = c;
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void interact(PlayerInteractEvent event) {
    if (event.getHand() != EquipmentSlot.HAND || !tool.isSelector(event.getItem())) return;
    event.setCancelled(true);
    Player p = event.getPlayer();
    PlayerSelection s = selections.get(p);
    if (!p.hasPermission("decoswap.select")) return;
    if (s.mode() == SelectionMode.OBJECT && event.getClickedBlock() != null) {
      BlockKey key = BlockKey.of(event.getClickedBlock().getLocation());
      if (!s.blocks().contains(key) && s.blocks().size() >= config.maxBlocks()) {
        lang.send(p, "error.limit", Map.of("limit", config.maxBlocks()));
        return;
      }
      boolean added = s.toggle(key);
      feedback(p, added, "selection.added-block", "selection.removed-block");
      selections.changed(p);
    } else if (s.mode() == SelectionMode.REGION && event.getClickedBlock() != null) {
      int position = event.getAction().isLeftClick() ? 1 : 2;
      s.position(position, event.getClickedBlock().getLocation());
      lang.send(
          p,
          "selection.region-pos",
          Map.of(
              "position",
              position,
              "x",
              event.getClickedBlock().getX(),
              "y",
              event.getClickedBlock().getY(),
              "z",
              event.getClickedBlock().getZ()));
      selections.changed(p);
    } else if (s.mode() == SelectionMode.ANCHOR) {
      Location l =
          event.getClickedBlock() == null
              ? p.getLocation()
              : event.getClickedBlock().getLocation().add(.5, 0, .5);
      l.setYaw(p.getYaw());
      l.setPitch(p.getPitch());
      setAnchor(p, s, l);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void entityInteract(PlayerInteractEntityEvent event) {
    if (event.getHand() != EquipmentSlot.HAND
        || !tool.isSelector(event.getPlayer().getInventory().getItemInMainHand())) return;
    event.setCancelled(true);
    toggleEntity(event.getPlayer(), event.getRightClicked());
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void entityDamage(EntityDamageByEntityEvent event) {
    if (!(event.getDamager() instanceof Player p)
        || !tool.isSelector(p.getInventory().getItemInMainHand())) return;
    event.setCancelled(true);
    toggleEntity(p, event.getEntity());
  }

  private void toggleEntity(Player p, Entity entity) {
    PlayerSelection s = selections.get(p);
    if (s.mode() != SelectionMode.OBJECT
        || entity instanceof Player
        || entity.getPersistentDataContainer().has(DecoSwapPlugin.VISUAL_KEY)
        || (!config.captureNonPersistent() && !entity.isPersistent())) return;
    if (!s.entities().contains(entity.getUniqueId())
        && s.entities().size() >= config.maxEntities()) {
      lang.send(p, "error.limit", Map.of("limit", config.maxEntities()));
      return;
    }
    boolean added = s.toggle(entity.getUniqueId());
    feedback(p, added, "selection.added-entity", "selection.removed-entity");
    selections.changed(p);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void swap(PlayerSwapHandItemsEvent event) {
    Player p = event.getPlayer();
    if (!p.isSneaking() || !tool.isSelector(p.getInventory().getItemInMainHand())) return;
    event.setCancelled(true);
    PlayerSelection s = selections.get(p);
    s.mode(s.mode().next());
    lang.send(p, "mode.changed", Map.of("mode", s.mode().name()));
    selections.changed(p);
  }

  @EventHandler
  public void quit(PlayerQuitEvent e) {
    selections.remove(e.getPlayer());
  }

  @EventHandler
  public void join(PlayerJoinEvent e) {
    visuals.hideFromJoiner(e.getPlayer());
  }

  @EventHandler
  public void world(PlayerChangedWorldEvent e) {
    Bukkit.getScheduler().runTask(plugin, () -> selections.changed(e.getPlayer()));
  }

  private void setAnchor(Player p, PlayerSelection s, Location l) {
    s.anchor(Anchor.from(l));
    lang.send(
        p,
        "anchor.set",
        Map.of(
            "x",
            fmt(l.getX()),
            "y",
            fmt(l.getY()),
            "z",
            fmt(l.getZ()),
            "facing",
            s.anchor().cardinal().name()));
    selections.changed(p);
  }

  private void feedback(Player p, boolean added, String yes, String no) {
    lang.send(p, added ? yes : no);
    if (config.sounds())
      p.playSound(
          p.getLocation(),
          added ? Sound.UI_BUTTON_CLICK : Sound.BLOCK_NOTE_BLOCK_BASS,
          .4f,
          added ? 1.5f : .8f);
  }

  private String fmt(double v) {
    return String.format(java.util.Locale.ROOT, "%.2f", v);
  }
}
