# Stories and Village Projects

## Progression overhaul in 2.6.3

Accepted freight contracts and shared guild projects appear separately in the
journal and can be pinned in the tracker. Freight counts the same loose goods
and courier-satchel cargo as the supply action. Load the goods with
`/vq routes contracts supply`; the next arrival on the selected route completes
the delivery. A settlement hold is shown instead of an impossible delivery.
Shared projects show the guild's delivered total and the actual material;
carry the rest and use `/vq town project contribute`. Personal earned project
rewards remain visible after leaving the guild and can still be collected with
`/vq town project claim`. Reading these cards never delivers goods, expires a
contract or collects a reward.

On Peaceful, Marius accepts watch supplies personally: **16 Torches** for his
first task, **8 Iron Ingots** for the second, then **16 Bread and 1 Shield** for
the last. Accept each task and bring the entire bundle back to Marius. Hand-in
consumes the supplies and uses the usual stage rewards. His dialogue, item rows
and tracker explain this route; it needs no raid or night-time wait. Changing
back to another difficulty restores the combat requirements with your actual
kill, healing and raid evidence preserved. Supplies never count as combat events.

Completed collection counters still require the actual hand-in items. If you
store them elsewhere, the short tracker points to the next missing material.

Village Quest 2.6.3 separates personal stories into two slots: one Core
story and **A Place at the Table**. Local village stories keep their own state.
Kingdom chapters do not clear Core objectives or use the Core cooldown.
Parties have the same two independent slots and separate join offers. Accept or
decline each chapter using its own buttons. With two open offers, add `core` or
`kingdom` to `/vq party share story accept` or `/vq party share story decline`.
Kingdom sharing requires the same village and an explicit chapter invitation.
Admitted offline partners keep earned progress and pending claims, including
after another partner moves to the next chapter. Collect a pending Kingdom claim
before changing its village. These progression changes are included in 2.6.3.

Each active Core story, Kingdom story, local story and commission has its own
journal card and can be selected as your personal tracker focus. The single pin
survives chapter and language changes. An old title-based pin moves only when it
matches exactly one active quest; an ambiguous old pin is cleared while your
tracker filter preference is kept. The HUD shows one short current objective;
open the journal for the full requirements and delivery details.
Completed parts are highlighted independently in the journal and Questmaster,
including mixed harvest or crafting lines. Their color follows the recorded
numeric progress, and stays the same when the text wraps or the language changes.
Friends, accepted village follow-ups, pilgrim expeditions and artifact tasks
use concise next steps; the journal retains the full instructions.

The short tracker also distinguishes waiting for a convoy from following its
compass trail, speaking to the courier and delivering the warning. For the bait
caravan, show the Questmaster a banner or iron sword to choose an approach;
then follow the compass. On Peaceful, gather 12 bread and 16 oak planks before
following the route checks. The final shrine relay shows the next fresh freight
contract or caravan incident; Map Only mode needs no interactive incident.
Lutte’s tracker distinguishes a coast still being surveyed, a search that needs
another region and the coordinates of an available coastal signal target.

### Pilgrim expeditions on Peaceful

All seven combat contract families have an expedition alternative. Accept the
contract, enter its target dimension and travel normally at least 320 horizontal
blocks from the first recorded position. The three distance checks follow that
distance; walking in circles does not replace it. The active card shows the
recorded starting coordinates and each requirement separately.

| Contract | Dimension | Supplies consumed by the pilgrim |
| --- | --- | --- |
| Quench for the Hall | Overworld | 12 iron ingots |
| Wool Before Rain | Overworld | 16 white wool |
| Tracks in the Dark | Overworld | 12 redstone dust |
| Fangs by the Hedgerow | Overworld | 12 string |
| Ash on the Pass | Nether | 12 nether wart |
| Smoke over Blackstone | Nether | 32 blackstone |
| Stillness Beyond the Gate | End | 12 chorus fruit |

Carry the supplies when returning. Overworld expeditions require returning
within 32 blocks of their recorded starting point. Nether and End expeditions
require returning to the Overworld. Newly accepted End reward contracts also
require entering an End City after acceptance. Speak to the pilgrim to hand in
the materials; the displayed card uses the saved mode until a normal difficulty
transition updates it. Switching difficulty preserves the contract and rewards
but clears incompatible objective progress.

