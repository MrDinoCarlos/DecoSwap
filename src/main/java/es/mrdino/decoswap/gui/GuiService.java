package es.mrdino.decoswap.gui;

import es.mrdino.decoswap.decoration.*;
import es.mrdino.decoswap.deployment.*;
import es.mrdino.decoswap.group.*;
import es.mrdino.decoswap.language.LanguageService;
import es.mrdino.decoswap.selection.SelectionManager;
import es.mrdino.decoswap.storage.DecorationRepository;
import es.mrdino.decoswap.util.Rotation;
import es.mrdino.decoswap.util.TextureReference;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;

public final class GuiService implements Listener {
  private static final int PAGE_SIZE = 45;
  private static final String HEAD_DECORATION =
      "143e0f73d8f447a573f33226fe4f9683b64dda42e7142c130b5b33c29f160183";
  private static final String HEAD_GROUP =
      "1f575bb54a2e9133aaa1310a14642f78a014fcc9360774171663d34db236ccc4";
  private static final String HEAD_ACTIVE =
      "505f7080eb7ad15a68ae161322cd2ba25da5d24cda452d0e88c8e496ebc6b7a0";
  private static final String HEAD_SELECTION =
      "73c3a9bdc8c40c42d841daeb71ea9e7d1c54ab31a23a2d926591d55514117e5d";
  private static final String HEAD_HELP =
      "46ba63344f49dd1c4f5488e926bf3d9e2b29916a6c50d610bb40a5273dc8c82";
  private static final String HEAD_BACK =
      "37aee9a75bf0df7897183015cca0b2a7d755c63388ff01752d5f4419fc645";
  private static final String HEAD_NEXT =
      "682ad1b9cb4dd21259c0d75aa315ff389c3cef752be3949338164bac84a96e";
  private static final String HEAD_CONFIRM =
      "a79a5c95ee17abfef45c8dc224189964944d560f19a44f19f8a46aef3fee4756";
  private static final String HEAD_CANCEL =
      "58f88f8c75f56d6de7f79b1afe1e5ba4e85190824c51cf1bd1c139eba5882001";
  private static final String ICON_CUSTOM = "__custom__";
  private static final String ICON_DEFAULT = "__default__";
  private static final List<GroupIcon> GROUP_ICONS =
      List.of(
          new GroupIcon(
              "gui.group-icon.christmas", "f0393a331ae7a2bda670736ff271ecf122f37bb81b56c27883a41444db9329dc"),
          new GroupIcon(
              "gui.group-icon.cap", "dd06f605114f78caaf282f9d1798cd7ccd6f2ed25b3a20510fee9d23ca3950c9"),
          new GroupIcon(
              "gui.group-icon.snowglobe", "186156d7f2132669c367ab89523c2e1b9866e40b2b891393744657f1c355"),
          new GroupIcon(
              "gui.group-icon.halloween", "c8622a3d53ccc78436c0f66004cb4b7729c466ea10065e839a06b6288bfda986"),
          new GroupIcon(
              "gui.group-icon.lantern", "55a96da10e9a1d00fd2ab72ed21fba75455b1085607858d2081455a25ddb3da0"));

  private final JavaPlugin plugin;
  private final LanguageService lang;
  private final GuideBookService guide;
  private final DecorationService decorationService;
  private final DecorationRepository decorations;
  private final GroupRepository groups;
  private final GroupService groupService;
  private final DeploymentEngine deployments;
  private final SelectionManager selections;
  private final CustomHeadFactory heads;
  private final Map<UUID, String> iconPrompts = new HashMap<>();

  public GuiService(
      JavaPlugin plugin,
      LanguageService lang,
      GuideBookService guide,
      DecorationService decorationService,
      DecorationRepository decorations,
      GroupRepository groups,
      GroupService groupService,
      DeploymentEngine deployments,
      SelectionManager selections) {
    this.plugin = plugin;
    this.lang = lang;
    this.guide = guide;
    this.decorationService = decorationService;
    this.decorations = decorations;
    this.groups = groups;
    this.groupService = groupService;
    this.deployments = deployments;
    this.selections = selections;
    this.heads = new CustomHeadFactory(plugin);
  }

