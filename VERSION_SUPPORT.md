# Village Quest Version Support

Current canonical maintainer state: `MAINTAINER.md`.

Village Quest uses a single-active-line development policy.

## 2.2.0 content release

`Village Quest 2.2.0 - The Shrines Between Roads` is released exclusively for Minecraft `26.2`. It is the current stable content line and is not backported to the two older Minecraft targets.

## 2.1.1 parity release

By explicit maintainer decision, `Village Quest 2.1.1 - Homesteads & Wayfinding` was published as an additional feature-parity release for:

- Minecraft `26.2`
- Minecraft `26.1.2`
- Minecraft `1.21.11`

The release contains the same intended features, fixes, documentation, and resources on all three targets with target-appropriate Mojang or Yarn APIs.

## Current support policy

- Minecraft `26.2` becomes the only active content-development line.
- New quests, systems, items, interfaces, balancing passes, visual redesigns, and other content updates are not backported to `26.1.2` or `1.21.11`.
- The released `2.1.x` builds for older Minecraft versions remain available for download.
- Older lines may receive deliberately scoped maintenance releases for confirmed bug fixes, crash prevention, save-safety fixes, severe exploits, and meaningful performance improvements.
- Every maintenance change is ported against the target line's own mappings and APIs, tested independently, and released separately from the active content line.
- Compatibility improvements, presentation changes, balance adjustments, and new content do not by themselves create a backport commitment.
- A new `26.2` content release does not require empty parity releases for the two maintenance lines. They remain on their latest stable build until a real maintenance batch is worth publishing.

When a later stable Minecraft version becomes the chosen Village Quest development target, it replaces `26.2` as the single active line. The previous line then moves to the same maintenance/archive status rather than creating another permanently active branch.

Exceptions require an explicit maintainer decision. Community demand and a very low-risk port may be considered, but neither guarantees a backport.
