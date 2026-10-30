# Wanderers and Nightrunners

These are brief shared encounters in the Overworld. They are independent travelers;
they do not join your village, reserve an inn bed or trade. They occasionally
offer a small personal quest. They do not award village-contact progress,
reputation or helper unlocks.

Wanderers Hanno Mosswood, Bennet Fairbrook and Alva Willowford usually travel by
day along roads, village edges and open terrain. They walk short stretches, pause
to look around and may carry a torch after dark. They flee danger and never attack.
Surnames follow your language; each traveler keeps the same name and face during
the encounter.

Nightrunners Ronan Ironvale, Silas Ravenmere and Enna Ashford pursue their own
limited hunt. They can arrive between Overworld clock 13,000 and 23,000, mostly
on the surface and rarely in dry shallow cave entrances. They stop starting
voluntary hunts at dawn. They hunt ordinary zombies, husks, skeletons, strays,
bogged and already aggressive spiders. They exclude creepers, neutral mobs,
animals, pets, villagers, raiders, bosses, named special monsters and VQ enemies.
They do not take over a monster targeting a player or recently hurt by a player
or pet. If you join their existing fight, its original limits still apply.

Each Nightrunner commits to at most **three different monsters** and attempts at
most **15 strikes** during its whole visit, including self-defense. An ordinary
strike does **4 damage before armor**, at least one second apart. Pursuit stays
within 12 blocks of the original fight position and lasts at most 20 seconds per
target; losing sight for three seconds ends it. Low health (8 HP or less), a crowd
of three eligible threats or an unsafe/stuck path causes flight. They are not
invulnerable and do not heal themselves. Wanderers have 20 HP; Nightrunners 24 HP
and 2 armor.

Main-hand right-click gives a brief private line and, when safe, a 3–5 second
pause. Repeated clicking does not extend the traveler's life. Your first damaging
hit causes a warning and retreat. A second damaging hit by **you** within ten
seconds can make a Nightrunner defend itself for at most eight seconds, without
chasing you out of melee range. It can execute at most **three player
counterattacks in total**, across all attackers. These strikes can kill a wounded
player. Creative/spectator players and bystanders are never counterattack targets.
With server PvP disabled, it only warns and retreats. Pet damage causes flight,
never retaliation against its owner. Repeated aggression ends conversations with
that player for the remainder of this encounter, without guild penalties.

Neither traveler drops items, equipment or XP on death. A direct Nightrunner
monster final kill suppresses normal death loot and XP even after earlier player
damage, and grants no VQ quest kill. If **you** finish the monster, normal player
loot and quest rules apply. Finishing a weakened monster is possible; this feature
does not claim perfect detection of spawners or farms.

There are at most eight travelers, two Nightrunners and two travelers within 96
blocks of any player. Successful arrivals start regional cooldowns (five minutes
for Wanderers, ten for Nightrunners), including after a quick death. Encounters
normally last 4–8 active minutes, never more than 12. No player within 96 blocks
for 30 seconds permits removal. Unloading their chunk or restarting ends the
encounter; naming or trapping does not preserve it. They do not teleport to escape,
build, open doors/chests, place torches, use portals or ride boats/minecarts. A
blocked route gets bounded retries; an imprisoned traveler eventually expires.

If separate player groups move together, excess encounters are removed to keep
the same local cap. Arrival cooldowns remain in place when that happens.

Only loaded entity-ticking areas are used. No terrain generation or chunk tickets
are requested. Peaceful still allows passive encounters, with no hostile hunts.
See [Configuration](configuration.md) for the three ambient settings. Use the
matching mod version on client and server.

## Small roadside stories

At the first safe contact, there is a 20% chance of one eligible personal quest.
Reopening the conversation does not reroll it; **Maybe later** keeps the same
offer for a later conversation. Natural regional spawn rolls are 25% less likely than before (3/16
instead of 1/4), with existing caps and cooldowns retained.

Wanderers may ask for 2 bread or 3 baked potatoes, 45 cumulative seconds of
walking together within 12 blocks, or greetings to an adult villager at the
shown nearby village. Nightrunners may request 2 personal zombie/husk kills in
the Overworld, 1 iron ingot, or 2 newly placed torches within 32 blocks of the
encounter. The torch positions must be at least 4 blocks apart and both still
stand. Hostile kills are not offered on Peaceful.

Every quest rewards **4 emeralds and 2 experience levels**, shown with
icons before acceptance. There are exactly two buttons: **Accept** and **Maybe
later**. The latter preserves the offer for another conversation. Accepting
adds the quest to your tracker even if you do not yet have the goods. Once the
full amount is in your inventory, it is handed over automatically wherever you
are: 2 bread (preferred if both options are ready), otherwise 3 baked potatoes,
or 1 iron ingot for the iron quest. Nothing is consumed in part. Existing items
also count, so the quest can complete immediately after acceptance.
All quests complete without a return trip, with chat completion/reward messages
and the usual level-up sound (subject to your sound settings). A full inventory
keeps the earned reward pending until there is room for 4 emeralds.

Only one roadside quest can be active, and one rewarded completion is allowed
per configured quest-calendar day across both roles. Journal and tracker show
the task; progress survives death, logout and restart without a time limit.
Walking pauses during rest, danger or separation and can resume by speaking to
another wanderer if the original leaves. Personal quest resets do not reset
the encounter reward receipt. Multiplayer progress and rewards are individual.
