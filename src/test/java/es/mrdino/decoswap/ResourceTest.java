package es.mrdino.decoswap;

import static org.assertj.core.api.Assertions.*;

import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ResourceTest {
  @Test
  void languageBundlesHaveIdenticalKeys() {
    YamlConfiguration english =
        YamlConfiguration.loadConfiguration(new File("src/main/resources/lang/en_US.yml"));
    YamlConfiguration spanish =
        YamlConfiguration.loadConfiguration(new File("src/main/resources/lang/es_ES.yml"));
    assertThat(english.getKeys(true)).isEqualTo(spanish.getKeys(true));
    assertThat(english.getKeys(true)).isNotEmpty();
  }

  @Test
  void pluginAndConfigResourcesParse() {
    YamlConfiguration plugin =
        YamlConfiguration.loadConfiguration(new File("src/main/resources/plugin.yml"));
    YamlConfiguration config =
        YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
    assertThat(plugin.getString("main")).isEqualTo("es.mrdino.decoswap.DecoSwapPlugin");
    assertThat(plugin.getConfigurationSection("permissions").getKeys(true))
        .contains("decoswap.admin", "decoswap.restore.force");
    assertThat(config.getInt("operations.blocks-per-tick")).isPositive();
    assertThat(config.getString("conflicts.restore-policy")).isEqualTo("ABORT");
  }
}
