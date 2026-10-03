# Trust on the Roads of Concord

The NPC trading community keeps **Guild trust** and each registered physical village keeps **local trust** for each player. Both run from −100 to +100 and begin at zero in existing and new saves. They are independent of earned skill expertise, permanent Known/Trusted/Allied village bonds, network renown, multiplayer guild prestige and money. Old achievements do not award retrospective points.

## Standing and services

| Guild trust | Standing | Caravan purchases per player/connection/Daily reset | New Dispatches / Convoys |
|---|---|---:|---|
| −100 to −60 | Ostracised | 0 | Blocked |
| −59 to −20 | Distrusted | 2 | Blocked |
| −19 to +19 | Neutral | 4 | Available with existing unlocks |
| +20 to +59 | Reliable | 5 | Available |
| +60 to +100 | Respected | 6 | Available; new Dispatch reward +5%, rounded down |

An **open personal reparation case overrides positive standing**: new caravan purchases, Dispatch acceptance and Convoy start/join are blocked. Only affected villages block new ordinary requests. Accepted work, refunds, Daily/Weekly quests, stories, Vanilla trade and Wayshrines remain usable. Local trust does not replace village bonds or multiply economic bonuses. The Dispatch bonus is fixed on acceptance; a base reward of12 still pays12 with5%.

The Journal's Network page has **Villages**, **Trust** and **Chronicle** tabs. Village cards retain bond/supply information and add local standing. Trust shows personal values, real permissions, probation, the aid case and recent changes. Chronicle holds32 visible events; older reward receipts remain saved to prevent replay. An undiscovered crime location does not become a discovered village or route.

## Earning trust

Only real server-confirmed completions count. Repeatable Guild gains share a limit of **8 per existing Daily reset**; local gains have a separate **8 per village/reset** limit. Daily and Weekly scheduling remains based on real-world reset settings. Points lost through caps are not banked for a later day; money and existing quest rewards still pay normally. Completions made while social reputation is disabled are also finalized without trust points; enabling it later does not award those completions retrospectively.

| Completion | Guild gain | Local gain | Source limit |
|---|---:|---|---|
| Daily | 1 | — | 2 points/reset, including bonus Daily |
| Weekly | 3 | — | Once per quest instance |
| Village request | 1 | 4 at destination | Existing village request/reset rule |
| Regional Dispatch | 3 | 2 source,4 destination | 2 trust-bearing deliveries/reset |
| Guild Convoy | 6 | 2 per personally witnessed village stop | 1/reset; real registered escort presence |
| Scripted caravan incident | 2 | — | 1/reset; actual successful helper |
| Resettlement support | 5 | 8 at restored village | Once per recovery cycle; at most8 helpers |
| First whole story completion | 3 | 6 at bound village, if any | Once; outside repeatable caps |
| First local Afterstory | 2 | 4 at historical village | Once; outside repeatable caps |

Resettlement support is accepted at your own Homestead Notice Post for an observed abandoned settlement. Hand in **32 wheat and16 any planks** together. This records aid; it does not spawn villagers or restore a settlement by itself. Trust is awarded after the existing village-life system confirms restoration, including for offline helpers. A player responsible for an open local case is excluded for that cycle.

## Violence and proof

VQ crews and their pack mule represent their saved route destination, including while travelling or visiting their owner's yard. Adult or young Vanilla villagers are protected inside a registered VQ village: same dimension, at most64 horizontal and32 vertical blocks from the physical anchor. Anchor heights are saved when their chunks load normally; old XZ-only anchors wait for a known height without forcing chunk loads. Questmaster/Pilgrim keep their existing protection; intended story enemies are not civilians. The first accepted hit freezes the crime location.

Only accepted health/absorption damage with a server-resolved player cause counts: direct attack, owned projectile, tamed animal or owned primed TNT. Cancelled hits and zero damage do not count. A first nonfatal hit of at most4 damage grants one warning per attacker, shared by the same-tick sweep. The warned trader pauses30 seconds and the crew retreats; guards do not attack for a warning. Another hit within10 server-play minutes, or a first hit above4 damage, opens a case. Fatal hits immediately escalate.

