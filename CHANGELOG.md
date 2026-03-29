# Changelog

Hotfix-heavy polish phases are grouped into version ranges to keep this file readable. Major content and larger technical changes keep their own entries.

## 1.20.0 - Village Stories Foundation And Progression Clarity Rework

Release date: 2026-03-29

### Village Stories Foundation

- Added the first persistent `Story` category to the Questmaster alongside the existing daily, weekly, and special layers.
- Added the first multi-step story arc `The Failing Harvest` with `4` sequential chapters that track real harvest, hive, oven-output, and villager-trade actions.
- Expanded the story system with `4` more multi-step arcs:
  - `The Silent Forge`
  - `Market Road Troubles`
  - `Restless Pens`
  - `Night Bells`
- The new story arcs now unlock sequentially instead of dumping every arc on the player at once, keeping the `Story` tab readable while still building long-form progression.
- Added the `Restless Pens` animals story arc with breeding, wool turn-ins, villager consultation, and a final shared-bell moment that restores the village pens and unlocks the `Pasture Charter`.
- `The Silent Forge` now includes deeper forge restoration turn-ins, rare ore mining, and a finale that requires buying any villager `Sharpness` book and binding it onto a `Diamond Sword` at an anvil before turning the blade in.
- `Market Road Troubles` now leans on earned emeralds, real paperwork turn-ins, a larger villager-trade push, and a final `speak with 5 employed villagers` chapter to make the market recovery feel more social and less checklist-driven.
- `Night Bells` now ends with a full raid-success requirement rather than a generic bell interaction, tracked via a fresh post-acceptance raid victory state.
- Added the first `Village Projects` journal layer with `Village Ledger` as the foundation project and `Apiary Charter` as the first unlockable permanent upgrade.
- Completing `The Failing Harvest` now unlocks `Apiary Charter`, which grants `+5` bonus `Farming` reputation on future farming reputation rewards.
- Added `Forge Charter`, `Market Charter`, `Pasture Charter`, and `Watch Bell` as new permanent story rewards with distinct long-term effects tied to their part of village life.
- Added a second `Trade` daily, `Market Rounds`, built around visiting employed villagers and making a small real trade, so the `Trade` reputation curve is less bottlenecked.
- Story chapters now send a one-time `ready to turn in` message once all objectives for the active chapter are complete, so players know to return to the Questmaster.
- Reworked the early Questmaster onboarding so `Story` stays hidden until the player finishes their first normal daily quest, while `Special` only appears after the first real reputation gain.
- The `Special` category no longer dumps every future relic quest on new players at once; unreached relic quests and the `Magic Shard` trial now stay hidden until they are actually relevant.
- Relic quests are now tied to both their matching story arc and the old reputation threshold, so special commissions appear as earned follow-ups to village work instead of isolated reputation gates.
- Added lore-style chat hints when story work, special commissions, or the shard trial first become relevant.
- Village projects now also surface as a lightweight memory layer in future reward text, explicitly calling out when a charter or bell is strengthening the reputation payout you just earned.
- Extended persistent player data, quest-state saving, the journal, the quest tracker HUD, and the Questmaster UI to include the larger story roster and the new village-project state.
- Added admin helpers to inspect/reset story progress and to show/unlock/lock village projects for testing.

### Progression Clarity Rework

- Reworked the current unreleased local batch to make the mod clearer and stronger instead of merely bigger.
- The `Questmaster` is now more clearly the center of village-core progression:
  - normal `Daily` and `Weekly` rotation no longer includes combat quests
  - `Story` now surfaces only the active arc or next available arc instead of listing the whole archive at once
  - daily reward previews now show reputation and project-aware follow-up effects more clearly
- Reframed the old `Monster Hunting` lane into `Roadside Watch` / `Wegewacht` so monster work reads more like village protection and road safety than a generic combat track.
- Kept `Night Bells` as the late protective side arc, but moved ordinary combat out of the main routine village loop so it no longer competes with the farming / craft / trade / pasture identity of the mod.
- Reworked the `Pilgrim` away from feeling like a second Questmaster:
  - the weaker overlapping non-combat road contracts were removed again
  - the remaining pilgrim work is now limited to the `Wayfinder` roadmark unlock and late dangerous road-watch work tied to `Watch Bell`
  - the dangerous pilgrim side contracts now center on roadside safety and night danger instead of duplicating ordinary village labor
