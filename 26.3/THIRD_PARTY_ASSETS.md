# Village Quest Third-Party Assets

This inventory records the provenance of creative resources in the Minecraft
`26.3` package. Paths below are relative to `26.3` unless stated otherwise.


## Project-generated coastal signals and golem repair — 2026-10-25

`src/main/resources/assets/village-quest/textures/gui/artwork/friends_coastal_signals.png`
and `friends_golem_repair.png` in the same directory were made with the built-in
Imagegen tool for the authorized screenshot corrections. Actual fields_harvest
and caravan_road art provided palette/pixel-style references. Both opaque
768x288 PNGs are resized from the original generated 2048x768 outputs, preserving the full composition. Prompt/provenance:
26.3/design/friends-npc-ui/refinement-unreleased.4/PROMPTS.md. No external asset
was downloaded; existing herb/harp art is retained.

## Project-generated Raphael harp — 2026-10-25

`src/main/resources/assets/village-quest/textures/gui/artwork/raphael_harp.png`
was created with the built-in Imagegen tool on explicit user request, matching
the existing fields_harvest and caravan_road pixel-art references. Opaque
768x288 PNG (resized from the original 2048x768 output) of a medieval wooden harp, manuscript and quill in an inn; no quest
or reward is attached. Prompt/provenance:26.3/design/friends-npc-ui/PROMPT.md.

## Project-generated Friends herb vignette — 2026-10-25

`src/main/resources/assets/village-quest/textures/gui/artwork/friends_herbs.png`
was created with the built-in Imagegen tool for this project, using the existing
fields_harvest and caravan_road illustrations as style references. Opaque
768x288 PNG (resized from the original 2048x768 output), displayed through the existing artwork renderer. No third-party
asset download or Vanilla texture replacement. Prompt/provenance: the shared
26.3/design/friends-npc-ui/PROMPT.md implementation note.

## Project-generated golden weather tonic — pending sources after55

`src/main/resources/assets/village-quest/textures/item/weather_tonic.png` was
created with the built-in image generator on2026-10-23 for this project and
prepared as a native16x16 inventory sprite. It uses its own item/model and does
not replace or redistribute a Vanilla potion texture. Original generated input,
full prompt and preparation evidence are recorded in
`design/living-kingdoms-2.6/implementation/friends-quests/ASSETS.md`.

## Project-owned entity skins

### Ambient Adventurers (2.6.3, Minecraft 26.3 and 26.2)

Six original project-directed skins under `textures/entity/ambient/` provide
three Wanderers and three Nightrunners (two men and one woman per role).
Existing Questmaster, Pilgrim and Kingdom NPCs are style/quality references;
their original PNGs are unchanged. Built-in Imagegen supplied two outfit/head
studies and four individual head studies. All six raw outputs and exact prompts
are retained in `design/ambient-adventurers-2.6.3/generated/` and `PROMPTS.json`.
`pack-skins.ps1` mechanically samples the selected panels into native Java wide
64x64 UVs, aligns native face details, opens raised hair using the existing
Pilgrim strand topology and copies each role's master clothing unchanged.
`SKIN_MANIFEST.json` separates source and final SHA256 hashes. All six base faces
of each body part are opaque; unused UVs are transparent and alpha is binary.
`skin-review.png` is an inspected offline UV projection, not an engine screenshot.
Native Ingame/animation/modmix acceptance is NOT RUN. No external asset library
or new dependency was added. Existing project asset licensing applies.

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

Fantasy map terrain (2026-10-21): `textures/gui/map/fantasy-motifs-v1.png`
and `fantasy-grounds-v1.png` were generated with built-in Imagegen using the
project's `routes-v3.png` as a style reference. Originals, exact prompts and
deterministic atlas export details are in
`design/ui-redesign-r27/assets/fantasy-terrain-v1/`. These project creative assets
follow the asset terms in LICENSE; they are not third-party downloads or LGPL code.
`fantasy-trees-v2.png` adds sixteen separately generated individual trees for the
user-requested density/placement refinement; the first grove sheet is retained
as a design source, while the runtime uses its rocky/snowy ridge rows.

Additional2026-10-20 quest motif pairs are produced with the built-in generator,
with separately authored small/detailed scenes and inspected64x24/192x72 exports.
Originals, exact prompt set, inspected crops and SHA256 manifest are retained in
`design/ui-redesign-r27/assets/quest-v3/`. They are project-directed illustrations
under the creative asset terms, with no external photographs or new dependency.
Story state artwork is selected explicitly, including the failing-harvest field.


The 2026-10-20 Journal-v3 runtime components under `textures/gui/ui/v3/` are
project-directed built-in image-generation assets: an isolated transparent
wood/gold frame (416x234), an opaque parchment/village header (288x96), and a
small transparent lantern/ivy decoration (32x40).
Originals, generation/cleanup notes and nearest-neighbor resolution export are
retained in `design/ui-redesign-r27/assets/runtime-v3/`. The header was rendered
onto parchment because transparent generation introduced colored edge artifacts;
its text and actions are rendered live by Minecraft. Existing project-owned
card and button artwork supplies reusable paper/title/button surfaces. These
assets follow the creative terms in `LICENSE`, not the code license.

The initial2026-10-19 bakehouse/carpenter/forge v1.1 pairs are historical drafts.
The completed2026-10-20 library replaces them with matching Journal-v3 scenes and
contains20base families plus two necessary story-state variants, each with a
separately authored64x24 and192x72 export (44PNGs). Built-in generation originals,
exact prompts, inspected crops and SHA256 manifest are in the quest-v3 directory
above; original drafts remain preserved. These project-directed illustrations
follow the creative terms in `LICENSE`, not the LGPL code license. Native asset
inspection does not constitute Ingame or Minecraft-font approval.

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
# Quest-v3 motif additions (20 October 2026)

