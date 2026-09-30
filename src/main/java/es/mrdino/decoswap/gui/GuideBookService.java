package es.mrdino.decoswap.gui;

import es.mrdino.decoswap.language.LanguageService;
import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;

/** Opens the localized vanilla written-book quick guide. */
public final class GuideBookService {
  private final JavaPlugin plugin;
  private final LanguageService lang;

  public GuideBookService(JavaPlugin plugin, LanguageService lang) {
    this.plugin = plugin;
    this.lang = lang;
  }

  public void open(Player player) {
    ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
    book.editMeta(
        meta -> {
          if (!(meta instanceof BookMeta written)) return;
          written.title(text(player, "guide.title"));
          written.author(text(player, "guide.author"));
          written.setGeneration(BookMeta.Generation.ORIGINAL);
          List<Component> pages = new ArrayList<>();
          Map<String, Object> values =
              Map.of("version", plugin.getPluginMeta().getVersion());
          for (int page = 1; page <= 9; page++)
            pages.add(lang.component(player, "guide.page." + page, values));
          written.addPages(pages.toArray(Component[]::new));
        });
    player.openBook(book);
  }

  private Component text(Player player, String key) {
    return lang.component(player, key);
  }

}