New Ash on the Pass reward choices include four Blaze Rods as a material option,
alongside the existing supplies and currency. This provides fuel for brewing
and material for Eyes of Ender on Peaceful. Nether wart can be gathered from
Nether fortresses without fighting. Existing accepted reward snapshots keep
their original choices; later contracts use the updated choices. The usual
contract cycle still applies. Peaceful expedition completion also counts toward
the existing pilgrim prerequisite for Shadows on the Trade Road.

### Night Bells on Peaceful

Use the bell or right-click a lantern, soul lantern, campfire, soul campfire or
fence gate in the bound village. Each distinct 8×8 area counts once per chapter;
walking, trading and repeating the same area do not add a check. The journal
names your bound village and suggests an unused, loaded area. This does not
promise that an object is already there: you can place one yourself.

| Chapter | Protection checks | Supplies consumed at the Questmaster |
| --- | --- | --- |
| 1 | Bell and 3 different areas | 16 torches |
| 2 | 3 different areas | 16 oak fences (not fence gates) |
| 3 | 3 different areas | 16 bread |
| 4 | Bell and 4 different areas | 8 lanterns and 1 shield |

The final chapter also needs a living iron golem in the same village within
48 blocks of you, and confirmation using the bell or guild notice board.
Placed lanterns do not replace the lanterns for hand-in. Accepted chapters use
their saved objective mode in the dialogue. Changing difficulty resets only
the incompatible objectives; village identity, chapters and rewards remain.
Older area checks remain counted even though their original coordinates cannot
be reconstructed. New checks use exact area coordinates, including in parties.

Village follow-ups such as **Tack for the Long Drive** are once-only for that
village. Completing a Weekly, a Daily, or waiting for a Minecraft day does not
renew them. Open **Village requests** for available local help and that request's
own reset information. **Update progress** reloads current progress and carried
supplies; it does not regenerate a story or advance a reset.
Use exactly the same Village Quest version on client and server, including any Unreleased revision. A mismatch is rejected before normal VQ synchronization.

Back up your world before updating to 2.6.3.
Existing Kingdom chapter and reward evidence is migrated without paying rewards
again. Downgrading a world saved with the two slots to unreleased.2 is unsupported.
Unknown or damaged new story formats are preserved and prevent reconnection
until a matching version can read them safely.

## Living Kingdoms 2.6

See [Living Kingdoms stories and tools](kingdom-stories-and-tools.md) for
**A Place at the Table**, the Village Seal and **The Unwritten Promise**.
See [Travelling NPCs](travelling-npcs.md) for Simon, Lutte and Marius.

## Core Story Order

The visible core `Questmaster` story chain is:

1. `The Failing Harvest`
2. `The Silent Forge`
3. `Market Road Troubles`
4. `Restless Pens`

When these four are all complete:

- `Watch Bell` is unlocked automatically as a derived village project
- the dangerous `Roadside Watch` rumor lane becomes available through the `Pilgrim`

After each fully completed `4`-chapter arc, the next story waits behind a real `1 hour` cooldown. The `Story` tab shows that countdown directly.

## Late Story Follow-Up

After `Watch Bell`, the `Story` tab can also show a later locked follow-up:

- `Shadows on the Trade Road`

Unlock rules:

- `Watch Bell` must already be unlocked
- `3` completed `Pilgrim` combat rumors are required
- the story shows as a greyed-out locked entry before those rumor clears are finished

This arc is intentionally separate from the four core village-growth arcs above.

Beginning with the `2.0.0` release, completing it opens one more solo follow-up:

- `The Empty Caravan`

That arc turns the road-defense storyline into the persistent `Caravan Yard` trade-route system.

`Night Bells` remains a visible four-chapter Questmaster arc. Its Combat path keeps the original hostile and raid objectives; Peaceful replaces only those impossible objectives with bell, patrol, distinct-location, guardian, and confirmation steps. Changing difficulty clears incompatible partial objective progress instead of converting one mode's progress into the other.

## Story Arc Overview

