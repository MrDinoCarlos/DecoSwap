package es.mrdino.decoswap.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Merges new bundled YAML keys while preserving server-owned values. */
public final class ResourceUpdater {
  private ResourceUpdater() {}

  public static void updateYaml(JavaPlugin plugin, Path target, String resource, Logger logger) {
    try {
      Files.createDirectories(target.getParent());
      YamlConfiguration bundled;
      String bundledText;
      try (InputStream stream = plugin.getResource(resource)) {
        if (stream == null) {
          logger.warning("Bundled resource is missing: " + resource);
          return;
        }
        bundledText = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        bundled = YamlConfiguration.loadConfiguration(new StringReader(bundledText));
      }

      boolean existed = Files.exists(target);
      if (!existed) {
        AtomicFiles.write(target, bundledText.getBytes(StandardCharsets.UTF_8));
        writeDefaults(target.resolveSibling(target.getFileName() + ".defaults.yml"), bundled);
        return;
      }

      YamlConfiguration current = YamlConfiguration.loadConfiguration(target.toFile());
      Path defaultsPath = target.resolveSibling(target.getFileName() + ".defaults.yml");
      YamlConfiguration previous =
          Files.exists(defaultsPath)
              ? YamlConfiguration.loadConfiguration(defaultsPath.toFile())
              : new YamlConfiguration();
      int changed = mergeLeaves(current, previous, bundled);
      if (changed > 0) {
        Path backup =
            target.resolveSibling(
                target.getFileName() + ".bak-" + Instant.now().toString().replace(':', '-'));
        Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
        String serialized = current.saveToString();
        String header = leadingComments(bundledText);
        if (!header.isBlank() && !serialized.startsWith("#")) serialized = header + serialized;
        AtomicFiles.write(target, serialized.getBytes(StandardCharsets.UTF_8));
        logger.info(
            "Updated "
                + resource
                + " with "
                + changed
                + " new/default setting(s); backup: "
                + backup.getFileName());
      }
      writeDefaults(defaultsPath, bundled);
    } catch (Exception exception) {
      logger.log(Level.SEVERE, "Could not update bundled resource " + resource, exception);
    }
  }

  private static int mergeLeaves(
      YamlConfiguration current, YamlConfiguration previous, YamlConfiguration bundled) {
    int changed = 0;
    for (String path : bundled.getKeys(true)) {
      if (bundled.isConfigurationSection(path)) continue;
      Object next = bundled.get(path);
      Object old = previous.get(path);
      if (!current.contains(path) || (old != null && equal(current.get(path), old))) {
        if (!equal(current.get(path), next)) {
          current.set(path, next);
          changed++;
        }
      }
    }
    return changed;
  }

  private static boolean equal(Object a, Object b) {
    if (a instanceof Number left && b instanceof Number right)
      return Double.compare(left.doubleValue(), right.doubleValue()) == 0;
    return Objects.deepEquals(a, b);
  }

  private static void writeDefaults(Path path, YamlConfiguration defaults) throws IOException {
    AtomicFiles.write(path, defaults.saveToString().getBytes(StandardCharsets.UTF_8));
  }

  private static String leadingComments(String text) {
    StringBuilder header = new StringBuilder();
    for (String line : text.split("\\R")) {
      if (line.startsWith("#") || line.isBlank())
        header.append(line).append(System.lineSeparator());
      else break;
    }
    return header.toString();
  }
}
