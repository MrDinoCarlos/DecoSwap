package es.mrdino.decoswap.decoration;

import es.mrdino.decoswap.util.BlockKey;
import java.util.UUID;

/** A human-readable entity difference reported by the template scanner. */
public record EntityChange(
    int localId,
    String type,
    String name,
    BlockKey location,
    UUID currentUuid,
    boolean missing,
    boolean additional) {}