| Offence | New Guild value | New local value |
|---|---|---|
| Assault | old−15 | old−25 |
| Crew kill | min(old−60,−60) | min(old−70,−60) |
| Registered villager kill | min(old−40,−20) | min(old−70,−60) |
| Pack mule kill | min(old−35,−20) | min(old−45,−45) |

Values clamp to−100..100. Repeated hits against the same victim with no gap above60 seconds count as one loss, while renewing aid/time. Death replaces that victim's own previous assault loss; unrelated gains and losses survive. Different killed victims remain different offences. Personal cases and victim receipts survive reload, route removal, guild changes and logout. Partners and route owners do not inherit guilt.

Environmental or unknown causes do not invent an offender. A proven recent injury can link a fatal fall/fire within10 seconds; intermediate unattributed fire/fall ticks preserve the original deadline, another proven attacker replaces the old cause, and other ambiguous intervening causes remove attribution. Pushing, placed lava and unidentified mod damage are not general grief detection. Creative/Spectator interventions are exempt by default.

## Active reparation

Open **Trust / Reparation** at your own valid Homestead Notice Post, a valid affected village board, or an existing nearby Questmaster. The Questmaster fallback works even without routes or a surviving victim village. A board in a village where your local trust is at most−20 can also open the first reconciliation case after an administrative/imported trust change. Stand within8 blocks. The Journal is read-only and explains where to go.

- **Minor case:**10 online minutes alive and not Spectator, plus **one chosen aid**:32 wheat **or**16 any planks **or**4 iron ingots. You can change the choice before any delivery.
- **Major case (any death):**30 online minutes, plus **64 wheat,32 any planks and8 iron ingots**. Partial deliveries and any order are supported.

The server consumes only the missing amounts carried in ordinary inventory. Old, duplicate, foreign, distant or invalid requests consume nothing. Offline time, death screens and `/time set` do not advance the timer. More actual aggression resets contributed materials and the minimum timer; severity cannot downgrade and debt does not stack beyond one30-minute/three-material case. Aid grants no money, expertise, bond deliveries or ordinary trust reward.

When time and materials are complete, the case closes automatically. Affected values rise to at least−10, higher values remain. **Probation** lasts for two ordinary completions which actually gain at least one trust point after caps. Baseline trade4 and new work are open; positive rank bonuses pause. An administrative low value without an offence also has the minor reconciliation route.

## Crew lives and safe freight

Crew and mule can take ordinary damage unless disabled in configuration. Guards defend only against proven attackers, within a fixed24-block route leash and20 seconds of new aggression. Others retreat along checked, loaded road positions. No chasing partners, teleporting to attackers, forced chunk loads or freight loot.

A real death interrupts the affected journey and records the member's logical identity. Freight remains in saved ledgers and existing safe cancellation/return claims. Full inventories keep uncollected claims. No success bonus is paid for destroyed work. Natural death has the same journey/replacement effect without personal punishment. At the next safe regular departure, a dead role receives a new identity, generation and full name; living members keep theirs. Unload, visual mode changes, ferry projection and cleanup do not count as deaths.

## Configuration and testing

See [Configuration](configuration.md) for the three independent social options and [Admin commands](commands-and-admin.md) for inspect/set/pardon and nonpersistent preview modes. Unknown or damaged optional reputation data is preserved read-only; normal quests remain available. Both client and server need the matching new2.5 packet contract.

This is an **Unreleased26.2 feature**. Automated state/codec/inventory tests and a separate headless Mixin smoke test are available. Actual Minecraft UI, combat/navigation, two-player play, old-world migration and protection-mod compatibility require the final manual acceptance pass; they are not claimed tested.

## Freight returns and persistence boundaries

Cancelled Regional Dispatches return only what fits in the ordinary36 inventory slots. Unclaimed quantities stay saved under the same assignment and can be reclaimed later; no freight is dropped into the world because inventory is full. Partial claims survive normal save/reload.

Player inventories and mod SavedData have separate storage files. These mechanisms do not promise an atomic transaction across arbitrary hard crashes or inconsistent external world backups. Preserve complete world backups rather than mixing player and world-data snapshots.
