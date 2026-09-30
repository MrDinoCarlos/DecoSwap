# Commands

`/decoswap` and `/ds` are equivalent. Arguments in brackets are optional.

| Command | Purpose |
|---|---|
| `/ds help` | Localized command overview |
| `/ds gui` | Open the management GUI |
| `/ds reload` | Reload configuration, languages, integrations, and visuals |
| `/ds wand` | Receive the PDC-identified selector |
| `/ds mode <object\|region\|anchor>` | Change selector mode |
| `/ds select info` | Show exact selection counts |
| `/ds select clear` | Clear blocks and entities |
| `/ds select region [blocks\|entities\|all]` | Import the current cuboid into the exact selection |
| `/ds select blocks` / `entities` | Keep only that category |
| `/ds select invert` | Invert non-air blocks inside the current region |
| `/ds select remove <type>` | Remove a Material or EntityType from selection |
| `/ds undo` | Undo a recent selection edit |
| `/ds anchor set` / `info` / `clear` | Set, inspect, or hide the optional Anchor |
| `/ds save <name> [--force]` | Save or update a Decoration; never removes world objects |
| `/ds update <name>` | Explicit update alias for a saved Decoration |
| `/ds delete <name>` / `/ds info <name>` / `/ds list` | Manage saved Decorations from the simple root command set |
| `/ds pack <name> [--keep-world]` | Safely update, then optionally remove exact selected objects |
| `/ds changes <name>` | Optional manual scan; block/entity edits are also detected automatically and shown in red to admins |
| `/ds deploy <name> [home\|here] [--rotation 0\|90\|180\|270]` | Deploy a Decoration or Group; `/ds place` is the simple alias |
| `/ds restore <name> [deployment-id] [--force]` | Restore a Decoration or Group; `/ds remove` is the simple alias |
| `/ds group create\|delete <name>` | Create or delete a Group |
| `/ds group add\|remove <group> <decoration>` | Edit ordered Group membership |
| `/ds group info\|list` | Inspect Groups |
| `/ds group deploy\|restore <name>` | Deploy or restore a Group; `place`, `show`, `hide`, and `disable` are aliases where unambiguous |
| `/ds group enable\|disable <name>` | Aliases for deploy and restore |
| `/ds active` | List active Deployments |
| `/ds status <decoration>` | Show active instance count |
| `/ds recovery list` | List incomplete transactions |
| `/ds recovery inspect <id>` | Inspect a recovery record |
| `/ds recovery rollback <id>` | Restore its retained snapshot |
| `/ds recovery accept <id>` | Accept the current world and archive recovery data |
| `/ds language <auto\|en_US\|es_ES>` | Set a persistent player language preference |
| `/ds version` | Show server, adapter, integration, and schema information |
| `/ds guide` | Open the localized in-game quick guide; `/ds book` is an alias |

In the Groups GUI, **Shift + left click** opens the icon picker. Choose a built-in festive head, reset to the default, or select **Custom Minecraft texture** and paste a `https://textures.minecraft.net/texture/<hash>` URL in chat. Type `cancel` to abort.

Names are normalized to safe internal IDs. Display names remain readable. Path separators, traversal, device names, and unsafe characters are rejected.

The root commands are the recommended interface so Decorations and Groups use the same words (`save`, `pack`, `place`, `remove`). The older `/ds decoration ...` forms remain accepted as compatibility aliases but are intentionally hidden from tab completion.
