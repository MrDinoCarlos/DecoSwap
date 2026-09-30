package es.mrdino.decoswap.decoration;

import es.mrdino.decoswap.util.BlockKey;
import java.util.List;
import java.util.UUID;

/** Immutable result of comparing a saved template with its current world objects. */
public record DecorationChanges(
    String decorationId,
    List<BlockKey> changedBlocks,
    List<UUID> changedEntities,
    List<BlockKey> changedEntityLocations,
    int missingEntities,
    int additionalEntities,
    List<EntityChange> entityDetails) {
  public DecorationChanges {
    changedBlocks = List.copyOf(changedBlocks);
    changedEntities = List.copyOf(changedEntities);
    changedEntityLocations = List.copyOf(changedEntityLocations);
    entityDetails = List.copyOf(entityDetails);
  }

  public int totalChanges() {
    return changedBlocks.size() + changedEntities.size() + missingEntities + additionalEntities;
  }

  public boolean isEmpty() {
    return totalChanges() == 0;
  }
}
