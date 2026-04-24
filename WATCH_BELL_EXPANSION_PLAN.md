# Watch Bell Expansion Plan

## Scope

This document defines a shared content and implementation plan for the next large `Watch Bell` follow-up batch.

Targets:

- `1.21.11` line
- `26.1.2` line

Important:

- the design is shared
- the implementation must still be mirrored per version, not copy-pasted blindly
- `1.21.11` stays on `Yarn`
- `26.1.2` stays on `Mojang` mappings

## Implementation Status

- `1.21.11`:
  - the shipped `1.22.0` implementation is active as of `2026-04-24`
  - this line contains the new story arc, caravan runtime, compass story modes, and admin test hooks
  - the current shipped tuning includes:
    - `45`-heart caravan merchants
    - staggered `3`-second wave pulses with up to `2` hostile spawns per pulse
    - a tighter `16-26` block hostile spawn ring
    - `5`-second initial `Glowing` on each wave hostile
    - `3` final-wave traitors with `38.4` max health
    - roughly `1/3` caravan guards with slow wooden-sword defensive swings
    - spaced convoy spawn placement to avoid merchant overlap glitches
    - remaining-hostiles status line in active rescue/final progress
    - dry-ground-only caravan anchors / spawn points
    - leash and last-enemy marking safety for hidden or escaped raiders
    - same-cycle `testfinal` setup for faster testing
- `26.1.2`:
  - the shipped Mojang-mapped `1.22.0` implementation is active as of `2026-04-24`
  - this line contains the same story arc, caravan runtime, compass story modes, admin test hooks, tuning, and safety behavior as the `1.21.11` batch
  - continue to use this document as behavior guidance, but keep mapping/API differences explicit when changing either line

## Locked Design Decisions

- this becomes a visible late `Questmaster` story arc
- it is **not** part of the core four story arcs that auto-unlock `Watch Bell`
- unlock condition:
  - `Watch Bell` must already be unlocked
  - the player must have completed `3` different `Pilgrim` combat rumors
- chapter 1 uses `4` different villages, not `4 villages near your area`
- villager conversation objectives count:
  - employed villagers
  - unemployed villagers
  - nitwits
- only adult villagers count
- the provided skins are used as follows:
  - `caravan.png` for caravan merchants
  - `traitor.png` for the hostile late-wave bandit unit
- the caravan content is night-first:
  - the signal can only be found at night
  - once an encounter has started, it may continue past dawn
- the full arc contains exactly `4` successful caravan rescues
- rescue encounters use wave structure:
  - early waves use standard mobs
  - the last wave introduces elite `Traitors`

## Recommended Arc Identity

Working title:

- `Shadows on the Trade Road`

Backup titles if we want to rename later:

- `The Lantern Road`
- `Bells Against the Dark`
- `Road of Missing Carts`

Core fantasy:

- the village can only truly grow if trade reaches neighboring settlements
- merchants have started carrying goods and `magic shards` farther out
- rumors spread that caravans are disappearing at night
- the player becomes the first real road defender, not just a local hunter

## Story Role in Progression

This arc should sit in a new late-story bucket.

It should **not** be mixed into the existing `questmasterArcs()` logic that currently defines the core visible story flow for:

1. `The Failing Harvest`
2. `The Silent Forge`
3. `Market Road Troubles`
4. `Restless Pens`

Recommended structure:

- keep current core-arc gating exactly as-is for `Watch Bell`
- add a second visible story list for late arcs
- unlock `Shadows on the Trade Road` only after:
  - `Watch Bell`
  - `3` different completed combat rumors

This avoids repeating the old `Night Bells` problem where combat-side content gets entangled with the core village-growth flow.

## Arc Structure

### Chapter 1: `Whispers Between Bells`

Story beat:

- the village knows traders are missing, but nobody knows where the disappearances begin

Objectives:

- speak to `6` different adult villagers in the home village
- discover `4` different villages in the overworld
- in each of those `4` villages, speak to `2` different adult villagers

Progress model:

- `Home Rumors Heard: 0/6`
- `Villages Investigated: 0/4`
- `Village Interviews: 0/8`

