# Configuration and Map Cache

Village Quest creates two human-readable files under `.minecraft/config/village-quest/`. Edit them while Minecraft or the dedicated server is stopped, then restart. Invalid values are logged and replaced with safe defaults; changing these files never rewrites quest progression.

## Server Settings

`server.properties` supplies the authoritative initial values for an integrated or dedicated server. In 2.6, the four journal world options can be overridden for one saved world by its owner or a server operator:

### Journal setup and saved world options (2.6)

Open the golden cog in the Journal or `/vq settings`. The Journal opens directly;
closing settings without saving preserves your choices. **Save** applies the
selection; **Use defaults** explicitly saves recommended options.

Only the world owner/operators can change calendar, adventure profile, story
pause and caravan presentation. Saved world choices override these four file
defaults for that world. Everyone can set personal tracker, minimap, news, sounds
and speech bubbles. Settings grant no quest progress or rewards.

### File defaults

| Setting | Default | Purpose |
|---|---|---|
| `quest_calendar_minecraft_days` | `false` | Opt in to Daily after 1 Minecraft day and Weekly after 7 Minecraft days, measured by the shared Overworld clock. |
| `reset_timezone` | `AUTO` | `AUTO` uses the running server JVM's timezone. A Java zone such as `America/New_York` or `Europe/Berlin` can be set explicitly. |
| `daily_reset_hour` | `6` | Local hour from `0` to `23` for Daily reset. |
| `weekly_reset_day` | `MONDAY` | Local weekday for Weekly reset. |
| `weekly_reset_hour` | `6` | Local hour for Weekly reset. |
| `story_cooldown_enabled` | `true` | Keep the one-hour real-time waiting period between completed story arcs. Set `false` to skip only this wait; story prerequisites/rewards and Weekly timers stay unchanged. |
| `adventure_profile` | `STANDARD` | `RELAXED`, `STANDARD`, or `HARDENED`; changes quantities, combat pressure, incident protection, and repairable route-failure strain, never content or exclusive rewards. |
| `allow_player_caravan_yards` | `true` | Allows a confirmed player base to serve as the route network's home node. |
| `physical_caravans` | `FULL` | `FULL`, `REDUCED`, or `MAP_ONLY`; simulation and economy remain active in every mode. |
| `ambient_adventurers.enabled` | `true` | Enable temporary Wanderers and Nightrunners; independent of caravan presentation and saved world setup. Restart after editing. |
| `ambient_adventurers.max_total` | `8` | Ambient traveler cap across the Overworld, 0–8. Local cap stays at two within 96 blocks of any player. |
| `ambient_adventurers.max_nightrunners` | `2` | Global Nightrunner cap, 0–2, also bounded by the total cap. |

`AUTO` does not read a player's locale or force Central European time. In singleplayer it follows the integrated server process; on a dedicated server it follows that server's configured Java/OS timezone. Java's timezone rules handle daylight-saving transitions.

Older generated files do not need to be deleted. Missing `story_cooldown_enabled` means `true`; add `story_cooldown_enabled=false` manually and restart to disable this wait. Existing saved deadlines are retained and ignored while disabled. If `adventure_profile` is absent, the server uses `STANDARD`; add the line manually while the server is stopped to choose another profile.

### Minecraft-day quest calendar (2.6)

Choose Minecraft days in journal Settings as the world owner/operator, or (for a world without journal overrides) add `quest_calendar_minecraft_days=true` to `config/village-quest/server.properties` while the game/server is stopped, then restart. Missing, invalid or `false` values keep the real-time calendar. This is a server/world-owner setting; all players and dimensions share the Overworld clock. The timezone/hour/weekday settings apply to real-time mode.

Daily and bonus quests renew after 24,000 advancing Overworld clock ticks; Weeklys renew after 168,000. The first interval starts when this mode is enabled. Sleeping and forward time commands count the skipped time; stopped daylight progression, a paused server and offline time do not advance this calendar. A backwards time command rebases the clock while retaining completed cycles and partial elapsed days. Reset displays show remaining Minecraft days, rather than an estimated wall-clock duration.

Changing modes preserves the current quest choices, progress, completed markers, party sessions and reward identities, including offline members. It does not immediately grant a new set of quests. Switching to Minecraft time starts fresh 1/7-day waits; switching to real time waits for the next configured real-time reset. Repeated switching cannot reopen a completed cycle. Accepted Daily/Weekly quests remain active until completion or explicit cancellation; completion uses the current cycle limit. World restarts retain the elapsed portion of each Minecraft interval.

All day/week-based gameplay systems use this same calendar, including notice-board delivery locks, village support, reputation caps, Pilgrim offers, merchant/smoker daily limits, trade income and freight deadlines. Durations explicitly measured in seconds/minutes and tick-based movement retain their stated units.

## Social reputation (2.5)

| Setting | Default | Purpose |
|---|---|---|
| `socialReputation.enabled` | `true` | Enables personal trust changes, sanctions, access gates and aid timers. Disabling preserves saved data and permits baseline services; re-enabling restores the existing state. Terminal rewards while disabled are not banked. |
| `socialReputation.trackCreative` | `false` | Allows known Creative attackers to be tracked when enabled. Spectator remains exempt. Switching modes does not erase existing Survival consequences. |
| `socialReputation.caravanMortality` | `true` | Makes VQ crew and mule vulnerable. `false` protects those actors only, not Vanilla villagers. |

