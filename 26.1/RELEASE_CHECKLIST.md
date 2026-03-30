# Release Checklist

Run this once before publishing a release build.

## Build

- `./gradlew build` succeeds on Java `21`
- latest jar and sources jar are present in `build/libs`
- no Fabric example-template metadata remains in `fabric.mod.json`

## Fresh World

- create a new world
- verify the game starts with only `Village Quest` + Fabric API
- verify the journal opens
- verify `/questmaster` summons the Questmaster near the player
- verify the Questmaster can be used to show today's quest offer
- verify a daily quest can be accepted and completed
- verify the Weekly category shows a real weekly quest
- verify wallet rewards are added correctly
- verify the `Magic Shard` renders correctly in inventory and as a drop

## Existing/Test World

- open the known test world
- verify no fatal world-load issues occur
- verify legacy compatibility ids still load old content cleanly enough for release

## Wallet

- `/wallet` shows the current balance
- `/questadmin wallet add/remove/set/show` all work
- `silvermark` and `crown` suggestions and parsing work
- German client shows `Silberling` / `Krone`
- English client shows `Silvermark` / `Crown`

## Magic Shards

- completed dailies can still roll `Magic Shard` drops
- the first successful quest on a fresh player still guarantees `1` `Magic Shard`
- after a normal daily, `1` shard still unlocks one extra daily through the Questmaster
- with `10` shards, the Questmaster offers `Whispers of the Shards`

## Weekly Quests

- a weekly quest can be accepted through the Questmaster UI
- weekly progress tracks correctly for at least one farming/crafting quest
- weekly claim works and pays the heavier reward package
- weekly appears in the journal and tracker while active
- weekly timer appears after weekly completion
- weekly resets on Monday `06:00 Europe/Berlin`
- `/questadmin resetweekly`, `/questadmin nextweekly`, and `/questadmin completeweekly` all work
- at least one combat weekly can be forced and pays `2 Crowns` plus `2 Magic Shards`

## Secret Quest

- the hidden quest offer appears when carrying `10` Magic Shards
- accepting it consumes exactly `10` shards
- all five magical objectives track correctly
- returning to the Questmaster after the trial gives a treasure map
- the buried cache can be dug up at the marked location
- only the owning player can open the cache
- the cache contains the `Starreach Ring`
- carrying the ring in the offhand extends building reach by `2` blocks

## Pilgrim

- natural pilgrim spawn still occurs
- pilgrim stays for about `20` real minutes
- bottom-right timer counts down correctly
- if the pilgrim leaves while the UI is open, the UI closes cleanly
- after a pilgrim leaves, no new natural pilgrim appears for `1` full in-game day
- `/questadmin pilgrim spawn` still works during that cooldown

## Pilgrim Shop

- wallet header counts render cleanly
- wallet coin icons render cleanly
- goods list text fits
- `Buy` button text is vertically centered
- trade preview icons render cleanly
- buy flow succeeds when affordable
- buy flow is blocked cleanly when not affordable

## Custom Paintings

- every custom painting can be bought or given
- every custom painting places with the correct final size
- no green hover-name text appears when looking at placed `village-quest` paintings
- `Happy Doge` remains `4x3`

## Localization

- German client: key shop/journal/wallet/trader flows read naturally
- English client: key shop/journal/wallet/trader flows read naturally
- German and English special-quest texts read naturally
- no obvious missing translation keys appear in logs or UI

## Packaging

- publish the remapped jar from `build/libs`
- keep the matching sources jar alongside the release if desired
