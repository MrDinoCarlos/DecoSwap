package es.mrdino.decoswap.util;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Pattern;

/** Validates Mojang texture URLs and returns the stable texture hash used by PlayerProfile. */
public final class TextureReference {
  private static final Pattern HASH = Pattern.compile("[0-9a-fA-F]{32,64}");
  private static final String HOST = "textures.minecraft.net";

  private TextureReference() {}

  public static Optional<String> hash(String input) {
    if (input == null) return Optional.empty();
    String value = input.trim();
    if (HASH.matcher(value).matches()) return Optional.of(value.toLowerCase(java.util.Locale.ROOT));
    try {
      URI uri = URI.create(value);
      if (!("http".equalsIgnoreCase(uri.getScheme())
              || "https".equalsIgnoreCase(uri.getScheme()))
          || !HOST.equalsIgnoreCase(uri.getHost())
          || uri.getQuery() != null
          || uri.getFragment() != null) return Optional.empty();
      String prefix = "/texture/";
      if (!uri.getPath().startsWith(prefix)) return Optional.empty();
      String candidate = uri.getPath().substring(prefix.length());
      return HASH.matcher(candidate).matches()
          ? Optional.of(candidate.toLowerCase(java.util.Locale.ROOT))
          : Optional.empty();
    } catch (IllegalArgumentException ignored) {
      return Optional.empty();
    }
  }

  public static String url(String hash) {
    return "https://textures.minecraft.net/texture/" + hash;
  }
}
