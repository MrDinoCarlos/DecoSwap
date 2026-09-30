package es.mrdino.decoswap.serialization.block;

import java.util.*;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Nameable;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.*;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.block.sign.Side;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

final class CoreBlockStateSerializer implements BlockStateSerializer<BlockState> {
  private static final GsonComponentSerializer JSON = GsonComponentSerializer.gson();

  @Override
  public boolean supports(BlockState state) {
    return true;
  }

  @Override
  public void capture(BlockState state, Map<String, String> p, Map<Integer, byte[]> items) {
    p.put("state.type", state.getClass().getName());
    if (state instanceof InventoryHolder h) {
      ItemStack[] contents = h.getInventory().getContents();
      for (int i = 0; i < contents.length; i++)
        if (contents[i] != null && !contents[i].isEmpty())
          items.put(i, contents[i].serializeAsBytes());
    }
    if (state instanceof Sign sign) {
      captureSide(sign, Side.FRONT, "sign.front", p);
      captureSide(sign, Side.BACK, "sign.back", p);
      p.put("sign.waxed", Boolean.toString(sign.isWaxed()));
    }
    if (state instanceof Banner banner) {
      p.put("banner.base", banner.getBaseColor().name());
      int n = 0;
      for (Pattern pattern : banner.getPatterns()) {
        p.put("banner." + n + ".color", pattern.getColor().name());
        p.put(
            "banner." + n + ".type",
            Registry.BANNER_PATTERN.getKeyOrThrow(pattern.getPattern()).toString());
        n++;
      }
      p.put("banner.count", Integer.toString(n));
    }
    if (state instanceof Skull skull) {
      if (skull.getPlayerProfile() != null)
        p.put("skull.profile", skull.getPlayerProfile().getUniqueId().toString());
      p.put("skull.rotation", skull.getRotation().name());
    }
    if (state instanceof CreatureSpawner s) {
      p.put(
          "spawner.type", s.getSpawnedType() == null ? "" : s.getSpawnedType().getKey().toString());
      p.put("spawner.delay", Integer.toString(s.getDelay()));
      p.put("spawner.minDelay", Integer.toString(s.getMinSpawnDelay()));
      p.put("spawner.maxDelay", Integer.toString(s.getMaxSpawnDelay()));
      p.put("spawner.count", Integer.toString(s.getSpawnCount()));
      p.put("spawner.range", Integer.toString(s.getRequiredPlayerRange()));
      p.put("spawner.spawnRange", Integer.toString(s.getSpawnRange()));
      p.put("spawner.maxNearby", Integer.toString(s.getMaxNearbyEntities()));
    }
    if (state instanceof CommandBlock c) {
      p.put("command.command", c.getCommand());
      p.put("command.name", c.getName());
    }
    if (state instanceof Lectern l) p.put("lectern.page", Integer.toString(l.getPage()));
    if (state instanceof Jukebox j) {
      ItemStack playing = j.getRecord();
      if (!playing.isEmpty()) items.put(-1, playing.serializeAsBytes());
    }
    if (state instanceof Campfire c) {
      for (int i = 0; i < c.getSize(); i++) {
        ItemStack item = c.getItem(i);
        if (item != null && !item.isEmpty()) items.put(1000 + i, item.serializeAsBytes());
        p.put("campfire.cook." + i, Integer.toString(c.getCookTime(i)));
        p.put("campfire.total." + i, Integer.toString(c.getCookTimeTotal(i)));
      }
    }
    if (state instanceof Lockable l) p.put("lock", l.getLock());
    if (state instanceof Nameable n && n.customName() != null)
      p.put("customName", JSON.serialize(n.customName()));
  }

