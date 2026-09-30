package es.mrdino.decoswap.serialization.entity;

import java.util.Map;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

class GenericEntitySerializer implements EntitySerializer<Entity> {
  protected static final GsonComponentSerializer JSON = GsonComponentSerializer.gson();

  @Override
  public boolean supports(Entity entity) {
    return true;
  }

  @Override
  public void capture(Entity e, Map<String, String> p, Map<String, byte[]> items) {
    if (e.customName() != null) p.put("name", JSON.serialize(e.customName()));
    p.put("nameVisible", Boolean.toString(e.isCustomNameVisible()));
    p.put("gravity", Boolean.toString(e.hasGravity()));
    p.put("glowing", Boolean.toString(e.isGlowing()));
    p.put("invulnerable", Boolean.toString(e.isInvulnerable()));
    p.put("silent", Boolean.toString(e.isSilent()));
    p.put("persistent", Boolean.toString(e.isPersistent()));
    p.put("fireTicks", Integer.toString(e.getFireTicks()));
    p.put("freezeTicks", Integer.toString(e.getFreezeTicks()));
    p.put("visualFire", Boolean.toString(e.isVisualFire()));
    Vector v = e.getVelocity();
    p.put("velocity", v.getX() + "," + v.getY() + "," + v.getZ());
  }

  @Override
  public void apply(Entity e, Map<String, String> p, Map<String, byte[]> items) {
    if (p.containsKey("name")) e.customName(JSON.deserialize(p.get("name")));
    e.setCustomNameVisible(bool(p, "nameVisible", false));
    e.setGravity(bool(p, "gravity", true));
    e.setGlowing(bool(p, "glowing", false));
    e.setInvulnerable(bool(p, "invulnerable", false));
    e.setSilent(bool(p, "silent", false));
    e.setPersistent(bool(p, "persistent", true));
    e.setFireTicks(integer(p, "fireTicks", 0));
    e.setFreezeTicks(integer(p, "freezeTicks", 0));
    e.setVisualFire(bool(p, "visualFire", false));
    String[] v = p.getOrDefault("velocity", "0,0,0").split(",");
    if (v.length == 3)
      try {
        e.setVelocity(
            new Vector(
                Double.parseDouble(v[0]), Double.parseDouble(v[1]), Double.parseDouble(v[2])));
      } catch (NumberFormatException ignored) {
      }
  }

  protected static boolean bool(Map<String, String> p, String k, boolean f) {
    return p.containsKey(k) ? Boolean.parseBoolean(p.get(k)) : f;
  }

  protected static int integer(Map<String, String> p, String k, int f) {
    try {
      return Integer.parseInt(p.get(k));
    } catch (Exception e) {
      return f;
    }
  }

  protected static float decimal(Map<String, String> p, String k, float f) {
    try {
      return Float.parseFloat(p.get(k));
    } catch (Exception e) {
      return f;
    }
  }
}
