# Village Quest 2.1.1 Item Art Manifest

This folder contains the reproducible source and processing record for the `Homesteads & Wayfinding` item-art pass. The artwork was created with the built-in OpenAI image-generation workflow under the maintainer's direction, then cropped, color-unmixed, pixel-quantized, and assembled locally by `build_assets.py`.

## Shared production prompt

Create a single centered Minecraft-style pixel-art inventory item on a flat chroma-magenta background. Use a compact readable silhouette, crisp square pixels, transparent-ready separation, no text, no logo, no UI frame, no scene, no shadow outside the object, and a coherent Village Quest palette of aged brass, dark oak, teal enamel, restrained burgundy cloth, parchment, and deep midnight blue. Preserve the item's gameplay identity at 32 pixels.

## Item-specific prompt set

- `magic_shard_master.png`: a faceted violet-blue magical crystal shard held by a small aged-brass setting, bright but not neon.
- `starreach_ring_master.png`: an aged-gold signet ring with a deep-blue star-like gemstone and subtle teal glint.
- `merchant_seal_master.png`: a dark-wood merchant stamp with aged-brass bands and a small teal wax/emblem detail.
- `shepherd_flute_master.png`: a short dark-wood shepherd flute with brass ferrules, teal bindings, and visible finger holes.
- `apiarists_smoker_master.png`: a compact iron-and-brass bee smoker with dark wooden bellows and a restrained teal accent.
- `caravan_ledger_master.png`: a closed dark-leather route ledger with brass corners, burgundy strap, parchment edge, and teal compass medallion.
- `roadwarden_horn_master.png`: a curved dark horn with aged-brass mouthpiece/caps, burgundy wrap, and teal roadwarden badge.
- `surveyors_compass_master.png`: a square aged-brass wayfinding instrument with a deep-blue cardinal dial, teal brackets, and a fixed outer housing suitable for separating into bezel and rotating inner disk.

## Outputs

- `source/`: original high-resolution generated masters.
- `transparent/`: transparent review intermediates.
- `components/`: separated fixed compass housing and rotating dial.
- `previews/`: contact sheet and representative compass frames.
- `build_assets.py`: deterministic conversion into the runtime sprites under `src/main/resources/assets/village-quest/textures/item/`.

The seven static items are emitted at `32x32`. The compass is emitted as a fixed-housing base plus 32 clockwise dial frames at exact `11.25` degree steps; `N` remains the target-bearing mark.
