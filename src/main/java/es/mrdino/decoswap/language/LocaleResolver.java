package es.mrdino.decoswap.language;

import java.util.*;

public final class LocaleResolver {
  private LocaleResolver() {}

  public static String resolve(
      String manual,
      String client,
      boolean automatic,
      String defaultLocale,
      String fallback,
      Set<String> available) {
    if (manual != null && available.contains(manual)) return manual;
    if (automatic && client != null) {
      String normalized = client.replace('-', '_');
      if (available.contains(normalized)) return normalized;
      if (normalized.toLowerCase(Locale.ROOT).startsWith("es") && available.contains("es_ES"))
        return "es_ES";
    }
    if (available.contains(defaultLocale)) return defaultLocale;
    if (available.contains(fallback)) return fallback;
    return available.stream().sorted().findFirst().orElse("en_US");
  }
}
