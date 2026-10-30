# Living Village Network

The journal's **Guild trust** measures your social standing with the Guild.
Completed Daily and Weekly quests can contribute within the repeatable-work
limits. This is separate from the permanent bond used for a village mandate:
two actual village requests, or one request plus four qualifying villager trades
in that village. Daily and Weekly completions do not substitute for those local
requests. The **Village requests** screen shows the local progress that matters
for the mandate.

The network systems introduced in 2.3 remain available in Stable 2.5 and the
2.6 test version. For founding, staff, markets and diplomacy see
[Living Kingdoms 2.6](living-kingdoms.md).

`Living Village Network` is the complete Village Quest 2.3 content layer. It connects village identity, local supply, Notice Board choices, freight routes, Wayshrine energy, optional multiplayer guilds, and a bounded prestige path without replacing existing stories or permanent trust.

## Two values, two purposes

- `Known`, `Trusted`, and `Allied` are permanent personal bonds. A supply crisis never removes an earned tier, story unlock, or Wayshrine term.
- `Crisis`, `Strained`, `Stable`, `Recovering`, and `Thriving` describe the mutable local supply condition.

Village Quest 2.6.3 adds a second way to become Trusted: complete one
actual request and make four successful trades with adult villagers inside that
same village. Trades start counting after the first request; at most two count
per villager per quest-calendar day and at most four count in total. Completing
two actual requests remains the other way. Allied always requires eight actual
requests; trades never increase the request counter. The Notice Post shows the
counted trades and explains these limits when you hover over the bond path.
Old saved request counts and valid earned ranks remain unchanged. These changes are included in 2.6.3.

Existing 2.2.x villages migrate lazily and idempotently to `Stable` at `50/100`. Supply never decays while a player is offline.

## Identities, needs, and three solutions

Each identity alternates between two bounded needs:

| Identity | Needs |
|---|---|
| Granary | Seed reserves; Village pantry |
| Forge | Fuel and ore; Building stock |
| Pasture | Winter fodder; Husbandry supplies |
| Apiary | Bee forage; Wax stock |
| Archive | Writing supplies; Archive preservation |

A Guild Notice Post offers three server-authoritative choices. Current need, previous delivery, village condition, personal trust, guild project, specialization, and Adventure Profile all affect the result. Two choices normally target the primary need. In 2.6.3, the third choice always offers ordinary upkeep with cobblestone (32 before profile, condition and trust adjustments). This lets you help without bees, livestock or specialist crops. The previous specialist delivery is avoided when alternatives exist; upkeep remains available each normal request cycle. Rewards still use the supplied material's value, and regular requests retain their daily limit.

On local visits, villages first try their decorative Guild Corner; a safe compact
Notice Post supplies the same requests when the large structure cannot fit.
This includes forests and older villages. `/vq town retrofit` retries near where
you stand and can recover a failed placement. Natural vegetation may be cleared;
existing buildings and containers stay protected. Explicit retrofit also restores a deliberately removed generated board.
The Journal's Trust page can be opened without a board, but local delivery
requests at the board build the permanent bond needed for a village mandate.
After Village Welcome, these local requests also work before you register a
trade route. Use a loaded Notice Post in the same valid village where you are
standing. Local stories and afterstories do not have to be completed first.
Disconnected requests pay the ordinary delivery reward and count toward your
personal bond. Network supply, renown, specialization and guild delivery bonuses
apply only after the village is a registered destination; connecting it later
does not pay those bonuses for earlier work. Abandoned or recovering settlements
and active reparation cases retain their recovery requirements.

The board presents those choices as three compact item selectors above one calm detail field. Each selector shows the carried and required amounts, while the selected request receives the readable title, need, reward, support contribution, and progress bar below; hover text retains the full offer details. Three standalone Bond seals show the current and upcoming trust states on one restrained connector. The painted assets remain text-free, so all values and EN/DE/ES labels stay dynamic.

Matching work starts from `24` supply and other valid help from `14`. Trusted and Allied villages reduce required quantities and improve rewards; Allied also contributes additional supply. At `100`, the need changes and the new cycle starts at `55`, so long-term play gains variety rather than endlessly larger stacks. A village still accepts only one board delivery per reset, but skipping days loses nothing and no exclusive daily reward exists.

## Routes, freight, and repairable consequences

Freight offers prioritize goods needed by recorded destination villages and name the matching route numbers. Any valid contract can still be used as general supply, while matching cargo has the larger local effect.

Every destination arrival adds some supply and advances its Wayshrine-energy meter. Matching freight helps more. Each three energy steps create a shared charge in a Wayshrine bound to that village; completing a full supply cycle can also generate a charge. Magic Shards remain the fast emergency source rather than the only renewable source.

