package es.mrdino.decoswap.deployment;

import java.util.Locale;

public enum ConflictPolicy {
  ABORT,
  WARN_AND_SKIP,
  FORCE;

  public static ConflictPolicy parse(String value) {
    try {
      return valueOf(value.toUpperCase(Locale.ROOT));
    } catch (Exception ignored) {
      return ABORT;
    }
  }
}
