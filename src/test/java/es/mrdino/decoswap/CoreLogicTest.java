package es.mrdino.decoswap;

import static org.assertj.core.api.Assertions.*;

import es.mrdino.decoswap.decoration.*;
import es.mrdino.decoswap.deployment.*;
import es.mrdino.decoswap.group.DecorationGroup;
import es.mrdino.decoswap.language.LocaleResolver;
import es.mrdino.decoswap.storage.BinaryCodec;
import es.mrdino.decoswap.util.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class CoreLogicTest {
  @Test
  void rotationsCoverAllQuarterTurns() {
    assertThat(Rotation.NONE.rotate(2, 3)).containsExactly(2, 3);
    assertThat(Rotation.CLOCKWISE_90.rotate(2, 3)).containsExactly(-3, 2);
    assertThat(Rotation.CLOCKWISE_180.rotate(2, 3)).containsExactly(-2, -3);
    assertThat(Rotation.COUNTERCLOCKWISE_90.rotate(2, 3)).containsExactly(3, -2);
  }

  @Test
  void anchorTransformsRelativeCoordinates() {
    Anchor a = new Anchor(UUID.randomUUID(), "world", 10, 20, 30, 0, 0);
    assertThat(a.transformRelative(2, 3, 4, Rotation.CLOCKWISE_90, a)).containsExactly(6, 23, 32);
  }

  @Test
  void namesAreNormalizedAndTraversalRejected() {
    assertThat(Names.normalize(" Árbol de Navidad ")).isEqualTo("arbol_de_navidad");
    assertThat(Names.normalize("../secrets")).isEmpty();
    assertThat(Names.normalize("CON")).isEmpty();
  }

  @Test
  void groupMembershipIsOrderedAndUnique() {
    DecorationGroup g = new DecorationGroup("winter", "Winter", List.of());
    assertThat(g.add("tree")).isTrue();
    assertThat(g.add("tree")).isFalse();
    assertThat(g.add("lights")).isTrue();
    assertThat(g.members()).containsExactly("tree", "lights");
  }

  @Test
  void groupIconTextureAcceptsOnlyMojangTextureReferences() {
    DecorationGroup g = new DecorationGroup("winter", "Winter", List.of());
    String url = "https://textures.minecraft.net/texture/0123456789abcdef0123456789abcdef";
    g.iconTexture(TextureReference.hash(url).orElseThrow());
    assertThat(g.iconTexture()).isEqualTo("0123456789abcdef0123456789abcdef");
    assertThat(TextureReference.hash(url.replace("https://", "http://")))
        .contains("0123456789abcdef0123456789abcdef");
    assertThat(TextureReference.hash("https://example.com/texture/abc")).isEmpty();
  }

  @Test
  void binaryStorageRoundTrips() throws Exception {
    Decoration source = sample(1);
    BinaryCodec codec = new BinaryCodec();
    Decoration read = codec.decodeDecoration(codec.encodeDecoration(source));
    assertThat(read.id()).isEqualTo(source.id());
    assertThat(read.blocks()).hasSize(1);
    assertThat(read.entities()).hasSize(1);
    assertThat(read.checksum()).isEqualTo(source.checksum());
    assertThat(read.blocks().getFirst().properties()).containsEntry("sign.front.0", "hello");
  }

  @Test
  void futureSchemaIsRejected() throws Exception {
    BinaryCodec codec = new BinaryCodec();
    byte[] bytes = codec.encodeDecoration(sample(BinaryCodec.SCHEMA_VERSION + 1));
    assertThatThrownBy(() -> codec.decodeDecoration(bytes))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("schema");
  }

  @Test
  void legacySchemaMigratesToCurrent() throws Exception {
    Decoration legacy = sample(0);
    Decoration migrated = new es.mrdino.decoswap.storage.DecorationMigrator().migrate(legacy);
    assertThat(migrated.schemaVersion()).isEqualTo(BinaryCodec.SCHEMA_VERSION);
    assertThat(migrated.checksum()).isEqualTo(legacy.checksum());
  }

  @Test
  void conflictDetectionFindsOnlyChangedAndMissingValues() {
    Map<String, String> expected = Map.of("a", "stone", "b", "dirt");
    assertThat(ConflictDetector.find(expected, Map.of("a", "stone", "b", "grass")))
        .containsExactly("b");
    assertThat(ConflictDetector.find(expected, Map.of("a", "stone"))).containsExactly("b");
  }

  @Test
  void transactionStatesContainRecoveryLifecycle() {
    assertThat(TransactionState.values())
        .containsExactly(
            TransactionState.PREPARED,
            TransactionState.APPLYING,
            TransactionState.ACTIVE,
            TransactionState.RESTORING,
            TransactionState.COMMITTED,
            TransactionState.FAILED,
            TransactionState.RECOVERY_REQUIRED);
  }

  @Test
  void localeResolutionUsesSpanishAndFallback() {
    Set<String> available = Set.of("en_US", "es_ES");
    assertThat(LocaleResolver.resolve(null, "es_MX", true, "en_US", "en_US", available))
        .isEqualTo("es_ES");
    assertThat(LocaleResolver.resolve(null, "de_DE", true, "missing", "en_US", available))
        .isEqualTo("en_US");
    assertThat(LocaleResolver.resolve("es_ES", "en_US", true, "en_US", "en_US", available))
        .isEqualTo("es_ES");
  }

  @Test
  void fingerprintsAreStableAndSensitiveToContent() {
    BlockRecord a = new BlockRecord(0, 0, 0, "minecraft:stone", new byte[0], Map.of(), Map.of());
    BlockRecord b = new BlockRecord(0, 0, 0, "minecraft:dirt", new byte[0], Map.of(), Map.of());
    assertThat(Fingerprint.decoration(List.of(a), List.of()))
        .isEqualTo(Fingerprint.decoration(List.of(a), List.of()));
    assertThat(Fingerprint.decoration(List.of(a), List.of()))
        .isNotEqualTo(Fingerprint.decoration(List.of(b), List.of()));
  }

  private Decoration sample(int schema) {
    UUID world = UUID.randomUUID();
    BlockRecord block =
        new BlockRecord(
            1,
            2,
            3,
            "minecraft:oak_sign[rotation=0,waterlogged=false]",
            new byte[] {1, 2},
            Map.of("sign.front.0", "hello"),
            Map.of());
    EntityRecord entity =
        new EntityRecord(
            0,
            "minecraft:armor_stand",
            .5,
            1,
            .5,
            0,
            0,
            Map.of("small", "true"),
            Map.of(),
            new byte[0],
            List.of("tag"),
            List.of(),
            null);
    List<BlockRecord> blocks = List.of(block);
    List<EntityRecord> entities = List.of(entity);
    return new Decoration(
        UUID.randomUUID(),
        "winter_tree",
        "Winter Tree",
        schema,
        UUID.randomUUID(),
        "Builder",
        Instant.ofEpochMilli(1),
        Instant.ofEpochMilli(2),
        "1.21.4",
        "0.1.3",
        new Anchor(world, "world", 10, 64, 20, 0, 0),
        Bounds.of(blocks, entities),
        "",
        Set.of("winter"),
        Fingerprint.decoration(blocks, entities),
        blocks,
        entities);
  }
}
