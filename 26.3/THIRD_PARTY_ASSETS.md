# Village Quest Third-Party Assets

This inventory records the provenance of creative resources in the Minecraft
`26.3` package. Paths below are relative to `26.3` unless stated otherwise.

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