`textures/gui/artwork/night_watch_{64x24,192x72}.png` and
`village_home_{64x24,192x72}.png` were generated with built-in OpenAI image_gen
for this project. Separate small/detailed scenes were cropped and exported at
their logical resolutions. Originals, prompt set, export script and hash manifest:
`design/ui-redesign-r27/assets/quest-v3/`. Thematic scenes contain no live values.

## V3 Market/Questboard reference families · 20 October 2026

Built-in image_gen, guided by the approved Journal V3 reference and the user's
actual UI-tour screenshots. Full designs, exact prompts, visual/function notes
and hashes: `design/ui-redesign-r27/assets/family-reference-v3/`. The separate
192x72 market-stall scene is cropped from the generated market reference using
`export_market_scene.py`. No generated whole-screen bitmap or table/quest text
is used as runtime UI. Existing quest motif images and V3 ornaments are reused.
Illustrative design text is not authoritative game content; runtime components,
amounts, factors and action states come from existing models.

## Additional V3 family design references (2026-10-20)

Runtime refinement after those references: the built-in OpenAI image_gen tool
generated a blank gold title plaque with ivy and a separate logistics landscape.
Originals, exact prompts, crop/resize-only export script, alpha and SHA256 manifest
are in `design/ui-redesign-r27/assets/runtime-v3/refinement-2026-10-20/`.
Exports `title_plaque_v4.png` and `logistics_hero_v4.png` contain no UI text or
live values; the title is shared and the landscape decorates the Caravan Master.
No external asset library or additional licensing source was introduced.

Ten project-directed built-in Imagegen references for trust, pilgrim, prosperity,
notice board, village story, dispatch, caravan, wayshrines, route map and guild path
are retained under design/ui-redesign-r27/assets/remaining-family-reference-v3/.
Exact prompts, source images, SHA256/dimensions and inspected deviations accompany
them. These are design-only references, not new runtime assets. Existing server
data, native coin/items and live map rendering remain authoritative.


The annotated .35 follow-up replaces the bright logistics inset with the
project-directed built-in Imagegen asset `ui/v3/dispatch_freight_v5.png` in .36.
The new scene uses subdued freight-wagon imagery and contains no lettering.
Original, exact prompt and nearest-neighbour-only 256x96 export record are
retained under `design/ui-redesign-r27/implementation/ingame-review-2026-10-21/dispatch36/`.

### Procedural Journal text backing (.37)

`textures/gui/ui/v3/hero_text_wash.png` is a project-authored 356×170 RGBA
mask: parchment RGB (235,211,160), alpha capped at190, smoothstep feather over
28px on the top/bottom and72px on the right. Generated locally with Pillow;
no external image, model output or new third-party artwork. Used as one scaled
texture draw over the existing hero illustration.


### Trader stall (next candidate)

`textures/block/living_kingdoms/stall_wood.png` and `stall_cloth.png` are original project-authored 16×16 RGBA pixel textures generated with deterministic local code (wood grain and teal/linen stripes). `models/block/market_stall.json` is an original cuboid counter, four legs, posts and cloth canopy model. No external images, licensed third-party assets or generated raster-service images were used.

### Four recurring tavern guests and raised hair (.52)

Lutte, Simon, Marius and Raphael use project-directed built-in Imagegen concepts
and refinements, retained under design/living-kingdoms-2.6/implementation/npc-names-v2/.
PROMPTS.md and finish-skins.py disclose their prompt set and deterministic Java
wide UV/texel finishing. Runtime64x64guest_*.png files have binary alpha and full
base faces; SKIN_QUALITY_MANIFEST.json records final hashes. Raphael's gold VQ
embroidery and glasses removal were explicitly requested. Empty head overlays on
13existing project skins were filled using matching original hair texels, while
base faces/clothes and original rank hat pixels were retained. Old Questmaster,
Pilgrim, caravan and traitor overlays remain. No external asset library was added.
Native visual acceptance remains NOT RUN. The .53 item-atlas journal button PNG is
an unchanged duplicate of the existing project GUI texture, not newly authored art.

### Open head contours and four guest face refinements (.54)

Built-in Imagegen edited the four existing guest heads using the premium Pilgrim
skin as a style reference. The saved head-rework-generated PNGs, exact prompts,
UV crop coordinates and deterministic finishing are documented beside the older
sources in npc-names-v2/PROMPTS.md and pack-head-rework.ps1. Only head UVs change
on guests; clothes/VQ embroidery remain pixel-identical. Other Kingdom skins reuse
the existing Pilgrim strand transparency to open the old solid outer hair; base
identities stay identical. Mayor hat pixels differing from the old speaker's hair
are protected. The blonde raw design's overlapping back illustration is not used;
its original back is retained. Native visual acceptance remains NOT RUN.

### Pending source corrections after .55 (not packaged)

Built-in Imagegen supplied the Raphael mouth/beard study and the golden settings
cog. Raw images and prompts remain in npc-names-v2 and the existing UI review
folder. finish-mouths.ps1 integrates the existing merchant mouth texels on four
guests and the existing caravan guard beard footprint in a brighter brown,
removing only identified outer-hair tufts. Clothing and other facial features are
preserved. pack-settings-gear.ps1 reduces the generated cog to a32x32binary-alpha
sprite with a transparent central hole. No external asset library was added.
Ingame appearance remains NOT RUN; no new JAR was requested.
# Village Watch Seal addition, 2026-10-23

Original AI-generated village-watch shield/lantern asset created with built-in OpenAI Imagegen; saved source and processing notes in `design/living-kingdoms-2.6/implementation/friends-quests/ASSETS.md`. Own item texture/models; no external source artwork or vanilla texture override.
