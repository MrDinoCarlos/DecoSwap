package es.mrdino.decoswap.serialization.entity;

import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.CommandMinecart;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

class VehicleEntitySerializer extends GenericEntitySerializer {
  @Override
  public boolean supports(Entity entity) {
    return entity instanceof Vehicle;
  }

  @Override
  public void capture(Entity raw, Map<String, String> p, Map<String, byte[]> items) {
    super.capture(raw, p, items);
    if (raw instanceof Minecart cart) {
      p.put("minecart.damage", Double.toString(cart.getDamage()));
      p.put("minecart.maxSpeed", Double.toString(cart.getMaxSpeed()));
      p.put("minecart.display", cart.getDisplayBlockData().getAsString(true));
      p.put("minecart.offset", Integer.toString(cart.getDisplayBlockOffset()));
    }
    if (raw instanceof InventoryHolder holder) {
      ItemStack[] contents = holder.getInventory().getContents();
      for (int i = 0; i < contents.length; i++)
        if (contents[i] != null && !contents[i].isEmpty())
          items.put("inventory." + i, contents[i].serializeAsBytes());
    }
    if (raw instanceof CommandMinecart command) {
      p.put("commandMinecart.command", command.getCommand());
      p.put("commandMinecart.name", command.getName());
    }
  }

  @Override
  public void apply(Entity raw, Map<String, String> p, Map<String, byte[]> items) {
    super.apply(raw, p, items);
    if (raw instanceof Minecart cart) {
      try {
        cart.setDamage(Double.parseDouble(p.getOrDefault("minecart.damage", "0")));
        cart.setMaxSpeed(Double.parseDouble(p.getOrDefault("minecart.maxSpeed", "0.4")));
      } catch (NumberFormatException ignored) {
      }
      if (p.containsKey("minecart.display"))
        cart.setDisplayBlockData(Bukkit.createBlockData(p.get("minecart.display")));
      cart.setDisplayBlockOffset(integer(p, "minecart.offset", 6));
    }
    if (raw instanceof InventoryHolder holder) {
      holder.getInventory().clear();
      items.forEach(
          (key, value) -> {
            if (key.startsWith("inventory."))
              try {
                int slot = Integer.parseInt(key.substring(10));
                if (slot < holder.getInventory().getSize())
                  holder.getInventory().setItem(slot, ItemStack.deserializeBytes(value));
              } catch (Exception ignored) {
              }
          });
    }
    if (raw instanceof CommandMinecart command) {
      command.setCommand(p.getOrDefault("commandMinecart.command", ""));
      command.setName(p.getOrDefault("commandMinecart.name", "@"));
    }
  }
}
