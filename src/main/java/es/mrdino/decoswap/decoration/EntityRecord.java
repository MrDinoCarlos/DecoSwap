package es.mrdino.decoswap.decoration;

import java.util.*;

public record EntityRecord(
    int localId,
    String type,
    double x,
    double y,
    double z,
    float yaw,
    float pitch,
    Map<String, String> properties,
    Map<String, byte[]> items,
    byte[] pdc,
    List<String> scoreboardTags,
    List<Integer> passengers,
    Integer leashHolder) {
  public static final Comparator<EntityRecord> ORDER =
      Comparator.comparingInt(EntityRecord::localId);

  public EntityRecord {
    properties = Map.copyOf(properties);
    items = Map.copyOf(items);
    pdc = pdc == null ? new byte[0] : pdc.clone();
    scoreboardTags = List.copyOf(scoreboardTags);
    passengers = List.copyOf(passengers);
  }

  @Override
  public byte[] pdc() {
    return pdc.clone();
  }
}
