# Storage

DecoSwap creates this layout below `plugins/DecoSwap/`:

```text
config.yml
groups.yml
players.yml
lang/en_US.yml
lang/es_ES.yml
config.yml.defaults.yml
lang/en_US.yml.defaults.yml
lang/es_ES.yml.defaults.yml
decorations/<safe-id>/meta.yml
decorations/<safe-id>/data.dswap
snapshots/<deployment-uuid>.dsnap
transactions/<deployment-uuid>.yml
transactions/<deployment-uuid>.expected
transactions/archive/
backups/
```

Large content uses a bounded, versioned GZIP binary format. `DSWP` identifies Decoration payloads and `DSNP` identifies Snapshot/expected-state payloads. Each header has a format version; Decorations also carry a schema version. Lengths and collection counts are validated while reading. Metadata and transaction state remain YAML for inspection.

`groups.yml` stores ordered Group membership and an optional `icon-texture` Mojang texture hash for the Groups GUI. Existing groups without `icon-texture` continue using the default group head.

Writes use a same-directory temporary file, close/force the file channel, and atomically replace the destination where the filesystem supports it. Existing templates are backed up before update or delete. Retention is configurable.

The `.defaults.yml` files are internal snapshots of the bundled defaults. During startup and `/ds reload`, DecoSwap adds missing keys and updates values that still match the previous bundled default, while preserving administrator-modified values. A dated `.bak-*` copy is created before a merge.

Saved blocks contain relative coordinates, canonical BlockData, supported tile-state properties, serialized ItemStack bytes, and PDC bytes. Saved entities contain local IDs, relative transforms, semantic properties, ItemStack bytes, scoreboard tags, PDC, passenger IDs, and captured leash relationships. Runtime `decoswap:*` editor/deployment keys are removed from reusable templates.

Snapshots contain only exact coordinates that the Deployment will change, relative to its target Anchor. Expected-state files hold the semantic deployed block state used for conflict checks. Completed files are deleted or archived according to configuration. Files needed by an incomplete transaction are never silently discarded.

Data with a newer schema is rejected without overwriting it. Invalid files remain in place and are reported; administrators can recover from `backups/`.