- Reworked `Village Projects` so they are less same-shaped and less purely number-driven:
  - `Apiary Charter` keeps the `+5 Farming Reputation` identity
  - `Forge Charter` now grants `+2 levels` on future crafting rewards
  - `Market Charter` now functions as the trust unlock that lets the pilgrim later teach old roadmarks onto the `Wayfinder's Compass`
  - `Pasture Charter` now grants `+2 Silvermarks` on future animals rewards
  - `Watch Bell` now grants `+5 Roadside Watch Reputation` and unlocks dangerous pilgrim road work
- Strengthened project memory and distinction in reward text, journal wording, and follow-up flavor so the player is more likely to remember the village change rather than just a bonus number.
- Reworked several story and pilgrim text/objective framings to feel less like MMO-style checklist chains and more like natural responses to village problems, especially around:
  - `Silent Forge`
  - `Market Road Troubles`
  - `Restless Pens`
  - `Night Bells`
  - the remaining pilgrim contracts
- Reworked quest rewards away from raw XP points into direct level rewards so high-level players still feel meaningful progress:
  - dailies now scale by difficulty in whole levels
  - weeklies now pay larger level chunks based on overall effort
  - story chapters now pay clearer step-up level rewards with stronger finales
  - pilgrim contracts now pay level rewards instead of raw XP
- `Forge Charter` was updated to match that reward rework and now adds `+2 levels` to future crafting rewards instead of a flat XP bonus.
- Doubled all newly introduced level rewards after the first local balancing pass so they stay meaningful in longer-running worlds:
  - dailies now pay `2 / 4 / 6` levels by difficulty
  - weeklies now pay `14 / 16 / 18` levels by effort tier
  - story chapters now pay `4 / 6 / 8 / 10` levels
  - pilgrim contracts now pay `4 / 6` levels depending on danger and effort
- Accepting a quest now automatically enables the permanent quest tracker and sends a chat hint explaining that `/questtracker` can toggle it back off again.
- Reworked the `Wayfinder's Compass` into a staged relic instead of an all-at-once unlock:
  - the base relic now always starts with `Home`
  - `Apiary Charter` now unlocks a follow-up `Questmaster` calibration step instead of instantly adding biome modes
  - biome routes are now earned by taking the compass to `Desert`, `Jungle`, `Frozen Peaks`, and `Mushroom Fields` and right-clicking there to inscribe each field bearing
  - structure routes are now earned later through the pilgrim contract `Roadmarks for the Compass`
- Reworked `Roadmarks for the Compass` away from a paper-and-ink turn-in into a real roadside-imprinting contract:
  - the player now carries the compass to a `Village`, `Pillager Outpost`, `Woodland Mansion`, and `Swamp Hut`
  - each roadmark is inscribed on-site with a right-click on the compass
- Added short contextual chat hints while carrying the compass so players are told when a valid biome or structure can currently be inscribed.
- `Home` on the `Wayfinder's Compass` now asks for a second confirming right-click within `10` seconds before teleporting, so accidental taps no longer trigger an unwanted return.
- `Apiary Charter` now carries a stronger second after-effect besides its farming-reputation bonus: it gives the Questmaster enough trusted landmarks to calibrate the `Wayfinder's Compass` against the land.
- Added dedicated structure-tag resources for the pilgrim roadmark contract so village/outpost/mansion/swamp-hut imprint checks stay explicit and maintainable.
- The pilgrim now spends the first `15` seconds after spawning trying to path toward the player they arrived for, then falls back to the normal idle wandering behavior.
- The questmaster now also spends the first `5` seconds after spawning trying to path toward the player they arrived for, then falls back to the normal idle wandering behavior.
- Both the pilgrim and the questmaster now visibly carry a torch by night whenever they are not currently in sword-drawn self-defense.
- Their night-torch presentation now also uses a raised blocking-style arm pose on the client, so the torch reads more like active light-carrying than a loose item hanging at the side.
- `Night Bells` no longer sits in the visible `Questmaster` story lane; the main village-core arc chain now ends after `Restless Pens`, and `Watch Bell` is derived automatically once all four village-core stories are complete.
- Dangerous `Roadside Watch` work now begins only after that derived `Watch Bell` unlock and stays fully with the `Pilgrim` instead of re-entering the `Questmaster` story flow.
- The pilgrim roadside lane was simplified again so it reads more like a late optional daily:
  - `Roadmarks for the Compass` is now a one-time special pilgrim contract and, when available, temporarily replaces the ordinary combat rumor instead of sitting beside it
  - once `Watch Bell` is unlocked and `Roadmarks` is no longer pending, the pilgrim now offers exactly `1` dangerous roadside rumor per day instead of a two-choice combat menu
