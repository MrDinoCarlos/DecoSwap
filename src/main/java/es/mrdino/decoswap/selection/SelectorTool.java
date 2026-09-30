package es.mrdino.decoswap.selection;

import es.mrdino.decoswap.DecoSwapPlugin;
import es.mrdino.decoswap.config.PluginConfig;
import es.mrdino.decoswap.language.LanguageService;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class SelectorTool {
  private PluginConfig config;
  private final LanguageService lang;

  public SelectorTool(PluginConfig config, LanguageService lang) {
    this.config = config;
    this.lang = lang;
  }

  public void config(PluginConfig config) {
    this.config = config;
  }

  public ItemStack create(Player player) {
    ItemStack item = new ItemStack(config.selectorMaterial());
    item.editMeta(
        meta -> {
          meta.displayName(lang.component(player, "wand.name"));
          meta.lore(
              List.of(
                  lang.component(player, "wand.lore.1"),
                  lang.component(player, "wand.lore.2"),
                  lang.component(player, "wand.lore.3")));
          meta.getPersistentDataContainer()
              .set(DecoSwapPlugin.SELECTOR_KEY, PersistentDataType.BYTE, (byte) 1);
          meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        });
    return item;
  }

  public boolean isSelector(ItemStack item) {
    return item != null
        && !item.isEmpty()
        && item.hasItemMeta()
        && item.getItemMeta()
            .getPersistentDataContainer()
            .has(DecoSwapPlugin.SELECTOR_KEY, PersistentDataType.BYTE);
  }
}
