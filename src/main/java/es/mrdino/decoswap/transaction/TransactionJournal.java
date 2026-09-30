package es.mrdino.decoswap.transaction;

import es.mrdino.decoswap.decoration.Anchor;
import es.mrdino.decoswap.deployment.*;
import es.mrdino.decoswap.util.AtomicFiles;
import es.mrdino.decoswap.util.BlockKey;
import es.mrdino.decoswap.util.Rotation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class TransactionJournal {
  private final JavaPlugin plugin;
  private final Path root, archive;
  private final SnapshotStore snapshots;

  public TransactionJournal(JavaPlugin plugin, SnapshotStore snapshots) {
    this.plugin = plugin;
    this.snapshots = snapshots;
    root = plugin.getDataFolder().toPath().resolve("transactions");
    archive = root.resolve("archive");
  }

  public synchronized void persist(Deployment d, String type, String error) throws IOException {
    YamlConfiguration y = new YamlConfiguration();
    y.set("format-version", 1);
    y.set("transaction-id", d.id().toString());
    y.set("type", type);
    y.set("state", d.state().name());
    y.set("decoration.id", d.decorationId().toString());
    y.set("decoration.name", d.decorationName());
    y.set("actor", d.actor() == null ? null : d.actor().toString());
    y.set("group", d.groupId());
    y.set("created", d.createdAt().toString());
    y.set("updated", Instant.now().toString());
    y.set("target.world.uuid", d.target().worldId().toString());
    y.set("target.world.name", d.target().worldName());
    y.set("target.x", d.target().x());
    y.set("target.y", d.target().y());
    y.set("target.z", d.target().z());
    y.set("target.yaw", d.target().yaw());
    y.set("target.pitch", d.target().pitch());
    y.set("rotation", d.rotation().degrees());
    y.set("error", error);
    for (var e : d.spawnedEntities().entrySet())
      y.set("spawned." + e.getKey(), e.getValue().toString());
    AtomicFiles.write(path(d.id()), y.saveToString().getBytes(StandardCharsets.UTF_8));
  }

  public List<Entry> scan() {
    List<Entry> entries = new ArrayList<>();
    try {
      Files.createDirectories(root);
      try (var s = Files.list(root)) {
        for (Path p : s.filter(f -> f.getFileName().toString().endsWith(".yml")).toList()) {
          try {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(p.toFile());
            UUID id = UUID.fromString(y.getString("transaction-id"));
            TransactionState state = TransactionState.valueOf(y.getString("state"));
            Deployment d = readDeployment(y, id, state);
            if (Files.exists(snapshots.expectedPath(id)))
              for (var b : snapshots.readExpected(id)) {
                BlockKey key =
                    new BlockKey(
                        d.target().worldId(),
                        (int) Math.floor(d.target().x()) + b.x(),
                        (int) Math.floor(d.target().y()) + b.y(),
                        (int) Math.floor(d.target().z()) + b.z());
                d.expect(key, es.mrdino.decoswap.util.Fingerprint.block(b));
              }
            var spawned = y.getConfigurationSection("spawned");
            if (spawned != null)
              for (String key : spawned.getKeys(false))
                d.spawned(Integer.parseInt(key), UUID.fromString(spawned.getString(key)));
            entries.add(
                new Entry(
                    id,
                    y.getString("type", "DEPLOY"),
                    state,
                    d,
                    y.getString("updated", ""),
                    y.getString("error", "")));
          } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Invalid transaction journal " + p, e);
          }
        }
      }
    } catch (IOException e) {
      plugin.getLogger().log(Level.SEVERE, "Could not scan transaction journal", e);
    }
    return entries;
  }

  private Deployment readDeployment(YamlConfiguration y, UUID id, TransactionState state) {
    Anchor a =
        new Anchor(
            UUID.fromString(y.getString("target.world.uuid")),
            y.getString("target.world.name"),
            y.getDouble("target.x"),
            y.getDouble("target.y"),
            y.getDouble("target.z"),
            (float) y.getDouble("target.yaw"),
            (float) y.getDouble("target.pitch"));
    String actor = y.getString("actor");
    return new Deployment(
        id,
        UUID.fromString(y.getString("decoration.id")),
        y.getString("decoration.name"),
        actor == null ? null : UUID.fromString(actor),
        y.getString("group"),
        Instant.parse(y.getString("created")),
        a,
        Rotation.ofDegrees(y.getInt("rotation")),
        state);
  }

  public synchronized void archive(UUID id) throws IOException {
    Path source = path(id);
    if (!Files.exists(source)) return;
    Files.createDirectories(archive);
    Files.move(
        source,
        archive.resolve(id + "-" + Instant.now().toEpochMilli() + ".yml"),
        StandardCopyOption.REPLACE_EXISTING);
  }

  public Path path(UUID id) {
    return root.resolve(id + ".yml");
  }

  public record Entry(
      UUID id,
      String type,
      TransactionState state,
      Deployment deployment,
      String updated,
      String error) {}
}
