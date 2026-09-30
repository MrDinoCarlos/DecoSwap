package es.mrdino.decoswap.serialization.block;

import es.mrdino.decoswap.config.PluginConfig;
import es.mrdino.decoswap.decoration.Anchor;
import es.mrdino.decoswap.decoration.BlockRecord;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.persistence.PersistentDataHolder;

public final class BlockSerializerRegistry {
  private final List<BlockStateSerializer<?>> serializers = new ArrayList<>();
  private final Set<Class<?>> warnedStates = Collections.synchronizedSet(new HashSet<>());
  private PluginConfig config;
  private final Logger logger;

  public BlockSerializerRegistry(PluginConfig config, Logger logger) {
    this.config = config;
    this.logger = logger;
    register(new CoreBlockStateSerializer());
  }

  public void register(BlockStateSerializer<?> serializer) {
    serializers.add(serializer);
  }

  public void config(PluginConfig config) {
    this.config = config;
  }

  public BlockRecord captureTemplate(
      Block block,
      Anchor anchor,
      boolean inventoryPermission,
      boolean commandPermission,
      boolean dangerousPermission)
      throws Exception {
    validateSensitive(block, inventoryPermission, commandPermission, dangerousPermission);
    BlockRecord record = capture(block, anchor);
    return new BlockRecord(
        record.x(),
        record.y(),
        record.z(),
        record.blockData(),
        config.capturePdc() ? record.pdc() : new byte[0],
        record.properties(),
        config.captureInventories() ? record.items() : Map.of());
  }

  public BlockRecord capture(Block block, Anchor anchor) throws Exception {
    BlockState state = block.getState();
    Map<String, String> properties = new LinkedHashMap<>();
    Map<Integer, byte[]> items = new LinkedHashMap<>();
    byte[] pdc = new byte[0];
    if (state instanceof PersistentDataHolder holder)
      pdc = holder.getPersistentDataContainer().serializeToBytes();
    for (BlockStateSerializer<?> s : serializers)
      if (s.supports(state)) {
        captureUnchecked(s, state, properties, items);
        break;
      }
    if (state instanceof TileState
        && state instanceof InventoryHolder == false
        && state instanceof Sign == false
        && state instanceof Banner == false
        && state instanceof Skull == false
        && state instanceof CreatureSpawner == false
        && state instanceof CommandBlock == false
        && state instanceof Lectern == false
        && state instanceof Jukebox == false
        && state instanceof Campfire == false
        && warnedStates.add(state.getClass()))
      logger.warning(
          "Block state "
              + state.getClass().getName()
              + " has no specialized semantic serializer; BlockData and PDC are retained, but"
              + " type-specific state may be incomplete.");
    int x = block.getX() - (int) Math.floor(anchor.x()),
        y = block.getY() - (int) Math.floor(anchor.y()),
        z = block.getZ() - (int) Math.floor(anchor.z());
    return new BlockRecord(x, y, z, block.getBlockData().getAsString(true), pdc, properties, items);
  }

  public void apply(
      Block block, BlockRecord record, org.bukkit.block.structure.StructureRotation rotation)
      throws Exception {
    BlockData data = Bukkit.createBlockData(record.blockData());
    data.rotate(rotation);
    block.setBlockData(data, false);
    BlockState state = block.getState();
    if (state instanceof PersistentDataHolder holder && record.pdc().length > 0)
      holder.getPersistentDataContainer().readFromBytes(record.pdc(), true);
    for (BlockStateSerializer<?> s : serializers)
      if (s.supports(state)) {
        applyUnchecked(s, state, record.properties(), record.items());
        break;
      }
    state.update(true, false);
  }

  private void validateSensitive(
      Block b,
      boolean inventoryPermission,
      boolean commandPermission,
      boolean dangerousPermission) {
    Material m = b.getType();
    if (m == Material.COMMAND_BLOCK
        || m == Material.CHAIN_COMMAND_BLOCK
        || m == Material.REPEATING_COMMAND_BLOCK) {
      if (!config.commandBlocks() || !commandPermission)
        throw new SecurityException("Command block capture is disabled or not permitted");
    }
    if (m == Material.STRUCTURE_BLOCK && (!config.structureBlocks() || !dangerousPermission))
      throw new SecurityException("Structure block capture is disabled or not permitted");
    if (m == Material.SPAWNER && (!config.spawners() || !dangerousPermission))
      throw new SecurityException("Spawner capture is disabled or not permitted");
    if (config.captureInventories()
        && b.getState() instanceof InventoryHolder
        && !inventoryPermission)
      throw new SecurityException("Inventory capture permission required");
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void captureUnchecked(
      BlockStateSerializer s, BlockState state, Map<String, String> p, Map<Integer, byte[]> i)
      throws Exception {
    s.capture(state, p, i);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void applyUnchecked(
      BlockStateSerializer s, BlockState state, Map<String, String> p, Map<Integer, byte[]> i)
      throws Exception {
    s.apply(state, p, i);
  }
}