Rules:

- only unique villager UUIDs count
- one village can contribute at most `2` interview counts
- the same village must not count twice as two different villages
- `NONE` and `NITWIT` professions are valid here

Narrative result:

- the player confirms the attacks are not isolated
- the pattern points to night traffic and shard-bearing caravans

### Chapter 2: `A Needle for the Night Road`

Story beat:

- a `Toolsmith` explains that the `Wayfinder's Compass` can be tuned to shard-heavy caravans in distress

Objectives:

- have the `Wayfinder's Compass` in inventory
- speak to a `Toolsmith`
- receive a calibration that unlocks a new temporary story mode: `Caravan Distress`

Acceptance rule:

- this chapter should not be accepted if the player does not currently have the compass
- the `Questmaster` should show a clear requirement hint

Behavior rules:

- by day, `Caravan Distress` gives a lore-valid failure message
- by night, it can bind to an active distress signal

Narrative result:

- the player learns that caravans carrying shard stock leave a trace the compass can follow

### Chapter 3: `First Signal`

Story beat:

- the first rescue teaches the player what a live caravan ambush looks like

Objectives:

- wait for night
- use `Caravan Distress`
- travel to the distress point
- save `1` caravan

Encounter intent:

- this is the tutorial rescue
- slightly lighter than the standard rescue waves

Recommended tutorial composition:

- wave 1:
  - `3` zombies
  - `1` skeleton
  - `1` spider
- wave 2:
  - `4` zombies
  - `2` skeletons
  - `2` spiders

Success condition:

- all spawned hostiles are dead
- at least one caravan merchant survives

Failure condition:

- total caravan health reaches `0`

Narrative result:

- the threat is real
- the merchants confirm this is happening on multiple roads

### Chapter 4: `Holding the Verge`

Story beat:

- the first rescue was not an exception, and now the player is deliberately patrolling the night roads

Objectives:

- complete `2` more successful caravan rescues

Progress model:

- `Caravans Saved: 1/3`

Standard rescue composition:

- wave 1:
  - `3` zombies
  - `2` skeletons
  - `1` spider
- wave 2:
  - `4` zombies
  - `2` skeletons
  - `2` spiders
- wave 3:
  - `4` zombies
  - `2` skeletons
  - `2` spiders

Caravan composition:

- `3` to `4` caravan merchants
- each merchant has `45` hearts
- they use the provided `caravan` skin

Bossbar:

- raid-style bar
- shows total combined caravan health, not player health
- active tracker / quest-log status should also show remaining hostile mobs

Narrative result:

- the survivors start noticing that shard-heavy caravans are targeted more often than simple produce carts

### Chapter 5: `A Letter for the Guild`

Story beat:

- after the repeated rescues, one surviving caravan merchant finally understands the full scale of the threat and sends a written warning home

Objectives:

- speak to `1` surviving caravan merchant after a successful rescue
- receive `1` `Guild Warning Letter`
- deliver the letter to the `Questmaster`

Why this chapter exists:

- breaks up the escort loop so the arc does not become `four escort chapters in a row`
- lets the story breathe
- creates a clean handoff into a scheduled final defense

Implementation note:

- do **not** define `home` as a fuzzy world area
- do **not** bind it to bed spawn or server spawn for quest completion
- use the `Questmaster` as the authoritative delivery target

Reason:

- the existing compass `home` behavior is based on respawn / server spawn
- that is useful for travel, but not reliable enough as story-village identity
- the `Questmaster` is the strongest lore and technical anchor for "bringing the warning home"

Narrative result:

- the letter warns that a major bandit force plans to strike a large guild caravan
- the attack is expected in `2` nights
- the village now knows the next assault is not random, but scheduled

### Chapter 6: `Bell Over the Trade Road`

Story beat:

- the player prepares for a known attack window and proves the village can defend a major guild caravan, not just scattered traders

Objectives:

- wait through the `2`-night warning window
- on the attack night, use `Guild Convoy` to locate the major guild caravan
- save the large caravan
- return to the `Questmaster`

