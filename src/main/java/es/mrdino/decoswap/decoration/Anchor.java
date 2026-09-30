package es.mrdino.decoswap.decoration;

import es.mrdino.decoswap.util.Rotation;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

public record Anchor(
    UUID worldId, String worldName, double x, double y, double z, float yaw, float pitch) {
  public static Anchor from(Location location) {
    World world = location.getWorld();
    if (world == null) throw new IllegalArgumentException("Anchor location requires a world");
    return new Anchor(
        world.getUID(),
        world.getName(),
        location.getX(),
        location.getY(),
        location.getZ(),
        location.getYaw(),
        location.getPitch());
  }

  public double[] relative(Location location) {
    return new double[] {location.getX() - x, location.getY() - y, location.getZ() - z};
  }

  public double[] transformRelative(
      double rx, double ry, double rz, Rotation rotation, Anchor target) {
    double[] value = rotation.rotate(rx, rz);
    return new double[] {target.x + value[0], target.y + ry, target.z + value[1]};
  }

  public Location transform(
      World world, double rx, double ry, double rz, Rotation rotation, Anchor target) {
    double[] transformed = transformRelative(rx, ry, rz, rotation, target);
    return new Location(
        world,
        transformed[0],
        transformed[1],
        transformed[2],
        (float) (target.yaw + rotation.degrees()),
        target.pitch);
  }

  public BlockFace cardinal() {
    int index = Math.floorMod(Math.round(yaw / 90f), 4);
    return new BlockFace[] {BlockFace.SOUTH, BlockFace.WEST, BlockFace.NORTH, BlockFace.EAST}
        [index];
  }

  public Vector forward() {
    double radians = Math.toRadians(yaw);
    return new Vector(-Math.sin(radians), 0, Math.cos(radians)).normalize();
  }
}