Failed incidents reduce destination supply by a bounded amount and never below a repairable floor. They do not remove bond tiers or completed cycles. Each route has one of two explicit incident plans:

- `careful`: fewer incidents, lower incident rewards, much lower destination strain, and an additional protected journey;
- `bold`: more incidents and larger incident rewards, but more strain if the response fails.

Choose with `/vq routes approach <route> <careful|bold>`. Story-required incidents continue to bypass normal random prevention, and Peaceful/MAP_ONLY story fallbacks remain completable.

## Adventure Profiles

The server owner chooses `RELAXED`, `STANDARD`, or `HARDENED` in `server.properties` with `adventure_profile`. Profiles change request quantities, hostile pressure, protected journeys, and failure tolerance. They never remove content or grant exclusive rewards. Vanilla difficulty and actually present party members still control combat on top; Peaceful always creates zero hostile waves.

## Optional multiplayer guild

Players can create a shared guild without merging personal story or Archive ownership:

- `/vq guild create <name>`
- `/vq guild invite <player>` and `/vq guild accept`
- `/vq guild status`, `/vq guild promote <player>`, `/vq guild leave`
- `/vq guild transfer <player>` and `/vq guild kick <player>` for recoverable leadership management
- `/vq guild project <common_reserve|waystation|archive_exchange>`

Leaders manage promotion; Leaders and Stewards can choose projects and invite members. All members contribute guild renown through board work and route arrivals, including while other members are offline. At guild rank 2 one shared project can be selected: extra supply, extra Wayshrine energy, or improved board rewards. Personal story rewards, unique tools, and Guild Archive generations remain personal.

## Network prestige and Journal

The Journal's village/network area shows the lowest-supply next action and rank/profile information. Recorded village reports explain trust, condition, need, supply and energy; guild data remains separate. The 2.6 interface labels the village destination **Villages**, with paged cards and reports; older versions use **Network**. Conditions remain written out with secondary accents: Crisis red, Strained amber, Stable gold, Recovering teal and Thriving green.

Network renown has five bounded ranks and five titles. At rank 2, preview exactly one permanent specialization with `/vq network specialize <steward|courier|wayfarer>`, then apply that choice with the same command followed by `confirm`:

- Steward strengthens matching Notice Board work;
- Courier strengthens matching freight;
- Wayfarer accelerates route-generated Wayshrine energy.

Use `/vq network` for the same authoritative summary in chat. Rank 5 is the cap; there is no infinite quantity ladder and no loss of earned rank.

## Save and server safety

Village network data uses schema `2`; optional guild data has its own schema `1`. Missing fields receive bounded defaults, invalid records are ignored, and repeated loading does not grant progress. Virtual routes still work without loading their chunks. Reconnects and offline members do not advance timers or lose supply. Serverbound board selections are revalidated against the current generated offer before any item is removed.

## Social trust in2.5

The new [Trust in Bonds & Bounty](social-reputation.md) layer is personal and reversible. It does not remove permanent bonds, earned expertise or network prestige. Network village cards display local trust separately from bond/supply; the Trust and Chronicle subtabs explain benefits, personal sanctions and active aid.

Placement uses actual Vanilla/CTOV house and jobsite footprints: the Notice Post stays within 10 horizontal blocks of a real building; distant roads and the combined village bounds do not count. Automatic placement keeps the entire footprint at least 8 horizontal blocks from every nearby player and waits for a safe loaded site if necessary. Generated posts drop no item, so repeated retrofit cannot farm them; crafted posts keep their ordinary drops, including crafted replacements at an old generated site. Recovery restores the compact board rather than replenishing decorative materials.

### Pending quest usability update
The notice-board delivery lock follows the world's selected quest calendar. In Minecraft-day mode a new local delivery is available after each completed 24,000 advancing Overworld clock ticks; in real-time mode it uses the configured daily reset. The request page shows the remaining wait and refreshes while open. Each player still completes one request per village and cycle.

Accepted local stories and commissions are stored across logout/restart and appear in the quest tracker together with the main story. Action goals count within 128 horizontal blocks of the bound village centre (either bound centre for commissions). Final bundles are consumed automatically only after the objectives, explicit Shared Table choice, access and village checks pass. Missing bundles remain outstanding; completion and rewards still use the existing once-only path.

For Long Drive, right-click two different animals within 24 horizontal blocks of the bound village centre, then travel at least 32 blocks together with each while staying within 16 blocks of it and within the 128-block village area. Selection and completion are confirmed. Carry two leads for final automatic delivery. The quest tracker shows each selected animal's travelled and remaining distance.
