package es.mrdino.decoswap.command;

import es.mrdino.decoswap.DecoSwapPlugin;
import es.mrdino.decoswap.compat.ServerCapabilities;
import es.mrdino.decoswap.compat.easyarmorstands.EasyArmorStandsHook;
import es.mrdino.decoswap.decoration.*;
import es.mrdino.decoswap.deployment.*;
import es.mrdino.decoswap.group.*;
import es.mrdino.decoswap.gui.GuiService;
import es.mrdino.decoswap.gui.GuideBookService;
import es.mrdino.decoswap.language.LanguageService;
import es.mrdino.decoswap.selection.*;
import es.mrdino.decoswap.storage.*;
import es.mrdino.decoswap.transaction.TransactionJournal;
import es.mrdino.decoswap.util.*;
import es.mrdino.decoswap.util.Rotation;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletionException;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;

public final class DecoSwapCommand implements CommandExecutor, TabCompleter {
  private final DecoSwapPlugin plugin;
  private final LanguageService lang;
  private final SelectionManager selections;
  private final SelectorTool selector;
  private final DecorationService decorationService;
  private final DecorationRepository decorations;
  private final DeploymentEngine deployments;
  private final GroupRepository groups;
  private final GroupService groupService;
  private final GuiService gui;
  private final GuideBookService guide;
  private final ServerCapabilities capabilities;
  private final EasyArmorStandsHook easy;

