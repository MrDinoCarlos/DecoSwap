package es.mrdino.decoswap.compat.easyarmorstands;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class RuntimeEasyArmorStandsHook implements EasyArmorStandsHook {
  private final Plugin plugin;

  public RuntimeEasyArmorStandsHook(boolean configured) {
    Plugin found = configured ? Bukkit.getPluginManager().getPlugin("EasyArmorStands") : null;
    plugin = found != null && found.isEnabled() ? found : null;
  }

  public boolean enabled() {
    return plugin != null;
  }

  public String detectedVersion() {
    return plugin == null ? "disabled" : plugin.getPluginMeta().getVersion();
  }
}
