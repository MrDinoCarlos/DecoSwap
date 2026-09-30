package es.mrdino.decoswap.deployment;

import java.util.*;

public final class ConflictDetector {
  private ConflictDetector() {}

  public static <K, V> Set<K> find(Map<K, V> expected, Map<K, V> actual) {
    Set<K> result = new LinkedHashSet<>();
    expected.forEach(
        (k, v) -> {
          if (!Objects.equals(v, actual.get(k))) result.add(k);
        });
    return Collections.unmodifiableSet(result);
  }
}