Timing model:

- the final attack is scheduled for the `second` coming night after the warning is delivered
- the tracker should show a clear countdown state
- the player should receive reminder text at dusk

Recommended quest UI wording:

- `Guild Caravan Attack Expected In: 2 nights`
- `Guild Caravan Attack Expected In: 1 night`
- `The convoy is on the road tonight`

Final encounter composition:

- caravan size:
  - `5` to `6` caravan merchants
- wave count:
  - more waves than the earlier rescues
- baseline hostile pressure:
  - clearly higher than the earlier rescues
- recommended starting point:
  - wave 1:
    - `4` zombies
    - `2` skeletons
    - `2` spiders
  - wave 2:
    - `5` zombies
    - `3` skeletons
    - `2` spiders
  - wave 3:
    - `6` zombies
    - `3` skeletons
    - `3` spiders

Final wave addition:

- spawn `3` `Traitors`
- they use the provided `traitor` skin
- they act as hostile bandit elites, not undead
- they carry `Diamond Swords`
- their melee pressure should feel close to a `Wither Skeleton`
- they should survive about `6` solid hits from a plain `Netherite Sword`
- they do **not** apply `Wither`
- instead they can apply brief `Poison`

Optional stretch addition:

- `1` armored undead road captain or banner-bearing leader

Narrative result:

- the trade road is no longer just a rumor lane
- the village now has a believable reason to expand trade outward

Final reward recommendation:

- keep the story road modes as temporary stage-bound compass modes
- use `Caravan Distress` for the normal rescue chapters and `Guild Convoy` for the final attack night
- unlock new late-road shop and decor content
- keep a formal new village project as an optional stretch goal, not an MVP requirement

## Dialogue Plan

The best fit is the current `talk to villager` style used elsewhere in story content, but with explicit chat lines attached to the interaction.

Important handling rule:

- do **not** consume the villager interaction by default
- send the chat dialogue and still allow vanilla trading to open for employed villagers
- unemployed villagers and nitwits then become valid silent-UI conversation targets automatically

### Home Village Rumor Pool

These are the first `6` conversation lines in chapter 1.

Suggested pool:

- `Two wagons for the north road should have returned before the last rain.`
- `The square has room for more stalls, but the roads are eating the merchants before the market can grow.`
- `I heard the bell after midnight and thought it was only wind. Then I noticed no carts returned at dawn.`
- `The mill counted sacks that never arrived. Someone out there is taking more than coin.`
- `Old trade routes do not empty themselves. Something is making the merchants vanish after sundown.`
- `The caravans carry shard stock now. If anything hunts value on the road, that would draw it in.`
- `No one dares say it loudly, but people have started waiting for footsteps that never come back.`
- `If the village wants to grow, the roads have to stay alive after dark too.`

### Neighbor Village Rumor Pool

These are used when speaking to villagers inside the `4` discovered outside villages.

Suggested pool:

- `No, I have heard nothing myself. Only that fewer bells are answered on the trade road now.`
- `A cousin of mine sent word that two merchants never crossed the river bridge.`
- `We saw torchlight on the road and thought help had come. By morning the wagons were stripped.`
- `The missing carts all had shard chests, or so people say. Grain alone is not what is being hunted.`
- `One trader reached us on foot and would not speak until sunrise. He kept looking behind him.`
- `The attacks are worst after dusk. Day traffic still passes, but the night road belongs to something else.`
- `We stopped sending small caravans. They vanish too easily once the road goes dark.`
- `People blame monsters, but it feels organized now. The same routes are hit again and again.`
- `I heard wheels in the dark and then nothing. Not even shouting, just sudden silence.`
- `If your village still sends merchants at night, warn them. The road is no longer only long.`

### Toolsmith Calibration Lines

Suggested lines when chapter 2 advances:

- `The compass can be taught a caravan's shard-hum, but only when the night air is cold enough to carry it.`
- `By day the road is too loud. After dusk, the false sounds fall away and the real signal remains.`
- `Bring the needle back under the stars. If a caravan is in trouble, it will pull the point like a wound pulls blood.`

