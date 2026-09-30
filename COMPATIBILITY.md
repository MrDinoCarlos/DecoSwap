# Compatibility

DecoSwap compiles against Paper 1.21.4 with Java 21 bytecode. Common code uses the 1.21.4 Paper/Bukkit surface and avoids NMS. Capability reporting is exposed through `/ds version`; unsupported version strings warn instead of crashing when the underlying API remains compatible.

The intended single-JAR range is:

- Paper 1.21.4
- later Paper 1.21.x releases
- Paper 26.1.x, 26.2.x, and 26.3

Later releases can only be certified by running the manual matrix in [MANUAL_TESTING.md](MANUAL_TESTING.md) against their final server builds. The plugin isolates server capability reporting and optional integration detection, keeps persisted entities semantic rather than NBT-only, uses namespaced registry keys, and ignores unknown stored properties. These choices reduce class-loading and data-format coupling.

EasyArmorStands is a soft dependency. Runtime detection accepts enabled 2.x or 3.x branches without linking their classes. DecoSwap preserves the Bukkit/Paper state those versions edit: armor stand pose, size, marker, visibility, base plate, arms, equipment and locks; display transformations and interpolation; display content; position, yaw/pitch; PDC; and scoreboard tags. DecoSwap reacts only while its PDC-marked selector is held, so it does not cancel unrelated EasyArmorStands editing.

WorldEdit, FAWE, ProtocolLib, WorldGuard, and schematic plugins are neither required nor used. Protection-plugin-specific hooks are outside 0.1.3; server operators should grant DecoSwap’s world-operation permissions only to trusted administrators.
