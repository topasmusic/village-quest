# Getting Started

## Requirements

- Minecraft `26.2`
- Fabric Loader `0.19.3+`
- Fabric API `0.153.0+26.2`
- Java `25`

## Installation

1. Install Fabric Loader for Minecraft `26.2`.
2. Put `village-quest-<version>-mc26.2.jar` into your `mods` folder.
3. Put the matching Fabric API jar into the same `mods` folder.
4. Start the game with Java `25`.

## First Useful Commands

- `/vq questmaster`
  Summons the `Questmaster` near you.
- `/vq journal`
  Opens your journal.
- `/vq wallet`
  Shows your current wallet balance.
- `/vq reputation`
  Shows your current reputation.
- `/vq questtracker`
  Toggles the permanent quest tracker.
- `/vq questtracker on`
  Forces the tracker on.
- `/vq questtracker off`
  Forces the tracker off.

## Your First Session

1. Use `/vq questmaster`.
2. Accept a normal `Daily` quest.
3. Finish and turn in that daily.
4. `Story` now unlocks in the Questmaster.
5. Keep completing quests until you earn your first real reputation gain.
6. `Special` now unlocks in the Questmaster.
7. Use `/vq journal` regularly to review active quests, projects, relic notes, and progression.

## What Unlocks When

- `Story`
  Unlocks after the first normal daily is completed.
  After each full `4`-chapter story arc, the next story appears only after a real `1 hour` cooldown.
- `Shadows on the Trade Road`
  Appears later as a locked late-story entry after `Watch Bell`.
  Unlocks after `3` completed `Pilgrim` combat rumors.
  Chapter `2` also requires that you already own the `Wayfinder's Compass`.
- `Special`
  Unlocks after the first real reputation gain.
- `Pilgrim` road combat
  Unlocks late through `Watch Bell`.

## Currency

`Village Quest` uses a digital wallet.

- `10 Silvermarks = 1 Crown`
- quest rewards are paid into the wallet
- pilgrim purchases are paid from the wallet

## Quest Tracker

- Accepting any quest automatically enables the permanent quest tracker.
- A chat line reminds you that `/vq questtracker` can turn it off again.

## Journal

The first time you open the inventory, an animated pointer introduces the Journal bookmark. The hint remains until you click it, is shown once again after this onboarding update for existing installations, and follows the compact `J` fallback when an inventory-screen mod needs the compatibility layout.

The journal is your in-game record of:

- active quests
- commands
- quest flow
- reputation
- village projects
- carried relics
- overall summary

On the first page:

- if you have no active quest, the journal now points you to the built-in `Questmaster` button below the book
- if you do have an active `Daily` or `Weekly`, the red `X` lets you cancel it

The primary Village Quest boards automatically keep a similar centered footprint when switching between a maximized window and fullscreen. Their buttons, scrolling areas, tooltips, and draggable route map stay aligned with the responsive presentation.

## Reset Times

- daily reset: `06:00 Europe/Berlin`
- weekly reset: Monday `06:00 Europe/Berlin`
- story follow-up cooldown after a completed arc: real `1 hour`
- while the Questmaster is gathering the next Story lead, the Story tab stays selectable with a `0` badge and shows the remaining cooldown in its footer

## Questmaster Notices And Sounds

- The Questmaster sends one chat notice when a new Daily, Weekly, or Story assignment becomes available.
- A concrete offer is announced only once. Rejoining, reopening the board, or leaving the server running does not repeat it.
- Quest acceptance has a soft cue, routine progress stays quiet, completed objectives and stage transitions are clearer, and the level-up sound remains reserved for the final reward.

## Quest Completion And Item Turn-ins

- Pure action-only Daily and Weekly quests complete automatically when their final objective is met.
- Daily and Weekly quests that consume delivery items remain ready until `Claim` is pressed at the `Questmaster`; progress events never remove those items.
- Story chapters and Special commissions always return to the `Questmaster` for their final hand-in.
- Required delivery items must still be available when `Claim` is pressed and are consumed by the quest.
- A multi-item delivery is checked as one complete bundle before any item is removed.
- Farming deliveries use the actual Wheat or Potato item yield from a mature crop rather than awarding one point per broken crop block.

Quest wording tells you which rule applies:

| Wording | What counts |
|---|---|
| `Mine`, `harvest`, `craft`, `smelt`, `breed`, or another action | Perform that action after accepting the quest. Pre-existing stock does not advance the action objective. |
| `Bring`, `deliver`, `provide`, or `turn in` | Carry the requested items when claiming. Unless the quest also names a fresh action, stored, traded, or otherwise obtained goods are valid. |
| An action followed by a delivery | First complete the tracked action after accepting, then carry the complete delivery bundle to the Questmaster. |

- Minecraft merges identical item stacks, so Village Quest records fresh work as separate quest progress instead of trying to mark individual items forever.
- In a hybrid objective, pre-existing stock cannot replace the required fresh action, but matching items currently carried may form part of the final delivery bundle.
- Only items in participating player inventories count. Items still inside chests or shulker boxes must be taken out before claiming.
- Supported party hand-ins may pool the participating inventories, but the whole bundle must still be present before anything is consumed.