  public void openMain(Player p) {
    MenuHolder h = new MenuHolder(Menu.MAIN);
    Inventory inv = Bukkit.createInventory(h, 27, lang.component(p, "gui.main-title"));
    h.inventory = inv;
    inv.setItem(11, head(HEAD_DECORATION, lang.component(p, "gui.decorations")));
    inv.setItem(13, head(HEAD_GROUP, lang.component(p, "gui.groups")));
    inv.setItem(15, head(HEAD_ACTIVE, lang.component(p, "gui.active")));
    inv.setItem(20, head(HEAD_SELECTION, lang.component(p, "gui.selection")));
    inv.setItem(24, head(HEAD_HELP, lang.component(p, "gui.help")));
    p.openInventory(inv);
  }

  private void openDecorations(Player p) {
    openDecorations(p, 0);
  }

  private void openDecorations(Player p, int requestedPage) {
    List<Decoration> entries = List.copyOf(decorations.all());
    int page = validPage(requestedPage, entries.size());
    MenuHolder h = new MenuHolder(Menu.DECORATIONS);
    h.page = page;
    Inventory inv = Bukkit.createInventory(h, 54, lang.component(p, "gui.decoration-title"));
    h.inventory = inv;
    int slot = 0;
    for (Decoration d : page(entries, page)) {
      boolean active = !deployments.forDecoration(d.uuid()).isEmpty();
      ItemStack icon =
          headLines(
              HEAD_DECORATION,
              Component.text(d.displayName()),
              lang.lines(
                  p,
                  "gui.decoration-lore",
                  Map.of(
                      "blocks",
                      d.blockCount(),
                      "entities",
                      d.entityCount(),
                      "world",
                      d.homeAnchor().worldName(),
                      "state",
                      lang.component(p, active ? "state.active" : "state.inactive"),
                      "updated",
                      date(d.modifiedAt()))));
      inv.setItem(slot, icon);
      h.ids.put(slot, d.id());
      slot++;
    }
    navigation(inv, p, page, entries.size());
    p.openInventory(inv);
  }

  private void openGroups(Player p) {
    openGroups(p, 0);
  }