### Rescue Survivor Lines

Use short post-encounter lines from caravan merchants.

Suggested pool:

- `We thought the road was done for us.`
- `They came for the shard crates first. They barely looked at the cloth bolts.`
- `This was the third attack route we heard about this week.`
- `Tell your village the night roads are being watched by more than chance.`
- `Without a road guard, no market beyond the bell will survive.`
- `If the caravans stop, the neighboring villages stop trusting us too.`

### Failure and Warning Lines

Suggested messages:

- `The signal is too faint by daylight. Return after sundown.`
- `The caravan is lost. The road fell silent before you could hold it.`
- `No distress call answers the compass tonight. Try again after the next dark road.`
- `This village has already told you what it knows. Ask two others here, then move on.`

## Systems To Add

### 1. Late Story Arc Unlock Bucket

Need:

- a clean distinction between:
  - core visible arcs
  - late visible arcs

Reason:

- `Watch Bell` must remain derived from the core four
- the new arc must not accidentally change the existing unlock chain

### 2. Combat Rumor Completion Counter

The good news:

- the mod already stores completed combat rumors per player through the existing pilgrim completion flags

Implementation direction:

- add a helper that counts distinct completed combat rumors
- unlock the arc when that count reaches `3`

### 3. Village Detection Helper

Need:

- a reusable `isPlayerInsideVillageStructure` helper
- a reusable `villageSignatureAt(pos)` helper

Recommended base:

- reuse the current village structure detection logic already used by the compass roadmark system

Distinct village tracking should use:

- structure start identity
- structure bounding-box anchor
- or a stable chunk-based signature tied to the containing village start

It should **not** use:

- villager UUIDs
- arbitrary radius circles

### 4. Conversation Quest Helper

Need:

- quest-specific villager dialogue on right-click
- unique-per-villager counting
- per-village counting caps

This should work for:

- employed villagers
- unemployed villagers
- nitwits

### 5. `Caravan Distress` Compass Mode

Need:

- a new compass mode label
- story-bound night search logic
- a message when the player tries it by day

Recommended behavior:

- unlock temporarily after chapter 2 calibration
- switch to `Guild Convoy` for the final chapter signal
- stay as temporary story modes unless a later explicit design pass wants a permanent unlock

### 6. Caravan Encounter Runtime Service

Need:

- a new service that owns the active rescue encounter
- distress target rolling
- encounter activation on player proximity
- wave spawning
- scheduled final convoy timing
- cleanup
- success and failure handling

Recommended state split:

- persistent player-side coordinates and progress in saved quest state
- live spawned entity UUIDs and bossbar tracking in runtime maps

Special final-encounter rule:

- the chapter-6 convoy should not behave like a random normal rescue
- it should be scheduled from the warning letter hand-in
- on the target night, the compass should switch over to `Guild Convoy` for the guild convoy event

### 7. Caravan Merchant NPC

Need:

- a new non-shop, non-questmaster NPC entity
- uses the existing player-style humanoid render path for now
- no custom click UI

Behavior:

- high health
- no trading UI
- can emit short post-rescue chat lines
- uses `caravan.png`

### 8. Traitor Elite Hostile

Need:

- a new hostile humanoid entity
- intended role: elite bandit / traitor wave finisher
- uses the same player-style renderer path with a dedicated texture

Behavior:

- hostile by default
- melee fighter with `Diamond Sword`
- applies short `Poison` on hit instead of `Wither`
- can target caravan merchants first, then the player once engaged

Balance target:

- damage feel should be roughly `Wither Skeleton` territory
- durability target should feel clearly above `Wither Skeleton`
- poison should create pressure, not a long lockout
- current tuned point:
  - `38.4` max health
  - short `Poison I`
  - poison applies on successful hits
  - no knockback gimmick
- tune for roughly `6` clean hits from an unenchanted full-charge `Netherite Sword`

Spawn use:

- reserved for the last wave of a rescue
- finale chapter currently uses `3` traitors after the latest playtest balance pass

### 9. Caravan Health Bossbar

Need:

