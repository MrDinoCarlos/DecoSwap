package es.mrdino.decoswap.util;

import es.mrdino.decoswap.decoration.BlockRecord;
import es.mrdino.decoswap.decoration.EntityRecord;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.HexFormat;

public final class Fingerprint {
  private Fingerprint() {}

  public static String decoration(List<BlockRecord> blocks, List<EntityRecord> entities) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      blocks.stream()
          .sorted(BlockRecord.ORDER)
          .forEach(b -> update(digest, b.x() + "," + b.y() + "," + b.z() + ":" + block(b)));
      entities.stream()
          .sorted(EntityRecord.ORDER)
          .forEach(
              e -> {
                update(
                    digest,
                    e.localId()
                        + ":"
                        + e.type()
                        + ":"
                        + e.x()
                        + ","
                        + e.y()
                        + ","
                        + e.z()
                        + ":"
                        + new TreeMap<>(e.properties()));
                updateBytes(digest, e.pdc());
                new TreeMap<>(e.items())
                    .forEach(
                        (k, v) -> {
                          update(digest, k);
                          updateBytes(digest, v);
                        });
              });
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static void update(MessageDigest digest, String value) {
    digest.update(value.getBytes(StandardCharsets.UTF_8));
  }

  public static String block(BlockRecord block) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      update(digest, block.blockData());
      update(digest, new TreeMap<>(block.properties()).toString());
      updateBytes(digest, block.pdc());
      new TreeMap<>(block.items())
          .forEach(
              (k, v) -> {
                update(digest, Integer.toString(k));
                updateBytes(digest, v);
              });
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  public static String entity(EntityRecord entity) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      update(
          digest,
          entity.localId()
              + ":"
              + entity.type()
              + ":"
              + entity.x()
              + ","
              + entity.y()
              + ","
              + entity.z()
              + ":"
              + entity.yaw()
              + ","
              + entity.pitch()
              + ":"
              + new TreeMap<>(entity.properties())
              + ":tags="
              + new TreeSet<>(entity.scoreboardTags())
              + ":passengers="
              + entity.passengers()
              + ":leash="
              + entity.leashHolder());
      updateBytes(digest, entity.pdc());
      new TreeMap<>(entity.items())
          .forEach(
              (k, v) -> {
                update(digest, k);
                updateBytes(digest, v);
              });
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static void updateBytes(MessageDigest digest, byte[] value) {
    digest.update(java.nio.ByteBuffer.allocate(4).putInt(value.length).array());
    digest.update(value);
  }
}
