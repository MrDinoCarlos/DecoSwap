package es.mrdino.decoswap.util;

import java.util.UUID;
import org.bukkit.Location;

public record BlockKey(UUID worldId, int x, int y, int z) {
  public static BlockKey of(Location location) {
    if (location.getWorld() == null) throw new IllegalArgumentException("Location has no world");
    return new BlockKey(
        location.getWorld().getUID(),
        location.getBlockX(),
        location.getBlockY(),
        location.getBlockZ());
  }
}
