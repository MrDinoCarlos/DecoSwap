package es.mrdino.decoswap;

import es.mrdino.decoswap.api.*;
import es.mrdino.decoswap.command.DecoSwapCommand;
import es.mrdino.decoswap.compat.*;
import es.mrdino.decoswap.compat.easyarmorstands.*;
import es.mrdino.decoswap.config.PluginConfig;
import es.mrdino.decoswap.decoration.ChangeMonitor;
import es.mrdino.decoswap.decoration.DecorationService;
import es.mrdino.decoswap.deployment.DeploymentEngine;
import es.mrdino.decoswap.group.*;
import es.mrdino.decoswap.gui.GuiService;
import es.mrdino.decoswap.gui.GuideBookService;
import es.mrdino.decoswap.language.LanguageService;
import es.mrdino.decoswap.listener.SelectionListener;
import es.mrdino.decoswap.selection.*;
import es.mrdino.decoswap.serialization.block.BlockSerializerRegistry;
import es.mrdino.decoswap.serialization.entity.EntitySerializerRegistry;
import es.mrdino.decoswap.storage.DecorationRepository;
import es.mrdino.decoswap.transaction.*;
import es.mrdino.decoswap.util.ResourceUpdater;
import es.mrdino.decoswap.visual.VisualService;
import java.io.File;
import org.bukkit.*;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class DecoSwapPlugin extends JavaPlugin {
  public static NamespacedKey SELECTOR_KEY,
      VISUAL_KEY,
      SPAWNED_KEY,
      DECORATION_ID_KEY,
      DEPLOYMENT_ID_KEY,
      LOCAL_ENTITY_ID_KEY;
  private PluginConfig settings;
  private LanguageService languages;
  private SelectionManager selections;
  private SelectorTool selector;
  private VisualService visuals;
  private SelectionListener selectionListener;
  private DecorationRepository decorations;
  private GroupRepository groups;
  private BlockSerializerRegistry blockSerializers;
  private EntitySerializerRegistry entitySerializers;
  private DeploymentEngine deployments;

  @Override
  public void onLoad() {
    SELECTOR_KEY = new NamespacedKey(this, "selector");
    VISUAL_KEY = new NamespacedKey(this, "visual");
    SPAWNED_KEY = new NamespacedKey(this, "spawned");
    DECORATION_ID_KEY = new NamespacedKey(this, "decoration_id");
    DEPLOYMENT_ID_KEY = new NamespacedKey(this, "deployment_id");
    LOCAL_ENTITY_ID_KEY = new NamespacedKey(this, "local_entity_id");
  }

  @Override
  public void onEnable() {
    getLogger().info("Enabling DecoSwap v" + getPluginMeta().getVersion());
    saveDefaultConfig();
    updateConfigResource();
    saveIfMissing("groups.yml");
    settings = PluginConfig.load(this);
    languages = new LanguageService(this, settings);
    ServerCapabilities capabilities = new BaselineCapabilities();
    getLogger().info("Server: " + Bukkit.getName() + " " + Bukkit.getMinecraftVersion());
    if (!capabilities.supportedVersion())
      getLogger()
          .warning(
              "This server version is outside DecoSwap's tested compatibility range (1.21.4 through"
                  + " 26.3). The baseline adapter will be used where APIs remain compatible.");
    decorations = new DecorationRepository(this, settings);
    decorations.load();
    groups = new GroupRepository(this);
    groups.load();
    blockSerializers = new BlockSerializerRegistry(settings, getLogger());
    entitySerializers = new EntitySerializerRegistry(this, settings);
    SnapshotStore snapshots = new SnapshotStore(this);
    TransactionJournal journal = new TransactionJournal(this, snapshots);
    deployments =
        new DeploymentEngine(
            this, settings, languages, blockSerializers, entitySerializers, snapshots, journal);
    deployments.load();
    selections = new SelectionManager(this, languages, settings);
    selector = new SelectorTool(settings, languages);
    visuals = new VisualService(this, settings, selections, selector, languages);
    selections.visuals(visuals);
    visuals.start();
    selectionListener =
        new SelectionListener(this, settings, selections, selector, visuals, languages);
    getServer().getPluginManager().registerEvents(selectionListener, this);
    DecorationService decorationService =
        new DecorationService(this, decorations, blockSerializers, entitySerializers);
    new ChangeMonitor(this, languages, decorations, decorationService, deployments, selections);
    GroupService groupService = new GroupService(decorations, deployments);
    GuideBookService guide = new GuideBookService(this, languages);
    GuiService gui =
        new GuiService(
            this,
            languages,
            guide,
            decorationService,
            decorations,
            groups,
            groupService,
            deployments,
            selections);
    getServer().getPluginManager().registerEvents(gui, this);
    EasyArmorStandsHook easy = new RuntimeEasyArmorStandsHook(settings.easyArmorStands());
    if (easy.enabled()) getLogger().info("EasyArmorStands integration enabled.");
    DecoSwapCommand handler =
        new DecoSwapCommand(
            this,
            languages,
            selections,
            selector,
            decorationService,
            decorations,
            deployments,
            groups,
            groupService,
            gui,
            guide,
            capabilities,
            easy);
    PluginCommand command = getCommand("decoswap");
    if (command == null)
      throw new IllegalStateException("Command decoswap is missing from plugin.yml");
    command.setExecutor(handler);
    command.setTabCompleter(handler);
    getServer()
        .getServicesManager()
        .register(
            DecoSwapAPI.class,
            new DecoSwapApiImpl(decorations, groups, deployments),
            this,
            ServicePriority.Normal);
    getLogger().info("Loaded " + decorations.all().size() + " decorations.");
    getLogger().info("Loaded " + groups.all().size() + " groups.");
    getLogger().info("Ready.");
  }

  @Override
  public void onDisable() {
    if (visuals != null) visuals.stop();
    getServer().getServicesManager().unregisterAll(this);
    getLogger().info("Disabled; editor visuals were removed and persistent transactions retained.");
  }

  public void reloadDecoSwap() {
    updateConfigResource();
    reloadConfig();
    settings = PluginConfig.load(this);
    languages.config(settings);
    languages.reload();
    decorations.config(settings);
    blockSerializers.config(settings);
    entitySerializers.config(settings);
    deployments.config(settings);
    selections.config(settings);
    selector.config(settings);
    selectionListener.config(settings);
    visuals.config(settings);
  }

  public PluginConfig settings() {
    return settings;
  }

  private void saveIfMissing(String resource) {
    File file = new File(getDataFolder(), resource);
    if (!file.exists()) saveResource(resource, false);
  }

  private void updateConfigResource() {
    ResourceUpdater.updateYaml(
        this, getDataFolder().toPath().resolve("config.yml"), "config.yml", getLogger());
  }
}