- Simplified several quest tracker lines that had become too system-visible or redundant:
  - `The Silent Forge` tool chapter now shows one clear per-item completion state instead of separate crafted and carried sub-states
  - `The Silent Forge` finale now breaks the blade proof into three explicit steps: `Sharpness Book Bought`, `Diamond Sword Sharpened`, and `Finished Blade Carried`
  - short inventory checks such as honey, ledger, hay, wool, and the compass pickaxe now use direct item labels instead of `On Hand` / `im Inventar`
- Added a quieter in-world clarity note to the journal flow page and to the relevant forge/compass quest text explaining that ledgers asking for forged or crafted proof only accept fresh work made during the active quest, not old stock pulled from a chest.
- The `Wayfinder's Compass` relic reward was tuned down from `10` to `3` `Netherite Ingots`.
- Corrected the German journal entry for the `Wayfinder's Compass` so it now matches the actual staged flow:
  - first `Home`
  - then `Field Bearings` at the `Questmaster`
  - then `Roadmarks for the Compass` at the `Pilgrim`
- Shortened and tightened the journal relic text for the `Wayfinder's Compass` in both languages so it reads more like a concise in-world note and less like a technical manual.
- Expanded the `Provisions Satchel` shop text so it now explicitly tells players it always carries `32 Golden Carrots` plus one or two rotating bonus finds such as ingots, emeralds, diamonds, torches, cooked beef, coal, and on rare days a golden apple.
- Reworked the pilgrim rumor-contract selector away from small icon chips over the gold arrow:
  - multiple contract choices now render as full-width text buttons in the upper rumor panel
  - the lower rumor body can now start higher and read more cleanly without the old icon overlap
- Shop offers in the pilgrim detail panel now actually render their own description text in the lower body area instead of burning most of the space on the old generic wallet hint.
- Shortened the `Provisions Satchel` description again so it fits cleanly inside the pilgrim shop detail box without truncating.
- The pilgrim shop detail body now has its own scroll field for longer offer text, and the `Provisions Satchel` wording now starts with `Contains ...` / `Enthaelt ...`.
- The rumor selector buttons were enlarged again to better fill the upper panel, and the lower rumor scroll body was moved slightly downward so it no longer starts too close to the divider.
- The pilgrim `Rumor` view was then corrected back onto the same base board layout as the normal trade tab after the separate quest-board experiment broke the geometry:
  - the `Rumor` tab now sits on the regular pilgrim board again instead of a mismatched overlay
  - the upper contract buttons stay enlarged inside the normal right-hand panel
  - the lower rumor scroll body keeps the tighter text spacing without switching to a second board texture
- The rumor geometry was then tightened again on that shared board:
  - the `Rumor` tab sits a touch further left for cleaner alignment
  - the stacked contract choices sit slightly higher and closer together
  - the lower rumor text body starts slightly lower so the status and first paragraph stop grazing the upper edge
- The pilgrim UI then got a cleaner split between shop and rumor presentation:
  - the normal shop detail text no longer has the stray horizontal divider line above it
  - shop description text now starts a little higher again after that line removal
  - rumor now swaps to a dedicated board texture derived from the normal pilgrim layout, so the old gold arrow is gone without changing the overall board geometry
  - the `Rumor` tab was nudged almost flush with the right edge, and the two quest buttons were lifted a little higher again
- The pilgrim shop detail text was then nudged a little higher again so the lower lines stop clipping above the buy button.
- The pilgrim rumor panel now also uses the same top selection-button presentation even when only a single rumor contract is available, so every pilgrim quest renders with a clear top button instead of dropping into a special no-button layout.
- The `Rumor` tab itself was nudged one more pixel to the right for cleaner alignment against the board edge.
- The journal active-page empty-state hint now points at the built-in `Questmaster` button below instead of telling the player to type `/questmaster`.
- Weekly quests now mirror dailies with cancel support:
  - the journal shows a red `X` for the active weekly
  - the Questmaster board now exposes `Cancel` for active weeklies
  - canceling a weekly clears only the accepted state and weekly progress, but keeps the same weekly contract for that week instead of rerolling
- The pilgrim rumor selection text inside the upper quest buttons was nudged again:
  - slightly larger
  - horizontally cleaner
  - vertically more centered inside the button
- Added a proper multi-page repo wiki under `docs/wiki/` for the current local development branch:
  - `Wiki Home`
  - `Getting Started`
  - `Core Progression`
  - `Stories and Village Projects`
  - `Relics, Wayfinder, and Magic Shards`
  - `Pilgrim and Roadside Watch`
  - `Quest Reference`
  - `Commands and Admin Tools`
