package es.mrdino.decoswap.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

public final class Names {
  private static final Set<String> RESERVED =
      Set.of(
          "con",
          "prn",
          "aux",
          "nul",
          "clock$",
          "decorations",
          "snapshots",
          "transactions",
          "backups");

  private Names() {}

  public static String normalize(String input) {
    if (input == null) return "";
    if (input.contains("..") || input.contains("/") || input.contains("\\")) return "";
    String value =
        Normalizer.normalize(input.trim(), Normalizer.Form.NFKD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9 _-]", "")
            .replaceAll("[ _-]+", "_")
            .replaceAll("^_+|_+$", "");
    return isSafeId(value) ? value : "";
  }

  public static boolean isSafeId(String value) {
    return value != null
        && value.matches("[a-z0-9][a-z0-9_-]{0,63}")
        && !RESERVED.contains(value)
        && !value.contains("..");
  }
}