  private void openGroups(Player p, int requestedPage) {
    List<DecorationGroup> entries =
        groups.all().stream()
            .sorted(
                Comparator.comparing(DecorationGroup::displayName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    int page = validPage(requestedPage, entries.size());
    MenuHolder h = new MenuHolder(Menu.GROUPS);
    h.page = page;
    Inventory inv = Bukkit.createInventory(h, 54, lang.component(p, "gui.group-title"));
    h.inventory = inv;
    int slot = 0;
    for (DecorationGroup g : page(entries, page)) {
      inv.setItem(
          slot,
          headLines(
              g.iconTexture() == null ? HEAD_GROUP : g.iconTexture(),
              Component.text(g.displayName()),
              lang.lines(p, "gui.group-lore", Map.of("count", g.members().size()))));
      h.ids.put(slot, g.id());
      slot++;
    }
    navigation(inv, p, page, entries.size());
    p.openInventory(inv);
  }

  private void openGroupIcons(Player p, DecorationGroup group) {
    MenuHolder h = new MenuHolder(Menu.GROUP_ICONS);
    h.context = group.id();
    Inventory inv = Bukkit.createInventory(h, 27, lang.component(p, "gui.group-icon-title"));
    h.inventory = inv;
    int slot = 10;
    for (GroupIcon choice : GROUP_ICONS) {
      inv.setItem(
          slot,
        headLines(
            choice.texture(),
            lang.component(p, choice.labelKey()),
            lang.lines(p, "gui.group-icon-select")));
      h.ids.put(slot, choice.texture());
      slot++;
    }
    inv.setItem(
        16,
        headLines(
            group.iconTexture() == null ? HEAD_GROUP : group.iconTexture(),
            lang.component(p, "gui.group-icon-custom"),
            lang.lines(p, "gui.group-icon-custom-lore")));
    h.ids.put(16, ICON_CUSTOM);
    inv.setItem(
        17,
        headLines(
            HEAD_GROUP,
            lang.component(p, "gui.group-icon-default"),
            lang.lines(p, "gui.group-icon-default-lore")));
    h.ids.put(17, ICON_DEFAULT);
    inv.setItem(22, head(HEAD_BACK, lang.component(p, "gui.back")));
    p.openInventory(inv);
  }

  private void openActive(Player p) {
    openActive(p, 0);
  }

  private void openActive(Player p, int requestedPage) {
    List<Deployment> entries =
        deployments.active().stream()
            .sorted(Comparator.comparing(Deployment::createdAt).reversed())
            .toList();
    int page = validPage(requestedPage, entries.size());
    MenuHolder h = new MenuHolder(Menu.ACTIVE);
    h.page = page;
    Inventory inv = Bukkit.createInventory(h, 54, lang.component(p, "gui.active-title"));
    h.inventory = inv;
    int slot = 0;
    for (Deployment d : page(entries, page)) {
      inv.setItem(
          slot,
          headLines(
              HEAD_ACTIVE,
              Component.text(d.decorationName()),
              lang.lines(
                  p,
                  "gui.active-lore",
                  Map.of(
                      "world",
                      d.target().worldName(),
                      "state",
                      d.state(),
                      "time",
                      date(d.createdAt())))));
      h.ids.put(slot, d.id().toString());
      slot++;
    }
    navigation(inv, p, page, entries.size());
    p.openInventory(inv);
  }

  private void confirmDelete(Player p, String id, int returnPage) {
    MenuHolder h = new MenuHolder(Menu.CONFIRM);
    h.context = id;
    h.page = returnPage;
    Inventory inv = Bukkit.createInventory(h, 27, lang.component(p, "gui.confirm-title"));
    h.inventory = inv;
    inv.setItem(11, head(HEAD_CONFIRM, lang.component(p, "gui.confirm")));
    inv.setItem(15, head(HEAD_CANCEL, lang.component(p, "gui.cancel")));
    p.openInventory(inv);
  }

  @EventHandler
  public void click(InventoryClickEvent e) {
    if (!(e.getInventory().getHolder(false) instanceof MenuHolder h)
        || !(e.getWhoClicked() instanceof Player p)) return;
    e.setCancelled(true);
    int slot = e.getRawSlot();
    if (slot < 0 || slot >= e.getInventory().getSize()) return;
    if (h.menu != Menu.CONFIRM && slot == 49) {
      openMain(p);
      return;
    }
    if (h.menu != Menu.CONFIRM && (slot == 45 || slot == 53)) {
      int page = h.page + (slot == 45 ? -1 : 1);
      switch (h.menu) {
        case DECORATIONS -> openDecorations(p, page);
        case GROUPS -> openGroups(p, page);
        case ACTIVE -> openActive(p, page);
        default -> {
          return;
        }
      }
      return;
    }
    if (h.menu == Menu.MAIN) {
      switch (slot) {
        case 11 -> openDecorations(p);
        case 13 -> openGroups(p);
        case 15 -> openActive(p);
        case 20 -> {
          var s = selections.get(p);
          lang.send(
              p,
              "selection.info",
              Map.of("blocks", s.blocks().size(), "entities", s.entities().size()));
          p.closeInventory();
        }
        case 24 -> {
          p.closeInventory();
          guide.open(p);
        }
      }
      return;
    }
    if (h.menu == Menu.GROUP_ICONS) {
      if (slot == 22 && h.context != null) {
        openGroups(p);
        return;
      }
      String texture = h.ids.get(slot);
      if (texture == null || h.context == null || !has(p, "decoswap.group.edit")) return;
      if (ICON_CUSTOM.equals(texture)) {
        iconPrompts.put(p.getUniqueId(), h.context);
        p.closeInventory();
        lang.send(p, "gui.group-icon-prompt");
      } else {
        saveGroupIcon(p, h.context, ICON_DEFAULT.equals(texture) ? null : texture);
      }
      return;
    }
    String id = h.ids.get(slot);
    if (h.menu == Menu.DECORATIONS && id != null) {
      Decoration d = decorations.find(id).orElse(null);
      if (d == null) return;
      if (e.isShiftClick() && e.isRightClick()) {
        if (!has(p, "decoswap.decoration.delete")) return;
        confirmDelete(p, id, h.page);
      } else if (e.getClick() == ClickType.MIDDLE) {
        if (!has(p, "decoswap.decoration.changes")) return;
        try {
          DecorationChanges report = decorationService.detectChanges(p, d, selections.get(p));
          selections.showChanges(p, report);
          if (report.isEmpty()) lang.send(p, "changes.none", Map.of("name", d.displayName()));
          else
            lang.send(
                p,
                "changes.found",
                Map.of(
                    "name", d.displayName(),
                    "total", report.totalChanges(),
                    "blocks", report.changedBlocks().size(),
                    "entities", report.changedEntities().size(),
                    "missing", report.missingEntities(),
                    "additional", report.additionalEntities()));
        } catch (Exception x) {
          error(p, x);
        }
      } else if (e.isShiftClick() && e.isLeftClick()) {
        if (!has(p, "decoswap.decoration.deploy")) return;
        p.closeInventory();
        deployments
            .deploy(d, d.homeAnchor(), Rotation.NONE, p, null, false)
            .whenComplete((v, x) -> messageDeploy(p, d, v, x));
      } else if (e.isRightClick()) {
        if (!has(p, "decoswap.decoration.info")) return;
        lang.send(p, "decoration.info", info(d));
      } else {
        if (deployments.forDecoration(d.uuid()).isEmpty()) {
          if (!has(p, "decoswap.decoration.deploy")) return;
          p.closeInventory();
          deployments
              .deploy(d, d.homeAnchor(), Rotation.NONE, p, null, false)
              .whenComplete((v, x) -> messageDeploy(p, d, v, x));
        } else {
          if (!has(p, "decoswap.decoration.restore")) return;
          p.closeInventory();
          List<Deployment> matches = deployments.forDecoration(d.uuid());
          if (matches.size() == 1)
            deployments
                .restore(matches.getFirst(), p, false)
                .whenComplete((v, x) -> messageRestore(p, d, v, x));
          else lang.send(p, "error.ambiguous");
        }
      }
    } else if (h.menu == Menu.GROUPS && id != null) {
      DecorationGroup g = groups.find(id).orElse(null);
      if (g == null) return;
      if (e.isShiftClick() && e.isLeftClick()) {
        if (!has(p, "decoswap.group.edit")) return;
        openGroupIcons(p, g);
        return;
      }
      p.closeInventory();
      if (e.isRightClick()) {
        if (!has(p, "decoswap.group.restore")) return;
        groupService
            .restore(g, p)
            .whenComplete(
                (v, x) -> message(p, x, "group.restore-success", Map.of("name", g.displayName())));
      } else {
        if (!has(p, "decoswap.group.deploy")) return;
        groupService
            .deploy(g, p)
            .whenComplete(
                (v, x) -> message(p, x, "group.deploy-success", Map.of("name", g.displayName())));
      }
    } else if (h.menu == Menu.ACTIVE && id != null) {
      if (!has(p, "decoswap.decoration.restore")) return;
      try {
        Deployment d =
            deployments.active().stream()
                .filter(x -> x.id().equals(UUID.fromString(id)))
                .findFirst()
                .orElse(null);
        p.closeInventory();
        if (d != null)
          deployments.restore(d, p, false).whenComplete((v, x) -> messageRestore(p, null, v, x));
      } catch (Exception ignored) {
      }
    } else if (h.menu == Menu.CONFIRM) {
      if (slot == 11 && h.context != null) {
        if (!has(p, "decoswap.decoration.delete")) return;
        try {
          Decoration d = decorations.find(h.context).orElse(null);
          if (d != null && !deployments.forDecoration(d.uuid()).isEmpty()) {
            lang.send(p, "error.active");
            return;
          }
          decorations.delete(h.context);
          lang.send(p, "delete.success", Map.of("name", h.context));
          p.closeInventory();
        } catch (Exception x) {
          error(p, x);
        }
      } else if (slot == 15) openDecorations(p, h.page);
    }
  }

  @EventHandler
  public void iconChat(AsyncPlayerChatEvent e) {
    String groupId = iconPrompts.remove(e.getPlayer().getUniqueId());
    if (groupId == null) return;
    e.setCancelled(true);
    String message = e.getMessage().trim();
    Bukkit.getScheduler()
        .runTask(plugin, () -> applyIconInput(e.getPlayer(), groupId, message));
  }

  @EventHandler
  public void iconQuit(PlayerQuitEvent e) {
    iconPrompts.remove(e.getPlayer().getUniqueId());
  }

  private void applyIconInput(Player player, String groupId, String input) {
    if (input.equalsIgnoreCase("cancel")) {
      lang.send(player, "gui.group-icon-cancelled");
      openGroups(player);
      return;
    }
    String hash = TextureReference.hash(input).orElse(null);
    if (hash == null) {
      lang.send(player, "gui.group-icon-invalid");
      openGroups(player);
      return;
    }
    saveGroupIcon(player, groupId, hash);
  }

  private void saveGroupIcon(Player player, String groupId, String texture) {
    DecorationGroup group = groups.find(groupId).orElse(null);
    if (group == null) return;
    group.iconTexture(texture);
    try {
      groups.save();
      lang.send(player, "gui.group-icon-saved", Map.of("name", group.displayName()));
      openGroups(player);
    } catch (Exception error) {
      error(player, error);
    }
  }

  private void messageDeploy(Player p, Decoration d, Deployment v, Throwable x) {
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              if (x != null) error(p, x);
              else
                lang.send(
                    p, "deploy.success", Map.of("name", d.displayName(), "id", v.id().toString()));
            });
  }

