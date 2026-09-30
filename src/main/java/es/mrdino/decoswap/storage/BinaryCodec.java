package es.mrdino.decoswap.storage;

import es.mrdino.decoswap.decoration.*;
import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class BinaryCodec {
  public static final int FORMAT_VERSION = 1, SCHEMA_VERSION = 1;
  private static final int DECORATION_MAGIC = 0x44535750, SNAPSHOT_MAGIC = 0x44534E50;

  public byte[] encodeDecoration(Decoration d) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
      out.writeInt(DECORATION_MAGIC);
      out.writeInt(FORMAT_VERSION);
      writeUuid(out, d.uuid());
      writeString(out, d.id());
      writeString(out, d.displayName());
      out.writeInt(d.schemaVersion());
      writeNullableUuid(out, d.creatorUuid());
      writeString(out, d.creatorName());
      out.writeLong(d.createdAt().toEpochMilli());
      out.writeLong(d.modifiedAt().toEpochMilli());
      writeString(out, d.sourceMinecraft());
      writeString(out, d.sourcePlugin());
      writeAnchor(out, d.homeAnchor());
      writeString(out, d.description());
      writeStrings(out, d.tags());
      writeString(out, d.checksum());
      writeBlocks(out, d.blocks());
      writeEntities(out, d.entities());
    }
    return bytes.toByteArray();
  }

  public Decoration decodeDecoration(byte[] bytes) throws IOException {
    try (DataInputStream in =
        new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(bytes)))) {
      if (in.readInt() != DECORATION_MAGIC) throw new IOException("Not a DecoSwap decoration");
      int format = in.readInt();
      if (format > FORMAT_VERSION) throw new IOException("Unsupported format version " + format);
      UUID uuid = readUuid(in);
      String id = readString(in), name = readString(in);
      int schema = in.readInt();
      if (schema > SCHEMA_VERSION) throw new IOException("Unsupported schema version " + schema);
      UUID creator = readNullableUuid(in);
      String creatorName = readString(in);
      Instant created = Instant.ofEpochMilli(in.readLong()),
          modified = Instant.ofEpochMilli(in.readLong());
      String mc = readString(in), plugin = readString(in);
      Anchor anchor = readAnchor(in);
      String desc = readString(in);
      Set<String> tags = new LinkedHashSet<>(readStrings(in));
      String checksum = readString(in);
      List<BlockRecord> blocks = readBlocks(in);
      List<EntityRecord> entities = readEntities(in);
      return new Decoration(
          uuid,
          id,
          name,
          schema,
          creator,
          creatorName,
          created,
          modified,
          mc,
          plugin,
          anchor,
          Bounds.of(blocks, entities),
          desc,
          tags,
          checksum,
          blocks,
          entities);
    }
  }

  public byte[] encodeSnapshot(List<BlockRecord> blocks) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
      out.writeInt(SNAPSHOT_MAGIC);
      out.writeInt(FORMAT_VERSION);
      writeBlocks(out, blocks);
    }
    return bytes.toByteArray();
  }

  public List<BlockRecord> decodeSnapshot(byte[] bytes) throws IOException {
    try (DataInputStream in =
        new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(bytes)))) {
      if (in.readInt() != SNAPSHOT_MAGIC) throw new IOException("Not a DecoSwap snapshot");
      int version = in.readInt();
      if (version > FORMAT_VERSION)
        throw new IOException("Unsupported snapshot version " + version);
      return readBlocks(in);
    }
  }

  private void writeAnchor(DataOutput out, Anchor a) throws IOException {
    writeUuid(out, a.worldId());
    writeString(out, a.worldName());
    out.writeDouble(a.x());
    out.writeDouble(a.y());
    out.writeDouble(a.z());
    out.writeFloat(a.yaw());
    out.writeFloat(a.pitch());
  }

  private Anchor readAnchor(DataInput in) throws IOException {
    return new Anchor(
        readUuid(in),
        readString(in),
        in.readDouble(),
        in.readDouble(),
        in.readDouble(),
        in.readFloat(),
        in.readFloat());
  }

  private void writeBlocks(DataOutput out, List<BlockRecord> blocks) throws IOException {
    out.writeInt(blocks.size());
    for (BlockRecord b : blocks) {
      out.writeInt(b.x());
      out.writeInt(b.y());
      out.writeInt(b.z());
      writeString(out, b.blockData());
      writeBytes(out, b.pdc());
      writeStringMap(out, b.properties());
      writeBytesMap(out, b.items());
    }
  }

  private List<BlockRecord> readBlocks(DataInput in) throws IOException {
    int count = bounded(in.readInt(), 2_000_000, "block count");
    List<BlockRecord> result = new ArrayList<>(count);
    for (int i = 0; i < count; i++)
      result.add(
          new BlockRecord(
              in.readInt(),
              in.readInt(),
              in.readInt(),
              readString(in),
              readBytes(in),
              readStringMap(in),
              readBytesMapInt(in)));
    return result;
  }

  private void writeEntities(DataOutput out, List<EntityRecord> entities) throws IOException {
    out.writeInt(entities.size());
    for (EntityRecord e : entities) {
      out.writeInt(e.localId());
      writeString(out, e.type());
      out.writeDouble(e.x());
      out.writeDouble(e.y());
      out.writeDouble(e.z());
      out.writeFloat(e.yaw());
      out.writeFloat(e.pitch());
      writeStringMap(out, e.properties());
      writeNamedBytesMap(out, e.items());
      writeBytes(out, e.pdc());
      writeStrings(out, e.scoreboardTags());
      out.writeInt(e.passengers().size());
      for (int id : e.passengers()) out.writeInt(id);
      out.writeBoolean(e.leashHolder() != null);
      if (e.leashHolder() != null) out.writeInt(e.leashHolder());
    }
  }

  private List<EntityRecord> readEntities(DataInput in) throws IOException {
    int count = bounded(in.readInt(), 100_000, "entity count");
    List<EntityRecord> result = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      int id = in.readInt();
      String type = readString(in);
      double x = in.readDouble(), y = in.readDouble(), z = in.readDouble();
      float yaw = in.readFloat(), pitch = in.readFloat();
      Map<String, String> props = readStringMap(in);
      Map<String, byte[]> items = readNamedBytesMap(in);
      byte[] pdc = readBytes(in);
      List<String> tags = readStrings(in);
      int pc = bounded(in.readInt(), 10_000, "passengers");
      List<Integer> passengers = new ArrayList<>(pc);
      for (int n = 0; n < pc; n++) passengers.add(in.readInt());
      Integer leash = in.readBoolean() ? in.readInt() : null;
      result.add(
          new EntityRecord(
              id, type, x, y, z, yaw, pitch, props, items, pdc, tags, passengers, leash));
    }
    return result;
  }

  private void writeStringMap(DataOutput out, Map<String, String> map) throws IOException {
    out.writeInt(map.size());
    for (var e : new TreeMap<>(map).entrySet()) {
      writeString(out, e.getKey());
      writeString(out, e.getValue());
    }
  }

  private Map<String, String> readStringMap(DataInput in) throws IOException {
    int count = bounded(in.readInt(), 100_000, "map");
    Map<String, String> map = new LinkedHashMap<>();
    for (int i = 0; i < count; i++) map.put(readString(in), readString(in));
    return map;
  }

  private void writeBytesMap(DataOutput out, Map<Integer, byte[]> map) throws IOException {
    out.writeInt(map.size());
    for (var e : new TreeMap<>(map).entrySet()) {
      out.writeInt(e.getKey());
      writeBytes(out, e.getValue());
    }
  }

  private Map<Integer, byte[]> readBytesMapInt(DataInput in) throws IOException {
    int count = bounded(in.readInt(), 100_000, "item map");
    Map<Integer, byte[]> map = new LinkedHashMap<>();
    for (int i = 0; i < count; i++) map.put(in.readInt(), readBytes(in));
    return map;
  }

  private void writeNamedBytesMap(DataOutput out, Map<String, byte[]> map) throws IOException {
    out.writeInt(map.size());
    for (var e : new TreeMap<>(map).entrySet()) {
      writeString(out, e.getKey());
      writeBytes(out, e.getValue());
    }
  }

  private Map<String, byte[]> readNamedBytesMap(DataInput in) throws IOException {
    int count = bounded(in.readInt(), 100_000, "named item map");
    Map<String, byte[]> map = new LinkedHashMap<>();
    for (int i = 0; i < count; i++) map.put(readString(in), readBytes(in));
    return map;
  }

  private void writeStrings(DataOutput out, Collection<String> values) throws IOException {
    out.writeInt(values.size());
    for (String v : values) writeString(out, v);
  }

  private List<String> readStrings(DataInput in) throws IOException {
    int count = bounded(in.readInt(), 100_000, "string list");
    List<String> values = new ArrayList<>(count);
    for (int i = 0; i < count; i++) values.add(readString(in));
    return values;
  }

  private void writeString(DataOutput out, String value) throws IOException {
    byte[] b = (value == null ? "" : value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    if (b.length > 16_777_216) throw new IOException("String too long");
    out.writeInt(b.length);
    out.write(b);
  }

  private String readString(DataInput in) throws IOException {
    int length = bounded(in.readInt(), 16_777_216, "string");
    byte[] b = new byte[length];
    in.readFully(b);
    return new String(b, java.nio.charset.StandardCharsets.UTF_8);
  }

  private void writeBytes(DataOutput out, byte[] value) throws IOException {
    out.writeInt(value.length);
    out.write(value);
  }

  private byte[] readBytes(DataInput in) throws IOException {
    int length = bounded(in.readInt(), 64_000_000, "binary field");
    byte[] b = new byte[length];
    in.readFully(b);
    return b;
  }

  private void writeUuid(DataOutput out, UUID id) throws IOException {
    out.writeLong(id.getMostSignificantBits());
    out.writeLong(id.getLeastSignificantBits());
  }

  private UUID readUuid(DataInput in) throws IOException {
    return new UUID(in.readLong(), in.readLong());
  }

  private void writeNullableUuid(DataOutput out, UUID id) throws IOException {
    out.writeBoolean(id != null);
    if (id != null) writeUuid(out, id);
  }

  private UUID readNullableUuid(DataInput in) throws IOException {
    return in.readBoolean() ? readUuid(in) : null;
  }

  private int bounded(int value, int max, String field) throws IOException {
    if (value < 0 || value > max) throw new IOException("Invalid " + field + ": " + value);
    return value;
  }
}