- Linked that wiki from the root `README.md` so the repo now has a discoverable central documentation entry point beyond the release notes and memory files.
- The `Wayfinder's Compass` unlock steps were tightened without shrinking the finished relic:
  - `Field Bearings` and `Roadmarks for the Compass` still only ask for `4` calibration targets each
  - but once each step is completed, the compass now unlocks the broader old mode roster again instead of staying limited to those four quest targets
  - the finished biome roster once again includes:
    - `Forest`
    - `Plains`
    - `Taiga`
    - `Snowy Plains`
    - `Mountain`
    - `Meadow`
    - `Cherry Grove`
    - `Jungle`
    - `Desert`
    - `Frozen Peaks`
    - `Savanna`
    - `Swamp`
    - `Badlands`
    - `Beach`
    - `River`
    - `Ocean`
    - `Mushroom Fields`
  - the finished structure roster once again includes:
    - `Village`
    - `Pillager Outpost`
    - `Stronghold`
    - `Trial Chamber`
    - `Woodland Mansion`
    - `Ocean Monument`
    - `Jungle Temple`
    - `Swamp Hut`
    - `Mineshaft`
    - `Shipwreck`
    - `Ruined Portal`
  - `Field Bearings` is now also visibly blocked until the player is actually carrying the `Wayfinder's Compass`
- Expanded the `Pilgrim` roadside combat lane into a fuller late-game rumor rotation:
  - existing overworld rumors were simplified into single-mob protection jobs
  - added new dangerous-road rumors for:
    - `Tracks in the Dark`
    - `Fangs by the Hedgerow`
    - `Ash on the Pass`
    - `Smoke over Blackstone`
    - `Stillness beyond the Gate`
  - overworld rumors stay lighter, while `Nether` and especially `End` roadside jobs now pay clearly higher money, `Roadside Watch`, and level rewards
- Pilgrim contract presentation now also uses lane colors for faster recognition:
  - overworld roadside work renders in green
  - nether roadside work renders in red
  - end roadside work renders in purple
  - the one-time `Roadmarks for the Compass` contract keeps its own blue-green accent
- Build validation for the full local rework batch succeeded with Java 21:
  - `.\gradlew.bat build`

## 1.19.5 - Quest Progress Hotfixes

- Hotfixed villager-trade quest progress so completed villager deals again count reliably even when the trade result is quick-moved with `Shift`-click.
- Emerald-income objectives tied to villager trades now also read the actual completed `TradeOffer`, so earned emerald payouts are credited reliably again.
- Audited the remaining quest hooks for the same progress-loss pattern and added a dedicated anvil quick-move hook so the shard relic's anvil-enchant step also survives `Shift`-click output pickup.
- Tightened the `Pet Collar` daily so it only counts real collar recolors on the player's own tamed `Wolf` or `Cat`, ignoring unrelated tameables and same-color no-op dye uses.

## 1.19.4 - NPC Self-Defense Update

- Added a short self-defense mode for the `Pilgrim` and `Questmaster` when they are attacked by players, including warning lines, retaliation lines, and temporary sword draw behavior.
- The `Pilgrim` now retaliates earlier than the `Questmaster`, while both still remain killable and only turn hostile toward the attacking player.
- Added visible held-weapon rendering and a reliable close-range hit flow so retaliation now actually draws a sword and lands hits instead of only body-checking.
- Expanded the warning/retaliation line pool and slowed line repetition down so the NPC chatter stays more varied and less spammy.
- Tuned the self-defense balance down so both NPCs chase and swing more slowly and deal less damage.
- The `Questmaster` now closes active interactions before defending and blocks fresh conversations while in combat.
- If a player kills the `Questmaster`, only that player is locked out from `/questmaster` for `5` minutes.
- Added persistent per-player storage plus summon feedback text for that personal `Questmaster` cooldown.

## 1.19.3 - Furnace Quest Hook Fixes

- Fixed furnace-based quest progress so taking cooked items from the output slot now counts reliably again.
- Added a second furnace screen-handler hook so output collected via `Shift`-click / quick-move also grants quest progress.
- This restores reliable progress for furnace-based objectives such as `Bakehouse Stock`, `River Meal`, and `Smith Smelting`.
- Added explicit turn-in consumption for crafted/cooked quest items so they are actually pulled from the inventory on completion instead of only counting progress.
- This now covers `Bakehouse Stock`, `Wheat Harvest`, `River Meal`, `Smith Smelting`, `Harvest for the Village`, and `Smith Week`.
- The `Surveyor's Compass` relic quest continues to consume the plain crafted `Netherite Pickaxe` on claim.
- Reworked the `Provisions Satchel` away from plain bread-and-torches into a better random travel bundle with guaranteed `Golden Carrots`, a low-chance `Golden Apple`, and weighted bonus loot rolls such as ingots, emeralds, diamonds, coal, torches, and cooked food.

