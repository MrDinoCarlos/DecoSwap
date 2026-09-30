package es.mrdino.decoswap.storage;

import es.mrdino.decoswap.decoration.Decoration;
import java.io.IOException;

public final class DecorationMigrator {
  public Decoration migrate(Decoration source) throws IOException {
    if (source.schemaVersion() == BinaryCodec.SCHEMA_VERSION) return source;
    if (source.schemaVersion() != 0)
      throw new IOException("No migration path from schema " + source.schemaVersion());
    return new Decoration(
        source.uuid(),
        source.id(),
        source.displayName(),
        BinaryCodec.SCHEMA_VERSION,
        source.creatorUuid(),
        source.creatorName(),
        source.createdAt(),
        source.modifiedAt(),
        source.sourceMinecraft(),
        source.sourcePlugin(),
        source.homeAnchor(),
        source.bounds(),
        source.description(),
        source.tags(),
        source.checksum(),
        source.blocks(),
        source.entities());
  }
}
