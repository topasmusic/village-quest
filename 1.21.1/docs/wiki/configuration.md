# Configuration and Map Cache

Village Quest 2.1.1 creates two human-readable files under `.minecraft/config/village-quest/`. Edit them while Minecraft or the dedicated server is stopped, then restart. Invalid values are logged and replaced with safe defaults; changing these files never rewrites quest progression.

## Server Settings

`server.properties` is authoritative for an integrated or dedicated server:

| Setting | Default | Purpose |
|---|---|---|
| `reset_timezone` | `AUTO` | `AUTO` uses the running server JVM's timezone. A Java zone such as `America/New_York` or `Europe/Berlin` can be set explicitly. |
| `daily_reset_hour` | `6` | Local hour from `0` to `23` for Daily reset. |
| `weekly_reset_day` | `MONDAY` | Local weekday for Weekly reset. |
| `weekly_reset_hour` | `6` | Local hour for Weekly reset. |
| `allow_player_caravan_yards` | `true` | Allows a confirmed player base to serve as the route network's home node. |
| `physical_caravans` | `FULL` | `FULL`, `REDUCED`, or `MAP_ONLY`; simulation and economy remain active in every mode. |

`AUTO` does not read a player's locale or force Central European time. In singleplayer it follows the integrated server process; on a dedicated server it follows that server's configured Java/OS timezone. Java's timezone rules handle daylight-saving transitions.

## Client Settings

`client.properties` affects only the local presentation:

- Quest Tracker: default enabled state, corner, scale, background opacity
- notifications: new-quest chat, caravan notices
- feedback: progress sounds and their volume
- minimap: default enabled state, corner, scale, opacity
- map layers: player, villages/homestead, caravans, route lines
- terrain map: persistent cache, `LOW`/`BALANCED`/`HIGH` quality, maximum size, retention days
- tutorial hints: the first-use Journal pointer and related guidance

Valid HUD positions are `TOP_LEFT`, `TOP_RIGHT`, `BOTTOM_LEFT`, and `BOTTOM_RIGHT`. Scale values accept `0.65` through `1.75`; opacity and sound volume use `0.0` through `1.0` except minimap opacity, whose safe minimum is `0.2`.

The first shared tracker preference is copied to the player's saved tracker state once. Afterwards the `.` key, `/vq questtracker`, and the Journal all control that same authoritative state. The `,` key toggles the minimap. Both bindings appear under `Village Quest` in Minecraft Controls.

## Persistent Terrain Cache

Map tiles live in `.minecraft/village-quest/map-cache/`. Singleplayer save paths or multiplayer addresses are hashed into separate folders; dimensions and quality grids have their own subfolders. Tile files contain only recoverable surface colors and heights, never quest progress, wallets, routes, inventories, or authoritative server data.

The renderer never force-loads a chunk. Loaded terrain is preferred and refreshes the corresponding cached sample; otherwise an explored disk tile supplies the map. Old files are removed after the configured retention period and the oldest remaining tiles are trimmed when the size limit is exceeded.

Terrain cells, their height shading, coastlines, and decorative marks are anchored to fixed world coordinates. Dragging the full map changes only the visible viewport; it does not resample the same land against a new screen-relative grid. Real terrain changes in loaded chunks can still refresh their corresponding cells intentionally.

The whole `map-cache` directory is safe to delete. It will be recreated and the map will refill as terrain is observed again. Saved Village Quest progression remains in the normal world save data, where Minecraft provides atomic backup and server ownership semantics.

## Diagnostics

Run `/vq diagnose` for a read-only report covering active reset settings, home type, route capacity, route states, progress, road quality, waypoints, events, physical merchants, possibly stuck groups, and tagged orphan entities. Server admins can run `/vq diagnose <player>` for another player. The command does not repair, teleport, despawn, or mutate anything; it is safe to paste into a bug report before deciding whether an admin action is necessary.
