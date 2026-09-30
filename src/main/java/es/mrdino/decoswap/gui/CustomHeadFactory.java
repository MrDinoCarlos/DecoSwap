package es.mrdino.decoswap.gui;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

/** Creates vanilla player heads backed by Mojang skin textures without a resource pack. */
final class CustomHeadFactory {
  private static final String TEXTURE_URL = "https://textures.minecraft.net/texture/";

  private final JavaPlugin plugin;
  private final Map<String, Optional<PlayerProfile>> profiles = new HashMap<>();
  private final Set<String> reportedFailures = new HashSet<>();

  CustomHeadFactory(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  // Bukkit's baseline profile setter is deprecated by Paper in favor of Paper's legacy profile
  // type, but it is the stable cross-version API available on the 1.21.4 compile baseline.
  @SuppressWarnings("deprecation")
  ItemStack create(String textureHash, Component name, Component... lore) {
    ItemStack item = new ItemStack(Material.PLAYER_HEAD);
    item.editMeta(
        meta -> {
          meta.displayName(name);
          if (lore.length > 0) meta.lore(List.of(lore));
          if (meta instanceof SkullMeta skull) {
            profile(textureHash).ifPresent(skull::setOwnerProfile);
          }
        });
    return item;
  }

  private Optional<PlayerProfile> profile(String textureHash) {
    if (textureHash == null || !textureHash.matches("[0-9a-fA-F]{32,64}")) {
      report(textureHash, new IllegalArgumentException("invalid texture hash"));
      return Optional.empty();
    }
    return profiles.computeIfAbsent(
        textureHash,
        hash -> {
          try {
            UUID id =
                UUID.nameUUIDFromBytes(("decoswap-head:" + hash).getBytes(StandardCharsets.UTF_8));
            PlayerProfile profile = Bukkit.createPlayerProfile(id);
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(URI.create(TEXTURE_URL + hash).toURL());
            profile.setTextures(textures);
            return Optional.of(profile);
          } catch (Exception exception) {
            report(hash, exception);
            return Optional.empty();
          }
        });
  }

  private void report(String hash, Exception exception) {
    if (!reportedFailures.add(String.valueOf(hash))) return;
    plugin.getLogger().log(Level.WARNING, "Could not create GUI custom head " + hash, exception);
  }
}
