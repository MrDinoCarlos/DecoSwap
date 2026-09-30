package es.mrdino.decoswap.serialization.entity;

import java.util.Map;
import org.bukkit.Color;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

class DisplayEntitySerializer extends GenericEntitySerializer {
  @Override
  public boolean supports(Entity entity) {
    return entity instanceof Display || entity instanceof Interaction;
  }

  @Override
  public void capture(Entity raw, Map<String, String> p, Map<String, byte[]> items) {
    super.capture(raw, p, items);
    if (raw instanceof Interaction i) {
      p.put("interaction.width", Float.toString(i.getInteractionWidth()));
      p.put("interaction.height", Float.toString(i.getInteractionHeight()));
      p.put("interaction.responsive", Boolean.toString(i.isResponsive()));
      return;
    }
    Display d = (Display) raw;
    captureTransform(d.getTransformation(), p);
    p.put("display.billboard", d.getBillboard().name());
    p.put("display.interpolationDelay", Integer.toString(d.getInterpolationDelay()));
    p.put("display.interpolationDuration", Integer.toString(d.getInterpolationDuration()));
    p.put("display.teleportDuration", Integer.toString(d.getTeleportDuration()));
    p.put("display.shadowRadius", Float.toString(d.getShadowRadius()));
    p.put("display.shadowStrength", Float.toString(d.getShadowStrength()));
    p.put("display.viewRange", Float.toString(d.getViewRange()));
    p.put("display.width", Float.toString(d.getDisplayWidth()));
    p.put("display.height", Float.toString(d.getDisplayHeight()));
    if (d.getBrightness() != null) {
      p.put("display.blockLight", Integer.toString(d.getBrightness().getBlockLight()));
      p.put("display.skyLight", Integer.toString(d.getBrightness().getSkyLight()));
    }
    p.put(
        "display.glowColor",
        d.getGlowColorOverride() == null ? "" : Integer.toString(d.getGlowColorOverride().asRGB()));
    if (d instanceof BlockDisplay b) p.put("blockDisplay.data", b.getBlock().getAsString(true));
    if (d instanceof ItemDisplay i) {
      ItemStack stack = i.getItemStack();
      if (!stack.isEmpty()) items.put("itemDisplay.item", stack.serializeAsBytes());
      p.put("itemDisplay.transform", i.getItemDisplayTransform().name());
    }
    if (d instanceof TextDisplay t) {
      if (t.text() != null) p.put("text.text", JSON.serialize(t.text()));
      p.put("text.lineWidth", Integer.toString(t.getLineWidth()));
      p.put("text.opacity", Byte.toString(t.getTextOpacity()));
      p.put("text.alignment", t.getAlignment().name());
      p.put("text.seeThrough", Boolean.toString(t.isSeeThrough()));
      p.put("text.shadowed", Boolean.toString(t.isShadowed()));
      p.put("text.defaultBackground", Boolean.toString(t.isDefaultBackground()));
      if (t.getBackgroundColor() != null)
        p.put("text.background", Integer.toString(t.getBackgroundColor().asARGB()));
    }
  }