## 1.19.2 - Inventory Compat Follow-Up

- The journal bookmark tab now uses the right-side inventory edge as its normal placement again.
- Added a compatibility fallback for `Status Effect Bars`: when that mod is loaded and the player currently has active status effects, the journal tab automatically moves to the upper-right bookmark position instead of fighting the effect panel.
- Kept the animated hidden-behind-inventory presentation so the tab only peeks out at rest and slides outward on hover.
- Fine-tuned the upper-right fallback tab position slightly downward so it sits more naturally against the vanilla inventory frame.

## 1.19.1 - Questmaster And Inventory Polish

- The `Questmaster` summon logic now searches around the player's current height first, so the NPC can appear in underground bases and caves instead of only surfacing above ground.
- Fixed breeding progress so quests that ask for `2` animals bred now count real breeding pairs once, instead of double-counting both parents during the same pairing.
- Reworked the inventory journal access into an animated bookmark tab on the right edge of the vanilla inventory UI.
- The bookmark tab now sits behind the inventory panel so only its edge peeks out at rest, then slides outward on hover before opening the journal on click.
- Improved compatibility with `Better Inventory` by moving the journal access away from the old top-right floating button area.

## 1.19.0 - Reputation Expansion Batch

- Added the fifth reputation track `Monster Hunting` and extended the journal and reputation views to show it cleanly even when no unlocks are tied to a track yet.
- Added `4` monster-hunting daily quests to the main pool:
  - `Zombie Cull`
  - `Skeleton Patrol`
  - `Spider Sweep`
  - `Creeper Watch`
- Wired monster kills into the daily system while keeping the existing weekly combat hooks active.
- Added the missing farming relic questline `Apiarist's Smoker` at `200` `Farming` reputation.
- Added the missing crafting relic questline `Surveyor's Compass` at `200` `Crafting` reputation.
- Raised all reputation-gated relic unlock thresholds from `120` to `200`, including `Merchant's Seal` and `Shepherd's Flute`.
- `Apiarist's Smoker` now tracks real hive harvesting progress and rewards a relic that can once per in-game day ready a hive for harvest and calm nearby bees.
- `Surveyor's Compass` now requires mining `16` redstone ore, crafting a `Netherite Pickaxe` after accepting the quest, and turning in exactly one plain unenchanted `Netherite Pickaxe`.
- The crafting relic quest never consumes an enchanted pickaxe and additionally rewards `10 Netherite Ingots`.
- Added intuitive non-command `Surveyor's Compass` behavior:
  - sneak-use cycles target modes
  - normal use binds the compass toward nearby villages, trial chambers, or selected biomes
- Added the two new relic items, their custom textures/models, Questmaster entries, `/setquest` aliases, and full German/English localization.
- Follow-up `1.19.0` hotfixes:
  - the `Special` quest list in the Questmaster now scrolls when the entry column grows taller than the panel
  - the journal reputation view now spans two pages so `Monster Hunting` is visible instead of being cut off at the bottom
  - `Apiarist's Smoker` now supports up to `10` hive uses per in-game day and reports the remaining daily uses in feedback text
  - `Surveyor's Compass` was reworked in presentation and behavior into a home-focused `Wayfinder's Compass`
  - the compass keeps its dedicated `Home` mode and roughly `30` minute return cooldown, but now again supports a broad list of biome and structure target modes
  - the Pilgrim unlock curve is now staged across the full reputation ladder, including new `120` unlocks for `Farming`, `Animals`, and `Village Trade`
  - `Monster Hunting` now has its own full Pilgrim unlock path at `25`, `60`, `120`, and `200`
  - the journal now adds a relic page that only appears while the player is currently carrying at least one special/relic item
  - relic and special item names now use a consistent gold presentation across item names and Questmaster reward lines
  - the Wayfinder compass lore/reward description was shortened so it fits more reliably in the UI info areas

## 1.18.0 - Weekly Quest System

- Added the full weekly-quest module with persistent per-player state, weekly choice rotation, claim flow, save data, and Questmaster session handling.
- Added `7` real weekly quests:
  - `Harvest for the Village`
  - `Bakehouse Stock`
  - `Smith Week`
  - `Stall and Pasture`
  - `Market Week`
  - `Night Watch`
  - `Road Warden`
