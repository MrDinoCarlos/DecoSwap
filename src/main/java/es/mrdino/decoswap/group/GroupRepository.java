package es.mrdino.decoswap.group;

import es.mrdino.decoswap.util.AtomicFiles;
import es.mrdino.decoswap.util.Names;
import es.mrdino.decoswap.util.TextureReference;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class GroupRepository {
  private final Path file;
  private final Map<String, DecorationGroup> groups = new LinkedHashMap<>();

  public GroupRepository(JavaPlugin plugin) {
    file = plugin.getDataFolder().toPath().resolve("groups.yml");
  }

  public void load() {
    groups.clear();
    YamlConfiguration y = YamlConfiguration.loadConfiguration(file.toFile());
    ConfigurationSection root = y.getConfigurationSection("groups");
    if (root == null) return;
    for (String id : root.getKeys(false)) {
      if (!Names.isSafeId(id)) continue;
      groups.put(
          id,
          new DecorationGroup(
              id,
              root.getString(id + ".display-name", id),
              root.getStringList(id + ".members"),
              TextureReference.hash(root.getString(id + ".icon-texture")).orElse(null)));
    }
  }

  public Collection<DecorationGroup> all() {
    return Collections.unmodifiableCollection(groups.values());
  }

  public Optional<DecorationGroup> find(String name) {
    return Optional.ofNullable(groups.get(Names.normalize(name)));
  }

  public DecorationGroup create(String name) throws IOException {
    String id = Names.normalize(name);
    if (id.isBlank()) throw new IllegalArgumentException("invalid-name");
    if (groups.containsKey(id)) throw new IllegalStateException("already-exists");
    DecorationGroup g = new DecorationGroup(id, name, List.of());
    groups.put(id, g);
    save();
    return g;
  }

  public void delete(String name) throws IOException {
    groups.remove(Names.normalize(name));
    save();
  }

  public void save() throws IOException {
    YamlConfiguration y = new YamlConfiguration();
    y.set("format-version", 2);
    for (DecorationGroup g : groups.values()) {
      y.set("groups." + g.id() + ".display-name", g.displayName());
      y.set("groups." + g.id() + ".members", g.members());
      if (g.iconTexture() == null) y.set("groups." + g.id() + ".icon-texture", null);
      else y.set("groups." + g.id() + ".icon-texture", g.iconTexture());
    }
    AtomicFiles.write(file, y.saveToString().getBytes(StandardCharsets.UTF_8));
  }
}
