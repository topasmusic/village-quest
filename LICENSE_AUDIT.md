# Village Quest License And Asset Provenance Audit

Audit date: 2026-07-19

Status: **The mixed `2.0.0` licensing package is implemented locally but must not be published while the legacy entity skins remain unresolved.** Functional code is `LGPL-3.0-only`; owned original assets and creative content use the Village Quest All Rights Reserved notice; third-party material is explicitly excluded and inventoried.

This document is a maintainer audit, not legal advice.

## Scope

The audit covers the three maintained lines:

- `26.2`
- `26.1.2`
- `1.21.11`

## Findings

### Original project work

- Git history currently identifies `topasmusic` as the sole committer. A commit identity is useful evidence of maintenance, but it is not by itself proof that every committed asset was created by that account holder.
- The functional Java implementation and project-maintained build logic are now designated `LGPL-3.0-only`. Owned authored quest prose, localization, interface art, branding, and project-specific creative assets are covered separately by the Village Quest All Rights Reserved notice.
- The newer generated Village Quest interface art was produced for this project. Under the applicable OpenAI terms, the user owns generated output as between the user and OpenAI to the extent permitted by law; generated output may nevertheless be non-unique and does not cure third-party rights in supplied inputs.

### Contributor records

- The maintainer reports that `Landschaftswart` handed over continued development of Village Quest and agreed to removal from the visible author credits. The `2.0.0` metadata therefore lists `TopasMusic` as the sole visible author. Keeping a short written confirmation of the handover, the future code license, and the credit removal is still recommended as durable evidence.
- The wiki credits `Lutte` for the original Spanish translation. That separable contribution is recorded in `THIRD_PARTY_ASSETS.md`, retained with attribution, and carried under the historical MIT terms rather than claimed as an exclusively owned Village Quest asset.
- German law treats the creator as the author and may require co-authors to consent to exploitation or changes of an inseparable joint work. Durable written records remain recommended even where a contribution is kept under compatible prior terms instead of being claimed as owned project material.

### Assets that cannot currently be claimed as exclusive Village Quest property

- The legacy NPC and caravan skins were obtained from a public Minecraft skin website, but the source URLs, creators, and license terms are not recorded. Recoloring a downloaded skin does not establish ownership of the underlying design.
- The maintainer reports that the meme-themed paintings and the newer interface art were generated or prepared for Village Quest. The licensing notice claims only protectable original expression owned by the maintainer and expressly excludes underlying third-party characters, memes, marks, photographs, and other rights.
- Any other texture supplied from an external website or derived from Minecraft artwork needs the same source-and-license record. File presence and a local filename are not proof of ownership.

### External platforms and dependencies

- Minecraft, its code, textures, character skins, names, and trademarks remain Mojang/Microsoft property. Village Quest can be distributed as an original mod, but it must not claim those rights or distribute a modded copy of Minecraft.
- Fabric Loader and Fabric API are external runtime/build dependencies under Apache-2.0. They are referenced as dependencies rather than claimed as Village Quest work.
- Fabric metadata accepts a license string or list and recommends SPDX identifiers for open-source licenses. The `2.0.0` metadata therefore lists both `LGPL-3.0-only` and `LicenseRef-Village-Quest-Assets-ARR`, with the complete scope explained in the bundled `LICENSE` file.

### Existing MIT releases

- Versions already distributed under MIT retain the permissions granted with those copies. A new license can govern only future versions and commits distributed with it.
- The current unreleased working tree may use the mixed notice locally, but it must not be published until every entity skin marked unresolved in `THIRD_PARTY_ASSETS.md` is replaced or its source and permission are recovered.

## Adopted mixed-license model

The maintainer selected a split model for `2.0.0`:

- functional source code under `LGPL-3.0-only`;
- original owned assets and creative content under the Village Quest All Rights Reserved notice;
- limited permission to install, play, run on servers, back up, and make ordinary media coverage of an official unmodified release;
- third-party material excluded and documented separately;
- name, logo, and branding withheld from the LGPL grant; and
- prior MIT releases and permissions explicitly preserved.

The complete GPLv3 and LGPLv3 texts are stored as `COPYING` and `COPYING.LESSER`. `LICENSE-MIT`, `THIRD_PARTY_ASSETS.md`, and `THIRD_PARTY_NOTICES.md` preserve the historical and third-party context. The build places all six files under `META-INF/village-quest/` in runtime and sources JARs.

## Superseded options considered before the mixed-license decision

### Custom Village Quest Proprietary License 1.0 — not selected

Best match for the intended policy. It can expressly permit:

- downloading official releases and playing the mod privately;
- running the unmodified official release on Minecraft servers;
- inclusion of the exact official file through an approved platform mechanism such as Modrinth;
- private backups.

It can expressly prohibit without prior written permission:

- modifications and derivative works;
- source, binary, texture, model, localization, or other asset reuse;
- reuploads and redistribution outside approved exact-file platform mechanisms;
- sublicensing, resale, and misrepresentation of authorship;
- removal of copyright and provenance notices.

The license must exclude third-party material and must state that earlier MIT releases remain under their original license.

### All Rights Reserved without a custom license — not selected

This is maximally restrictive by default and is recognized by hosting platforms, but it gives players and server operators less explicit guidance about normal installation, servers, backups, and modpacks. A short custom proprietary license is clearer and safer operationally.

### PolyForm Strict 1.0.0 — not selected

PolyForm Strict permits noncommercial use while prohibiting distribution and modification. That is close to the desired protection, but it may unintentionally exclude monetized or commercial servers and ordinary third-party modpack distribution. It also cannot resolve missing rights in downloaded skins or contributor work.

### Business Source License, Creative Commons, and project-wide open-source licensing — not selected

- Business Source License is designed around time-delayed conversion and production-use limits, not a consumer Minecraft mod whose official binary should remain playable.
- Creative Commons licenses are primarily designed for creative works; variants that allow redistribution or adaptation do not match the requested control, and they are not a good primary software license.
- A license covering the complete mod package would grant unwanted rights to the creative assets. The adopted split model instead applies LGPL only to functional code and keeps the protected asset scope separate.

## Remaining cleanup before publication

1. Replace every web-downloaded NPC/caravan skin with provably original, commissioned, or appropriately licensed artwork, or record the creator, source URL, license, and permission.
2. Keep written confirmation of the reported `Landschaftswart` handover for durable project records.
3. Preserve `Lutte` attribution and the historical MIT notice for the separable Spanish translation unless a different written permission is obtained.
4. Keep `THIRD_PARTY_ASSETS.md` and `THIRD_PARTY_NOTICES.md` current whenever an asset, dependency, or provenance record changes.
5. Keep hosting-page license descriptions aligned with the mixed package rather than presenting either the entire JAR as LGPL or the entire repository as exclusively owned.

## Release gate

The mixed-license files and metadata may remain in the unreleased source tree. Publication is blocked only by material still marked unresolved in the bundled provenance inventory, currently the legacy NPC and caravan skins. No maintainer should describe the entire mod package as exclusively owned or describe the protected creative assets as LGPL-covered.
