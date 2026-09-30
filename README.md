# DecoSwap

DecoSwap 0.1.3 is a Paper plugin for capturing exact blocks and entities as reusable Decorations, organizing them into Groups, and deploying them with a fresh rollback Snapshot. It targets Paper 1.21.4 and uses a semantic compatibility layer intended to remain usable throughout later 1.21.x and Paper 26.1–26.3.

Created by [MrDinoCarlos](https://github.com/MrDinoCarlos) · [mrdino.es](https://mrdino.es/) · [GitHub](https://github.com/MrDinoCarlos)

## Requirements

- Paper 1.21.4 or a compatible later release
- Java 21 or the Java version required by the selected Paper release
- No mandatory plugins. EasyArmorStands is detected optionally.

Build with `./gradlew build` (`gradlew.bat build` on Windows). The JAR is written to `build/libs/DecoSwap-0.1.3.jar`.

## Basic workflow

1. Run `/ds wand`.
2. In OBJECT mode, click only the blocks and entities that belong to the Decoration. Sneak + swap hand cycles OBJECT, REGION, and ANCHOR.
3. Run `/ds save christmas_tree`; the player's current position is used automatically when no Anchor was set.
4. Run `/ds pack christmas_tree` only when you want to remove those exact selected objects.
5. Later, run `/ds place christmas_tree`.
6. Run `/ds remove christmas_tree` to remove the exact spawned entities and restore the blocks captured immediately before deployment.

`SAVE` captures or updates a template and keeps the selected world objects. An Anchor is optional: new templates use the player's current position, while updates keep the template's home Anchor unless a new one is explicitly set. `PACK` safely writes the template first, then removes only selected objects. `PLACE`/`DEPLOY` writes a fresh pre-deployment snapshot before changing the world. `REMOVE`/`RESTORE` validates the deployed state, removes spawned entity UUIDs, and restores that snapshot. Block and entity edits at a saved home location are detected automatically for admins, shown with red editor outlines, and reported with names and coordinates; `/ds changes <name>` remains available for an immediate manual scan. Use `/ds save <name>` to accept edits or `/ds save <name> --force` to confirm an explicit overwrite.

The first Pack cannot reconstruct a permanent block that was replaced before DecoSwap began tracking the position. Prefer exact selection and make a world backup before the first pack of an existing build. Every normal Deploy does capture the current state first.

## Selection and visuals

Selections are sets of exact block coordinates and entity UUIDs. Region selection imports objects into the same exact set, so individual objects can be removed afterward. Players and DecoSwap editor entities cannot be captured; non-persistent entities are excluded by default.

`/ds gui` opens localized, paginated menus for Decorations, Groups, active Deployments, selection information, and help. Its controls use vanilla custom player heads backed by Mojang skin textures. In the Groups menu, Shift + left click opens festive presets or a validated custom `textures.minecraft.net` URL; the selected icon is persisted in `groups.yml`. No resource pack, ItemsAdder, Oraxen, or other asset plugin is required.

Selection feedback uses temporary BlockDisplay and TextDisplay entities. Small selections receive twelve-edge wireframes per object. Large selections receive an aggregate bounds outline while the crosshair target remains exact. Visuals are non-persistent, PDC tagged, interaction-free, visible only to their owner, and removed on clear, quit, disable, and startup orphan cleanup. DecoSwap does not use particles.

## Deployment safety

Deployment follows `capture current blocks → atomically write compressed snapshot → atomically journal PREPARED → batch world changes → journal ACTIVE`. Restoration compares semantic fingerprints that include BlockData, tile PDC, properties, and inventory bytes. The default `ABORT` policy preserves changed world data. `WARN_AND_SKIP` and `FORCE` are configurable; `/ds restore ... --force` requires its own permission.

Overlaps are rejected by default. If explicitly enabled, ownership is tracked per exact block and administrators should restore in reverse deployment order. Incomplete transactions survive restart and appear under `/ds recovery`.

See [GUIDE.md](GUIDE.md), [COMMANDS.md](COMMANDS.md), [PERMISSIONS.md](PERMISSIONS.md), [STORAGE.md](STORAGE.md), [COMPATIBILITY.md](COMPATIBILITY.md), and [MANUAL_TESTING.md](MANUAL_TESTING.md).

The in-game quick guide is available with `/ds guide` and from the GUI help button. It explains selection, saving, updating, packing, placement, restoration, change detection, and Groups. More advanced operational details belong in the [project wiki](https://github.com/MrDinoCarlos/DecoSwap/wiki).

## License

DecoSwap is distributed under the [PolyForm Noncommercial License 1.0.0](LICENSE). You may use, study, modify, and share the plugin for permitted noncommercial purposes. Commercial use is not permitted under this license.
