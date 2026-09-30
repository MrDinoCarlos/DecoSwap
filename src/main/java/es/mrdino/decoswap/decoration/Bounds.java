package es.mrdino.decoswap.decoration;

import java.util.Collection;

public record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
  public static Bounds of(Collection<BlockRecord> blocks, Collection<EntityRecord> entities) {
    double minX = 0, minY = 0, minZ = 0, maxX = 0, maxY = 0, maxZ = 0;
    boolean first = true;
    for (BlockRecord b : blocks) {
      double[] v = {b.x(), b.y(), b.z()};
      if (first) {
        minX = maxX = v[0];
        minY = maxY = v[1];
        minZ = maxZ = v[2];
        first = false;
      } else {
        minX = Math.min(minX, v[0]);
        minY = Math.min(minY, v[1]);
        minZ = Math.min(minZ, v[2]);
        maxX = Math.max(maxX, v[0]);
        maxY = Math.max(maxY, v[1]);
        maxZ = Math.max(maxZ, v[2]);
      }
    }
    for (EntityRecord e : entities) {
      double[] v = {e.x(), e.y(), e.z()};
      if (first) {
        minX = maxX = v[0];
        minY = maxY = v[1];
        minZ = maxZ = v[2];
        first = false;
      } else {
        minX = Math.min(minX, v[0]);
        minY = Math.min(minY, v[1]);
        minZ = Math.min(minZ, v[2]);
        maxX = Math.max(maxX, v[0]);
        maxY = Math.max(maxY, v[1]);
        maxZ = Math.max(maxZ, v[2]);
      }
    }
    return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
  }
}
