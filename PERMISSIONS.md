# Permissions

All administrative nodes default to server operators. `decoswap.admin` grants every listed DecoSwap permission.

| Permission | Controls |
|---|---|
| `decoswap.use`, `decoswap.help` | Base command and help |
| `decoswap.wand`, `decoswap.select`, `decoswap.anchor` | Editing tools |
| `decoswap.decoration.create` | Reserved creation policy node |
| `decoswap.decoration.save` | New saves |
| `decoswap.decoration.update` | Template overwrite |
| `decoswap.decoration.delete` | Deletion |
| `decoswap.decoration.pack` | Save/update and exact world removal |
| `decoswap.decoration.deploy` | Deployment |
| `decoswap.decoration.restore` | Safe restoration |
| `decoswap.decoration.info` | Lists, info, active and status |
| `decoswap.decoration.changes` | Compare a saved template and show changed objects with red outlines |
| `decoswap.group.create`, `.edit`, `.delete` | Group management |
| `decoswap.group.deploy`, `.restore` | Group world operations |
| `decoswap.capture.inventory` | Capture inventories and their items |
| `decoswap.capture.commandblock` | Reserved per-user command-block policy node |
| `decoswap.capture.dangerous` | Reserved broad dangerous-data policy node |
| `decoswap.deploy.force` | Explicit overlap override |
| `decoswap.restore.force` | Conflict-overwriting restore |
| `decoswap.recovery` | Recovery inspection and action |
| `decoswap.reload` | Safe plugin configuration reload |
| `decoswap.admin` | All nodes above |

Dangerous block categories must also be enabled in `config.yml`. Configuration is the global gate; permissions are the operator gate. Inventory capture always requires `decoswap.capture.inventory`.
