package es.mrdino.decoswap.compat;

import org.bukkit.Bukkit;

public final class BaselineCapabilities implements ServerCapabilities {
  private final String version = Bukkit.getMinecraftVersion();

  public String adapterName() {
    return "Paper API 1.21.4 semantic adapter";
  }

  public String minecraftVersion() {
    return version;
  }

  public boolean supportedVersion() {
    String clean = version.split("-", 2)[0];
    String[] p = clean.split("\\.");
    try {
      int major = Integer.parseInt(p[0]),
          minor = p.length > 1 ? Integer.parseInt(p[1]) : 0,
          patch = p.length > 2 ? Integer.parseInt(p[2]) : 0;
      if (major == 1) return minor == 21 && patch >= 4;
      if (major == 26) return minor >= 1 && minor <= 3;
      return major > 1 && major < 26;
    } catch (NumberFormatException e) {
      return false;
    }
  }
}