| Arc | Theme | Project Unlocked |
|---|---|---|
| `The Failing Harvest` | food, hives, ovens, market proof | `Apiary Charter` |
| `The Silent Forge` | ore, smithy rebuild, forge proof | `Forge Charter` |
| `Market Road Troubles` | trade flow, ledgers, staffed stalls | `Market Charter` |
| `Restless Pens` | breeding, wool, pasture order | `Pasture Charter` |

## Reading Story Objectives

- Action verbs such as `mine`, `harvest`, `craft`, `smelt`, `trade`, `breed`, and `speak` describe progress that must happen after accepting the chapter.
- Delivery verbs such as `bring`, `supply`, and `turn in` describe items that must be carried when claiming. Stored or traded goods are valid for a pure delivery.
- Hybrid chapters require both: complete the fresh action, then return with the full listed bundle. The Questmaster validates the entire bundle before consuming any part of it.
- Individual stacks are not permanently tagged. Fresh work is recorded as chapter progress, while the carried delivery is checked separately at the end.

## Late Arc Overview

### `Shadows on the Trade Road`

This is the late road-defense follow-up unlocked after `Watch Bell` and `3` completed `Pilgrim` combat rumors.

### Chapter 1: `Whispers Between Bells`

- speak with `6` adult villagers in the home village
- then fully investigate `4` other villages
- each of those `4` villages needs `2` different adult villager interviews
- employed villagers, unemployed villagers, and nitwits all count

### Chapter 2: `A Needle for the Night Road`

- you must already own the `Wayfinder's Compass`
- speak to a `Toolsmith`
- the toolsmith calibrates the compass for story-linked night-road tracking

### Chapter 3: `First Signal`

- wait for night
- use the new `Caravan Distress` compass mode
- reach the live distress point
- save `1` caravan

### Chapter 4: `Holding the Verge`

- answer `2` more nighttime distress calls
- keep each caravan alive through the full attack

### Chapter 5: `A Letter for the Guild`

- speak to the surviving courier after a successful rescue
- receive the `Guild Warning Letter`
- bring it back to the `Questmaster`

### Chapter 6: `Bell Over the Trade Road`

- wait through the `2`-night warning window
- use the `Guild Convoy` compass mode on the attack night
- defend the larger guild caravan through the full assault
- the last wave adds elite `Traitor` bandits

Combat size follows the world's Vanilla difficulty and the number of active story-party members actually present within `64` blocks when the encounter starts. On `Peaceful`, the nighttime schedule and two-night final warning still apply. Bring `8 Bread` per caravan: it is consumed automatically within `10` blocks of the caravan's starting point. Guide the merchants through `3` checkpoints per ordinary escort or `4` for the final convoy. At each checkpoint, stay within `10` blocks until at least half the merchants arrive within `8` blocks. The journal shows the next checkpoint coordinates and tracks bread separately; no hostile wave is queued. Offers and completion dialogue describe the saved objective mode without changing chapter rewards.

### `The Empty Caravan`

This six-chapter solo follow-up becomes available after `Shadows on the Trade Road` is complete.

#### Chapter 1: `The Cart That Returned Alone`

- follow the `Wayfinder's Compass` to the abandoned caravan site
- investigate what returned without its crew

#### Chapter 2: `Three Broken Signs`

- follow a sequence of `3` clue sites along the road
- each discovery advances the investigation to the next sign

#### Chapter 3: `Ink Beneath Wax`

- turn in `12` paper
- turn in `3` ink sacs
- turn in `2` honeycomb

This is a pure supply delivery. Stored or traded goods are valid, and the full bundle is consumed together.

#### Chapter 4: `Names Behind the Seal`

- interview an adult `Cartographer`
- interview an adult `Librarian`
- interview an adult `Cleric`

#### Chapter 5: `A Caravan Made of Bait`

- make a visible choice at the `Questmaster`
- hold any banner and interact to offer amnesty, or hold an iron sword and interact to demand justice
- defend the bait caravan from the resulting attack
- amnesty favors trade reputation; justice favors currency and roadside-watch reputation

On `Peaceful`, the choice remains, followed by route provisioning and inspections.
Bring `12 Bread` and `16 Oak Planks`; both are consumed when you approach the
first route marker within `12` blocks. Follow the Wayfinder's Compass through
`3` successive checkpoints. Each inspection reveals the next target. Delivered
supplies remain credited even after they leave your inventory; no combat is required.