  public DecoSwapCommand(
      DecoSwapPlugin plugin,
      LanguageService lang,
      SelectionManager selections,
      SelectorTool selector,
      DecorationService decorationService,
      DecorationRepository decorations,
      DeploymentEngine deployments,
      GroupRepository groups,
      GroupService groupService,
      GuiService gui,
      GuideBookService guide,
      ServerCapabilities capabilities,
      EasyArmorStandsHook easy) {
    this.plugin = plugin;
    this.lang = lang;
    this.selections = selections;
    this.selector = selector;
    this.decorationService = decorationService;
    this.decorations = decorations;
    this.deployments = deployments;
    this.groups = groups;
    this.groupService = groupService;
    this.gui = gui;
    this.guide = guide;
    this.capabilities = capabilities;
    this.easy = easy;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!sender.hasPermission("decoswap.use") && !sender.hasPermission("decoswap.admin")) {
      lang.send(sender, "error.no-permission");
      return true;
    }
    try {
      if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
        help(sender);
        return true;
      }
      String root = args[0].toLowerCase(Locale.ROOT);
      switch (root) {
        case "wand" -> wand(sender);
        case "mode" -> mode(sender, args);
        case "select" -> select(sender, args);
        case "undo" -> undo(sender);
        case "anchor" -> anchor(sender, args);
        case "save" -> save(sender, args, false, false);
        case "update" -> save(sender, args, false, true);
        case "pack" -> save(sender, args, true, true);
        case "delete" -> {
          usage(args, 2, "/ds delete <name>");
          delete(sender, args[1]);
        }
        case "info" -> {
          usage(args, 2, "/ds info <name>");
          info(sender, args[1]);
        }
        case "list" -> listDecorations(sender);
        case "changes", "check", "scan" -> changes(sender, args);
        case "decoration" -> decoration(sender, args);
        case "deploy", "place", "show" -> deploy(sender, args);
        case "restore", "remove", "hide" -> restore(sender, args);
        case "group" -> group(sender, args);
        case "active" -> active(sender);
        case "status" -> status(sender, args);
        case "recovery" -> recovery(sender, args);
        case "language" -> language(sender, args);
        case "gui" -> {
          Player p = player(sender);
          if (allowed(sender, "decoswap.use")) gui.openMain(p);
        }
        case "guide", "book" -> guide.open(player(sender));
        case "reload" -> {
          if (allowed(sender, "decoswap.reload")) {
            plugin.reloadDecoSwap();
            lang.send(sender, "reload.success");
          }
        }
        case "version" -> version(sender);
        default -> lang.send(sender, "error.unknown-command");
      }
    } catch (CommandProblem p) {
      if (!p.key.isEmpty()) lang.send(sender, p.key, p.values);
    } catch (Exception e) {
      plugin
          .getLogger()
          .log(java.util.logging.Level.WARNING, "Command failed for " + sender.getName(), e);
      lang.send(
          sender,
          "error.operation",
          Map.of("reason", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
    }
    return true;
  }

  private void wand(CommandSender sender) {
    require(sender, "decoswap.wand");
    Player p = player(sender);
    p.getInventory().addItem(selector.create(p));
    lang.send(p, "wand.received");
  }

  private void mode(CommandSender sender, String[] a) {
    require(sender, "decoswap.select");
    Player p = player(sender);
    usage(a, 2, "/ds mode <object|region|anchor>");
    try {
      SelectionMode mode = SelectionMode.valueOf(a[1].toUpperCase(Locale.ROOT));
      selections.get(p).mode(mode);
      lang.send(p, "mode.changed", Map.of("mode", mode.name()));
      selections.changed(p);
    } catch (IllegalArgumentException e) {
      throw usage("/ds mode <object|region|anchor>");
    }
  }

  private void select(CommandSender sender, String[] a) {
    require(sender, "decoswap.select");
    Player p = player(sender);
    PlayerSelection s = selections.get(p);
    String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "info";
    switch (sub) {
      case "info" ->
          lang.send(
              p,
              "selection.info",
              Map.of("blocks", s.blocks().size(), "entities", s.entities().size()));
      case "clear" -> {
        s.clear();
        selections.changed(p);
        lang.send(p, "selection.clear");
      }
      case "blocks" -> {
        int n = s.clearEntities();
        selections.changed(p);
        lang.send(p, "selection.filtered", Map.of("count", n));
      }
      case "entities" -> {
        int n = s.clearBlocks();
        selections.changed(p);
        lang.send(p, "selection.filtered", Map.of("count", n));
      }
      case "region" -> {
        String type = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "all";
        if (!Set.of("all", "blocks", "entities").contains(type))
          throw usage("/ds select region [blocks|entities|all]");
        try {
          selections
              .importRegion(p, type)
              .whenComplete(
                  (r, error) ->
                      Bukkit.getScheduler()
                          .runTask(
                              plugin,
                              () -> {
                                if (error != null) {
                                  if (!(unwrap(error)
                                      instanceof java.util.concurrent.CancellationException))
                                    handle(p, unwrap(error));
                                } else
                                  lang.send(
                                      p,
                                      "selection.region-imported",
                                      Map.of("blocks", r.blocks(), "entities", r.entities()));
                              }));
        } catch (IllegalStateException e) {
          throw problem(e.getMessage().equals("busy") ? "error.busy" : "error.region-incomplete");
        } catch (IllegalArgumentException e) {
          String[] bits = e.getMessage().split(":");
          throw problem(
              "error.region-too-large",
              Map.of(
                  "volume",
                  bits.length > 1 ? bits[1] : "?",
                  "limit",
                  plugin.settings().maxRegionVolume()));
        }
      }
      case "cancel" -> {
        lang.send(p, selections.cancelRegion(p) ? "selection.cancelled" : "selection.no-operation");
      }
      case "remove", "filter" -> {
        usage(a, 3, "/ds select remove <material|entity-type>");
        String value = a[2].toUpperCase(Locale.ROOT);
        Material m = Material.matchMaterial(value);
        int removed = 0;
        if (m != null)
          removed +=
              s.removeBlocks(
                  k -> {
                    World w = Bukkit.getWorld(k.worldId());
                    return w != null && w.getBlockAt(k.x(), k.y(), k.z()).getType() == m;
                  });
        EntityType type;
        try {
          type = EntityType.valueOf(value);
        } catch (Exception e) {
          type = null;
        }
        if (type != null) {
          EntityType finalType = type;
          removed +=
              s.removeEntitiesByType(
                  id -> {
                    Entity e = Bukkit.getEntity(id);
                    return e == null || e.getType() == finalType;
                  });
        }
        selections.changed(p);
        lang.send(p, "selection.filtered", Map.of("count", removed));
      }
      case "invert" -> invert(p, s);
      default ->
          throw usage("/ds select <info|clear|region|blocks|entities|invert|remove|filter|cancel>");
    }
  }

  private void invert(Player p, PlayerSelection s) {
    Location a = s.pos1(), b = s.pos2();
    if (a == null || b == null || a.getWorld() != b.getWorld())
      throw problem("error.region-incomplete");
    long volume =
        (Math.abs(a.getBlockX() - b.getBlockX()) + 1L)
            * (Math.abs(a.getBlockY() - b.getBlockY()) + 1L)
            * (Math.abs(a.getBlockZ() - b.getBlockZ()) + 1L);
    if (volume > plugin.settings().maxRegionVolume())
      throw problem(
          "error.region-too-large",
          Map.of("volume", volume, "limit", plugin.settings().maxRegionVolume()));
    List<BlockKey> keys = new ArrayList<>();
    for (int x = Math.min(a.getBlockX(), b.getBlockX());
        x <= Math.max(a.getBlockX(), b.getBlockX());
        x++)
      for (int y = Math.min(a.getBlockY(), b.getBlockY());
          y <= Math.max(a.getBlockY(), b.getBlockY());
          y++)
        for (int z = Math.min(a.getBlockZ(), b.getBlockZ());
            z <= Math.max(a.getBlockZ(), b.getBlockZ());
            z++)
          if (!a.getWorld().getBlockAt(x, y, z).getType().isAir())
            keys.add(new BlockKey(a.getWorld().getUID(), x, y, z));
    s.invertBlocks(keys, plugin.settings().maxBlocks());
    selections.changed(p);
  }

  private void undo(CommandSender sender) {
    require(sender, "decoswap.select");
    Player p = player(sender);
    boolean ok = selections.get(p).undo();
    if (ok) selections.changed(p);
    lang.send(p, ok ? "selection.undo" : "selection.undo-empty");
  }

  private void anchor(CommandSender sender, String[] a) {
    require(sender, "decoswap.anchor");
    Player p = player(sender);
    String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "info";
    PlayerSelection s = selections.get(p);
    if (sub.equals("set")) {
      s.anchor(Anchor.from(p.getLocation()));
      selections.changed(p);
      Anchor n = s.anchor();
      lang.send(
          p,
          "anchor.set",
          Map.of("x", fmt(n.x()), "y", fmt(n.y()), "z", fmt(n.z()), "facing", n.cardinal().name()));
    } else if (sub.equals("info")) {
      Anchor n = s.anchor();
      if (n == null) throw problem("error.no-anchor");
      lang.send(
          p,
          "anchor.info",
          Map.of(
              "world",
              n.worldName(),
              "x",
              fmt(n.x()),
              "y",
              fmt(n.y()),
              "z",
              fmt(n.z()),
              "yaw",
              fmt(n.yaw()),
              "pitch",
              fmt(n.pitch())));
    } else if (sub.equals("clear")) {
      s.clearAnchor();
      selections.changed(p);
      lang.send(p, "anchor.cleared");
    } else throw usage("/ds anchor <set|info|clear>");
  }

  private void decoration(CommandSender sender, String[] a) throws Exception {
    usage(a, 2, "/ds decoration <create|save|update|delete|info|list|pack|deploy|restore>");
    String sub = a[1].toLowerCase(Locale.ROOT);
    if (sub.equals("list")) {
      listDecorations(sender);
      return;
    }
    usage(a, 3, "/ds decoration " + sub + " <name>");
    String[] alias = new String[a.length - 1];
    alias[0] = a[2];
    if (a.length > 3) System.arraycopy(a, 3, alias, 1, a.length - 3);
    switch (sub) {
      case "create" -> {
        require(sender, "decoswap.decoration.create");
        save(sender, prepend("create", alias), false, false);
      }
      case "save" -> save(sender, prepend("save", alias), false, false);
      case "update" -> save(sender, prepend("save", alias), false, true);
      case "pack" -> save(sender, prepend("pack", alias), true, true);
      case "deploy" -> deploy(sender, prepend("deploy", alias));
      case "restore" -> restore(sender, prepend("restore", alias));
      case "delete" -> delete(sender, a[2]);
      case "info" -> info(sender, a[2]);
      default ->
          throw usage("/ds decoration <create|save|update|delete|info|list|pack|deploy|restore>");
    }
  }

  private void save(CommandSender sender, String[] a, boolean pack, boolean update) {
    require(
        sender,
        pack
            ? "decoswap.decoration.pack"
            : update
                ? "decoswap.decoration.update"
                : a[0].equalsIgnoreCase("create")
                    ? "decoswap.decoration.create"
                    : "decoswap.decoration.save");
    Player p = player(sender);
    usage(a, 2, "/ds " + (pack ? "pack" : "save") + " <name>");
    if (!update
        && !a[0].equalsIgnoreCase("create")
        && decorations.find(a[1]).isPresent()
        && !contains(a, "--new")) update = true;
    boolean overwrite = update || contains(a, "--overwrite") || contains(a, "--force");
    boolean keep = contains(a, "--keep-world");
    PlayerSelection selection = selections.get(p);
    Set<BlockKey> packedBlocks = Set.copyOf(selection.blocks());
    Set<UUID> packedEntities = Set.copyOf(selection.entities());
    if (selection.blocks().isEmpty() && selection.entities().isEmpty())
      throw problem("error.no-selection");
    decorationService
        .save(p, a[1], selection, overwrite)
        .whenComplete(
            (d, error) ->
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () -> {
                          if (error != null) {
                            Throwable x = unwrap(error);
                            if (x instanceof java.nio.file.FileAlreadyExistsException)
                              lang.send(p, "save.exists", Map.of("name", a[1]));
                            else handle(p, x);
                            return;
                          }
                          Bukkit.getPluginManager()
                              .callEvent(
                                  pack
                                      ? new es.mrdino.decoswap.api.event.DecorationPackEvent(d, p)
                                      : new es.mrdino.decoswap.api.event.DecorationSaveEvent(d, p));
                          if (pack && !keep) {
                            decorationService.removeSelected(packedBlocks, packedEntities);
                            selection.clear();
                            selections.clearChanges(p);
                            selections.changed(p);
                            lang.send(p, "pack.warning");
                            lang.send(p, "pack.success", Map.of("name", d.displayName()));
                          } else selections.clearChanges(p);
                          if (!(pack && !keep))
                            lang.send(
                                p,
                                "save.success",
                                Map.of(
                                    "name",
                                    d.displayName(),
                                    "blocks",
                                    d.blockCount(),
                                    "entities",
                                    d.entityCount()));
                        }));
  }

  private void delete(CommandSender sender, String name) throws Exception {
    require(sender, "decoswap.decoration.delete");
    Decoration d = find(name);
    if (!deployments.forDecoration(d.uuid()).isEmpty()) throw problem("error.active");
    decorations.delete(d.id());
    for (DecorationGroup g : groups.all()) g.remove(d.id());
    groups.save();
    lang.send(sender, "delete.success", Map.of("name", d.displayName()));
  }

  private void changes(CommandSender sender, String[] a) {
    require(sender, "decoswap.decoration.changes");
    Player p = player(sender);
    usage(a, 2, "/ds changes <name>");
    Decoration d = find(a[1]);
    try {
      DecorationChanges report = decorationService.detectChanges(p, d, selections.get(p));
      selections.showChanges(p, report);
      if (report.isEmpty()) {
        lang.send(p, "changes.none", Map.of("name", d.displayName()));
        return;
      }
      lang.send(
          p,
          "changes.found",
          Map.of(
              "name",
              d.displayName(),
              "total",
              report.totalChanges(),
              "blocks",
              report.changedBlocks().size(),
              "entities",
              report.changedEntities().size(),
              "missing",
              report.missingEntities(),
              "additional",
              report.additionalEntities()));
      report.changedBlocks().stream()
          .limit(12)
          .forEach(
              key ->
                  lang.send(
                      p,
                      "changes.block",
                      Map.of(
                          "world",
                          Optional.ofNullable(Bukkit.getWorld(key.worldId()))
                              .map(World::getName)
                              .orElse("unknown"),
                          "x",
                          key.x(),
                          "y",
                          key.y(),
                          "z",
                          key.z())));
      report.entityDetails().stream()
          .limit(12)
          .forEach(
              change ->
                  lang.send(
                      p,
                      "changes.entity",
                      Map.of(
                          "name", change.name(),
                          "type", change.type(),
                          "world",
                          Optional.ofNullable(Bukkit.getWorld(change.location().worldId()))
                              .map(World::getName)
                              .orElse("unknown"),
                          "x", change.location().x(),
                          "y", change.location().y(),
                          "z", change.location().z(),
                          "status",
                          lang.component(
                              p,
                              "changes.status."
                                  + (change.missing()
                                      ? "missing"
                                      : change.additional() ? "additional" : "changed")))));
      lang.send(p, "changes.apply", Map.of("name", d.displayName(), "id", d.id()));
    } catch (Exception x) {
      handle(p, x);
    }
  }

  private void info(CommandSender sender, String name) {
    require(sender, "decoswap.decoration.info");
    Decoration d = find(name);
    lang.send(sender, "decoration.info", infoMap(d));
  }

  private void listDecorations(CommandSender sender) {
    require(sender, "decoswap.decoration.info");
    lang.send(sender, "decoration.list-header", Map.of("count", decorations.all().size()));
    for (Decoration d : decorations.all())
      lang.send(
          sender,
          "decoration.list-entry",
          Map.of(
              "name",
              d.displayName(),
              "blocks",
              d.blockCount(),
              "entities",
              d.entityCount(),
              "state",
              lang.component(
                  sender,
                  deployments.forDecoration(d.uuid()).isEmpty()
                      ? "state.inactive"
                      : "state.active")));
  }

  private void deploy(CommandSender sender, String[] a) {
    Player p = player(sender);
    usage(
        a,
        2,
        "/ds "
            + (a[0].equalsIgnoreCase("place") ? "place" : "deploy")
            + " <name> [home|here] [--rotation 0|90|180|270]");
    Decoration d = decorations.find(a[1]).orElse(null);
    if (d == null) {
      DecorationGroup group = groups.find(a[1]).orElse(null);
      if (group == null) throw problem("error.not-found", Map.of("name", a[1]));
      require(sender, "decoswap.group.deploy");
      groupService
          .deploy(group, p)
          .whenComplete(
              (v, x) ->
                  Bukkit.getScheduler()
                      .runTask(
                          plugin,
                          () -> {
                            if (x != null) handle(p, unwrap(x));
                            else
                              lang.send(
                                  p, "group.deploy-success", Map.of("name", group.displayName()));
                          }));
      return;
    }
    require(sender, "decoswap.decoration.deploy");
    boolean here = contains(a, "here");
    int degrees = rotationArg(a, Integer.MIN_VALUE);
    Anchor target;
    Rotation rotation;
    if (here) {
      Location l = p.getLocation();
      int desired = Math.floorMod(Math.round(l.getYaw() / 90f) * 90, 360);
      int home = Math.floorMod(Math.round(d.homeAnchor().yaw() / 90f) * 90, 360);
      int relative = degrees == Integer.MIN_VALUE ? Math.floorMod(desired - home, 360) : degrees;
      rotation = Rotation.ofDegrees(relative);
      target =
          new Anchor(
              l.getWorld().getUID(),
              l.getWorld().getName(),
              l.getX(),
              l.getY(),
              l.getZ(),
              d.homeAnchor().yaw(),
              d.homeAnchor().pitch());
    } else {
      rotation = Rotation.ofDegrees(degrees == Integer.MIN_VALUE ? 0 : degrees);
      target = d.homeAnchor();
    }
    boolean force = contains(a, "--force") && sender.hasPermission("decoswap.deploy.force");
    deployments
        .deploy(d, target, rotation, p, null, force)
        .whenComplete(
            (v, x) ->
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () -> {
                          if (x != null) handle(p, unwrap(x));
                          else {
                            lang.send(
                                p, "deploy.success", Map.of("name", d.displayName(), "id", v.id()));
                            sound(p, Sound.ENTITY_PLAYER_LEVELUP);
                          }
                        }));
  }

  private void restore(CommandSender sender, String[] a) {
    Player p = player(sender);
    usage(
        a,
        2,
        "/ds "
            + (a[0].equalsIgnoreCase("remove") ? "remove" : "restore")
            + " <name> [deployment-id] [--force]");
    Decoration d = decorations.find(a[1]).orElse(null);
    if (d == null) {
      DecorationGroup group = groups.find(a[1]).orElse(null);
      if (group == null) throw problem("error.not-found", Map.of("name", a[1]));
      require(sender, "decoswap.group.restore");
      groupService
          .restore(group, p)
          .whenComplete(
              (v, x) ->
                  Bukkit.getScheduler()
                      .runTask(
                          plugin,
                          () -> {
                            if (x != null) handle(p, unwrap(x));
                            else
                              lang.send(
                                  p, "group.restore-success", Map.of("name", group.displayName()));
                          }));
      return;
    }
    require(sender, "decoswap.decoration.restore");
    List<Deployment> matches = deployments.forDecoration(d.uuid());
    Deployment target = null;
    if (a.length > 2 && !a[2].startsWith("--"))
      try {
        UUID id = UUID.fromString(a[2]);
        target = matches.stream().filter(x -> x.id().equals(id)).findFirst().orElse(null);
      } catch (IllegalArgumentException ignored) {
      }
    if (target == null) {
      if (matches.isEmpty()) throw problem("error.not-active");
      if (matches.size() > 1) throw problem("error.ambiguous");
      target = matches.getFirst();
    }
    boolean force = contains(a, "--force");
    if (force && !sender.hasPermission("decoswap.restore.force"))
      throw problem("error.no-permission");
    deployments
        .restore(target, p, force)
        .whenComplete(
            (v, x) ->
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () -> {
                          if (x != null) handle(p, unwrap(x));
                          else {
                            lang.send(
                                p,
                                "restore.success",
                                Map.of(
                                    "name",
                                    v.deployment().decorationName(),
                                    "missing",
                                    v.missingEntities()));
                            sound(p, Sound.ENTITY_PLAYER_LEVELUP);
                          }
                        }));
  }

  private void group(CommandSender sender, String[] a) throws Exception {
    usage(a, 2, "/ds group <create|delete|add|remove|info|list|deploy|restore|enable|disable>");
    String sub = a[1].toLowerCase(Locale.ROOT);
    if (sub.equals("list")) {
      require(sender, "decoswap.group.edit");
      lang.send(sender, "group.list-header", Map.of("count", groups.all().size()));
      for (DecorationGroup g : groups.all())
        lang.send(
            sender,
            "group.list-entry",
            Map.of("name", g.displayName(), "count", g.members().size()));
      return;
    }
    usage(a, 3, "/ds group " + sub + " <name>");
    switch (sub) {
      case "create" -> {
        require(sender, "decoswap.group.create");
        DecorationGroup g = groups.create(a[2]);
        lang.send(sender, "group.created", Map.of("name", g.displayName()));
      }
      case "delete" -> {
        require(sender, "decoswap.group.delete");
        DecorationGroup g = findGroup(a[2]);
        groups.delete(g.id());
        lang.send(sender, "group.deleted", Map.of("name", g.displayName()));
      }
      case "add", "remove" -> {
        require(sender, "decoswap.group.edit");
        usage(a, 4, "/ds group " + sub + " <group> <decoration>");
        DecorationGroup g = findGroup(a[2]);
        Decoration d = find(a[3]);
        boolean changed = sub.equals("add") ? g.add(d.id()) : g.remove(d.id());
        groups.save();
        lang.send(
            sender,
            sub.equals("add") ? "group.added" : "group.removed",
            Map.of("group", g.displayName(), "decoration", d.displayName()));
      }
      case "info" -> {
        require(sender, "decoswap.group.edit");
        DecorationGroup g = findGroup(a[2]);
        lang.send(
            sender,
            "group.info",
            Map.of(
                "name",
                g.displayName(),
                "count",
                g.members().size(),
                "members",
                String.join(", ", g.members())));
      }
      case "deploy", "place", "show", "enable" -> {
        require(sender, "decoswap.group.deploy");
        Player p = player(sender);
        DecorationGroup g = findGroup(a[2]);
        groupService
            .deploy(g, p)
            .whenComplete(
                (v, x) ->
                    Bukkit.getScheduler()
                        .runTask(
                            plugin,
                            () -> {
                              if (x != null) handle(p, unwrap(x));
                              else
                                lang.send(
                                    p, "group.deploy-success", Map.of("name", g.displayName()));
                            }));
      }
      case "restore", "hide", "disable" -> {
        require(sender, "decoswap.group.restore");
        Player p = player(sender);
        DecorationGroup g = findGroup(a[2]);
        groupService
            .restore(g, p)
            .whenComplete(
                (v, x) ->
                    Bukkit.getScheduler()
                        .runTask(
                            plugin,
                            () -> {
                              if (x != null) handle(p, unwrap(x));
                              else
                                lang.send(
                                    p, "group.restore-success", Map.of("name", g.displayName()));
                            }));
      }
      default -> throw usage("/ds group <create|delete|add|remove|info|list|deploy|restore>");
    }
  }

  private void active(CommandSender sender) {
    require(sender, "decoswap.decoration.info");
    lang.send(sender, "active.header", Map.of("count", deployments.active().size()));
    for (Deployment d : deployments.active())
      lang.send(
          sender,
          "active.entry",
          Map.of(
              "name",
              d.decorationName(),
              "id",
              d.id(),
              "world",
              d.target().worldName(),
              "state",
              d.state(),
              "time",
              date(d.createdAt())));
  }

  private void status(CommandSender sender, String[] a) {
    usage(a, 2, "/ds status <decoration>");
    Decoration d = find(a[1]);
    int count = deployments.forDecoration(d.uuid()).size();
    lang.send(
        sender,
        count == 0 ? "status.inactive" : "status.active",
        Map.of("name", d.displayName(), "count", count));
  }

  private void recovery(CommandSender sender, String[] a) {
    require(sender, "decoswap.recovery");
    String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
    if (sub.equals("list")) {
      if (deployments.recovery().isEmpty()) {
        lang.send(sender, "recovery.none");
        return;
      }
      lang.send(sender, "recovery.header", Map.of("count", deployments.recovery().size()));
      for (TransactionJournal.Entry e : deployments.recovery())
        lang.send(
            sender,
            "recovery.entry",
            Map.of(
                "id",
                e.id(),
                "type",
                e.type(),
                "state",
                e.state(),
                "decoration",
                e.deployment().decorationName()));
      return;
    }
    usage(a, 3, "/ds recovery <inspect|rollback|accept> <id>");
    UUID id;
    try {
      id = UUID.fromString(a[2]);
    } catch (Exception e) {
      throw problem("error.not-active");
    }
    TransactionJournal.Entry entry =
        deployments.recovery().stream().filter(e -> e.id().equals(id)).findFirst().orElse(null);
    if (entry == null) throw problem("error.not-active");
    if (sub.equals("inspect")) {
      lang.send(
          sender,
          "recovery.info",
          Map.of(
              "id",
              id,
              "type",
              entry.type(),
              "state",
              entry.state(),
              "decoration",
              entry.deployment().decorationName(),
              "deployment",
              entry.deployment().id(),
              "updated",
              entry.updated(),
              "error",
              entry.error()));
    } else if (sub.equals("accept")) {
      if (deployments.recoveryAccept(id)) lang.send(sender, "recovery.accepted", Map.of("id", id));
      else throw problem("error.operation", Map.of("reason", "Could not archive recovery data"));
    } else if (sub.equals("rollback")) {
      Player p = player(sender);
      deployments
          .recoveryRollback(id, p)
          .whenComplete(
              (v, x) ->
                  Bukkit.getScheduler()
                      .runTask(
                          plugin,
                          () -> {
                            if (x != null) handle(p, unwrap(x));
                            else
                              lang.send(
                                  p,
                                  "restore.success",
                                  Map.of(
                                      "name",
                                      v.deployment().decorationName(),
                                      "missing",
                                      v.missingEntities()));
                          }));
      lang.send(sender, "recovery.rollback-started", Map.of("id", id));
    } else throw usage("/ds recovery <list|inspect|rollback|accept>");
  }

  private void language(CommandSender sender, String[] a) {
    Player p = player(sender);
    usage(a, 2, "/ds language <auto|en_US|es_ES>");
    if (a[1].equalsIgnoreCase("auto")) {
      lang.set(p, null);
      lang.send(p, "language.auto");
    } else {
      String locale =
          lang.available().stream().filter(x -> x.equalsIgnoreCase(a[1])).findFirst().orElse(null);
      if (locale == null) throw usage("/ds language <auto|en_US|es_ES>");
      lang.set(p, locale);
      lang.send(p, "language.changed", Map.of("language", locale));
    }
  }

  private void version(CommandSender s) {
    lang.send(
        s,
        "version.info",
        Map.of(
            "plugin",
            plugin.getPluginMeta().getVersion(),
            "minecraft",
            capabilities.minecraftVersion(),
            "paper",
            Bukkit.getVersion(),
            "adapter",
            capabilities.adapterName(),
            "easy",
            easy.enabled()
                ? lang.component(s, "value.enabled")
                    .append(
                        net.kyori.adventure.text.Component.text(
                            " (" + easy.detectedVersion() + ")"))
                : lang.component(s, "value.disabled"),
            "schema",
            BinaryCodec.SCHEMA_VERSION));
  }

  private void help(CommandSender s) {
    require(s, "decoswap.help");
    lang.send(s, "help.header");
    for (int i = 1; i <= 8; i++) s.sendMessage(lang.component(s, "help.line." + i));
  }

  private void handle(CommandSender sender, Throwable x) {
    if (x instanceof DeploymentEngine.OverlapException e)
      lang.send(sender, "error.overlap", Map.of("count", e.count()));
    else if (x instanceof DeploymentEngine.ConflictException e)
      lang.send(sender, "error.conflicts", Map.of("count", e.count(), "id", e.deploymentId()));
    else {
      String message = x.getMessage();
      if (message != null
          && Set.of("busy", "active", "not-active", "no-selection", "no-anchor", "selection-world")
              .contains(message)) lang.send(sender, "error." + message);
      else if (message != null && message.startsWith("world-missing:"))
        lang.send(sender, "error.world-missing", Map.of("world", message.substring(14)));
      else
        lang.send(
            sender,
            "error.operation",
            Map.of("reason", message == null ? x.getClass().getSimpleName() : message));
    }
  }

  private Decoration find(String n) {
    return decorations.find(n).orElseThrow(() -> problem("error.not-found", Map.of("name", n)));
  }

  private DecorationGroup findGroup(String n) {
    return groups.find(n).orElseThrow(() -> problem("error.group-not-found", Map.of("name", n)));
  }

  private Player player(CommandSender s) {
    if (s instanceof Player p) return p;
    throw problem("error.player-only");
  }

  private boolean allowed(CommandSender s, String p) {
    if (s.hasPermission(p) || s.hasPermission("decoswap.admin")) return true;
    lang.send(s, "error.no-permission");
    return false;
  }

  private void require(CommandSender s, String p) {
    if (!allowed(s, p)) throw new SilentProblem();
  }

  private void usage(String[] a, int n, String text) {
    if (a.length < n) throw usage(text);
  }

  private CommandProblem usage(String text) {
    return problem("error.usage", Map.of("usage", text));
  }

  private CommandProblem problem(String key) {
    return new CommandProblem(key, Map.of());
  }

  private CommandProblem problem(String key, Map<String, ?> v) {
    return new CommandProblem(key, v);
  }

  private boolean contains(String[] a, String v) {
    return Arrays.stream(a).filter(Objects::nonNull).anyMatch(x -> x.equalsIgnoreCase(v));
  }

  private int rotationArg(String[] a, int fallback) {
    for (int i = 0; i < a.length - 1; i++)
      if (a[i].equalsIgnoreCase("--rotation"))
        try {
          return Integer.parseInt(a[i + 1]);
        } catch (Exception e) {
          throw problem("error.invalid-number", Map.of("value", a[i + 1]));
        }
    return fallback;
  }

  private String[] prepend(String first, String[] rest) {
    String[] r = new String[rest.length + 1];
    r[0] = first;
    System.arraycopy(rest, 0, r, 1, rest.length);
    return r;
  }

  private Throwable unwrap(Throwable t) {
    Throwable current = t;
    while ((current instanceof CompletionException
            || current instanceof java.util.concurrent.ExecutionException)
        && current.getCause() != null) current = current.getCause();
    return current;
  }

  private String fmt(double v) {
    return String.format(Locale.ROOT, "%.2f", v);
  }

  private String date(java.time.Instant i) {
    return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(i);
  }

  private void sound(Player p, Sound s) {
    if (plugin.settings().sounds()) p.playSound(p.getLocation(), s, .5f, 1.2f);
  }

  private Map<String, Object> infoMap(Decoration d) {
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

  @Override
  public List<String> onTabComplete(
      CommandSender sender, Command command, String alias, String[] a) {
    if (a.length == 1)
      return match(
          a[0],
          List.of(
              "help",
              "guide",
              "book",
              "gui",
              "reload",
              "wand",
              "mode",
              "select",
              "undo",
              "anchor",
              "save",
              "update",
              "pack",
              "delete",
              "info",
              "list",
              "changes",
              "check",
              "scan",
              "deploy",
              "place",
              "show",
              "restore",
              "remove",
              "hide",
              "group",
              "active",
              "status",
              "recovery",
              "language",
              "version"));
    if (a.length == 2)
      return switch (a[0].toLowerCase(Locale.ROOT)) {
        case "mode" -> match(a[1], List.of("object", "region", "anchor"));
        case "select" ->
            match(
                a[1],
                List.of(
                    "info",
                    "clear",
                    "region",
                    "blocks",
                    "entities",
                    "invert",
                    "remove",
                    "filter",
                    "cancel"));
        case "anchor" -> match(a[1], List.of("set", "info", "clear"));
        case "decoration" ->
            match(
                a[1],
                List.of(
                    "create", "save", "update", "delete", "info", "list", "pack", "deploy",
                    "restore"));
        case "deploy",
            "place",
            "show",
            "restore",
            "remove",
            "hide",
            "changes",
            "check",
            "scan",
            "status",
            "info",
            "delete" ->
            match(a[1], decorations.all().stream().map(Decoration::id).toList());
        case "group" ->
            match(
                a[1],
                List.of(
                    "create", "delete", "add", "remove", "info", "list", "deploy", "restore",
                    "place", "show", "enable", "disable", "hide"));
        case "recovery" -> match(a[1], List.of("list", "inspect", "rollback", "accept"));
        case "language" -> match(a[1], List.of("auto", "en_US", "es_ES"));
        default -> List.of();
      };
    if (a.length == 3 && a[0].equalsIgnoreCase("group") && !a[1].equalsIgnoreCase("create"))
      return match(a[2], groups.all().stream().map(DecorationGroup::id).toList());
    if (a.length == 4
        && a[0].equalsIgnoreCase("group")
        && (a[1].equalsIgnoreCase("add") || a[1].equalsIgnoreCase("remove")))
      return match(a[3], decorations.all().stream().map(Decoration::id).toList());
    if (a.length == 3 && a[0].equalsIgnoreCase("select") && a[1].equalsIgnoreCase("region"))
      return match(a[2], List.of("all", "blocks", "entities"));
    return List.of();
  }

  private List<String> match(String prefix, Collection<String> values) {
    String p = prefix.toLowerCase(Locale.ROOT);
    return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(p)).sorted().toList();
  }

  private static class CommandProblem extends RuntimeException {
    final String key;
    final Map<String, ?> values;

    CommandProblem(String key, Map<String, ?> values) {
      this.key = key;
      this.values = values;
    }
  }

  private static final class SilentProblem extends CommandProblem {
    SilentProblem() {
      super("", Map.of());
    }
  }
}
