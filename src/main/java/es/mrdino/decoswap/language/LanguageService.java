package es.mrdino.decoswap.language;

import es.mrdino.decoswap.config.PluginConfig;
import es.mrdino.decoswap.util.ResourceUpdater;
import java.io.File;
import java.io.IOException;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class LanguageService {
  private final JavaPlugin plugin;
  private final MiniMessage mini = MiniMessage.miniMessage();
  private final Map<String, YamlConfiguration> bundles = new HashMap<>();
  private final Map<UUID, String> overrides = new HashMap<>();
  private PluginConfig config;
  private File playersFile;

  public LanguageService(JavaPlugin plugin, PluginConfig config) {
    this.plugin = plugin;
    this.config = config;
    reload();
  }

  public void reload() {
    bundles.clear();
    for (String locale : List.of("en_US", "es_ES")) {
      File file = new File(plugin.getDataFolder(), "lang/" + locale + ".yml");
      ResourceUpdater.updateYaml(
          plugin, file.toPath(), "lang/" + locale + ".yml", plugin.getLogger());
      bundles.put(locale, YamlConfiguration.loadConfiguration(file));
    }
    playersFile = new File(plugin.getDataFolder(), "players.yml");
    YamlConfiguration y = YamlConfiguration.loadConfiguration(playersFile);
    overrides.clear();
    for (String key : y.getKeys(false)) {
      try {
        overrides.put(UUID.fromString(key), y.getString(key));
      } catch (IllegalArgumentException ignored) {
      }
    }
  }

  public void config(PluginConfig value) {
    config = value;
  }

  public String locale(CommandSender sender) {
    if (!(sender instanceof Player p)) return config.defaultLanguage();
    return LocaleResolver.resolve(
        overrides.get(p.getUniqueId()),
        p.locale().toString(),
        config.autoLocale(),
        config.defaultLanguage(),
        config.fallbackLanguage(),
        bundles.keySet());
  }

  public Component component(CommandSender sender, String key, Map<String, ?> values) {
    return mini.deserialize(rawText(sender, key), resolver(values));
  }

  /** Returns one Adventure component per configured newline, suitable for item lore lines. */
  public List<Component> lines(CommandSender sender, String key, Map<String, ?> values) {
    return Arrays.stream(rawText(sender, key).split("\\R", -1))
        .map(line -> mini.deserialize(line, resolver(values)))
        .toList();
  }

  public List<Component> lines(CommandSender sender, String key) {
    return lines(sender, key, Map.of());
  }

  public Component component(CommandSender sender, String key) {
    return component(sender, key, Map.of());
  }

  public void send(CommandSender sender, String key, Map<String, ?> values) {
    Component prefix = component(sender, "prefix");
    sender.sendMessage(prefix.append(component(sender, key, values)));
  }

  public void send(CommandSender sender, String key) {
    send(sender, key, Map.of());
  }

  public Component raw(String locale, String key, Map<String, ?> values) {
    YamlConfiguration y = bundles.getOrDefault(locale, bundles.get("en_US"));
    String value = y.getString(key, key);
    List<TagResolver> tags = new ArrayList<>();
    values.forEach(
        (k, v) ->
            tags.add(
                v instanceof Component c
                    ? Placeholder.component(k, c)
                    : Placeholder.unparsed(k, String.valueOf(v))));
    return mini.deserialize(value, TagResolver.resolver(tags));
  }

  public void set(Player player, String locale) {
    if (locale == null) overrides.remove(player.getUniqueId());
    else overrides.put(player.getUniqueId(), locale);
    YamlConfiguration y = new YamlConfiguration();
    overrides.forEach((id, l) -> y.set(id.toString(), l));
    try {
      y.save(playersFile);
    } catch (IOException e) {
      plugin
          .getLogger()
          .log(java.util.logging.Level.SEVERE, "Could not save language preferences", e);
    }
  }

  public Set<String> available() {
    return Collections.unmodifiableSet(bundles.keySet());
  }

  private String rawText(CommandSender sender, String key) {
    String locale = locale(sender);
    YamlConfiguration bundle = bundles.getOrDefault(locale, bundles.get(config.fallbackLanguage()));
    String raw = bundle == null ? null : bundle.getString(key);
    if (raw != null) return raw;
    YamlConfiguration fallback = bundles.get("en_US");
    return fallback == null ? key : fallback.getString(key, "<red>Missing language key: " + key + "</red>");
  }

  private TagResolver resolver(Map<String, ?> values) {
    List<TagResolver> tags = new ArrayList<>();
    values.forEach(
        (k, v) ->
            tags.add(
                v instanceof Component c
                    ? Placeholder.component(k, c)
                    : Placeholder.unparsed(k, String.valueOf(v))));
    return TagResolver.resolver(tags);
  }
}
