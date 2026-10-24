# Travelling NPCs — 2.6

Personal adventures with rare inn guests. See [Markets](kingdom-market.md) for visit conditions.

Simon Windbraid, Lutte Farshore and Marius offer personal optional quests during their ordinary inn visits. Speak to the actual guest and click the chat action to accept or hand in. Journal entries show progress and rewards; the quest master does not replace the guest. Progress remains when the guest leaves, when you disconnect, and after a restart. Party members keep independent progress. Raphael remains a visitor without his own quest line.

## Simon

| Quest | Task after acceptance | Reward |
| --- | --- | --- |
| Gifts of the Wild | Hand in 8 dandelions, 8 poppies and 8 ferns; shears gather ferns | 10 Silvermarks, 1.5 experience bars, 3 Swiftness I potions (3:00) |
| A Thousand Steps, a Thousand Stories | Walk, sprint or sneak 20,000 blocks; native statistics exclude vehicles, flight and teleports | 30 Silvermarks, 4 experience bars, the named feather Wayfarer’s Token |
| Friends Far Away | Speak with residents in 3 distinct canonical villages spanning at least 2 biomes | 25 Silvermarks, 4 experience bars, 3 Golden Weather Tonics |

A Golden Weather Tonic changes clear Overworld skies to rain, or rain/thunder to clear skies, for at least 20 active minutes. This is shared world weather. Sleep and weather commands do not shorten the active effect; restarting preserves remaining active ticks. Drink it in the Overworld: the effect and consumption occur only after the normal drinking animation finishes. Interrupting costs nothing. Another active tonic blocks use and shows its remaining minutes and seconds. Survival consumption returns a glass bottle, retained in the existing reward storage if inventory is full. Afterward the ordinary weather cycle resumes.

On later visits Simon has a 25% chance per player and visit to need another herb delivery (8 of each plant above), paying 1 Weather Tonic. Talking repeatedly, changing inns or relogging does not reroll the same visit. An unfinished herb request remains available.

## Lutte

1. **Land Beyond the Sea:** discover 3 different surface land biomes after accepting. Oceans and rivers do not count. Reward: 12 Silvermarks and 2 experience bars.
2. **Lights Across the Water:** build three signals in mapped coastal areas at least 512 blocks apart. Lutte supplies 3 oak logs, 6 cobblestone walls, 3 lanterns and one personal chart once. Other log/wall types work. Each signal is a grounded log, two wall blocks directly above it, and a standing normal lantern on top, personally placed in that order. Choose a base within 16 horizontal and 16 vertical blocks of the mapped point. The same chart advances to the next target. Never rebuild one signal to count twice. Broken signals must be repaired; unknown/unloaded sites never gain new progress. After restarting, visit previously finished sites once for fresh confirmation before hand-in. Reward: 20 Silvermarks, 3 experience bars and an Unbreaking II book.
3. **Lutte’s Masterpiece:** only after personal completion of **The Silent Forge**. Hand in 1 netherite ingot, 16 redstone and 8 amethyst shards, then choose exactly one: Lutte’s Precision Pickaxe (Efficiency IV, Unbreaking III), or Lutte’s Masterblade (Sharpness IV, Unbreaking III). The existing reward screen and stored claims preserve the choice if closed or inventory is full.

Coastal surveying runs in bounded server work without loading terrain. The chart initially marks the survey origin and updates as coast areas are found. A completed unsuccessful regional search offers an explicit new survey from the guest's current region; existing targets and progress remain. Missing building materials must be replaced by the player.

### Lost coastal chart

During the active signal quest, Lutte offers a replacement only when your inventory has no valid personal chart. This does not search every chest or dropped item in the world. Ordering supersedes old chart generations without changing destinations or issuing new building supplies.

| Replacement order | Materials | Drawing time |
| --- | --- | --- |
| First and second | 3 paper and 1 ink sac | 2 advancing Overworld Minecraft days |
| Third onward | 3 paper, 1 ink sac and 5 diamonds | 14 advancing Overworld Minecraft days |

Pay once, then collect from Lutte when the saved deadline is reached. Later conversations and restarts do not reset it. Sleeping advances this clock as it does the normal world day. Lutte becomes increasingly indignant about lost charts and eventually takes a fourteen-day holiday.

## Marius — the village watch

| Quest | Task after acceptance | Reward |
| --- | --- | --- |
| The night watch | Defeat 15 monsters near a village at night (13000–23000) | 12 silver, 2 XP bars |
| A colleague needs a break | Restore 50 actually missing HP across any iron golems, including player-built golems outside villages, using iron ingots | 20 silver, 3 XP bars; no holiday collectible |
| Stand with the village | Declare readiness, defeat at least 3 raiders personally and win that same native level I raid | One personal Village Watch Seal |

After accepting the final quest, choose **I am ready** or **I need more time**. Readiness schedules the next nightfall at the host village. The raid waits for you to return there at night, with no forced chunk loading. The journal, Questmaster and tracker show preparation, countdown, return waiting, raid progress and victory. Existing raids block this start without being extended. Loss, cancellation or victory below three personal kills clears only the attempt, keeps the quest open without penalty and requires explicit readiness again; no automatic retry.

After completing all three Marius quests, the owner can use the seal anywhere for 2 minutes of night vision and 1 minute of glow on monsters within 24 blocks. A repeat attempt shows the remaining recharge time in minutes and seconds. Its personal 10-active-minute cooldown survives item copies, logout and restart. The seal is not consumed; collection uses saved reward storage.

## Guests while away and quiet reports

A mature official inn can receive a saved rare visit while its chunks are unloaded. Its existing 40 loaded-minute opening period, active-village and recorded population requirements remain. The NPC appears only after normal loading and fresh safe building checks; only time with the loaded guest consumes the 6–10-minute visit. No forced chunks, automatic remote fights or invented population changes.

Related players learn the village name and X/Z without the visitor identity through the optional world report; current visits are also in the journal. Reports are combined with routine route news, at most every five active minutes with three visible entries. An arrival is marked delivered only after its visible report is sent. A still-current visit can be queued again after logout/restart before delivery, without a duplicate after acknowledged delivery. Turning world/caravan news off prevents these chat reports; the journal remains available.
