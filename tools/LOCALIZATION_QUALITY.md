# Language and UI quality workflow

## Writing new content — user rule, 2026-10-25

Use the current, editorially revised EN/DE/ES catalogs as the reference for new
quests, NPC dialogue, instructions and UI text. Continue their established tone,
personal address and terminology. Write natural, understandable sentences in
each language; literal translation alone is insufficient. State the actual
action, target, quantity, prerequisite, progress and reward clearly where they
matter. Match actual Minecraft/mod item names and the existing glossary.
Preserve previous editorial improvements. Review new content in all three
languages together with its actual source context, then apply the targeted
review and verification steps below. This is a continuing writing requirement,
not only a condition for the Friends NPC UI.

The checks run offline in Python and JUnit. They do not use computer/browser automation, screenshots, an LLM, or a Minecraft client/server. No external service or recurring task is required.

For local Windows checks, use `tools/Invoke-TopasProject.ps1` from the actual Topas Mods root with project `MC MODS/Topas Mods/Village Quest/26.3` (or `26.2`), task `verifyQuality`, and task arguments `@('--offline')`. `-CheckOnly` inspects the local plan without running it. The starter selects this PC's configured Python/JDK/cache/build mirror. Missing local tools require `tools/Setup-TopasLocalTools.ps1`; do not install dependencies in the shared source folder.

`verifyQuality` runs the native unit suite, including actual catalog text layout and Trust → Dispatches → Back → Trust input tests. `test`, `check`, and therefore `build` also depend on the offline language gate. CI runs `tools/quality_gate.py` for all four supported lines before its build matrix; a failed check stops the dependent build. The native UI additions apply to 26.3/26.2; older lines keep their own gameplay and native test suites.

## What is checked

- JSON, duplicate/missing keys, locale parity, formatting placeholders and damaged Unicode.
- Known contextual mistakes: spider fangs, compass identity, first route/Market Charter, localized ledger prerequisite, German reward labels and Atlas effect headings.
- Every existing key has a reviewed EN/DE/ES text fingerprint in the line's `tools/localization-review.json`, plus subject, speaker/display/meaning context, review evidence and indexed literal/dynamic source consumers where available. Registry-derived keys are explicitly identified as needing source inspection rather than an inferred exact consumer.
- All production Java source files and gameplay data JSON (including recipes/loot) have a context-audit fingerprint. Any source addition, deletion or edit makes the corresponding review stale even if the text did not change. This conservative check catches indirect changes to unlocks, objectives and rewards; harmless source edits still need a brief targeted context assessment. LF/CRLF conversion alone is ignored.
- Glossary rules cannot be waived by refreshing the text fingerprint. Validator regressions test corrupt inputs and ensure validation never approves its own inputs.
- The Cartographer's Lens survey quantity comes from the same runtime constant in the offer, objective display, completion boundary and lore. Its native regression checks argument equality, completion immediately below/at the target and all three locale templates. Other prose quantities are still protected by source/context review; they have not all been converted into runtime parameters.
- Native layout tests exercise all localized project cards and Pilgrim contract offers, final scroll-line reachability, header boundaries and repeated notice/dispatch input round trips at several viewport sizes. Font metrics are simulated at several widths.

## Updating texts or mechanics

1. Read the affected source and the existing context/glossary entry. Inspect delivery versus harvesting, NPC, item identity, conditions, prices, quantities and rewards. `uses` distinguishes exact literal consumers from dynamic-prefix matches; those matches are a navigation aid, not proof of semantic correctness.
2. Update EN/DE/ES together. Use runtime arguments for quantities or titles when practical. Add a regression for a newly found semantic, formatting or UI failure rather than only asserting that a snapshot exists.
3. After the actual context review, update only the affected key/source entries with a concrete evidence note. The script supports `--review-key KEY` and `--review-source src/main/java/...java` (repeatable) together with `--note "Specific source, condition and interpretation checked"`. Run it with the configured local Python, the line's `tools/localization_quality.py` and `--root` pointing to that line. Deleted inputs are removed only when explicitly selected. There is no CLI refresh-all; build/test never updates the review file. `build_review` exists only for initial migration/test fixtures, not for ongoing approvals.
4. Run `verifyQuality` through the Topas starter. Review contract changes with the code/text diff. Commit-ready files include the sidecar, standalone guard copies and Gradle verification script; private audit notes are not CI dependencies.

The initial text baseline is the complete 2026-10-12 editorial language/context audit. Source fingerprints are its source snapshot, **not** a claim that every Java class received a full code audit. Sidecars keep each line's intentional historical quantities and text variants.

## What a passing result does not establish

These checks prevent the known classes of regressions and make changed inputs require an explicit context assessment. A fingerprint cannot understand prose or determine whether a new translation is natural. Real Minecraft glyphs, resource-pack fonts, pixel appearance, GUI scaling, multiplayer and gameplay/save behavior are not covered by the modeled layout tests. Those checks remain separate and must be recorded as `NOT RUN` when not performed. No automated computer-use step is part of this workflow.

Single-line source/audit checkouts carry their own resource validator, context validator, review sidecar and Gradle verification script. Their native test/build gate validates that line without requiring other lines or the central Python regression files. Full repository CI additionally tests the validators themselves and all supported catalogs.

## Quest reward name colors — 2026-10-25

Use green for smaller named special quest rewards (Wayfarer’s Token, Village Watch Seal, Weather Tonic), gold for major story artifacts and native vanilla colors for ordinary materials, equipment, enchanted books and potions. Color is independent of ownership; describe any actual binding explicitly. Preserve existing major-artifact colors and check native reward tooltips as well as preview cards.
