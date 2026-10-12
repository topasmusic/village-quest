# Vanilla reward packages

[Wiki index](README.md) Â· [Quest reference](quest-reference.md) Â· [Prosperity](prosperity-and-economy.md)

New qualifying quests open a compact package selector after successful quest turn-in. Choose supplies, a task-specific work package, diamonds where unlocked, or fixed extra Silvermarks. Existing currency, XP, reputation, shards and special rewards still apply. Available packages, quantities and complete item components are frozen at acceptance; later advancement/stat changes do not alter them. Choose the package after completion, when the base rewards have been granted. Closing with Later/ESC saves the earned choice; reopen it with `/vq rewards claim`, including after a restart or another quest. Previously accepted, already chosen packages retain their automatic payout. Your last choice is suggested when available. Without a preference, End progress suggests work supplies; otherwise supplies are suggested. Diamond alternatives require the vanilla Diamonds advancement, which may also be obtained from a gift. There is no AFK, farm, playtime or mining-stat reward multiplier.

Each eligible party member selects and receives the full personal package. Existing participation, disconnect and shared turn-in rules remain. Old accepted/shared quests gain no retroactive extras. Already accepted repeatable contracts with incompatible target profiles keep their own objectives rather than merging into a different shared profile.

NORMAL extras: Honey, Potato, Autumn and Stall give 8 golden carrots or 3 SM; Wheat gives 12 or 5 SM; River gives 16 or 6 SM. Wood gives 16 iron and 16 coal or 8 SM. Coal Mining and Smith Smelting give 16 iron and 32 coal, 2 diamonds, or 12 SM. The introductory Daily gives 6 ordinary carrots or 2 SM. Other Dailies retain their existing rewards.

Weekly Harvest offers 48 golden carrots, 64 glass and 16 lanterns, or 20 SM. Bake offers 64 carrots, 64 rails and 8 powered rails, or 26 SM. Smith offers 64 carrots and 2 golden apples, 6 diamonds, or 32 SM. Pasture offers 48 carrots, 48 leather, or 20 SM. LIGHT/NORMAL/HEAVY counts use rounded 0.88/1/1.18 factors; Smith diamonds are exactly 5/6/7. Potion duration/strength and enchantments never scale. Other Weeklies retain their existing rewards.

Four Overworld combat Pilgrim contracts offer 1 golden apple and 2 Healing II potions or 11 SM. Ash/Smoke offer 2 eight-minute fire-resistance potions and 16 carrots or 16 SM. End offers 2 shulker shells or 20 SM. New peaceful End contracts additionally require entering an End City after acceptance; the visit appears in the objectives. Old accepted contracts retain their original conditions. Roadmarks rewards remain unchanged.

Only Story finales offer new extras: Harvest 48 carrots/20 SM; Pens 48 carrots/16 bookshelves/20 SM; Forge Efficiency IV + Unbreaking III diamond pickaxe/8 diamonds/32 SM; Market 64 rails + 16 powered rails + 16 lanterns/6 diamonds/24 SM; Night Feather Falling IV + Unbreaking III diamond boots/8 diamonds/32 SM; Shadows Power IV + Unbreaking III bow plus Unbreaking III shield/6 diamonds/24 SM; Caravan 48 carrots + 2 golden apples + 2 Healing II potions/8 diamonds/32 SM; Shrines Silk Touch + Efficiency IV + Unbreaking III diamond pickaxe/8 diamonds/32 SM. Earlier chapters keep their existing rewards. First new completion of each of the ten Guild Town commission types and five town-story types earns an additional 8 SM once. Concord adds 128 rails + 16 powered rails + 32 lanterns, 12 diamonds, or 48 SM.

The Pilgrim shop has six fixed packages before normal discounts: provisions 24 golden carrots for 12 SM; healing 1 golden apple + 2 Healing II potions for 14; Nether 2 eight-minute fire-resistance potions + 16 carrots for 20; light 64 glass + 16 lanterns for 30; rails 64 + 8 powered for 32; leather 48 for 32. Existing unlock/stock rules remain. Old prepaid provisions/healing orders keep their original random supplies or arrows/steak; new orders save the fixed package when paid.

New Smith Smelting on NORMAL collects 51 fresh raw iron, smelts 51 ingots and hands in only the ingots. Old accepted contracts still collect 102 and deliver 51 raw iron plus 51 ingots. The same rule applies to bonus and shared contracts.

Earned extra items that do not fit stay saved with their full components. Use `/vq rewards claim` when space is available. Claiming can be partial and never drops extras into the world. Storage is bounded to 256 serialized stacks, including the largest alternative for each active or earned choice; at most 32 active/earned choices are reserved; new rewards/orders pause near capacity until items are collected. Unknown reward schemas are preserved read-only. Normal save/reload is covered by tests; atomic recovery across an abrupt crash between inventory and SavedData saves is not guaranteed.

The compact selector shows centered native item previews and quantities; identical split stacks are combined for display only, while different potion/enchantment components remain distinct. Currency uses Crown and Silvermark icons with exact denomination counts. Tabs carry a representative icon; hover shows full reward details. Pilgrim trader cards now match the Quest Board framing and section-heading spacing.

Village gifts (German: Beigaben) are listed only under Rewards; objectives describe quest work only. After Later/ESC, the completed quest remains available at the Questmaster in its matching category with Collect gifts. This opens that specific earned offer, including after another quest, progress reset or save/reload. Each entry belongs to the current player and disappears after choosing. Pilgrim/Concord gifts use the Special category.

Clicking Later also shows a brief chat reminder that your gifts await at the Questmaster.

## Collecting postponed village gifts

The Questmaster groups uncollected gifts into one collection entry per category, displaying the number waiting. Collect gifts opens the oldest saved choice in that category. After collecting it, the entry updates to the next gift; it disappears when empty. Nothing is selected automatically or discarded.

New tasks with gift choices can be accepted only while fewer than eight offers are open. Already accepted offers count toward this limit, reserving space for turn-in. At the limit, a chat reminder directs you to the Questmaster; collect a gift to make room. Existing tasks still finish normally. Older saves with up to32earned choices retain all gifts, grouped for collection, and accept no new choice offers until below eight. Später/Later also points to the Questmaster in chat.

The Questmaster's Call describes a trade: the Questmaster values a Magic Shard and accepts one in exchange for another task that day. Its existing shard consumption and daily rules are unchanged.
