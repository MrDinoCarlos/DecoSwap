package es.mrdino.decoswap.config;

import es.mrdino.decoswap.deployment.ConflictPolicy;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public record PluginConfig(
    String defaultLanguage,
    String fallbackLanguage,
    boolean autoLocale,
    Material selectorMaterial,
    int maxRegionVolume,
    int maxBlocks,
    int maxEntities,
    boolean visuals,
    int blockOutlineLimit,
    int entityOutlineLimit,
    int detailRadius,
    Material lineMaterial,
    Material entityLineMaterial,
    Material changedLineMaterial,
    Material anchorMaterial,
    int blocksPerTick,
    int entitiesPerTick,
    ConflictPolicy conflictPolicy,
    boolean allowOverlap,
    boolean allowDuplicateHome,
    boolean captureNonPersistent,
    boolean captureScoreboardTags,
    boolean captureInventories,
    boolean capturePdc,
    boolean commandBlocks,
    boolean structureBlocks,
    boolean spawners,
    boolean backups,
    int backupKeep,
    boolean backupBeforeUpdate,
    boolean backupBeforeDelete,
    boolean backupBeforeMigration,
    boolean easyArmorStands,
    boolean sounds) {
  public static PluginConfig load(JavaPlugin plugin) {
    FileConfiguration c = plugin.getConfig();
    return new PluginConfig(
        lang(c, "language.default", "en_US"),
        lang(c, "language.fallback", "en_US"),
        c.getBoolean("language.auto-detect-client-locale", true),
        material(c, "selection.selector-material", Material.BLAZE_ROD),
        clamp(c.getInt("selection.max-region-volume", 1_000_000), 1, 20_000_000),
        clamp(c.getInt("selection.max-blocks", 500_000), 1, 2_000_000),
        clamp(c.getInt("selection.max-entities", 10_000), 1, 100_000),
        c.getBoolean("visuals.enabled", true),
        clamp(c.getInt("visuals.block-outline-limit", 200), 0, 2000),
        clamp(c.getInt("visuals.entity-outline-limit", 200), 0, 2000),
        clamp(c.getInt("visuals.detail-radius", 48), 4, 256),
        material(c, "visuals.line-material", Material.LIGHT_BLUE_STAINED_GLASS),
        material(c, "visuals.entity-line-material", Material.LIME_STAINED_GLASS),
        material(c, "visuals.changed-line-material", Material.RED_STAINED_GLASS),
        material(c, "visuals.anchor-material", Material.GOLD_BLOCK),
        clamp(c.getInt("operations.blocks-per-tick", 2500), 1, 100_000),
        clamp(c.getInt("operations.entities-per-tick", 100), 1, 10_000),
        ConflictPolicy.parse(c.getString("conflicts.restore-policy", "ABORT")),
        c.getBoolean("deployments.allow-overlap", false),
        c.getBoolean("deployments.allow-duplicate-home", false),
        c.getBoolean("entities.capture-non-persistent", false),
        c.getBoolean("entities.capture-scoreboard-tags", true),
        c.getBoolean("blocks.capture-inventories", true),
        c.getBoolean("blocks.capture-pdc", true),
        c.getBoolean("dangerous-data.command-blocks", false),
        c.getBoolean("dangerous-data.structure-blocks", false),
        c.getBoolean("dangerous-data.spawners", true),
        c.getBoolean("backups.enabled", true),
        clamp(c.getInt("backups.keep", 10), 1, 100),
        c.getBoolean("backups.before-update", true),
        c.getBoolean("backups.before-delete", true),
        c.getBoolean("backups.before-migration", true),
        c.getBoolean("integrations.easyarmorstands", true),
        c.getBoolean("sounds.enabled", true));
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  private static String lang(FileConfiguration c, String path, String fallback) {
    String v = c.getString(path, fallback);
    return v != null && v.matches("[a-z]{2}_[A-Z]{2}") ? v : fallback;
  }

  private static Material material(FileConfiguration c, String path, Material fallback) {
    Material m = Material.matchMaterial(c.getString(path, fallback.name()));
    return m == null ? fallback : m;
  }
}
