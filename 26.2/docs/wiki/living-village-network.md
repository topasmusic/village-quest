# Living Village Network

`Living Village Network` is the complete Village Quest 2.3 content layer. It connects village identity, local supply, Notice Board choices, freight routes, Wayshrine energy, optional multiplayer guilds, and a bounded prestige path without replacing existing stories or permanent trust.

## Two values, two purposes

- `Known`, `Trusted`, and `Allied` are permanent personal bonds. A supply crisis never removes an earned tier, story unlock, or Wayshrine term.
- `Crisis`, `Strained`, `Stable`, `Recovering`, and `Thriving` describe the mutable local supply condition.

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

A Guild Notice Post offers three server-authoritative choices. Current need, previous delivery, village condition, personal trust, guild project, specialization, and Adventure Profile all affect the result. Two choices normally target the primary need; every board retains an ordinary Overworld-resource solution. The previous delivery is avoided when alternatives exist.

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

The Journal's `Network` tab starts with the lowest-supply next action and a compact rank/profile summary. Every recorded village then receives its own collapsible card for trust, condition, need, supply, and energy; guild data uses a separate summary card. Conditions always remain written out and also use a secondary color accent: Crisis red, Strained amber, Stable gold, Recovering teal, and Thriving green.

Network renown has five bounded ranks and five titles. At rank 2, preview exactly one permanent specialization with `/vq network specialize <steward|courier|wayfarer>`, then apply that choice with the same command followed by `confirm`:

- Steward strengthens matching Notice Board work;
- Courier strengthens matching freight;
- Wayfarer accelerates route-generated Wayshrine energy.

Use `/vq network` for the same authoritative summary in chat. Rank 5 is the cap; there is no infinite quantity ladder and no loss of earned rank.

## Save and server safety

Village network data uses schema `2`; optional guild data has its own schema `1`. Missing fields receive bounded defaults, invalid records are ignored, and repeated loading does not grant progress. Virtual routes still work without loading their chunks. Reconnects and offline members do not advance timers or lose supply. Serverbound board selections are revalidated against the current generated offer before any item is removed.