- The two combat weeklies now provide the heavier reward tier requested for monster hunting: `2 Crowns` and `2 Magic Shards`.
- Integrated `Weekly` fully into the Questmaster UI, journal active-quest page, tracker HUD, and Questmaster reset footer timer.
- Added weekly admin helpers:
  - `/questadmin resetweekly [player]`
  - `/questadmin nextweekly [player]`
  - `/questadmin completeweekly [player]`
- Moved the shared reset timing to the new `06:00 Europe/Berlin` boundary, and weekly rotation now resets on Monday `06:00` Berlin time.

## 1.17.5-1.17.2 - Questmaster And Pilgrim UI Hotfixes

- Added the Questmaster daily reset timer and then polished its footer position, clipping, spacing, and header fit so it reads cleanly inside the new board.
- Fixed Questmaster detail scrolling and clipping for long special-quest text, improved header text wrapping, and cleaned up relic status placement for entries like `Whispers of the Shards`, `Merchant's Seal`, and `Shepherd's Flute`.
- Added the first custom-board Pilgrim UI pass based on `Custom Pictures/pilgrim ui.png` and aligned the existing wallet strip, trade detail area, price line, buy button, footer text, and timer to the new template.
- Removed the extra code-drawn trade arrow so the template's built-in arrow remains the only directional indicator in the Pilgrim trade view.
- Continued the Pilgrim template polish by raising and shrinking the footer text, moving the buy button deeper into the intended lower slot, and lifting the right-side price line into a cleaner position inside the upper trade box.
- Replaced the extra drawn Pilgrim buy-button frame with a direct `Buy` label rendered on the template slot itself, closer to the Questmaster UI style.
- Nudged the Pilgrim footer hint and despawn timer upward again so both texts sit more cleanly inside the lower parchment field.
- Lowered the Pilgrim footer hint and despawn timer again after review so they sit deeper in the bottom field instead of floating too high above it.
- Lowered the Pilgrim footer hint and despawn timer a final step further after screenshot review so both lines sit noticeably deeper in the footer field.
- Nudged the Pilgrim footer hint and despawn timer slightly back upward after that deeper pass so they sit more naturally in the footer strip.

## 1.17.1 - Guaranteed First Magic Shard

- The first quest a player ever completes now guarantees `1` `Magic Shard`.
- After that first reward, the normal `10%` shard drop chance resumes for regular daily quest completions.
- Added a persistent per-player flag plus a migration heuristic so established saves do not wrongly get a fresh starter shard.

## 1.17.0 - Questmaster UI V2 And Decline Removal

- Switched the Questmaster to the `Quest UI v2` board art from `Custom Pictures`.
- Reworked the footer into a single adaptive action button instead of separate `Accept` and `Decline` buttons.
- Removed the decline flow from active gameplay and commands, including `/dailyquest decline`.
- Cancelling an active daily now returns it to an available state instead of locking the player out for the rest of the day.

## 1.16.9-1.16.1 - Questmaster UI Polish Cycle

- Repeatedly refined the first Questmaster UI with smaller layout, cleaner column balance, improved category slots, safer click hitboxes, and better overall spacing.
- Added scroll support, tooltip support for truncated quest names, proper detail clipping, and clearer header/status separation.
- Imported and stabilized the custom Questmaster board art, including fixes for oversized/cropped texture rendering, centering, scaling, and template alignment.
- Prevented the Questmaster from despawning while a player still has the UI open.

## 1.16.0 - Questmaster UI Foundation

- Replaced the Questmaster's old chat-only interaction with a real custom quest window.
- Added the long-term `Daily`, `Weekly`, and `Special` category structure.
- Made `Daily` and `Special` fully usable through the UI, including accept, claim, cancel, shard bonus handling, and live state refresh.
- Added the networking/session plumbing needed to open, update, act on, and close Questmaster UI sessions correctly.

## 1.15.7-1.15.1 - Merchant's Seal Stabilization

- Stabilized `Merchant's Seal` after the initial relic rollout by fixing double-chat callbacks, broken interaction paths, and no-op use cases.
- Restored reliable sneak-use rerolls on the Pilgrim and later extended the seal to the vanilla wandering trader as well.
- Fixed the wandering-trader accessor/mixin crash so the client starts cleanly while keeping the new reroll support intact.

## 1.15.0 - Relic Questlines Pack 1

- Added two reputation-gated special relic questlines: `Merchant's Seal` and `Shepherd's Flute`.
- Added both new relic reward items with local custom textures and persistent per-player quest-state saving.
- Added a shared special-quest routing layer so the Questmaster, journal, tracker, and quest flow can support multiple relic questlines.
- `Merchant's Seal` is earned through villager trading, emerald income, and one Pilgrim purchase; it rerolls merchant wares once per day.
- `Shepherd's Flute` is earned through breeding, shearing, and wool gathering; it pulls nearby animals toward the player for a short time.

