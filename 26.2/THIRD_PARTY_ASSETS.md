# Village Quest Third-Party Assets

This inventory records the provenance of creative resources in the Minecraft
`26.2` package. Paths below are relative to `26.2` unless stated otherwise.

## Project-owned entity skins

The maintainer confirmed on 2026-09-24 that the head artwork for the caravan,
Pilgrim, Questmaster, and Traitor characters was created by TopasMusic. The
finished 2.5 clothing for these characters was authored for Village Quest.
The active entity texture set consists of:

- 20 `caravan_<livery>_<role>.png` skins: five route liveries, each with a
  Master, Trader, Guard, and Courier;
- `pilgrim.png`, `quest_master.png`, and `traitor.png`, with their approved
  2.5 clothing and TopasMusic head artwork.

The eight isolated head sources used to reproduce these skins are maintained
under `src/test/resources/assets/village-quest/skin-head-sources/`. They are
build and verification inputs, not additional runtime entity textures. These
23 complete skins are original Village Quest assets governed by the creative
asset terms in `LICENSE` (All Rights Reserved, with the stated permission to
use an official release). They are not LGPL-licensed code.

## Project-owned UI polish assets

On 2026-10-04, the maintainer directed generated artwork for the Trust handshake,
Making amends shield and Resettlement cottage/sprout icons. The final
`textures/gui/ui/icon_trust_handshake.png`, `icon_amends.png` and
`icon_resettlement.png` use a shared eight-colour palette at 32×32 pixels.
The approved generated Quest Board frame supplies shared wood, brass and
blue-green corner-stone artwork for `journal_board.png`,
`guild_notice_board_frame.png`, `guild_atlas_frame.png`, `wayshrine_board.png`,
`trade_route_board.png` and `pilgrim_board_v2.png`; their existing interior
layouts and native dimensions are retained. A generated gold VQ glyph replaces
the Questmaster's back emblem. The two detached bottom front-head-overlay
pixels were removed from both the skin and its approved head source at the
maintainer's request; all other head pixels remain unchanged.
These project assets follow the creative asset terms in `LICENSE`.

The same day's follow-up adds `textures/gui/caravan_master_inner.png`, a
project-generated clean parchment derived from the existing notice interior,
and `textures/gui/trust_board.png`, assembled from the approved journal frame
with a plain sidebar for exactly three tabs. The existing generated VQ patch
is reused on the Questmaster's outer jacket, with navy spacing separating the
letters from the suspenders; all head artwork remains unchanged in this pass.

A further maintainer-directed correction generated a border-only wood frame
from the approved reference. Its continuous rails and four gold/teal corners
replace the exterior artwork of the seven listed frame textures, including
`trust_board.png`; native dimensions, interior layouts and title plates remain.
The Caravan Master now uses the clean parchment directly, without an inner
frame. Transparent join pixels are backed with the matching generated rail's
nearest opaque shading; the ornaments and open map interiors remain intact.
Generation and mechanical assembly records remain private.

Later edited rail variants and a generated walnut material swatch remain in
private design records only. On 2026-10-06 the maintainer requested restoration
of the previous approved border-only frames, including their dark horizontal
edges. Those seven frame textures were restored from their unchanged backups;
the later material and UI rework are not used by the runtime. On subsequent
explicit instruction, only black exterior fringe pixels were made transparent.
Original wood grain, corner ornaments, plaques and inner shadows remain intact;
mechanical alpha-trim verification records remain private.
Subsequent contour cleanup removes small protrusions at the horizontal rail
silhouette without replacing the wood material. The Journal/Trust title plaque
uses mirrored existing edge strips for matching left/right outlines; its body
retains the approved gold artwork. No new artwork was generated.

## Spanish localization contribution

| File | Contributor | Provenance and terms | Modifications |
| --- | --- | --- | --- |
| `src/main/resources/assets/village-quest/lang/es_es.json` | `Lutte` | Original Spanish translation contributed to the pre-2.0 MIT-licensed project; retained with attribution and the historical MIT notice in `LICENSE-MIT` | Later missing keys, placeholders, and maintenance corrections completed by the Village Quest maintainer and project tools |

## Project-generated art with third-party references

The following painting files were generated or prepared for Village Quest under
the maintainer's direction. The project claims rights only in protectable original
expression, not in underlying memes, characters, trademarks, photographs, or
other third-party subject matter:

- `textures/painting/good_doge.png`
- `textures/painting/happy_doge.png`
- `textures/painting/pepe_the_almighty.png`
- `textures/painting/something_is_sus.png`
- other project-generated painting or promotional art where an underlying
  third-party reference is recognizable

This provenance note does not grant permission to reuse those files separately.

## Items not shipped by Village Quest

The `mini_blocks` resource pack and datapack found in local test environments are
not part of the Village Quest source or release JAR. They are therefore not
licensed or redistributed by this project and are not included as package assets
in this inventory.

## Updating this inventory

For future additions, record the creator, source, applicable terms, attribution,
modifications, and verification date before including a new resource.

## Reward modal background (2026-10-11)

`assets/village-quest/textures/gui/reward_panel.png`: generated specifically for Village Quest with OpenAI image generation on user request. No external source artwork; prompt and provenance in `design/2.5-vanilla-rewards/post-completion-rewards/UI_DESIGN.md`. Follows the project asset license.
