# Village Quest Version Support

Village Quest uses a single-active-line development policy.

## 2.1.1 parity release

By explicit maintainer decision, `Village Quest 2.1.1 - Homesteads & Wayfinding` was published as an additional feature-parity release for:

- Minecraft `26.2`
- Minecraft `26.1.2`
- Minecraft `1.21.11`

The release contains the same intended features, fixes, documentation, and resources on all three targets with target-appropriate Mojang or Yarn APIs.

## Current support policy

- Minecraft `26.2` becomes the only active content-development line.
- New quests, systems, items, interfaces, balancing passes, and other feature updates are not routinely backported to `26.1.2` or `1.21.11`.
- The released `2.1.x` builds for older Minecraft versions remain available for download.
- Older lines may receive a critical hotfix at the maintainer's discretion when necessary to prevent a startup crash, save or persistent-data corruption, or a severe exploit.
- Compatibility improvements, ordinary bugs, presentation changes, balance adjustments, and new content do not by themselves create a backport commitment.

When a later stable Minecraft version becomes the chosen Village Quest development target, it replaces `26.2` as the single active line. The previous line then moves to the same maintenance/archive status rather than creating another permanently active branch.

Exceptions require an explicit maintainer decision. Community demand and a very low-risk port may be considered, but neither guarantees a backport.