## 1.14.0 - Reputation Unlocks And Pilgrim Progression

- Added real reputation ranks and next-unlock hints on top of the new reputation system.
- Made the Pilgrim's offer pool depend on player reputation and block purchases of still-locked wares.
- Added the first real unlock paths for `Farming`, `Animals`, `Crafting`, and `Village Trade`.
- Added admin-only reputation debug commands for testing and balance work.

## 1.13.0 - Village Reputation Foundation

- Added four persistent reputation tracks: `Farming`, `Crafting`, `Animals`, and `Village Trade`.
- Daily quests now award reputation in addition to wallet currency and XP.
- Added `/reputation` and a dedicated journal page for reputation display.

## 1.12.9 - Leather Daily Removal

- Removed the leather daily from the active quest pool and from `/setquest`.
- Removed its active localized quest text and gameplay hooks while leaving the old peace-armor recipe content untouched.

## 1.12.8-1.12.2 - Inventory Journal Button Rollout

- Added a direct Journal button to the vanilla inventory screen.
- Replaced and tuned the icon art multiple times using assets from `Custom Pictures`.
- Fixed the associated crashes and rendering bugs by moving to the correct screen hooks and accessor mixins.
- Final result: a visible, clickable inventory Journal shortcut with stable position and texture sampling.

## 1.12.1 - Journal Player Command Overhaul

- Reordered the journal so active quests are shown first.
- Replaced the older generic help content with a fuller player command reference.
- Added clearer coverage for `/questmaster`, `/wallet`, `/journal`, `/dailyquest accept`, and `/questtracker`.

## 1.12.0 - Quest Progress Feedback And Tracker

- Added direct quest progress feedback in the action bar.
- Added milestone chat/sound feedback for larger quest steps.
- Added the optional persistent quest tracker HUD with `/questtracker`, `/questtracker on`, and `/questtracker off`.

## 1.11.7-1.11.5 - Starreach Ring Texture Iteration

- Tested multiple texture passes for the `Starreach Ring`, including a cleaned smaller texture and a second source variant from `Custom Pictures`.
- Reverted when the newer processed version looked worse in practice.
- Left the ring asset pipeline simple so future cleanup can happen at the source image level.

## 1.11.4-1.11.1 - Secret Shard Quest Debug And Fix Pass

- Added OP-only debug commands to jump to or teleport to the hidden shard cache for testing.
- Tightened the hidden buried-cache search radius around the treasure-map marker.
- Changed cache generation to a lazy spawn flow so the Questmaster no longer causes heavy lag while binding the cache.
- Fixed secret-quest enchant progress so valid anvil enchantments count, while rename/repair-only anvil uses do not.
- Shortened `Magic Shard` lore in both languages.

## 1.11.0 - Secret Shard Quest And Starreach Ring

- Added the full hidden `10 Magic Shard` questline `Whispers of the Shards`.
- The trial tracks amethyst, potion, enchantment, Ender Pearl, and Blaze Rod objectives before sending the player back to the Questmaster.
- Returning to the Questmaster now gives a real treasure map to a buried, owner-protected relic cache in the Overworld.
- Added `Starreach Ring` as the relic reward; it grants `+2` block interaction range while held in the offhand.
- Extended journal and shard lore so the two shard uses are explained and tracked properly.

## 1.10.2-1.10.1 - Magic Shard Bonus Daily And Daily Reopen

- Added the first real `Magic Shard` use: a second same-day bonus daily offered through the Questmaster after the normal daily is completed.
- Spending `1` shard rolls exactly one extra daily for that day, and that bonus daily does not drop another shard.
- Adjusted the old decline behavior so players could reopen the same daily later instead of being locked out for the whole day.

## 1.10.0 - Questmaster NPC Replaces Quest Block

- Replaced the old quest block with a summonable `Questmaster` NPC with its own skin and renderer.
- Added `/questmaster` for all players.
- The Questmaster now handles daily offers, progress, completion checks, and interaction flow; only one can exist within roughly `20` blocks.
- Removed old quest block assets, recipes, loot, and related release references.

## 1.9.15-1.9.14 - Magic Shard Introduction

- Added the `Magic Shard` item with custom art, model, lore, and a `10%` daily-completion drop chance.
- Added the missing modern item-definition file so the shard no longer appears as a purple missing-model block.

## 1.9.13-1.9.10 - Release Cleanup And Command Pruning