  @Override
  public void apply(BlockState state, Map<String, String> p, Map<Integer, byte[]> items) {
    if (state instanceof InventoryHolder h) {
      h.getInventory().clear();
      items.forEach(
          (slot, data) -> {
            if (slot >= 0 && slot < h.getInventory().getSize())
              h.getInventory().setItem(slot, ItemStack.deserializeBytes(data));
          });
    }
    if (state instanceof Sign sign) {
      applySide(sign, Side.FRONT, "sign.front", p);
      applySide(sign, Side.BACK, "sign.back", p);
      sign.setWaxed(Boolean.parseBoolean(p.getOrDefault("sign.waxed", "false")));
    }
    if (state instanceof Banner banner) {
      banner.setPatterns(new ArrayList<>());
      int count = parse(p, "banner.count", 0);
      for (int i = 0; i < count; i++) {
        DyeColor color = parseEnum(DyeColor.class, p.get("banner." + i + ".color"), DyeColor.WHITE);
        NamespacedKey key = NamespacedKey.fromString(p.get("banner." + i + ".type"));
        PatternType type = key == null ? null : Registry.BANNER_PATTERN.get(key);
        if (type != null) banner.addPattern(new Pattern(color, type));
      }
    }
    if (state instanceof Skull skull && p.containsKey("skull.profile")) {
      try {
        skull.setOwnerProfile(Bukkit.createPlayerProfile(UUID.fromString(p.get("skull.profile"))));
      } catch (Exception ignored) {
      }
    }
    if (state instanceof CreatureSpawner s) {
      String key = p.get("spawner.type");
      if (key != null && !key.isBlank()) {
        org.bukkit.entity.EntityType type = Registry.ENTITY_TYPE.get(NamespacedKey.fromString(key));
        if (type != null && type.isSpawnable()) s.setSpawnedType(type);
      }
      s.setDelay(parse(p, "spawner.delay", s.getDelay()));
      s.setMinSpawnDelay(parse(p, "spawner.minDelay", s.getMinSpawnDelay()));
      s.setMaxSpawnDelay(parse(p, "spawner.maxDelay", s.getMaxSpawnDelay()));
      s.setSpawnCount(parse(p, "spawner.count", s.getSpawnCount()));
      s.setRequiredPlayerRange(parse(p, "spawner.range", s.getRequiredPlayerRange()));
      s.setSpawnRange(parse(p, "spawner.spawnRange", s.getSpawnRange()));
      s.setMaxNearbyEntities(parse(p, "spawner.maxNearby", s.getMaxNearbyEntities()));
    }
    if (state instanceof CommandBlock c) {
      c.setCommand(p.getOrDefault("command.command", c.getCommand()));
      c.setName(p.getOrDefault("command.name", c.getName()));
    }
    if (state instanceof Lectern l) l.setPage(parse(p, "lectern.page", 0));
    if (state instanceof Jukebox j && items.containsKey(-1))
      j.setRecord(ItemStack.deserializeBytes(items.get(-1)));
    if (state instanceof Campfire c) {
      for (int i = 0; i < c.getSize(); i++) {
        byte[] value = items.get(1000 + i);
        if (value != null) c.setItem(i, ItemStack.deserializeBytes(value));
        c.setCookTime(i, parse(p, "campfire.cook." + i, 0));
        c.setCookTimeTotal(i, parse(p, "campfire.total." + i, 0));
      }
    }
    if (state instanceof Lockable l) l.setLock(p.getOrDefault("lock", ""));
    if (state instanceof Nameable n && p.containsKey("customName"))
      n.customName(JSON.deserialize(p.get("customName")));
  }

  private void captureSide(Sign sign, Side side, String prefix, Map<String, String> p) {
    var target = sign.getSide(side);
    for (int i = 0; i < target.lines().size(); i++)
      p.put(prefix + "." + i, JSON.serialize(target.line(i)));
    p.put(prefix + ".glowing", Boolean.toString(target.isGlowingText()));
    p.put(prefix + ".color", target.getColor().name());
  }

  private void applySide(Sign sign, Side side, String prefix, Map<String, String> p) {
    var target = sign.getSide(side);
    for (int i = 0; i < 4; i++)
      if (p.containsKey(prefix + "." + i))
        target.line(i, JSON.deserialize(p.get(prefix + "." + i)));
    target.setGlowingText(Boolean.parseBoolean(p.getOrDefault(prefix + ".glowing", "false")));
    target.setColor(parseEnum(DyeColor.class, p.get(prefix + ".color"), DyeColor.BLACK));
  }

  private int parse(Map<String, String> p, String key, int fallback) {
    try {
      return Integer.parseInt(p.get(key));
    } catch (Exception e) {
      return fallback;
    }
  }

  private <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback) {
    try {
      return Enum.valueOf(type, value);
    } catch (Exception e) {
      return fallback;
    }
  }
}
