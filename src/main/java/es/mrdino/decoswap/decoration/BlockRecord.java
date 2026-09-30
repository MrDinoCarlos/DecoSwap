package es.mrdino.decoswap.decoration;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;

public record BlockRecord(
    int x,
    int y,
    int z,
    String blockData,
    byte[] pdc,
    Map<String, String> properties,
    Map<Integer, byte[]> items) {
  public static final Comparator<BlockRecord> ORDER =
      Comparator.comparingInt(BlockRecord::x)
          .thenComparingInt(BlockRecord::y)
          .thenComparingInt(BlockRecord::z);

  public BlockRecord {
    pdc = pdc == null ? new byte[0] : pdc.clone();
    properties = Map.copyOf(properties);
    items = Map.copyOf(items);
  }

  @Override
  public byte[] pdc() {
    return pdc.clone();
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof BlockRecord b
        && x == b.x
        && y == b.y
        && z == b.z
        && blockData.equals(b.blockData)
        && Arrays.equals(pdc, b.pdc)
        && properties.equals(b.properties)
        && items.keySet().equals(b.items.keySet());
  }

  @Override
  public int hashCode() {
    return 31 * java.util.Objects.hash(x, y, z, blockData, properties, items.keySet())
        + Arrays.hashCode(pdc);
  }
}
