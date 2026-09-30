package es.mrdino.decoswap.serialization.entity;

import es.mrdino.decoswap.DecoSwapPlugin;
import es.mrdino.decoswap.config.PluginConfig;
import es.mrdino.decoswap.decoration.Anchor;
import es.mrdino.decoswap.decoration.EntityRecord;
import es.mrdino.decoswap.util.Rotation;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.java.JavaPlugin;

public final class EntitySerializerRegistry {
  private final List<EntitySerializer<?>> serializers = new ArrayList<>();
  private final Set<EntityType> warnedTypes = EnumSet.noneOf(EntityType.class);
  private PluginConfig config;
  private final JavaPlugin plugin;

  public EntitySerializerRegistry(JavaPlugin plugin, PluginConfig config) {
    this.plugin = plugin;
    this.config = config;
    register(new DisplayEntitySerializer());
    register(new ArmorStandEntitySerializer());
    register(new HangingEntitySerializer());
    register(new LivingEntitySerializer());
    register(new VehicleEntitySerializer());
    register(new GenericEntitySerializer());
  }

  public void register(EntitySerializer<?> serializer) {
    serializers.add(serializer);
  }

  public void config(PluginConfig config) {
    this.config = config;
  }

  public EntityRecord capture(
      Entity entity, Anchor anchor, int localId, Map<UUID, Integer> localIds) throws Exception {
    if (entity instanceof Player
        || entity.getPersistentDataContainer().has(DecoSwapPlugin.VISUAL_KEY))
      throw new IllegalArgumentException("Unsupported transient/player entity");
    Map<String, String> props = new LinkedHashMap<>();
    Map<String, byte[]> items = new LinkedHashMap<>();
    for (EntitySerializer<?> s : serializers)
      if (s.supports(entity)) {
        if (s.getClass() == GenericEntitySerializer.class && warnedTypes.add(entity.getType()))
          plugin
              .getLogger()
              .warning(
                  "Entity type "
                      + entity.getType()
                      + " uses the generic semantic serializer; common state and PDC are retained,"
                      + " but type-specific state may be incomplete.");
        captureUnchecked(s, entity, props, items);
        break;
      }
    PersistentDataContainer clean =
        entity.getPersistentDataContainer().getAdapterContext().newPersistentDataContainer();
    entity.getPersistentDataContainer().copyTo(clean, true);
    clean.remove(DecoSwapPlugin.SPAWNED_KEY);
    clean.remove(DecoSwapPlugin.DECORATION_ID_KEY);
    clean.remove(DecoSwapPlugin.DEPLOYMENT_ID_KEY);
    clean.remove(DecoSwapPlugin.LOCAL_ENTITY_ID_KEY);
    clean.remove(DecoSwapPlugin.VISUAL_KEY);
    double[] rel = anchor.relative(entity.getLocation());
    List<Integer> passengers =
        entity.getPassengers().stream()
            .map(Entity::getUniqueId)
            .map(localIds::get)
            .filter(Objects::nonNull)
            .toList();
    Integer leash = null;
    if (entity instanceof LivingEntity l && l.isLeashed() && l.getLeashHolder() != null)
      leash = localIds.get(l.getLeashHolder().getUniqueId());
    return new EntityRecord(
        localId,
        entity.getType().getKey().toString(),
        rel[0],
        rel[1],
        rel[2],
        entity.getYaw() - anchor.yaw(),
        entity.getPitch() - anchor.pitch(),
        props,
        items,
        clean.serializeToBytes(),
        config.captureScoreboardTags() ? new ArrayList<>(entity.getScoreboardTags()) : List.of(),
        passengers,
        leash);
  }

  public Entity spawn(
      EntityRecord record,
      World world,
      Anchor target,
      Rotation rotation,
      UUID decorationId,
      UUID deploymentId)
      throws Exception {
    NamespacedKey key = NamespacedKey.fromString(record.type());
    EntityType type = key == null ? null : Registry.ENTITY_TYPE.get(key);
    if (type == null || !type.isSpawnable() || type == EntityType.PLAYER)
      throw new IllegalArgumentException("Unsupported entity type " + record.type());
    double[] r = rotation.rotate(record.x(), record.z());
    Location location =
        new Location(
            world,
            target.x() + r[0],
            target.y() + record.y(),
            target.z() + r[1],
            target.yaw() + record.yaw() + rotation.degrees(),
            target.pitch() + record.pitch());
    Entity entity = world.spawnEntity(location, type, false);
    try {
      for (EntitySerializer<?> s : serializers)
        if (s.supports(entity)) {
          applyUnchecked(s, entity, record.properties(), record.items());
          break;
        }
      if (record.pdc().length > 0)
        entity.getPersistentDataContainer().readFromBytes(record.pdc(), true);
      entity
          .getPersistentDataContainer()
          .set(
              DecoSwapPlugin.SPAWNED_KEY, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
      entity
          .getPersistentDataContainer()
          .set(
              DecoSwapPlugin.DECORATION_ID_KEY,
              org.bukkit.persistence.PersistentDataType.STRING,
              decorationId.toString());
      entity
          .getPersistentDataContainer()
          .set(
              DecoSwapPlugin.DEPLOYMENT_ID_KEY,
              org.bukkit.persistence.PersistentDataType.STRING,
              deploymentId.toString());
      entity
          .getPersistentDataContainer()
          .set(
              DecoSwapPlugin.LOCAL_ENTITY_ID_KEY,
              org.bukkit.persistence.PersistentDataType.INTEGER,
              record.localId());
      for (String tag : record.scoreboardTags()) entity.addScoreboardTag(tag);
      return entity;
    } catch (Exception e) {
      entity.remove();
      throw e;
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void captureUnchecked(
      EntitySerializer s, Entity e, Map<String, String> p, Map<String, byte[]> i) throws Exception {
    s.capture(e, p, i);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void applyUnchecked(
      EntitySerializer s, Entity e, Map<String, String> p, Map<String, byte[]> i) throws Exception {
    s.apply(e, p, i);
  }
}
