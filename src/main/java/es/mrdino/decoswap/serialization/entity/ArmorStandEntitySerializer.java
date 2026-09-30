package es.mrdino.decoswap.serialization.entity;

import java.util.Map;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.EulerAngle;

class ArmorStandEntitySerializer extends GenericEntitySerializer {
  @Override
  public boolean supports(Entity entity) {
    return entity instanceof ArmorStand;
  }

  @Override
  public void capture(Entity raw, Map<String, String> p, Map<String, byte[]> items) {
    super.capture(raw, p, items);
    ArmorStand a = (ArmorStand) raw;
    p.put("arms", Boolean.toString(a.hasArms()));
    p.put("basePlate", Boolean.toString(a.hasBasePlate()));
    p.put("marker", Boolean.toString(a.isMarker()));
    p.put("small", Boolean.toString(a.isSmall()));
    p.put("visible", Boolean.toString(a.isVisible()));
    pose(p, "head", a.getHeadPose());
    pose(p, "body", a.getBodyPose());
    pose(p, "leftArm", a.getLeftArmPose());
    pose(p, "rightArm", a.getRightArmPose());
    pose(p, "leftLeg", a.getLeftLegPose());
    pose(p, "rightLeg", a.getRightLegPose());
    LivingEntitySerializer.captureEquipment(a.getEquipment(), items, p);
    for (EquipmentSlot slot : EquipmentSlot.values())
      for (ArmorStand.LockType lock : ArmorStand.LockType.values())
        try {
          p.put("lock." + slot + "." + lock, Boolean.toString(a.hasEquipmentLock(slot, lock)));
        } catch (IllegalArgumentException ignored) {
        }
  }

  @Override
  public void apply(Entity raw, Map<String, String> p, Map<String, byte[]> items) {
    super.apply(raw, p, items);
    ArmorStand a = (ArmorStand) raw;
    a.setArms(bool(p, "arms", false));
    a.setBasePlate(bool(p, "basePlate", true));
    a.setMarker(bool(p, "marker", false));
    a.setSmall(bool(p, "small", false));
    a.setVisible(bool(p, "visible", true));
    a.setHeadPose(pose(p, "head"));
    a.setBodyPose(pose(p, "body"));
    a.setLeftArmPose(pose(p, "leftArm"));
    a.setRightArmPose(pose(p, "rightArm"));
    a.setLeftLegPose(pose(p, "leftLeg"));
    a.setRightLegPose(pose(p, "rightLeg"));
    LivingEntitySerializer.applyEquipment(a.getEquipment(), items, p);
    for (EquipmentSlot slot : EquipmentSlot.values())
      for (ArmorStand.LockType lock : ArmorStand.LockType.values())
        try {
          if (bool(p, "lock." + slot + "." + lock, false)) a.addEquipmentLock(slot, lock);
        } catch (IllegalArgumentException ignored) {
        }
  }

  private void pose(Map<String, String> p, String key, EulerAngle e) {
    p.put("pose." + key, e.getX() + "," + e.getY() + "," + e.getZ());
  }

  private EulerAngle pose(Map<String, String> p, String key) {
    String[] s = p.getOrDefault("pose." + key, "0,0,0").split(",");
    try {
      return new EulerAngle(
          Double.parseDouble(s[0]), Double.parseDouble(s[1]), Double.parseDouble(s[2]));
    } catch (Exception e) {
      return EulerAngle.ZERO;
    }
  }
}
