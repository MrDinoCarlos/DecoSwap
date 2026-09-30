package es.mrdino.decoswap.decoration;

import java.time.Instant;
import java.util.*;

public record Decoration(
    UUID uuid,
    String id,
    String displayName,
    int schemaVersion,
    UUID creatorUuid,
    String creatorName,
    Instant createdAt,
    Instant modifiedAt,
    String sourceMinecraft,
    String sourcePlugin,
    Anchor homeAnchor,
    Bounds bounds,
    String description,
    Set<String> tags,
    String checksum,
    List<BlockRecord> blocks,
    List<EntityRecord> entities) {
  public Decoration {
    tags = Set.copyOf(tags);
    blocks = List.copyOf(blocks);
    entities = List.copyOf(entities);
  }

  public int blockCount() {
    return blocks.size();
  }

  public int entityCount() {
    return entities.size();
  }
}
