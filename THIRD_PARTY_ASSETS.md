# Village Quest Third-Party Assets

This inventory applies to the Village Quest `2.0.0` package. Paths are relative
to a maintained version line. The same listed resources are mirrored across the
`1.21.11`, `26.1.2`, and `26.2` lines unless noted otherwise.

An `unknown` or `unresolved` entry is a provenance warning, not permission to
copy, modify, or redistribute the file. Such material is excluded from both the
Village Quest All Rights Reserved declaration and `LGPL-3.0-only`.

## Unresolved legacy entity skins

The following medieval-style Minecraft skins were originally downloaded from a
public skin website. The original page, creator, and license are no longer known.
Some caravan variants were later recolored for route identity, but that does not
establish ownership of the underlying skin.

| File | Recorded origin | Creator | License or permission | Status |
| --- | --- | --- | --- | --- |
| `src/main/resources/assets/village-quest/textures/entity/caravan.png` | Public Minecraft skin website | Unknown | Unknown | Unresolved; replacement or source clearance required before release |
| `src/main/resources/assets/village-quest/textures/entity/caravan_burgundy.png` | Derived recolor of an unresolved caravan skin | Unknown | Unknown | Unresolved derivative |
| `src/main/resources/assets/village-quest/textures/entity/caravan_forest.png` | Derived recolor of an unresolved caravan skin | Unknown | Unknown | Unresolved derivative |
| `src/main/resources/assets/village-quest/textures/entity/caravan_ochre.png` | Derived recolor of an unresolved caravan skin | Unknown | Unknown | Unresolved derivative |
| `src/main/resources/assets/village-quest/textures/entity/caravan_violet.png` | Derived recolor of an unresolved caravan skin | Unknown | Unknown | Unresolved derivative |
| `src/main/resources/assets/village-quest/textures/entity/pilgrim.png` | Public Minecraft skin website | Unknown | Unknown | Unresolved; replacement or source clearance required before release |
| `src/main/resources/assets/village-quest/textures/entity/quest_master.png` | Public Minecraft skin website | Unknown | Unknown | Unresolved; replacement or source clearance required before release |
| `src/main/resources/assets/village-quest/textures/entity/traitor.png` | Public Minecraft skin website | Unknown | Unknown | Unresolved; replacement or source clearance required before release |

These entries are the outstanding asset-provenance release gate. They must not be
described as owned by Village Quest merely because their filenames are known or
because some colors were changed.

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

When an unresolved asset is replaced or its source is recovered, record the exact
file, creator, source URL, license or written permission, attribution text,
modifications, and the date on which the record was verified.