  private void messageRestore(
      Player p, Decoration d, DeploymentEngine.RestoreResult v, Throwable x) {
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              if (x != null) error(p, x);
              else
                lang.send(
                    p,
                    "restore.success",
                    Map.of(
                        "name", v.deployment().decorationName(), "missing", v.missingEntities()));
            });
  }

  private void message(Player p, Throwable x, String key, Map<String, ?> values) {
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              if (x != null) error(p, x);
              else lang.send(p, key, values);
            });
  }

  private void error(Player p, Throwable t) {
    Throwable x = t;
    while ((x instanceof java.util.concurrent.CompletionException
            || x instanceof java.util.concurrent.ExecutionException)
        && x.getCause() != null) x = x.getCause();
    if (x instanceof DeploymentEngine.OverlapException o)
      lang.send(p, "error.overlap", Map.of("count", o.count()));
    else if (x instanceof DeploymentEngine.ConflictException c)
      lang.send(p, "error.conflicts", Map.of("count", c.count(), "id", c.deploymentId()));
    else
      lang.send(
          p,
          "error.operation",
          Map.of("reason", x.getMessage() == null ? x.getClass().getSimpleName() : x.getMessage()));
  }

  private boolean has(Player player, String permission) {
    if (player.hasPermission(permission) || player.hasPermission("decoswap.admin")) return true;
    lang.send(player, "error.no-permission");
    return false;
  }

  private Map<String, Object> info(Decoration d) {
    return Map.of(
        "name",
        d.displayName(),
        "id",
        d.id(),
        "blocks",
        d.blockCount(),
        "entities",
        d.entityCount(),
        "world",
        d.homeAnchor().worldName(),
        "created",
        date(d.createdAt()),
        "updated",
        date(d.modifiedAt()),
        "checksum",
        d.checksum().substring(0, 12));
  }

  private String date(java.time.Instant i) {
    return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(i);
  }

  private ItemStack head(String texture, Component name, Component... lore) {
    return heads.create(texture, name, lore);
  }

  private ItemStack headLines(String texture, Component name, List<Component> lore) {
    return heads.create(texture, name, lore.toArray(Component[]::new));
  }

  private void navigation(Inventory inventory, Player player, int page, int entryCount) {
    int pages = Math.max(1, (entryCount + PAGE_SIZE - 1) / PAGE_SIZE);
    if (page > 0)
      inventory.setItem(
          45,
          head(
              HEAD_BACK,
              lang.component(player, "gui.previous-page"),
              lang.component(player, "gui.page", Map.of("page", page, "pages", pages))));
    inventory.setItem(49, head(HEAD_BACK, lang.component(player, "gui.back")));
    if (page + 1 < pages)
      inventory.setItem(
          53,
          head(
              HEAD_NEXT,
              lang.component(player, "gui.next-page"),
              lang.component(player, "gui.page", Map.of("page", page + 2, "pages", pages))));
  }

  private int validPage(int requested, int entryCount) {
    int last = Math.max(0, (entryCount - 1) / PAGE_SIZE);
    return Math.max(0, Math.min(requested, last));
  }

  private <T> List<T> page(List<T> entries, int page) {
    int from = Math.min(page * PAGE_SIZE, entries.size());
    int to = Math.min(from + PAGE_SIZE, entries.size());
    return entries.subList(from, to);
  }

  private enum Menu {
    MAIN,
    DECORATIONS,
    GROUPS,
    GROUP_ICONS,
    ACTIVE,
    CONFIRM
  }

  private record GroupIcon(String labelKey, String texture) {}

  private static final class MenuHolder implements InventoryHolder {
    private final Menu menu;
    private final Map<Integer, String> ids = new HashMap<>();
    private String context;
    private int page;
    private Inventory inventory;

    private MenuHolder(Menu menu) {
      this.menu = menu;
    }

    public Inventory getInventory() {
      return inventory;
    }
  }
}
