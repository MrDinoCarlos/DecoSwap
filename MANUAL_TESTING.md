# Manual testing matrix

Use a disposable test world and retain its backup. Repeat the version section on each supported server build.

## Selection

- Get `/ds wand`; verify only the PDC-marked tool activates controls.
- In OBJECT mode, select and deselect separated blocks and entities with left/right actions. Verify no damage, normal interaction, or block breaking occurs.
- Set REGION positions, import blocks/entities/all, then remove individual objects. Test selection limits and a region over the configured volume.
- Confirm small objects have exact Display wireframes, large selections switch to aggregate bounds, and the crosshair target stays highlighted.
- Join with a second player and verify editor visuals are visible only to the owner. Quit, change worlds, clear, disable, and restart; verify no visual entity remains.
- Confirm no particle appears during any selector operation.
- Set an Anchor, switch back to OBJECT mode, and verify its gizmo disappears immediately; use `/ds anchor clear` and confirm it is also removed without reconnecting.

## Blocks

Round-trip stairs, doors, trapdoors, rails, repeaters, comparators, signs/hanging signs, containers, shulker boxes, furnaces, hoppers, player heads, banners, lecterns, jukeboxes, beehives, campfires, decorated pots, spawners, and waterlogged blocks. Test rotations 0/90/180/270. Verify inventory permission and dangerous-data configuration.

## Entities

Round-trip armor stands with every pose, equipment slot and lock; an EasyArmorStands-edited armor stand; BlockDisplay; ItemDisplay; TextDisplay; Interaction; normal and glow item frames; paintings; mobs; boats; minecarts; passenger stacks; and captured leashes. Verify transient DecoSwap tags do not enter an updated template.

## Deployment and recovery

- Save without an Anchor, confirm the current player position becomes the home location, and verify Save leaves every selected object in the world.
- Change an armor stand pose, Display transformation, entity equipment, sign, and block state. Verify the automatic detector reports names/types and coordinates in chat and outlines changes red for each online admin; `/ds changes <name>` remains available for an immediate scan. Run `/ds save <name>` (or `/ds save <name> --force`) and verify the red report clears after the template is updated.
- Save, pack, deploy home, and restore. Compare containers, signs, PDC and exact spawned UUID cleanup.
- Deploy `here` in all four rotations. Verify relative positions, entity yaw, and directional BlockData.
- Change a deployed normal block, container item, sign, and tile PDC. Verify default restore aborts and reports the conflict count; test WARN_AND_SKIP and an authorized `--force`.
- Stop the server after PREPARED, during APPLYING, and during RESTORING in separate runs. Verify startup reports recovery and retains `.dsnap`; inspect and rollback each case.
- Manually delete one spawned entity and restore. Verify unrelated nearby entities remain and the missing count is reported.
- Attempt duplicate home and overlapping deployments. Verify rejection by default. When overlap is explicitly enabled, restore in reverse order.

## Groups

- Create a four-member group and verify deterministic member order.
- Deploy and restore it. Introduce a simulated failure in member four (unload/remove its world) and verify already deployed members roll back or a retained recovery record clearly identifies any rollback failure.

## GUI, language, and restart

- Exercise decoration, group, active, restore, and deletion-confirmation screens. In Groups, Shift-left click a group, apply each festive preset, reset to default, and paste a valid `https://textures.minecraft.net/texture/<hash>` URL; restart and verify the selected icon persists.
- Verify every actionable GUI icon is a custom player head, its Mojang texture renders without a resource pack, and previous/next navigation reaches entries beyond the first 45.
- Run `/ds guide` and click the GUI Help icon. Verify the vanilla written book is localized and includes selection, Save, Pack, Place, Remove, changes, Groups, safety, and website/wiki links.
- Test `en_US`, `es_ES`, automatic Spanish client selection, English fallback, console output, and persistence across reconnect.
- Change a value in `config.yml` and one value in each language file, then add a synthetic missing key to a disposable older copy. Start the newer plugin and verify modified values remain, new bundled keys appear, and dated backups are created before merges.
- Restart with active deployments. Verify `/ds active` is reconstructed and restore still uses the original snapshot after updating the template.

## Versions

Record server build, Java version, DecoSwap checksum, results, and deviations for:

- Paper 1.21.4
- one representative later 1.21.x release
- Paper 26.1.x
- Paper 26.2.x
- Paper 26.3

Record the plugin artifact as `0.1.3` for this release. Future compatible fixes increment the final number (`0.1.4`, `0.1.5`, and so on).

Run `./gradlew clean build` first and install the exact generated JAR for every matrix entry.