  @Override
  public void apply(Entity raw, Map<String, String> p, Map<String, byte[]> items) {
    super.apply(raw, p, items);
    if (raw instanceof Interaction i) {
      i.setInteractionWidth(decimal(p, "interaction.width", 1));
      i.setInteractionHeight(decimal(p, "interaction.height", 1));
      i.setResponsive(bool(p, "interaction.responsive", false));
      return;
    }
    Display d = (Display) raw;
    d.setTransformation(readTransform(p));
    try {
      d.setBillboard(Display.Billboard.valueOf(p.getOrDefault("display.billboard", "FIXED")));
    } catch (Exception ignored) {
    }
    d.setInterpolationDelay(integer(p, "display.interpolationDelay", 0));
    d.setInterpolationDuration(integer(p, "display.interpolationDuration", 0));
    d.setTeleportDuration(Math.max(0, Math.min(59, integer(p, "display.teleportDuration", 0))));
    d.setShadowRadius(decimal(p, "display.shadowRadius", 0));
    d.setShadowStrength(decimal(p, "display.shadowStrength", 1));
    d.setViewRange(decimal(p, "display.viewRange", 1));
    d.setDisplayWidth(decimal(p, "display.width", 0));
    d.setDisplayHeight(decimal(p, "display.height", 0));
    if (p.containsKey("display.blockLight"))
      d.setBrightness(
          new Display.Brightness(
              integer(p, "display.blockLight", 0), integer(p, "display.skyLight", 0)));
    if (!p.getOrDefault("display.glowColor", "").isBlank())
      try {
        d.setGlowColorOverride(Color.fromRGB(Integer.parseInt(p.get("display.glowColor"))));
      } catch (Exception ignored) {
      }
    if (d instanceof BlockDisplay b && p.containsKey("blockDisplay.data"))
      b.setBlock(org.bukkit.Bukkit.createBlockData(p.get("blockDisplay.data")));
    if (d instanceof ItemDisplay i) {
      if (items.containsKey("itemDisplay.item"))
        i.setItemStack(ItemStack.deserializeBytes(items.get("itemDisplay.item")));
      try {
        i.setItemDisplayTransform(
            ItemDisplay.ItemDisplayTransform.valueOf(p.get("itemDisplay.transform")));
      } catch (Exception ignored) {
      }
    }
    if (d instanceof TextDisplay t) {
      if (p.containsKey("text.text")) t.text(JSON.deserialize(p.get("text.text")));
      t.setLineWidth(integer(p, "text.lineWidth", 200));
      t.setTextOpacity((byte) integer(p, "text.opacity", -1));
      try {
        t.setAlignment(TextDisplay.TextAlignment.valueOf(p.get("text.alignment")));
      } catch (Exception ignored) {
      }
      t.setSeeThrough(bool(p, "text.seeThrough", false));
      t.setShadowed(bool(p, "text.shadowed", false));
      t.setDefaultBackground(bool(p, "text.defaultBackground", false));
      if (p.containsKey("text.background"))
        try {
          t.setBackgroundColor(Color.fromARGB(Integer.parseInt(p.get("text.background"))));
        } catch (Exception ignored) {
        }
    }
  }

  private void captureTransform(Transformation t, Map<String, String> p) {
    p.put("transform.translation", v(t.getTranslation()));
    p.put("transform.scale", v(t.getScale()));
    p.put("transform.left", q(t.getLeftRotation()));
    p.put("transform.right", q(t.getRightRotation()));
  }

  private Transformation readTransform(Map<String, String> p) {
    return new Transformation(
        vector(p.get("transform.translation"), new Vector3f()),
        quat(p.get("transform.left"), new Quaternionf()),
        vector(p.get("transform.scale"), new Vector3f(1)),
        quat(p.get("transform.right"), new Quaternionf()));
  }

  private String v(Vector3f v) {
    return v.x + "," + v.y + "," + v.z;
  }

  private String q(Quaternionf q) {
    return q.x + "," + q.y + "," + q.z + "," + q.w;
  }

  private Vector3f vector(String s, Vector3f fallback) {
    if (s == null) return fallback;
    String[] a = s.split(",");
    try {
      return new Vector3f(Float.parseFloat(a[0]), Float.parseFloat(a[1]), Float.parseFloat(a[2]));
    } catch (Exception e) {
      return fallback;
    }
  }

  private Quaternionf quat(String s, Quaternionf fallback) {
    if (s == null) return fallback;
    String[] a = s.split(",");
    try {
      return new Quaternionf(
          Float.parseFloat(a[0]),
          Float.parseFloat(a[1]),
          Float.parseFloat(a[2]),
          Float.parseFloat(a[3]));
    } catch (Exception e) {
      return fallback;
    }
  }
}