#### Chapter 6: `Roads Between Villages`

- turn in `32` gravel
- turn in `24` stone bricks
- turn in `8` lanterns
- turn in `4` leads
- unlock the `Caravan Yard` and receive the `Caravan Ledger`

This is also a pure supply delivery. The guild accepts stored or traded building materials.

## The Failing Harvest

### Chapter 1: `Thin Fields`

- harvest `16` Wheat items from ripe wheat crops after accepting
- harvest `8` Potato items from ripe potato plants after accepting
- carry and turn in `16` Wheat and `8` Potatoes

### Chapter 2: `Quiet Hives`

- carry and turn in `3` honey bottles and `1` honeycomb
- harvesting full hives records the normal story progress, but traded, stored, or otherwise acquired supplies are also accepted

### Chapter 3: `Bread For The Square`

- craft `6` bread after accepting
- pull `4` baked potatoes from an oven after accepting the chapter
- carry and turn in all `6` bread and `4` baked potatoes

### Chapter 4: `Market Relief`

- complete `2` real villager trades
- earn `4` emeralds from those trades

### Result

- unlocks `Apiary Charter`

## The Silent Forge

### Chapter 1: `Cold Hearth`

- mine `64` coal
- mine `40` raw iron
- mine `24` redstone
- mine `16` raw gold
- mine `4` diamonds
- carry the complete mineral bundle for turn-in

### Chapter 2: `The Bellows Breathe Again`

- pull `24` iron ingots from a furnace after accepting
- turn in `24` iron ingots
- turn in `3` blast furnaces
- turn in `3` cauldrons

The furnace work must be fresh. The blast furnaces and cauldrons are supplies, so stored or traded stations are valid.

### Chapter 3: `Tools For The Hall`

Craft after accepting:

- `1` iron pickaxe
- `1` bucket
- `1` pair of shears
- `1` shield

At turn-in, carry a complete pristine kit with those quantities. The craft progress and the carried kit are checked separately, then the kit is consumed.

### Chapter 4: `The Master's Edge`

The book purchases must happen while this chapter is active:

The quest presents two objectives together: **Knowledge of the librarians** and **Equipment for the hall**. Each equipment piece has its own checkmark, visible even before buying the books. Supplies may be gathered in any order; completion still requires both book purchases and the complete set. The story explains the forge's need for knowledge and supplies, with enchantment and hand-in rules in a separate note.

1. buy one enchanted book with `Sharpness` from a villager
2. buy one enchanted book with `Protection`, `Fire Protection`, `Blast Protection`, or `Projectile Protection` from a villager; any level counts for either book, and the purchases may happen in either order
3. bring one each of the `Iron Helmet`, `Iron Chestplate`, `Iron Leggings`, and `Iron Boots`, each with any of those four protection types, plus a `Diamond Sword` with `Sharpness`, to the Questmaster

Found books, books already owned when accepting, and trading enchanted equipment do not grant new book purchase progress. Equipment may come from any source and may already be enchanted or damaged. There is no crafting or anvil-event requirement. Any positive enchantment level counts; different armor pieces may use different protection types, independently of the protection book purchased. Only the five equipment pieces are consumed at hand-in; the books are kept and need not be applied to that equipment. Four copies of the same armor piece do not form a complete set.

For existing active saves, previously stored book milestones remain valid: older saves did not record the source of those books. New progress requires actual villager purchases. Modded enchantments with different registry IDs are not automatically treated as the vanilla enchantments named above.

After both book purchases are proven, the quest checks the finished set directly. Only the final five-piece delivery needs the Questmaster.

### Result

- unlocks `Forge Charter`

## Market Road Troubles

### Chapter 1: `Shuttered Stalls`

- earn `64` emeralds from real villager trades

### Chapter 2: `Ledger And Notices`

1. craft `96` paper after accepting
2. craft `16` books while this chapter is active, even before finishing all the paper
3. turn in `48` paper and `16` books together; the bundle is consumed

Both crafting counters advance independently. Saved progress remains valid; work made before accepting does not provide new crafting proof.

### Chapter 3: `Goods Must Flow`

- complete `24` real villager trades
- speak with adult villagers from any `4` different working professions; unemployed villagers and nitwits do not count
- previous profession contacts remain credited, including shared-party evidence

