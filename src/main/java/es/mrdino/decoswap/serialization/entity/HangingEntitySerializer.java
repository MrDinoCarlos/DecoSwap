package es.mrdino.decoswap.serialization.entity;

import java.util.Map;
import org.bukkit.Art;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;

class HangingEntitySerializer extends GenericEntitySerializer {
  @Override
  public boolean supports(Entity entity) {
    return entity instanceof Hanging;
  }

  @Override
  public void capture(Entity raw, Map<String, String> p, Map<String, byte[]> items) {
    super.capture(raw, p, items);
    Hanging h = (Hanging) raw;
    p.put("hanging.facing", h.getFacing().name());
    if (h instanceof ItemFrame f) {
      ItemStack item = f.getItem();
      if (!item.isEmpty()) items.put("frame.item", item.serializeAsBytes());
      p.put("frame.rotation", f.getRotation().name());
      p.put("frame.fixed", Boolean.toString(f.isFixed()));
      p.put("frame.visible", Boolean.toString(f.isVisible()));
      p.put("frame.dropChance", Float.toString(f.getItemDropChance()));
    }
    if (h instanceof Painting painting)
      p.put("painting.art", org.bukkit.Registry.ART.getKeyOrThrow(painting.getArt()).toString());
  }

  @Override
  public void apply(Entity raw, Map<String, String> p, Map<String, byte[]> items) {
    super.apply(raw, p, items);
    Hanging h = (Hanging) raw;
    try {
      h.setFacingDirection(BlockFace.valueOf(p.getOrDefault("hanging.facing", "NORTH")), true);
    } catch (Exception ignored) {
    }
    if (h instanceof ItemFrame f) {
      if (items.containsKey("frame.item"))
        f.setItem(ItemStack.deserializeBytes(items.get("frame.item")), false);
      try {
        f.setRotation(org.bukkit.Rotation.valueOf(p.get("frame.rotation")));
      } catch (Exception ignored) {
      }
      f.setFixed(bool(p, "frame.fixed", false));
      f.setVisible(bool(p, "frame.visible", true));
      f.setItemDropChance(decimal(p, "frame.dropChance", 1));
    }
    if (h instanceof Painting painting) {
      org.bukkit.NamespacedKey key =
          org.bukkit.NamespacedKey.fromString(p.getOrDefault("painting.art", "minecraft:kebab"));
      Art art = key == null ? null : org.bukkit.Registry.ART.get(key);
      if (art != null) painting.setArt(art, true);
    }
  }
}
