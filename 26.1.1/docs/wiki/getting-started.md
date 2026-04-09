# Getting Started

## Requirements

- Minecraft `26.1.1`
- Fabric Loader `0.18.6+`
- Fabric API `0.145.3+26.1.1`
- Java `25`

## Installation

1. Install Fabric Loader for Minecraft `26.1.1`.
2. Put `village-quest-<version>-mc26.1.1.jar` into your `mods` folder.
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

On this line, the old direct roots were removed. Use `/vq ...` or `/villagequest ...` for all player commands.

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
  After each full `4`-chapter story arc, the next story appears only after a real `3 hour` cooldown.
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

## Reset Times

- daily reset: `06:00 Europe/Berlin`
- weekly reset: Monday `06:00 Europe/Berlin`
- story follow-up cooldown after a completed arc: real `3 hours`