- Removed extra item payouts from daily quests so regular dailies now pay wallet currency and XP only.
- Pruned release-facing commands and documentation to match the slimmer production command set.
- Cleaned release metadata, README/checklist content, and outdated Fabric example-template leftovers.

## 1.9.9-1.9.1 - Pilgrim Economy And Presentation Polish

- Added the Pilgrim departure timer and a natural respawn cooldown; later tuned natural spawn odds.
- Polished the Pilgrim wallet strip, slot backgrounds, coin scales, button text alignment, and other UI details.
- Fixed placed-painting hover-name issues without breaking client startup.

## 1.9.0 - Painting Size Rebalance And Two-Currency Overhaul

- Reworked the active wallet economy into `Silvermark` and `Crown` with a `10 Silvermarks = 1 Crown` ratio.
- Added new local coin art from `Custom Pictures`.
- Rebuilt and repriced the Pilgrim paintings around the new economy, including `Happy Doge`.

## 1.8.2-1.8.0 - Painting Expansion And Currency Naming Cleanup

- Added a larger batch of custom Pilgrim paintings with German and English titles/descriptions.
- Rebalanced painting sizes and prices after the first pass.
- Cleaned up currency naming so player-facing names settled toward `Copper Penny`, `Iron Mark`, and `Gold Crown`, while old aliases stayed usable where needed.

## 1.7.8-1.7.1 - Pilgrim UI And Trade Polish

- Iteratively refined the Pilgrim trade screen with better text fit, hover cards, stronger selection states, and cleaner layout.
- Fixed the Pilgrim to pause properly during trade and use the intended custom skin.
- Moved the shop away from a vanilla-feeling trader window toward a custom parchment-and-wood market UI.

## 1.7.0 - Pilgrim Trader

- Added the traveling Pilgrim merchant entity with spawn/despawn behavior and a custom player-skin renderer.
- Added the wallet-based Pilgrim shop flow and the first rotating offer pool.
- Added admin spawn/despawn commands for testing.

## 1.6.2-1.6.0 - Digital Wallet And Journal Basics

- Replaced daily coin-item payouts with a persistent digital wallet stored per player.
- Added `/wallet`, journal balance display, admin wallet helpers, and an early `/shop` framework.
- Fixed journal line wrapping so longer dynamic entries stay inside the page.

## 1.5.10-1.5.8 - Wolkensprung Split

- Built the extraction boundary for the Wolkensprung storyline and then scaffolded it as a separate standalone mod.
- Removed Wolkensprung runtime hooks, entities, commands, persistence, and journal integration from `Village Quest`.
- During the split, daily acceptance/decline routing moved to `/dailyquest accept|decline` to avoid command collisions.

## 1.5.7-1.5.1 - Daily Admin, Balance, And Selection Polish

- Added several admin helpers for daily testing, including status/progress views, force-complete, next-daily simulation, and reset support.
- Added daily difficulty tiers and centralized reward balancing.
- Improved daily selection so the next day avoids repeating the same quest or category when possible.

## 1.5.0 - Daily Wave 2

- Added five more action-based dailies: river meal, autumn harvest, smith smelting, stall breeding, and village trading.
- Added the required furnace, villager-trade, and breeding hooks plus updated aliases, admin text, and localization.

## 1.4.4-1.4.1 - Project Continuity And Cleanup

- Added `MEMORY.md` and `CHANGELOG.md` for restart continuity.
- Cleaned up legacy compatibility registration and model warnings.
- Added defensive translation/tag cleanup during the early 1.21.11 porting phase.

## 1.4.0 - Daily Wave 1

- Added the first five action-based dailies and extended aliases, admin text, and localization around them.

## 1.3.1-1.3.0 - Daily Refactor And Reset Command

- Refactored daily quests into per-quest classes with shared dispatching.
- Added `/questadmin resetdaily` for admin testing.

## 1.2.1-1.2.0 - Compatibility And Localization

- Added legacy item registrations for older worlds/stats.
- Localized the core player-facing quest, journal, and reward flows.

## 1.1.2-1.1.0 - Early Daily-State And Peace-Armor Cleanup

- Localized peace-armor names/lore and cleaned up related translations.
- Restricted `/setquest` to OP/admin and fixed missing internal tag translations.
- Migrated daily progress to a generic key-based state model with save migration support.

## 1.0.5-1.0.1 - Early 1.21.11 Compatibility Fixes

- Fixed recipe tags, ingredient parsing, and result-component issues for older peace-armor content.
- Removed an early missing-sound warning and updated basic metadata/API usage during the first porting pass.

## 1.0.0 - Baseline

- Project brought onto Minecraft `1.21.11`.
- Build verified on Java 21.
