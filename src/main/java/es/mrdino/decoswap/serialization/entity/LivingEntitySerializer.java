package es.mrdino.decoswap.serialization.entity;

import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

class LivingEntitySerializer extends GenericEntitySerializer {
  @Override
  public boolean supports(org.bukkit.entity.Entity entity) {
    return entity instanceof LivingEntity && !(entity instanceof ArmorStand);
  }

  @Override
  public void capture(
      org.bukkit.entity.Entity raw, Map<String, String> p, Map<String, byte[]> items) {
    super.capture(raw, p, items);
    LivingEntity e = (LivingEntity) raw;
    p.put("ai", Boolean.toString(e.hasAI()));
    p.put("collidable", Boolean.toString(e.isCollidable()));
    p.put("canPickup", Boolean.toString(e.getCanPickupItems()));
    p.put("removeWhenFar", Boolean.toString(e.getRemoveWhenFarAway()));
    p.put("health", Double.toString(e.getHealth()));
    p.put("pose", e.getPose().name());
    for (Attribute attribute : Registry.ATTRIBUTE) {
      var instance = e.getAttribute(attribute);
      if (instance != null)
        p.put(
            "attribute." + Registry.ATTRIBUTE.getKeyOrThrow(attribute),
            Double.toString(instance.getBaseValue()));
    }
    captureEquipment(e.getEquipment(), items, p);
    if (e instanceof Ageable a) {
      p.put("age", Integer.toString(a.getAge()));
      p.put("ageLock", Boolean.toString(a.getAgeLock()));
    }
    if (e instanceof Tameable t) {
      p.put("tamed", Boolean.toString(t.isTamed()));
      if (t.getOwnerUniqueId() != null) p.put("owner", t.getOwnerUniqueId().toString());
    }
    if (e instanceof Villager v) {
      p.put(
          "villager.profession",
          Registry.VILLAGER_PROFESSION.getKeyOrThrow(v.getProfession()).toString());
      p.put("villager.type", Registry.VILLAGER_TYPE.getKeyOrThrow(v.getVillagerType()).toString());
      p.put("villager.level", Integer.toString(v.getVillagerLevel()));
    }
  }

  @Override
  public void apply(
      org.bukkit.entity.Entity raw, Map<String, String> p, Map<String, byte[]> items) {
    super.apply(raw, p, items);
    LivingEntity e = (LivingEntity) raw;
    e.setAI(bool(p, "ai", true));
    e.setCollidable(bool(p, "collidable", true));
    e.setCanPickupItems(bool(p, "canPickup", false));
    e.setRemoveWhenFarAway(bool(p, "removeWhenFar", false));
    p.forEach(
        (key, value) -> {
          if (key.startsWith("attribute.")) {
            NamespacedKey id = NamespacedKey.fromString(key.substring(10));
            Attribute attribute = id == null ? null : Registry.ATTRIBUTE.get(id);
            if (attribute != null && e.getAttribute(attribute) != null)
              try {
                e.getAttribute(attribute).setBaseValue(Double.parseDouble(value));
              } catch (Exception ignored) {
              }
          }
        });
    double max =
        e.getAttribute(Attribute.MAX_HEALTH) == null
            ? e.getHealth()
            : e.getAttribute(Attribute.MAX_HEALTH).getValue();
    try {
      e.setHealth(
          Math.max(
              0.01,
              Math.min(max, Double.parseDouble(p.getOrDefault("health", Double.toString(max))))));
    } catch (Exception ignored) {
    }
    applyEquipment(e.getEquipment(), items, p);
    if (e instanceof Ageable a) {
      a.setAge(integer(p, "age", 0));
      a.setAgeLock(bool(p, "ageLock", false));
    }
    if (e instanceof Tameable t) {
      t.setTamed(bool(p, "tamed", false));
      if (p.containsKey("owner"))
        try {
          t.setOwner(Bukkit.getOfflinePlayer(java.util.UUID.fromString(p.get("owner"))));
        } catch (Exception ignored) {
        }
    }
    if (e instanceof Villager v) {
      try {
        NamespacedKey profession = NamespacedKey.fromString(p.get("villager.profession"));
        if (profession != null && Registry.VILLAGER_PROFESSION.get(profession) != null)
          v.setProfession(Registry.VILLAGER_PROFESSION.get(profession));
      } catch (Exception ignored) {
      }
      try {
        NamespacedKey type = NamespacedKey.fromString(p.get("villager.type"));
        if (type != null && Registry.VILLAGER_TYPE.get(type) != null)
          v.setVillagerType(Registry.VILLAGER_TYPE.get(type));
      } catch (Exception ignored) {
      }
      v.setVillagerLevel(Math.max(1, Math.min(5, integer(p, "villager.level", 1))));
    }
  }

  static void captureEquipment(
      EntityEquipment eq, Map<String, byte[]> items, Map<String, String> p) {
    if (eq == null) return;
    for (EquipmentSlot slot : EquipmentSlot.values()) {
      try {
        ItemStack item = eq.getItem(slot);
        if (item != null && !item.isEmpty())
          items.put("equipment." + slot.name(), item.serializeAsBytes());
        p.put("drop." + slot.name(), Float.toString(eq.getDropChance(slot)));
      } catch (IllegalArgumentException ignored) {
      }
    }
  }

  static void applyEquipment(EntityEquipment eq, Map<String, byte[]> items, Map<String, String> p) {
    if (eq == null) return;
    for (EquipmentSlot slot : EquipmentSlot.values()) {
      try {
        byte[] data = items.get("equipment." + slot.name());
        if (data != null) eq.setItem(slot, ItemStack.deserializeBytes(data), true);
        eq.setDropChance(slot, decimal(p, "drop." + slot.name(), 0));
      } catch (IllegalArgumentException ignored) {
      }
    }
  }
}