These switches are independent: disabled trust with mortality enabled still permits natural/physical crew deaths and safe freight interruption, without personal trust punishment. To turn both off, set `enabled=false` and `caravanMortality=false`. Existing config files use defaults for missing keys; add these exact case-sensitive keys while the server is stopped and restart. Daily/Weekly time bases stay unchanged. See [Social reputation](social-reputation.md).

## Client Settings

`client.properties` affects only the local presentation:

- Quest Tracker: default enabled state, corner, scale, background opacity
- notifications: new-quest chat, caravan notices
- feedback: progress sounds and their volume
- minimap: default enabled state, corner, scale, opacity
- map layers: player, villages/homestead, caravans, route lines
- terrain map: persistent cache, `LOW`/`BALANCED`/`HIGH` quality, maximum size, retention days
- tutorial hints: the first-use Journal pointer and related guidance
- speech bubbles (2.6): `speech_bubbles_enabled=true` by default

Set `speech_bubbles_enabled=false` in `config/village-quest/client.properties`
under the active Minecraft client directory and fully restart the client to hide
world-anchored NPC speech bubbles. Staff, Questmaster, Pilgrim, caravans and existing
villager dialogue share this presentation setting. Full text remains in chat.
It changes neither quests nor server permissions; a hidden bubble does not itself
prove failed dialogue delivery.

Valid HUD positions are `TOP_LEFT`, `TOP_RIGHT`, `BOTTOM_LEFT`, and `BOTTOM_RIGHT`. Scale values accept `0.65` through `1.75`; opacity and sound volume use `0.0` through `1.0` except minimap opacity, whose safe minimum is `0.2`.

The first shared tracker preference is copied to the player's saved tracker state once. Afterwards the `.` key, `/vq questtracker`, and the Journal all control that same authoritative state. The `,` key toggles the minimap. Both bindings appear under `Village Quest` in Minecraft Controls.

## Persistent Terrain Cache

Map tiles live in `.minecraft/village-quest/map-cache/`. Singleplayer save paths or multiplayer addresses are hashed into separate folders; dimensions and quality grids have their own subfolders. Tile files contain only recoverable surface colors and heights, never quest progress, wallets, routes, inventories, or authoritative server data.

The renderer never force-loads a chunk. Loaded terrain is preferred and refreshes the corresponding cached sample; otherwise an explored disk tile supplies the map. Old files are removed after the configured retention period and the oldest remaining tiles are trimmed when the size limit is exceeded.

Terrain cells, their height shading, coastlines, and decorative marks are anchored to fixed world coordinates. Dragging the full map changes only the visible viewport; it does not resample the same land against a new screen-relative grid. Real terrain changes in loaded chunks can still refresh their corresponding cells intentionally.

The whole `map-cache` directory is safe to delete. It will be recreated and the map will refill as terrain is observed again. Saved Village Quest progression remains in the normal world save data, where Minecraft provides atomic backup and server ownership semantics.

## Diagnostics

Run `/vq diagnose` for a read-only report covering active reset settings, home type, route capacity, route states, progress, road quality, waypoints, events, physical merchants, possibly stuck groups, and tagged orphan entities. Server admins can run `/vq diagnose <player>` for another player. The command does not repair, teleport, despawn, or mutate anything; it is safe to paste into a bug report before deciding whether an admin action is necessary.

### Quest tracker placement and appearance

In **2.6.2-unreleased.1**, open the Journal's Quests tab and click the pin beside
an active task. Click again to release it. **Tracker: all / pinned** chooses
whether other tasks appear too; all Journal cards remain available. With no pin,
the tracker shows all active tasks. A fitting pinned task stays visible while
other pages rotate. On small displays its heading stays visible while overflow
objectives rotate. The pinned task also takes priority on the Journal home page.

The pin and filter are personal presentation preferences saved under
`config/village-quest/tracker-focus/`, scoped to world/server and player. A
completed, cancelled or differently titled replacement task releases its pin;
changing progress alone does not. Hiding the tracker retains the selection.

`/vq questtracker left` and `/vq questtracker right` move the tracker immediately and save the side in the local client configuration. A configured bottom anchor stays at the bottom. These commands do not toggle tracking. The default scale is `0.85` and background opacity is `0.5` (50%); existing stock `1.0` / `0.55` settings upgrade once. Other custom values are preserved. Minecraft GUI scale and window scaling are unchanged.
## Quiet world reports

The existing personal caravan-notification setting is labelled **World and caravan news**. It also controls the optional combined report for routine paid route arrivals, rare tavern guests and new village initiatives/mediation requests. At most one report appears per five active server minutes, with up to three entries; the first report gathers briefly after connection. Repeated reports for a route are coalesced to its latest arrival, not a sum of all income. Journal balances and village/route details remain authoritative. Additional entries point back to the journal instead of flooding chat. Routine reports may be discarded on disconnect/restart; saved game events and journal state remain. A still-current guest arrival is reconstructed from its saved visit until its visible report is sent and acknowledged. Disabled notifications never mark an unseen arrival delivered.

Immediate combat/route emergencies, quest choices and direct action feedback retain their existing behavior. The additional village-help warning is announced once per real incident. Ordinary new route incidents share a five-minute network-wide start gap; the introductory incident and a required story incident keep their progression exceptions. Compass imprint reminders use the action bar instead of repeated chat lines.
