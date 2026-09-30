package es.mrdino.decoswap.storage;

import es.mrdino.decoswap.config.PluginConfig;
import es.mrdino.decoswap.decoration.Decoration;
import es.mrdino.decoswap.util.AtomicFiles;
import es.mrdino.decoswap.util.Names;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class DecorationRepository {
  private final JavaPlugin plugin;
  private final BinaryCodec codec = new BinaryCodec();
  private final DecorationMigrator migrator = new DecorationMigrator();
  private final Map<String, Decoration> cache = new ConcurrentHashMap<>();
  private PluginConfig config;
  private final Path root, quarantine, backups;

  public DecorationRepository(JavaPlugin plugin, PluginConfig config) {
    this.plugin = plugin;
    this.config = config;
    root = plugin.getDataFolder().toPath().resolve("decorations");
    quarantine = plugin.getDataFolder().toPath().resolve("quarantine");
    backups = plugin.getDataFolder().toPath().resolve("backups");
  }

  public void config(PluginConfig c) {
    config = c;
  }

  public void load() {
    cache.clear();
    try {
      Files.createDirectories(root);
      try (var stream = Files.list(root)) {
        for (Path dir : stream.filter(Files::isDirectory).toList()) {
          Path data = dir.resolve("data.dswap");
          if (!Files.exists(data)) continue;
          try {
            Decoration d = codec.decodeDecoration(Files.readAllBytes(data));
            if (d.schemaVersion() < BinaryCodec.SCHEMA_VERSION) {
              if (config.backups() && config.backupBeforeMigration())
                backup(d.id() + "-pre-migration", data);
              d = migrator.migrate(d);
              AtomicFiles.write(data, codec.encodeDecoration(d));
              writeMeta(dir.resolve("meta.yml"), d);
            }
            if (!Names.isSafeId(d.id()) || cache.putIfAbsent(d.id(), d) != null)
              throw new IOException("Invalid or duplicate ID " + d.id());
          } catch (Exception e) {
            plugin
                .getLogger()
                .log(
                    Level.SEVERE,
                    "Could not load decoration at " + dir + "; it remains untouched",
                    e);
          }
        }
      }
    } catch (IOException e) {
      plugin.getLogger().log(Level.SEVERE, "Could not scan decorations", e);
    }
  }

  public Collection<Decoration> all() {
    return cache.values().stream()
        .sorted(Comparator.comparing(Decoration::displayName, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  public Optional<Decoration> find(String name) {
    String id = Names.normalize(name);
    return Optional.ofNullable(cache.get(id));
  }

  public Optional<Decoration> findByUuid(UUID uuid) {
    if (uuid == null) return Optional.empty();
    return cache.values().stream().filter(d -> uuid.equals(d.uuid())).findFirst();
  }

  public synchronized void save(Decoration d, boolean update) throws IOException {
    if (!Names.isSafeId(d.id())) throw new IOException("Unsafe decoration ID");
    Path dir = root.resolve(d.id()).normalize();
    if (!dir.startsWith(root)) throw new IOException("Unsafe decoration path");
    Path data = dir.resolve("data.dswap");
    if (Files.exists(data) && !update) throw new FileAlreadyExistsException(data.toString());
    if (Files.exists(data) && config.backups() && config.backupBeforeUpdate()) backup(d.id(), data);
    AtomicFiles.write(data, codec.encodeDecoration(d));
    writeMeta(dir.resolve("meta.yml"), d);
    cache.put(d.id(), d);
    pruneBackups();
  }

  public synchronized void delete(String id) throws IOException {
    if (!Names.isSafeId(id)) throw new IOException("Unsafe decoration ID");
    Path dir = root.resolve(id).normalize();
    if (!dir.startsWith(root)) throw new IOException("Unsafe path");
    Path data = dir.resolve("data.dswap");
    if (Files.exists(data) && config.backups() && config.backupBeforeDelete()) backup(id, data);
    Files.deleteIfExists(dir.resolve("meta.yml"));
    Files.deleteIfExists(data);
    Files.deleteIfExists(dir);
    cache.remove(id);
  }

  private void writeMeta(Path path, Decoration d) throws IOException {
    YamlConfiguration y = new YamlConfiguration();
    y.set("format-version", BinaryCodec.FORMAT_VERSION);
    y.set("schema-version", d.schemaVersion());
    y.set("uuid", d.uuid().toString());
    y.set("id", d.id());
    y.set("display-name", d.displayName());
    y.set("creator.uuid", d.creatorUuid() == null ? null : d.creatorUuid().toString());
    y.set("creator.name", d.creatorName());
    y.set("created", d.createdAt().toString());
    y.set("modified", d.modifiedAt().toString());
    y.set("source.minecraft", d.sourceMinecraft());
    y.set("source.decoswap", d.sourcePlugin());
    y.set("home.world.uuid", d.homeAnchor().worldId().toString());
    y.set("home.world.name", d.homeAnchor().worldName());
    y.set(
        "home.anchor",
        List.of(
            d.homeAnchor().x(),
            d.homeAnchor().y(),
            d.homeAnchor().z(),
            d.homeAnchor().yaw(),
            d.homeAnchor().pitch()));
    y.set("counts.blocks", d.blockCount());
    y.set("counts.entities", d.entityCount());
    y.set("description", d.description());
    y.set("tags", new ArrayList<>(d.tags()));
    y.set("checksum", d.checksum());
    try {
      AtomicFiles.write(path, y.saveToString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  private void backup(String id, Path source) throws IOException {
    Files.createDirectories(backups);
    String stamp = Instant.now().toString().replace(':', '-');
    Files.copy(
        source, backups.resolve(id + "-" + stamp + ".dswap"), StandardCopyOption.REPLACE_EXISTING);
  }

  private void pruneBackups() {
    try {
      if (!Files.isDirectory(backups)) return;
      try (var s = Files.list(backups)) {
        Map<String, List<Path>> groups = new HashMap<>();
        for (Path p : s.filter(Files::isRegularFile).toList()) {
          String prefix = p.getFileName().toString().split("-20", 2)[0];
          groups.computeIfAbsent(prefix, k -> new ArrayList<>()).add(p);
        }
        for (List<Path> paths : groups.values()) {
          paths.sort(Comparator.comparingLong(this::modified).reversed());
          for (int i = config.backupKeep(); i < paths.size(); i++)
            Files.deleteIfExists(paths.get(i));
        }
      }
    } catch (IOException e) {
      plugin.getLogger().warning("Could not prune backups: " + e.getMessage());
    }
  }

  private long modified(Path p) {
    try {
      return Files.getLastModifiedTime(p).toMillis();
    } catch (IOException e) {
      return 0;
    }
  }
}