- a raid-style bossbar
- label should describe caravan survival, not a monster boss

Recommended label:

- `Caravan Under Attack`

Bar source:

- sum of all active caravan merchant health

### 10. Hostile Targeting Rules

Need:

- monsters should prioritize the caravan when the encounter starts

Recommendation:

- use strong initial aggro on caravan merchants
- standard mobs and traitors should try to path into the caravan and attack it by default
- they should only switch to the player after the player damages them or otherwise hard-pulls aggro
- if the player dies or disengages long enough, they should be allowed to reacquire caravan targets

This keeps the encounter readable without making the AI look fake.

Special note for traitors:

- traitors should join late enough that the player clearly reads them as the climax of the encounter
- if they spawn too early, they blur into the normal mob pack and lose their identity

### 11. Failure and Reset Rules

Need:

- fail when all caravan health is gone
- roll a fresh distress location for the next attempt
- clean up leftover mobs and merchants cleanly

Recommended safety rule:

- if the player leaves the encounter area for too long, resolve it as failed and clean it up

Player death rule:

- player death does **not** instantly fail the encounter
- the encounter continues while the caravan is still alive
- if the player returns in time and at least `1` caravan merchant survives, the attempt can still be salvaged
- if the caravan is wiped while the player is dead or absent, that attempt fails

Retry rule:

- earlier rescue chapters should roll a fresh rescue on the next valid night
- the chapter-6 large convoy should not restart the full `2`-night setup after every failure
- after a failed final defense, re-arm the next attempt for the next coming night so retries stay painful but not tedious

## MVP vs Stretch

### MVP

- new visible late `Questmaster` arc
- unlock after `3` different completed combat rumors
- chapter 1 village investigation
- chapter 2 compass calibration
- chapter 3 to 6 with `4` total caravan rescues
- villager dialogue chat lines
- no-job and nitwit support
- `Caravan Distress` mode
- caravan merchants
- traitor elite hostile
- warning-letter handoff to `Questmaster`
- `2`-night scheduled finale setup
- bossbar
- rescue success and failure flow
- temporary stage-bound story compass modes

### Stretch

- special final-road captain
- custom sounds for distress pulses
- dedicated caravan reward chest
- new late-road `Pilgrim` shop offers
- a formal new village project such as `Caravan Charter`

## Recommended Implementation Order

1. refactor story arc categorization so late arcs are cleanly separated from the core four
2. add the pilgrim completion-count helper for `3` different combat rumors
3. add reusable village detection and village signature helpers
4. build chapter 1 investigation logic and dialogue system
5. add chapter 2 compass calibration gate and temporary `Caravan Distress` mode
6. build `CaravanEncounterService` with one tutorial rescue first
7. add caravan merchant entity and renderer
8. add survivor dialogue plus `Guild Warning Letter` handoff flow
9. add scheduled `2`-night finale timing and chapter-6 event binding
10. add traitor elite hostile entity and renderer
11. add bossbar and fail state
12. scale the encounter up to the full `4`-rescue arc with the large final convoy
13. add optional late reward content after the core story batch is stable

## Tuning Knobs To Revisit After Playtest

- if `4` different villages is too seed-dependent, reduce to `3`
- if standard rescue waves are too light, shorten the pulse delay before raising total mob counts again
- if `3` final traitors are too oppressive in solo play, reduce poison duration or attack damage before changing traitor count
- if traitors are dying too fast, raise max health from the current `38.4` toward `44` before adding extra gimmicks
- if the `2`-night wait feels too slow in survival, reduce it to `1` night without changing the letter setup
- if bossbar pressure feels unfair in solo play, lower mob pressure before lowering caravan count
- if right-click plus trading UI feels noisy, add a short chat cooldown per villager

## Bottom Line

This content batch is fully feasible inside the current mod architecture.

The hardest parts are not the chapter scripts themselves. The hardest parts are:

- clean late-arc progression separation
- stable village identity tracking
- a robust caravan encounter runtime with spawn, target, bossbar, fail, and cleanup rules

Everything else already has a strong base in the existing code.