### Chapter 4: `Market Day Returns`

- speak with `6` different adult villagers
- ring a bell while at least `6` adult villagers are within `20` blocks

### Result

- unlocks `Market Charter`

## Restless Pens

### Chapter 1: `Empty Troughs`

- trigger `12` breedings
- keep `48` hay bales ready for turn-in

### Chapter 2: `Wool Before Weather`

- shear `24` sheep
- turn in `48` wool of any color

### Chapter 3: `New Pastures`

- ride `2,400` blocks on horseback after accepting the chapter
- routine distance ticks are deliberately silent; reaching the full route-survey distance still plays the chapter-ready cue

### Chapter 4: `The Shepherd's Call`

- ring a bell
- have at least `8` adult pasture animals within `12` blocks when the bell rings
- carry any vanilla Horse Armor in your inventory, including Leather, Copper, Iron, Gold, Diamond or Netherite when present in this Minecraft version; the armor is kept

### Result

- unlocks `Pasture Charter`

## Village Project Effects

### Village Ledger

- story arcs and village projects are surfaced in the journal
- unlocks the matching `Village Ledger Plaque` trophy offer in the `Pilgrim` shop

### Apiary Charter

- future `Farming` reputation rewards gain `+5`
- unlocks the `Field Bearings` follow-up for the `Wayfinder's Compass`
- unlocks the matching `Apiary Charter Plaque` trophy offer in the `Pilgrim` shop

### Forge Charter

- future `Crafting` rewards gain `+0.75` experience levels, scaled to the player's current vanilla level
- unlocks the matching `Forge Charter Plaque` trophy offer in the `Pilgrim` shop

### Market Charter

- lets the `Pilgrim` later add old roadmarks to the `Wayfinder's Compass`
- immediately grants the `Caravan Ledger`, map access, and one provisional route with an `8 Silvermark` daily network cap
- provisional caravans use two merchants and do not yet roll recurring incidents
- unlocks the matching `Market Charter Plaque` trophy offer in the `Pilgrim` shop

### Pasture Charter

- future `Animals` rewards gain `+2 Silvermarks`
- unlocks the matching `Pasture Charter Plaque` trophy offer in the `Pilgrim` shop

### Watch Bell

- future `Roadside Watch` reputation rewards gain `+5`
- unlocks the late dangerous `Pilgrim` rumor lane
- reveals the locked late `Shadows on the Trade Road` story entry once the project exists
- unlocks the `Watch Bell Reliquary` trophy offer in the `Pilgrim` shop

### Caravan Yard

- unlocks after completing `The Empty Caravan`
- grants and enables the `Caravan Ledger`
- supports a home hub and up to `5` persistent trade routes between registered villages
- stores up to `48` surveyed waypoints per route, allowing deliberate player-built detours
- makes nearby simulated caravans appear as traveling merchant NPC groups
- lets player-built road surfaces and lighting improve speed and delivery earnings
- adds recurring route emergencies, security progression, and a live trade-route map
- expands the provisional route limit from `1` to `5`
- enables the five-rank Village Trade Guild, freight contracts, specializations, upgrades, and the `60 Silvermark` route-income cap
- keeps offline route proceeds in a capped trade-office escrow until the ledger is opened

See [Trade Routes and Caravans](./trade-routes-and-caravans.md) for the complete system and its in-game test command.

Village Quest 2.5 adds [chosen Vanilla reward packages and saved collection](vanilla-rewards.md), including `/vq rewards claim`. Existing accepted contracts and prepaid orders retain their original conditions.

## Encounter recovery

Empty Caravan, Shadows and False Distress do not count unloaded enemies as
kills. Return to the encounter in the Overworld to continue. After a server
restart a transient fight can be restarted; obsolete encounter bodies are
removed when their chunks load. This does not grant a victory or extra reward.
Before warning-letter handover, a courier missing for five continuous minutes
can be replaced near you in the Overworld. Brief unloads retain the original;
returning before the deadline restarts that window. An old replaced courier is
removed when its chunk loads again. A delivered warning letter stops further
courier spawning. The Empty Caravan
bait approach is chosen once; speaking again cannot replace an ongoing fight.
