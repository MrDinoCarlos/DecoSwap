package es.mrdino.decoswap.deployment;

import es.mrdino.decoswap.decoration.Anchor;
import es.mrdino.decoswap.util.BlockKey;
import es.mrdino.decoswap.util.Rotation;
import java.time.Instant;
import java.util.*;

public final class Deployment {
  private final UUID id, decorationId, actor;
  private final String decorationName, groupId;
  private final Instant createdAt;
  private final Anchor target;
  private final Rotation rotation;
  private TransactionState state;
  private final Map<BlockKey, String> expectedBlocks = new LinkedHashMap<>();
  private final Map<Integer, UUID> spawnedEntities = new LinkedHashMap<>();

  public Deployment(
      UUID id,
      UUID decorationId,
      String decorationName,
      UUID actor,
      String groupId,
      Instant createdAt,
      Anchor target,
      Rotation rotation,
      TransactionState state) {
    this.id = id;
    this.decorationId = decorationId;
    this.decorationName = decorationName;
    this.actor = actor;
    this.groupId = groupId;
    this.createdAt = createdAt;
    this.target = target;
    this.rotation = rotation;
    this.state = state;
  }

  public UUID id() {
    return id;
  }

  public UUID decorationId() {
    return decorationId;
  }

  public String decorationName() {
    return decorationName;
  }

  public UUID actor() {
    return actor;
  }

  public String groupId() {
    return groupId;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Anchor target() {
    return target;
  }

  public Rotation rotation() {
    return rotation;
  }

  public TransactionState state() {
    return state;
  }

  public void state(TransactionState s) {
    state = s;
  }

  public Map<BlockKey, String> expectedBlocks() {
    return Collections.unmodifiableMap(expectedBlocks);
  }

  public Map<Integer, UUID> spawnedEntities() {
    return Collections.unmodifiableMap(spawnedEntities);
  }

  public void expect(BlockKey key, String data) {
    expectedBlocks.put(key, data);
  }

  public void spawned(int localId, UUID uuid) {
    spawnedEntities.put(localId, uuid);
  }
}
