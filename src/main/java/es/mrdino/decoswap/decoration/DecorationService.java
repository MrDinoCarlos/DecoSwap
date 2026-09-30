package es.mrdino.decoswap.decoration;

import es.mrdino.decoswap.DecoSwapPlugin;
import es.mrdino.decoswap.selection.PlayerSelection;
import es.mrdino.decoswap.serialization.block.BlockSerializerRegistry;
import es.mrdino.decoswap.serialization.entity.EntitySerializerRegistry;
import es.mrdino.decoswap.storage.BinaryCodec;
import es.mrdino.decoswap.storage.DecorationRepository;
import es.mrdino.decoswap.util.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class DecorationService {
  private final JavaPlugin plugin;
  private final DecorationRepository repository;
  private final BlockSerializerRegistry blocks;
  private final EntitySerializerRegistry entities;

  public DecorationService(
      JavaPlugin plugin,
      DecorationRepository repository,
      BlockSerializerRegistry blocks,
      EntitySerializerRegistry entities) {
    this.plugin = plugin;
    this.repository = repository;
    this.blocks = blocks;
    this.entities = entities;
  }

  public Decoration capture(
      Player player, String name, PlayerSelection selection, Decoration existing) throws Exception {
    String id = Names.normalize(name);
    if (id.isBlank()) throw new IllegalArgumentException("invalid-name");
    Anchor anchor =
        selection.anchor() != null
            ? selection.anchor()
            : existing != null ? existing.homeAnchor() : Anchor.from(player.getLocation());
    World world = Bukkit.getWorld(anchor.worldId());
    if (world == null) throw new IllegalStateException("world-missing:" + anchor.worldName());
    List<BlockRecord> blockRecords = new ArrayList<>();
    for (BlockKey key : selection.blocks()) {
      if (!key.worldId().equals(world.getUID())) throw new IllegalStateException("selection-world");
      blockRecords.add(
          blocks.captureTemplate(
              world.getBlockAt(key.x(), key.y(), key.z()),
              anchor,
              player.hasPermission("decoswap.capture.inventory"),
              player.hasPermission("decoswap.capture.commandblock"),
              player.hasPermission("decoswap.capture.dangerous")));
    }
    Map<UUID, Integer> ids = new LinkedHashMap<>();
    int next = 0;
    for (UUID uuid : selection.entities()) {
      Entity e = Bukkit.getEntity(uuid);
      if (e != null) {
        if (e.getWorld() != world) throw new IllegalStateException("selection-world");
        ids.put(uuid, next++);
      }
    }
    List<EntityRecord> entityRecords = new ArrayList<>();
    for (var entry : ids.entrySet()) {
      Entity e = Bukkit.getEntity(entry.getKey());
      if (e != null) entityRecords.add(entities.capture(e, anchor, entry.getValue(), ids));
    }
    if (blockRecords.isEmpty() && entityRecords.isEmpty())
      throw new IllegalStateException("no-selection");
    Instant now = Instant.now();
    UUID uuid = existing == null ? UUID.randomUUID() : existing.uuid();
    Instant created = existing == null ? now : existing.createdAt();
    String checksum = Fingerprint.decoration(blockRecords, entityRecords);
    return new Decoration(
        uuid,
        id,
        name,
        BinaryCodec.SCHEMA_VERSION,
        player.getUniqueId(),
        player.getName(),
        created,
        now,
        Bukkit.getMinecraftVersion(),
        plugin.getPluginMeta().getVersion(),
        anchor,
        Bounds.of(blockRecords, entityRecords),
        existing == null ? "" : existing.description(),
        existing == null ? Set.of() : existing.tags(),
        checksum,
        blockRecords,
        entityRecords);
  }

  public CompletableFuture<Decoration> save(
      Player player, String name, PlayerSelection selection, boolean update) {
    try {
      Decoration existing = repository.find(name).orElse(null);
      if (existing != null && !update)
        return CompletableFuture.failedFuture(
            new java.nio.file.FileAlreadyExistsException(existing.id()));
      Decoration captured = capture(player, name, selection, existing);
      if (plugin.getConfig().getBoolean("operations.save-async", true))
        return CompletableFuture.supplyAsync(
            () -> {
              try {
                repository.save(captured, existing != null);
                return captured;
              } catch (Exception e) {
                throw new java.util.concurrent.CompletionException(e);
              }
            });
      repository.save(captured, existing != null);
      return CompletableFuture.completedFuture(captured);
    } catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  public void removeSelected(Player player, PlayerSelection selection) {
    for (UUID id : new ArrayList<>(selection.entities())) {
      Entity e = Bukkit.getEntity(id);
      if (e != null) e.remove();
    }
    for (BlockKey key : selection.blocks()) {
      World world = Bukkit.getWorld(key.worldId());
      if (world != null)
        world.getBlockAt(key.x(), key.y(), key.z()).setType(org.bukkit.Material.AIR, false);
    }
  }

  public void removeSelected(Set<BlockKey> selectedBlocks, Set<UUID> selectedEntities) {
    for (UUID id : selectedEntities) {
      Entity e = Bukkit.getEntity(id);
      if (e != null) e.remove();
    }
    for (BlockKey key : selectedBlocks) {
      World world = Bukkit.getWorld(key.worldId());
      if (world != null)
        world.getBlockAt(key.x(), key.y(), key.z()).setType(org.bukkit.Material.AIR, false);
    }
  }

  public DecorationChanges detectChanges(
      Player player, Decoration decoration, PlayerSelection selection) throws Exception {
    Anchor anchor = decoration.homeAnchor();
    World world = Bukkit.getWorld(anchor.worldId());
    if (world == null) throw new IllegalStateException("world-missing:" + anchor.worldName());
    List<BlockKey> changedBlocks = new ArrayList<>();
    for (BlockRecord expected : decoration.blocks()) {
      int x = (int) Math.floor(anchor.x()) + expected.x();
      int y = (int) Math.floor(anchor.y()) + expected.y();
      int z = (int) Math.floor(anchor.z()) + expected.z();
      BlockKey key = new BlockKey(world.getUID(), x, y, z);
      try {
        BlockRecord actual = blocks.capture(world.getBlockAt(x, y, z), anchor);
        if (!sameBlock(expected, actual)) changedBlocks.add(key);
      } catch (Exception ignored) {
        changedBlocks.add(key);
      }
    }

    List<UUID> selected = new ArrayList<>(selection.entities());
    Map<UUID, Integer> localIds = new LinkedHashMap<>();
    for (int i = 0; i < selected.size(); i++) localIds.put(selected.get(i), i);
    List<UUID> changedEntities = new ArrayList<>();
    List<BlockKey> changedEntityLocations = new ArrayList<>();
    List<EntityChange> entityDetails = new ArrayList<>();
    Set<UUID> used = new HashSet<>();
    for (EntityRecord expected : decoration.entities()) {
      NamespacedKey typeKey = NamespacedKey.fromString(expected.type());
      EntityType expectedType = typeKey == null ? null : Registry.ENTITY_TYPE.get(typeKey);
      Location expectedLocation =
          new Location(
              world,
              anchor.x() + expected.x(),
              anchor.y() + expected.y(),
              anchor.z() + expected.z());
      List<Entity> candidates = new ArrayList<>();
      for (UUID id : selected) {
        Entity candidate = Bukkit.getEntity(id);
        if (candidate != null
            && candidate.getWorld() == world
            && !used.contains(id)
            && (expectedType == null || candidate.getType() == expectedType))
          candidates.add(candidate);
      }
      for (Entity candidate : world.getNearbyEntities(expectedLocation, 4, 4, 4))
        if (!(candidate instanceof Player)
            && !candidate.getPersistentDataContainer().has(DecoSwapPlugin.VISUAL_KEY)
            && !used.contains(candidate.getUniqueId())
            && (expectedType == null || candidate.getType() == expectedType)
            && candidates.stream().noneMatch(e -> e.getUniqueId().equals(candidate.getUniqueId())))
          candidates.add(candidate);
      candidates.sort(
          Comparator.comparingDouble(e -> e.getLocation().distanceSquared(expectedLocation)));
      Entity entity = candidates.isEmpty() ? null : candidates.getFirst();
      if (entity == null) {
        BlockKey location =
            new BlockKey(
                world.getUID(),
                expectedLocation.getBlockX(),
                expectedLocation.getBlockY(),
                expectedLocation.getBlockZ());
        changedEntityLocations.add(location);
        entityDetails.add(
            new EntityChange(
                expected.localId(),
                friendlyType(expected.type()),
                expectedName(expected),
                location,
                null,
                true,
                false));
        continue;
      }
      used.add(entity.getUniqueId());
      localIds.put(entity.getUniqueId(), expected.localId());
      EntityRecord actual = entities.capture(entity, anchor, expected.localId(), localIds);
      if (!Fingerprint.entity(actual).equals(Fingerprint.entity(expected)))
        {
          changedEntities.add(entity.getUniqueId());
          entityDetails.add(
              new EntityChange(
                  expected.localId(),
                  friendlyType(expected.type()),
                  displayName(entity),
                  blockKey(entity.getLocation()),
                  entity.getUniqueId(),
                  false,
                  false));
        }
    }
    for (UUID id : selected)
      if (!used.contains(id)) {
        Entity extra = Bukkit.getEntity(id);
        if (extra != null) {
          changedEntities.add(id);
          entityDetails.add(
              new EntityChange(
                  -1,
                  friendlyType(extra.getType().getKey().toString()),
                  displayName(extra),
                  blockKey(extra.getLocation()),
                  id,
                  false,
                  true));
        }
      }
    return new DecorationChanges(
        decoration.id(),
        changedBlocks,
        changedEntities,
        changedEntityLocations,
        changedEntityLocations.size(),
        Math.max(0, selected.size() - used.size()),
        entityDetails);
  }

  private BlockKey blockKey(Location location) {
    return new BlockKey(
        location.getWorld().getUID(),
        location.getBlockX(),
        location.getBlockY(),
        location.getBlockZ());
  }

  private String displayName(Entity entity) {
    if (entity.customName() != null) {
      String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
          .serialize(entity.customName()).trim();
      if (!plain.isBlank()) return plain;
    }
    return friendlyType(entity.getType().getKey().toString());
  }

  private String expectedName(EntityRecord record) {
    String json = record.properties().get("name");
    if (json != null) {
      try {
        String plain =
            net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(
                    net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson()
                        .deserialize(json))
                .trim();
        if (!plain.isBlank()) return plain;
      } catch (RuntimeException ignored) {
        // Fall back to the stable entity type when an older template has invalid name data.
      }
    }
    return friendlyType(record.type());
  }

  private String friendlyType(String key) {
    String value = key == null ? "entity" : key.substring(key.indexOf(':') + 1);
    return java.util.Arrays.stream(value.split("[_-]"))
        .filter(s -> !s.isBlank())
        .map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT))
        .collect(java.util.stream.Collectors.joining(" "));
  }

  private boolean sameBlock(BlockRecord expected, BlockRecord actual) {
    if (!expected.blockData().equals(actual.blockData())
        || !expected.properties().equals(actual.properties())) return false;
    if (expected.pdc().length > 0 && !Arrays.equals(expected.pdc(), actual.pdc())) return false;
    if (!expected.items().isEmpty()) {
      if (!expected.items().keySet().equals(actual.items().keySet())) return false;
      for (Integer slot : expected.items().keySet())
        if (!Arrays.equals(expected.items().get(slot), actual.items().get(slot))) return false;
    }
    return true;
  }
}
