# Changelog

## 2.3.1

Release date: 2026-09-04

### New features

- No new gameplay features. This is a focused, save-compatible reliability hotfix for the existing 2.3.0 Living Village Network release.

### Bug fixes and improvements

- Froze Daily and Weekly shared-turn-in eligibility when party inventory requirements are consumed. Previously synced members retain their reconnect-safe completion, while late joiners and stale pre-consumption offers can no longer inherit a free reward.
- Separated the eight active Wayshrine slots from historical village records. A ninth and later connected village now receives its own coordinates, bond, request, and Living Village Network state instead of silently aliasing village index 7; a defensive limit of 1,024 historical records refuses further registration without changing existing data.
- Made the Notice Board payload codec symmetric with one shared maximum of eight offers. The server now announces and serializes exactly the same bounded count the client reads, leaving the following profile field aligned even when an oversized internal list is supplied.

### Verification

- The Java 25 test suite passes all `73` tests in `24` suites. Resource and localization validation confirms `2336` matching English, German, and Spanish keys for 26.2 and `1898` for each maintained 2.1.1 line. Minecraft was not launched for this targeted audit.

## 2.3.0

Release date: 2026-09-03

### New features

- Added the Living Village Network: permanent Known/Trusted/Allied bonds now coexist with visible, mutable village conditions, identity-specific needs, bounded supply cycles, and three server-authoritative Guild Notice Board solutions.
- Connected the network to need-aware freight, repairable route consequences, careful or bold incident approaches, and renewable Wayshrine energy. Adventure Profiles let servers choose Relaxed, Standard, or Hardened pacing without removing content or creating exclusive rewards.
- Added optional multiplayer guilds with recoverable Leader/Steward/Member roles, shared renown, and one shared network project while keeping story progress, Archive ownership, and unique rewards personal.
- Added five bounded network prestige ranks and a one-time confirmed Steward, Courier, or Wayfarer specialization, plus a complete Journal Network view and the redesigned Guild Atlas, Trust roster, Charter map, and Guild Notice Board.
- Included the complete save-compatible 2.2.1 quality, balance, longevity, and usability pass in this release.

### Bug fixes and improvements

- Made `Shadows on the Trade Road` difficulty-aware, including a completable no-hostile Peaceful path, present-party scaling, and a guaranteed Last Relay fallback.
- Fixed stemless Pumpkin and Melon progress in Autumn Harvest, successful Honey Bottle and Honeycomb tracking, shared Fresh Finery/Woodcutting/Coal Mining profiles, reconnect-safe shared turn-ins, exact-once Anvil Shift-click handling, and explicit specialization confirmation.
- Hardened server authority and validation around Notice Board item removal, route destinations and energy, guild permissions and offline members, schema migration, bounded request generation, and invalid client payloads.
- Reduced large resource and UI code surfaces into focused helpers, stabilized map and Journal rendering, and completed a calmer responsive Notice Board and corrected Atlas icon alignment.
- Removed the obsolete Peace Armor implementation and recipes without deleting unrelated save data, and removed development-only QA fixtures from the Stable command surface.

### Verification

- The final Java 25 build passes all `61` tests in `22` suites. Resource validation confirms `2336` matching English, German, and Spanish localization keys for 26.2 and also passes for both maintained 2.1.1 lines.
- The maintainer completed the requested solo, multiplayer, reconnect, dedicated-server, and interface gameplay checks successfully before the Stable build.

## 2.3.0-unreleased.16

Build date: 2026-09-03

### Notice Board frame closure and Bond spacing

- Raised the complete illustrated Notice Board interior by four UI pixels so its dark-oak header now begins directly beneath the frame's upper rail instead of exposing a black transparent strip.
- Shifted every interior-bound item, text line, selection underline, detail bar, hover target, and Bond label by the same amount to preserve the `.15` composition exactly.
- Raised the three Bond-state seals and their connector by four UI pixels, creating a clearer visual gap above the footer buttons without changing their state or interaction behavior.

### Verification

- The fresh Java 25 test/build passes with `63` tests in `23` suites. The resource validator confirms `2364` matching EN/DE/ES keys, and both archives embed `2.3.0-unreleased.16`. The maintainer-requested client capture confirms the closed upper frame seam and raised Bond-state seals at the real responsive UI scale; the test world was then saved and the client closed normally.

## 2.3.0-unreleased.15

Build date: 2026-09-03

### Calmer Notice Board and centered Charter markers

- Replaced the prop-heavy Guild Notice Board interior with a quieter Journal-aligned parchment composition: three compact selectors, one clear selected-request field, and a restrained Bond area now establish a single reading order without side furniture competing for attention.
- Realigned every item, count, reward, support value, progress bar, Bond label, hover target, and selection hitbox to the new painted regions. The three Bond seals now sit directly on one code-drawn connector instead of relying on decorative circles inside circles.
- Corrected the Charter marker backing geometry. Its shadow and all three octagonal layers now share the exact emblem center instead of extending farther to the right and bottom; the softer shadow also avoids the previous blocky dark offset.
- Kept the new `400 x 207` RGB inner art at a restrained 128-color visual palette for a compact archive and recorded its ImageGen source and post-processing in the asset log.

### Verification

- The fresh Java 25 test/build passes with `63` tests in `23` suites. The resource validator confirms `2364` matching EN/DE/ES keys, the texture regression confirms the new `400 x 207` non-indexed RGB interior, and both archives embed `2.3.0-unreleased.15`. The maintainer-requested client launch confirmed the final Board composition at the real responsive UI scale; the world was then saved and the client closed normally.

## 2.3.0-unreleased.14

Build date: 2026-09-02

### Notice Board cutout and text alignment

- Removed the generated white/gray transparency matte from every edge of the dedicated Notice Board frame. The title plate, rails, corners, and gems now composite cleanly over dark scenery without a rectangular seam.
- Widened the upper-right wallet backing and shifted its dynamic coin strip into the plate. Moved the village subtitle one pixel down and shortened the delivery bar so its amount remains comfortably inside the detail panel.
- Added a regression check requiring the frame to retain an open transparent interior and contain no bright neutral opaque matte pixels.

### Verification

- The fresh Java 25 test/build passes with `63` tests in `23` suites. The resource validator confirms `2364` matching EN/DE/ES keys; the new matte-seam regression passes, and both archives embed `2.3.0-unreleased.14` with the corrected `17,991`-byte RGBA frame. Minecraft was not launched for this pass.

## 2.3.0-unreleased.13

Build date: 2026-09-02

### Notice Board frame and spacing

- Replaced the shared heavy Atlas surround with a dedicated transparent `416 x 234` Notice Board frame. Its slim oak rails, restrained brass corners, wider title plaque, and clear interior let the illustrated board read as one interface instead of a cabinet squeezed inside a second cabinet.
- Moved the selected commission title and its supporting values fully inside the large detail field. The Bond summary now has its own breathing room above slightly smaller status seals, while the footer sits lower against the new narrow rail.
- Added a resource regression check for the new frame's exact runtime dimensions, true RGBA transparency, and non-indexed color mode.

### Verification

- The fresh Java 25 test/build passes with `63` tests in `23` suites. The resource validator confirms `2364` matching EN/DE/ES keys; the texture regression test confirms the dedicated `416 x 234` non-indexed RGBA frame, and both archives embed `2.3.0-unreleased.13`. Minecraft was not launched for this pass.

## 2.3.0-unreleased.12

Build date: 2026-09-02

### Notice Board visual follow-up

- Re-rendered the simplified Notice Board at its native `400 x 207` detail density instead of the deliberately coarse effective `200 x 104` grid. A restrained 128-color visual palette keeps it Minecraft-like and compact without the oversized 2x2 pixel clusters seen in `.11`.
- Moved the next Bond threshold five UI pixels down from the dark parchment seam into the clear lower panel and adjusted its hover area with it.
- Increased the size and contrast of compact offer counts, village-need context, reward, support, and progress text so secondary information remains readable against the parchment.

### Verification

- The fresh Java 25 test/build passes with `63` tests in `23` suites. The resource validator confirms `2364` matching EN/DE/ES keys; the texture regression test confirms the `400 x 207` non-indexed RGB resource, and both archives embed `2.3.0-unreleased.12`. Minecraft was not launched for this pass.

## 2.3.0-unreleased.11

Build date: 2026-09-02

### Notice Board readability redesign

- Rebuilt the Guild Notice Board around one clear selected commission instead of three equally dense cards. Three compact item selectors now show only the carried/required count; their full title, remaining amount, support, and reward remain available on hover.
- Increased the important text sizes, moved the adventure profile into the village subtitle, reduced repeated labels, and kept one large title, quantity, village need, reward, support value, and delivery bar in the central detail panel.
- Replaced the oversized circles-within-circles Bond path with three standalone server-authoritative status seals on a restrained connector. Only the next threshold remains permanently visible; the full path and perk explanation remain in its hover card.
- Replaced the painted inner board with a calmer Minecraft-style texture using an effective `200 x 104` art grid, a limited visual palette, and crisp two-pixel clusters. The packaged resource remains the required `400 x 207` RGB PNG but falls to roughly `28 KB`.

### Verification

- The fresh Java 25 test/build passes with `63` tests in `23` suites. The resource validator confirms `2364` matching EN/DE/ES keys; the texture regression test confirms the required `400 x 207` non-indexed RGB resource, and both archives embed `2.3.0-unreleased.11`. Minecraft was not launched for this pass.

## 2.3.0-unreleased.10

Build date: 2026-09-01

### Bug fixes and QA clarity

- Raised all five Trust-tab emblems by two UI pixels so their artwork is visually centered inside the round atlas frames.
- Replaced the combined specialization rejection with precise errors for an unknown key, insufficient network rank, or an already permanent choice. The rank-2 requirement remains unchanged.
- Clarified and highlighted that Solo Fix QA cleanup belongs after test 4/4; cleanup intentionally restores the synthetic renown and therefore makes the specialization test unavailable again.

### Verification

- The fresh Java 25 test/build passes with `63` tests in `23` suites. The resource validator confirms `2364` matching EN/DE/ES keys, and both archives embed `2.3.0-unreleased.10`. Minecraft was not launched for this pass.

## 2.3.0-unreleased.9

Build date: 2026-09-01

### Bug fixes

- Fixed the client bootstrap crash introduced by the `.7` Anvil shift-click guard and exposed by the first `.8` manual launch. Its injected runtime contract no longer lives inside the Mixin-owned `de.quest.mixin` package, so transformed Minecraft menu classes may load and implement it normally.
- Added a regression test that keeps runtime contracts referenced by transformed targets outside the configured Mixin package. No quest, save, command, or gameplay behavior changed.

### Verification

- The fresh Java 25 test/build passes with `63` tests in `23` suites. The resource validator confirms `2361` matching EN/DE/ES keys, both archives embed `2.3.0-unreleased.9`, and the runtime JAR contains the contract only as `de.quest.access.ForgingQuickMoveState`. No additional client launch was performed.

## 2.3.0-unreleased.8

Build date: 2026-08-31

### Admin QA

- Replaced the broad Unreleased all-systems test setup with the focused `/vq admin unreleased fixtest [player]` solo fixture for the gameplay-sensitive `.7` fixes. It prepares isolated stemless Pumpkin/Melon blocks, two full and one invalid Honey harvest target, a real Anvil repair/Shift-click diagnostic, and the minimum synthetic renown needed to preview and confirm a specialization.
- Added staged `autumn`, `honey`, and `anvil` helpers because only one Daily can be active at a time, plus marker-only inventory `fill`, Anvil `status`, guide, and `cleanup` helpers. The Anvil phase starts the existing Admin Core Test objective and supplements its idempotent completion flag with an exact real-hook dispatch counter; no objective is incremented artificially.
- The fixture refuses existing party members, accepted/completed or current-progress Dailies, non-Overworld setup, and existing permanent specializations. Cleanup removes only recorded matching blocks, tagged filler, fixture Daily state, and the synthetic renown delta; unrelated inventory and real renown gained during QA remain untouched.
- Removed the old `/vq admin unreleased testsetup`, `guide`, `conditions`, and `lastrelay` commands and their active localization/documentation. Multiplayer reconnect and pooled-progress cases remain explicitly outside this Solo fixture.

### Verification

- The fresh Java 25 test/build passes with `62` tests in `22` suites. The resource validator confirms `2361` matching EN/DE/ES keys, and both archives embed `2.3.0-unreleased.8`. No Minecraft client or manual gameplay test was started.

## 2.3.0-unreleased.7

Build date: 2026-08-31

### Architecture and reliability

- Audited every active Daily and Weekly objective against its authored target, event hook, shared profile, turn-in rules, completion, and reset path. Existing action/drop correlation, mature-crop and right-click-harvest handling, Fortune-aware drop tracking, furnace/crafting/trade/breeding/shearing hooks, atomic delivery consumption, and shard-bonus target profiles remain intact.
- Replaced the Beehive interaction's ordinal-based game-event injections with hooks tied directly to the successful shears-damage and bottle-consumption operations. Both paths remain server-only and still require a full Bee Nest or Beehive.
- Added targeted regressions for stem-independent Autumn Harvest fruit, valid/invalid honey harvesting, every corrected Daily party profile, and persistence of the shared turn-in ledger.

### Bug fixes and improvements

- Autumn Harvest now counts pumpkins and melons broken after quest acceptance regardless of whether a generated or player-grown stem is still adjacent. Inventory that existed before acceptance still does not count, and each block-break event advances only once.
- Fixed shared Daily profiles for Fresh Finery, Woodcutting, and Coal Mining. Parties now pool the current pet-collar recolor counter, both Woodcutting objectives, and the Coal Mining quest's actual raw-coal key while retaining the legacy collar completion flag for old saves.
- Made shared inventory turn-ins reconnect-safe. Once an eligible party claim consumes its bundle, a persisted server-owned ledger lets grace-period members claim the same shared completion without submitting the items again; leaving or expiring from the party copies that proof to personal quest state, while normal day/week resets clear it.
- Prevented partial-inventory anvil shift-clicks from dispatching the same successful output through both the normal result hook and quick-move fallback.
- Made the one-time Network specialization explicitly two-step. The first command previews the permanent choice and benefit; only the same command with `confirm` applies it.

### Verification

- The fresh Java 25 test/build passes with `60` tests in `21` suites. The resource validator confirms `2354` matching EN/DE/ES keys, and both archives embed `2.3.0-unreleased.7`. No Minecraft client or manual gameplay test was started for this audit pass.

## 2.3.0-unreleased.6

Build date: 2026-08-31

### Architecture and reliability

- Replaced viewport-relative surface-map rasterization with buffered, world-aligned cells. Wayshrine and Trade Route maps now reuse overlapping raster data while panning, and projection regression tests prove that the same world position retains the same pixel phase after moving away and back.
- Replaced the Journal Network tab's flat line payload with structured summary, village, and guild records. The client now builds independently collapsible cards instead of reconstructing a long server-formatted text block, and symmetric sender/receiver limits prevent oversized future lists from shifting the packet stream.
- Added resource regressions for Atlas backgrounds, all 33 Surveyor's Compass textures, the Apiary painting, Notice Board art, Village Ledger, and all five Trust icons, including exact dimensions, RGB/RGBA depth, alpha expectations, and protection against palette quantization.
- The fresh Java 25 test/build completed with `57` tests in `19` suites; the resource validator confirmed `2353` matching EN/DE/ES keys, both archives embed `2.3.0-unreleased.6`, and the prior `.5` plus published Stable artifacts remain byte-identical. No client was started for this pass.

### Bug fixes and improvements

- Made `Shadows on the Trade Road` rescue setup reliable on Peaceful and other difficulties. Candidate sites are now limited to roughly `320–500` blocks, accepted only when the entire spaced merchant formation fits on dry safe ground, and relocated or clearly rescheduled if the final spawn still fails. The admin rescue test also establishes the required night immediately; Peaceful keeps its visible merchants and escort path while spawning exactly zero hostile waves.
- Stopped Wayshrine-map pixels from subtly changing after a drag. Panning no longer invalidates the complete surface texture on release, while zoom and explicit recentering still rebuild deliberately.
- Fixed the Wayshrine rename field's pale doubled-looking text with a darker single-pass ink treatment, clearer placeholder, and stronger parchment contrast.
- Rebuilt the crowded Network tab into a next-action card, compact rank/profile summary, one condition-accented card per village, and a separate guild summary. Crisis, Strained, Stable, Recovering, and Thriving remain readable in text and gain red, amber, gold, teal, and green secondary accents.
- Reworked the Guild Notice Board around a new text-free `400 x 207` inner illustration with three integrated offer plaques, a fixed profile ribbon, two-line localized offer titles, aligned item/amount/reward/support zones, and click targets matching the painted cards. Selection and delivery remain server-authoritative.
- Reduced Charter landmarks from `30` to `22` pixels and proportionally reduced their backing, lock, completion mark, hover offset, and hitbox. Replaced the over-detailed Village Ledger with a simpler book-and-compass medallion, and replaced all five Trust emblems with centered limited-palette Minecraft-style silhouettes.
- Downscaled the three Guild Atlas backgrounds to `1184 x 592`, all 33 Surveyor's Compass textures to `128 x 128`, and the Apiary Charter painting to `256 x 256` using Lanczos while retaining RGB/RGBA data. Those targeted resources fell from `13,820,358` to `5,125,859` bytes, a reduction of about `8.69 MB` (`62.9%`).

## 2.3.0-unreleased.5

Build date: 2026-08-30

### New features

- Added non-destructive visible reactions for every Living Village condition. Crisis, Strained, Stable, Recovering, and Thriving villages now use bounded ambient particles, while a completed recovery can play a short celebration; no player blocks are replaced.
- Guild invitations, promotions, leadership transfers, and removals now accept cached player profiles, so an operator or guild leader can manage a member who is currently offline without touching that member's personal story, Archive ownership, or unique tools.
- Added the targeted `/vq admin unreleased testsetup [player]` QA entry point for mechanics that still need real play. It prepares five village identities and conditions, route consequences, Wayshrine energy, rank-2 guild state, rank-2-threshold network renown, required delivery items, an interactive ordered guide, and a Journal launch. Existing guilds are preserved.
- Added focused `/vq admin unreleased guide [player]`, `/vq admin unreleased conditions`, and `/vq admin unreleased lastrelay` helpers. The guide intentionally excludes deterministic removal, resource, migration, NBT, balance-math, and invalid-payload checks already covered by automation.

### Architecture and reliability

- Split party persistence, inventory handling, session identity/lifecycle, member messaging, runtime records, and view construction out of `QuestPartyService`. The remaining facade keeps the public integration surface while domain rules live in focused package-private modules.
- Split trade-route legacy data access, geometry/ferry calculations, route-inventory handling, surface resolution, incident approaches, and survey serialization/validation out of `TradeRouteService`.
- Split daily-quest catalog and inventory rules, Questmaster party/guild payload construction, Living Village Notice Board logic and atmosphere effects, Journal content/data/cards, Living Network commands, admin UI previews, and Living Network payloads into dedicated modules.
- Replaced positional Journal tab-icon coupling with icon keys owned by each section and retained the packaged-resource regression test for all five icons.
- Added explicit automated coverage for route geometry and surveys, Last Relay guarantees, Shadows balance/Peaceful behavior, Living Village state/migration/admin fixtures, request selection, guild persistence and roles, party persistence, Prosperity bounds, Journal icons, and continued absence of the removed Peace Armor resources.

### Bug fixes and improvements

- Kept every new Notice Board action server-authoritative: the server regenerates and validates the selected offer before removing inventory, so stale or forged client actions cannot consume supplies.
- The targeted QA fixture binds its first synthetic route and village record to the current real inhabited village when one is available. This permits a real Notice Post delivery test while retaining the other four identities as synthetic inspection fixtures.
- Last Relay QA now prepares fresh baseline freight and positions Route 1 immediately before its midpoint, allowing the story-critical incident bypass to be verified without waiting through a full route cycle.
- Offline/reconnect acceptance remains an explicit two-profile manual check; automated persistence tests cover stored party/guild state, but do not pretend to replace real client disconnect timing.

## 2.3.0-unreleased.4

Build date: 2026-08-30

### Bug fixes and improvements

- Fixed the new Journal Network tab rendering Minecraft's black-and-magenta missing-texture tile. The tab now uses the packaged `icon_social` network/community artwork, each section owns its icon key instead of relying on a parallel positional array, and a regression test verifies that all five Journal tab textures are present in the runtime resources.

## 2.3.0-unreleased.3

Build date: 2026-08-29

### Bug fixes and improvements

- Removed the obsolete alpha-era Peace Armor side system. Its four shapeless recipes, automatic recipe-book discoveries, lore-driven global monster-damage cancellation hook, dedicated handler, exclusive white-flower tag, and localization entries are gone. Existing pieces remain valid vanilla leather armor with their saved custom name and lore, but no longer grant probabilistic or full-set monster immunity; no custom item registry ID or world progress is removed.

## 2.3.0-unreleased.2

Build date: 2026-08-29

### New features

- Completed the planned `Living Village Network` content batch. Each Notice Board now presents three server-authoritative choices generated from village identity, current need, condition, prior delivery, personal trust, Adventure Profile, network specialization, and optional guild project. Choices remain deterministic, avoid immediate repeats when possible, always use ordinary obtainable resources, and are revalidated before inventory removal.
- Connected route arrivals now strengthen the concrete destination village and build renewable Wayshrine energy. Matching named freight contributes more, freight offers prioritize current recorded needs and identify matching routes, every three energy steps create a bound-shrine charge, and a completed supply project can create another charge. Magic Shards remain the fast reserve rather than the only sustainable source.
- Added explicit `careful` and `bold` incident plans per route. Careful operation reduces normal incident frequency, destination strain, and rewards while adding a protected journey; Bold increases incident opportunities and rewards at higher repairable risk. Required story incidents still bypass normal prevention.
- Added server-owned `RELAXED`, `STANDARD`, and `HARDENED` Adventure Profiles for request quantities, hostile pressure, incident protection, and bounded failure strain. Profiles never remove content or grant exclusive rewards; Peaceful remains a zero-hostile completion path.
- Added optional persistent multiplayer guilds with Leader, Steward, and Member roles, invitations, shared renown, and Common Reserve, Waystation, or Archive Exchange projects. Personal story rewards, Guild Archive ownership, and unique tools remain personal.
- Added a five-rank bounded network-prestige path with cosmetic honor titles and a one-time Steward, Courier, or Wayfarer specialization. The Journal now has a dedicated Network tab containing the current best action, every village's trust/condition/need/supply/energy, profile, guild state, prestige, and specialization.

### Bug fixes and improvements

- Upgraded living-village persistence to schema `2` with safe defaults for route arrivals, repairable failures, energy progress, owner renown, and specialization. Optional guild data uses its own versioned world SavedData, so a missing guild layer cannot damage personal quest state.
- Trusted and Allied bonds now provide concrete visible commission benefits through lower quantities and improved rewards; Allied work also supplies more. Village condition changes urgency and reward without creating an unbounded quantity ladder.
- Route incident failure can no longer silently affect only an abstract route status: it produces a bounded destination consequence with a hard repair floor, while successful board work and freight restore it. Offline time still causes no supply decay or missed-day punishment.
- The upgraded Lens provides a one-time in-world introduction to the system, Caravan Ledger bond cards show condition, need, supply, and Wayshrine energy, and route details expose the selected incident plan.
- Full progress reset also removes shared guild state. Added migration, NBT roundtrip, three-choice generation, anti-repeat, trust/profile balance, route-energy, repair floor, guild-role/project, prestige, specialization, and Peaceful-profile regression coverage.
- Added Leader-only guild transfer and member removal so an offline member cannot permanently trap the founder in a guild. Route energy now advances only when the destination has a bound Wayshrine, preventing generated charges from disappearing into an unavailable endpoint.

## 2.3.0-unreleased.1

Build date: 2026-08-29

### New features

- Began `Living Village Network` with a dedicated, explicitly versioned SavedData model. Every discovered Granary, Forge, Pasture, Apiary, and Archive village now receives a bounded supply condition, one of two identity-specific current needs, support progress, completed supply cycles, and revision metadata without replacing its existing Known/Trusted/Allied bond.
- Existing 2.2.x villages migrate lazily and idempotently to a safe `Stable` supply state. Missing or malformed legacy fields receive deterministic defaults, and repeated inspection or reload does not duplicate or advance village state.
- Guild Notice Post deliveries now improve the village's visible supply state. A delivery matching the current need contributes more support, while every previously valid request remains useful and completable; finishing a supply cycle selects the village identity's other need instead of raising quantities forever.
- The Cartographer's Lens inspection and Guild Notice Board expose the current village condition, primary need, and exact supply progress in English, German, and Spanish.

### Bug fixes and improvements

- Kept personal village trust and mutable network condition as separate data, so a future shortage cannot remove an earned Wayshrine tier or story unlock.
- Full admin progress resets and the marker-guarded shrine test-profile setup/reversal now clear the matching 2.3 network records, preventing synthetic villages from leaking into later real village indices.
- Added serialization, migration, idempotence, bounded-cycle, identity-need, and matching-delivery regression coverage for the 2.3 foundation.
- Includes the complete save-compatible `2.2.1-unreleased.1` quality, balance, accessibility, economy, recipe discovery, Journal, Last Relay, and Peaceful-story work.

## 2.2.1-unreleased.1

Build date: 2026-08-28

### New features

- Added difficulty- and nearby-party-aware scaling for the `Shadows on the Trade Road` combat encounters. Peaceful uses an explicit escort-success path with no hostile waves, so Vanilla mob removal cannot block the story.
- Added a story-safe `Last Relay` incident guarantee: after its fresh freight contract arrives, the next eligible route midpoint starts an incident regardless of route quality, patrols, Roadwarden charges, or the normal random roll. `MAP_ONLY` worlds may complete the relay through the freight arrival itself.
- Recipe-book discoveries now trigger from each recipe's signature ingredient or story component instead of requiring the complete crafting bundle at once.

### Bug fixes and improvements

- Rebalanced Village Festival and Guild Ceremony into small, self-funding three-use services with a guaranteed minimum bonus per payout; buying either service can no longer be an unavoidable currency loss.
- Replaced the harshest prime-number story requirements with readable stack-aligned goals, reduced the longest Restless Pens ride, and lowered the crowd requirements for Market Day and the Shepherd's Call.
- `The Quiet Hives` now accepts the required carried honey supplies regardless of how they were obtained, while direct harvesting still records normal progress.
- `The Master's Edge` accepts one Sharpness and one protection-family enchanted book as its training proof, including books acquired outside villager trading; the crafted and enchanted equipment turn-in remains intact.
- The Journal overview's `Next step` card now shows the highest-priority active quest and its current authoritative progress instead of generic advice.
- Corrected Wayshrine charge wording so two-charge Known journeys are never described as using a single charge.
- Updated the active-line README, shrine documentation, economy documentation, generated config heading, and Guild Path location for the current 2.2.x line.
- Retained the marker-guarded shrine test-profile recovery command from the private `2.2.0-unreleased.3` recovery build.

## 2.2.0-unreleased.3

Build date: 2026-08-28

### Bug fixes and improvements

- Added the temporary, marker-guarded `/vq admin shrines reverse [player]` recovery command. It removes the persistent shrine test profile, clears its five synthetic routes and shrine/archive state, subtracts the fixture's 300 Silvermarks from both wallet and earned-currency statistics, and relocks the Caravan Yard unless the real Empty Caravan story already earned it. Genuine story-derived Lens and Sigil flags remain intact; inventory items are intentionally untouched.

## 2.2.0

Release date: 2026-08-28

### New features

- Added a dedicated `Village Quest` tab to the vanilla crafting recipe book. All eight mod recipes are grouped there and unlock automatically, with the normal recipe toast, as soon as the player carries the complete required material set; the craftable-only filter and vanilla recipe placement continue to work normally.
- Added the journal `Guild Atlas` as a long-term progression world. `Guild Path` follows eleven tools and relics across Homestead Bay and the shrine road; `Charters` turns all eight permanent village projects into landmarks; `Trust` presents the five parallel reputation disciplines as a friendly static guild roster. The two landscape pages use localized server-backed status markers, hover-only detail cards, current-goal recentering, and fixed 100% scale, while the roster keeps all five standings and milestone bars visible together.
- Replaced Guild Notice Post chat inspection with a dedicated Village Quest request interface. It presents the requested stack, carried progress, Silvermark reward, current Known/Trusted/Allied bond, completed-request count, next threshold, next perk, wallet, and a server-authoritative delivery action.
- Rebalanced Guild Notice Post progression into a multi-day village relationship: each village accepts one delivery per configured daily reset, reaches Trusted after two completed requests and Allied after eight, and progresses independently from other villages. The catalog now contains twenty requests—four per village identity—with fivefold delivery quantities, difficulty-scaled rewards, and a deterministic no-repeat rotation. Successful delivery atomically removes the exact requested inventory amount; locked or incomplete attempts remove nothing. Homestead Wayshrines now count as Allied endpoints instead of lowering an earned village rate.

### Bug fixes and improvements

- Removed the Guild Path teaser from the Questmaster's Special category and moved long-term progression into the journal itself, eliminating the unclickable phantom `1` badge and keeping Special reserved for real quest or Archive offers. Server counts still require matching category IDs, and the client suppresses category counts whenever no matching entry was actually received.
- Replaced the old journal Trust and Legacy text lists with the unified Guild Atlas. Atlas and ordinary journal pages now share one thin spruce/brass/teal outer frame without stacking a second border, avoiding doubled edges, corner seams, and right-side gaps. The gold title plaque visibly overlaps the artwork, `Close` lives directly on the content, and coins plus redundant zoom controls are absent. Compact high-contrast detail cards on the map pages choose the free side of their hovered landmark, never cover the pointer or source marker, and disappear as soon as the pointer leaves both; no click is required.
- Replaced the pale Atlas sidebar book with the stronger teal-and-gold Guild volume and moved the parchment-and-quill emblem to Guide. Trust no longer behaves like a third draggable landscape: it now uses a bright generated parchment roster inside the existing outer frame, five separately generated and deliberately downsampled `32 x 32` emblems, visible rank/reputation text, colored milestone bars, and full next-unlock hover details. Its native-screen polish pass centers the five-row group higher and farther left without losing the botanical edge ornaments and moves every localized label clear of the medallion leaves. Farming, crafting, animals, trade, and wardens remain parallel and unlocked rather than receiving a false sequential route. Charters now uses eight round generated map medallions instead of surrounding tiny plaque items with coarse square inventory-style frames; progression badges and hover details remain server-backed.
- Removed the superseded high-resolution draggable Trust-map texture from the runtime package; the active static roster is now the only Trust-page background shipped in the mod.
- Rebuilt all eight Charter map medallions around large single-purpose silhouettes, a calm opaque interior, and a slimmer brass ring. Their compact runtime footprint now sits on a closely fitted dark pixel-rounded backing, keeping ledger, hive, forge, market, pasture, watch, caravan, and Wayshrine symbols distinct from the painted map without returning to square item frames.
- Reworked the six purchasable Charter decorations into shallow octagonal `1 x 1` wall medallions using matching `64 x 64` faces. Existing IDs, Pilgrim project unlocks, drops, support checks, and wall orientation remain intact, while the model and directional selection shape now follow the rounder silhouette instead of the old rectangular plaque.
- Removed the remaining one-pixel matte remnants from the shared Atlas frame: both left corner fringes, the inner-right rail, the long outer-bottom seam, and the neutral row below the top oak rail. Path, Charters, Trust, the ordinary Journal, and the Guild Notice Board now keep a clean transparent silhouette and a closed inner edge against bright world backgrounds.
- Made the configured inventory key close every Village Quest screen consistently. Text entry remains protected while renaming a Wayshrine, so letters bound to the inventory key can still be typed normally.
- Added a localized first-open Guild Atlas tutorial and progression mist for the two landscape pages. The one-time overlay explains dragging, hover landmarks, and the next currently reachable goal; afterward, a dark map-anchored fog keeps unreached regions subdued and clears around completed or currently available milestones without shimmering while the painted world is dragged. The static Trust roster intentionally has neither dragging nor fog because all five tracks are available in parallel.
- Fixed Guild Atlas Trust detail cards showing literal placeholder braces instead of the localized reputation value and rank.
- Moved the Cartographer's Lens introduction and shrine-story availability to two installed routes instead of three, reducing the gap before the new content while keeping its completed-freight and Caravan Yard requirements.
- Reworked Broken Heartstone recovery into an intuitive owner-only mining action. Its central Milestone now has wool-like mining time with the existing stone sound, yields the Cracked Shrine Core exactly once, suppresses its decorative block drop, removes the temporary trail map, and remains protected from other survival players.
- Added a shared conservative wilderness-site validator for the Broken Heartstone ruin and Starreach cache. It requires dry, level natural ground and rejects villages, route/home/shrine anchors, old inhabited chunks, tracked player terrain edits, block entities, and common construction traces before replacing terrain.
- Tagged both quest maps, remove them automatically at successful recovery, and added controlled trail abandonment. Reaccepting preserves and reissues an already placed unresolved site rather than generating repeated ruins; normal quest progress no longer exposes fallback coordinates.
- Opened Wayshrine binding to recorded `Known` villages with progression-scaled terms: Known endpoints use double fare, two charges, and ten minutes; Trusted use normal fare, one charge, and five minutes; Allied use normal fare, one charge, and four minutes. The guest multiplier remains layered on top, and destination tooltips expose the exact terms before travel.
- Rebuilt the Guild Notice Board as a smaller dedicated screen with clamped carried progress, compact request/reward presentation, and a horizontal bond path instead of reusing an oversized generic journal board.
- Added `/vq admin uitest noticeboard [known|trusted|allied]` as a non-persistent direct preview of every Guild Notice Board layout state for localization and GUI-scale checks.
- Added `/vq admin uitest wayshrine [owner|guest]` as a non-persistent five-shrine preview with mixed bond tiers, representative charges and fares, and inert synthetic indices for safe screenshot and layout checks.
- Polished the dedicated Guild Notice Board after native fullscreen and compact-window review: its wallet now clears the upper-right crystal fitting and aligns vertically with the title plaque, while the parchment backing extends safely beneath every scaled frame edge without a transparent seam.
- Rebuilt the Guild Notice Board around a text-free illustrated inner asset at the Guild Atlas's native frame size. A pinned commission parchment now gives the requested item a large circular display, separates the wax-seal reward, integrates delivery progress, and presents Known, Trusted, and Allied as three brass medallions joined by a teal guild cord. Dynamic localized text, inventory items, rewards, server-authoritative delivery, and wallet values remain code-rendered. The final readability pass replaces the medallion placeholders with dedicated `24 x 24` current/completed/locked guild seals, centers all three precisely in their painted sockets, subdues locked stages, moves the next unlock into the bond divider, reduces the request copy to the still-needed amount, strengthens small-label contrast, and keeps the complete bond sequence plus Wayshrine explanation in the hover tooltip.
- Made reissued Guild Archive tools update visibly as soon as an older serialized copy returns to the carried inventory. Superseded relics receive their red invalid name and lore within five server ticks and lose their enchanted glint while the existing server-authoritative generation check remains final.
- Removed the completed one-off admin fixtures `shrines ruintest`, `shrines boardtest`, `shrines crafttest`, and `archive testsetup`, including their unreachable state flags, methods, localization, and stale documentation. Broader diagnostics, resets, and reusable non-persistent UI profiles remain available.
- Fixed the server-config startup log using unsupported formatted placeholders, eliminating the Log4j parameter-mismatch warning.
- Fixed active Wayshrine crystals becoming visibly tick-stepped in long-running worlds after float precision discarded the per-frame partial tick. Rotation and bobbing now retain frame interpolation without changing their speed or range.

- Sunk the authored Broken Heartstone ruin one block into its validated natural site. Its floor now replaces the surface layer instead of sitting as a raised platform above it.
- Rebuilt the Guild Milestone's animated teal rune treatment on all four sides and removed the two one-sided lower protrusions from the Guild Wayshrine.
- Expanded the Guild Notice Post into an approximately two-block-wide, two-block-tall guild board with long oak legs, brass joinery, a clean wooden reverse, and a generated 128-pixel notice surface showing posted routes, supply tallies, seals, and request sheets. The live localized request remains available through interaction.
- Removed coplanar faces and out-of-range automatic UVs from the expanded board, eliminating camera-angle flicker and atlas bleeding around its frame and feet. The Guild Milestone's animated rune is now carved directly across all four pillar faces instead of appearing as four attached plaques.
- Added a reversible model-only material trial for the shrine set: Guild Wayshrine and Notice Board timber now reference vanilla Spruce Log grain, while shrine masonry references Deepslate Tiles and Deepslate Bricks for exact vanilla texel density. No authored source texture was overwritten. Newly placed Notice Boards also rotate their posted face toward the placing player.
- Refined the accepted Notice Board material trial: upright posts retain Spruce Log bark, while the broad back, feet, and crossbeams use Spruce Planks. Oversized wooden faces are segmented with native texel density instead of stretching one texture across the complete two-block silhouette.
- Finished the shrine-set material pass with a recessed aged-brass crown on the Guild Milestone and a shallow dark-spruce cross brace on the Notice Board reverse. The board front remains unchanged while its back now reads as supported joinery rather than an empty wooden wall.
- Refined the final world-model review: the Notice Board artwork now fits fully between its uprights, four narrow longitudinal braces replace the bulky rear cross, and the Milestone crown carries a generated transparent broken-crystal inlay over a calm polished-deepslate recess.
- The protected Broken Heartstone Milestone now collapses without dropping itself immediately after its Cracked Shrine Core is recovered. The recovery flag and item are committed first, then a resonant amethyst cue and the normal block-break effect remove the one-use quest artifact from the world without allowing duplicate cores.
- Fixed valid dry terrain near a dimension's minimum build height being rejected unconditionally. The authored `7 x 3 x 7` ruin now spawns correctly in shallow Superflat and compatible custom dimensions while retaining the existing water, obstruction, slope, village-distance, chunk, and world-border safeguards.
- Verified the complete reveal path ingame in the shallow Superflat test world: the trail map marked X 524 / Z 500 and the normal shrine scan placed the authored ruin at that position without using a direct structure-placement command.

## 2.2.0-unreleased.1

Build date: 2026-08-20

### New features

- Added `The Shrines Between Roads`, a six-chapter post-Caravan-Yard story arc that begins after `The Empty Caravan`, three connected villages, and three completed freight contracts. It introduces village study, a generated ruined shrine site, three guild craftspeople, the first activation, a trust chain, and a final live freight relay.
- Added persistent identities for connected villages: Granary, Forge, Pasture, Apiary, or Archive. The Cartographer's Lens records each village, its Known/Trusted/Allied bond, and a rotating local request; Guild Notice Posts accept the delivery and advance that bond.
- Added the two-block 3D Guild Wayshrine with separate active/inactive states, a softly pulsing emissive heartstone, active-only block light, animated particles, owner-bound destinations, safe-arrival validation, distance-based Silvermark costs, cooldowns, and combat/route-incident locks. Trusted connected villages and the Homestead can host a shrine.
- Replaced the compact Wayshrine list with a responsive Village Quest map board: terrain-backed clickable shrine markers, destination cards, distances, coordinates, exact prices, a live cooldown, the shared Crown/Silvermark wallet, and an embedded custom-name editor. Owners keep normal prices and exclusive rename/break rights; guests may share any active physical network without their own Sigil for double the normal travel fee, avoiding duplicate shrines around multiplayer villages.
- Added a dedicated `Village Bonds` page and Wayshrine markers to the Caravan Ledger. The route map now includes recorded village identities, current requests, bond progress, shrine coordinates, and the expanded long-range zoom controls from Unreleased 1.
- Added the Guild Notice Post, Emberglass Lantern, and Guild Milestone as fully modeled placeable 3D blocks with recipes, loot tables, mining tags, and matching voxel collision shapes. Their generated 64x64 material art now carries slate masonry, aged brass, oak grain, pinned parchment, and an irregular animated Emberglass glow instead of flat placeholder colors.
- Added the Wayfarer's Sigil, the temporary Cartographer's Lens, Cracked and Restored Shrine Cores, and the optional Guild Courier's Satchel. After the introductory three-village study, the Lens is fitted permanently into the Caravan Ledger; the Sigil also handles shrine, milestone, and route inspection so the story does not burden players with separate Mallet or Waystone inventory items. Dormant and attuned waystones now exist as the inactive and awakened crystal states of placed Wayshrines.
- Added three shrine-network route incidents: `Shattered Waystone`, `Stranded Shrine Pilgrims`, and `Runes Gone Dark`; they enter the incident pool only after the Wayfarer's Sigil has been unlocked.
- Added `/vq admin shrines testsetup [player]` for a self-contained shrine-content test profile. The active resource line includes all new keys and the finished runtime assets required by the mod.
- Magic Shards can now charge a bound Wayshrine with five shared journeys, up to fifty stored per shrine. Charging requires a second right-click on the same shrine within ten seconds, preventing accidental shard loss. The Wayshrine board shows the live charge reserve and lets each traveler choose between one arcane charge or the normal Silvermark price; shared guests may contribute and consume the same public reserve.
- Added the Questmaster's `Guild Archive` recovery ledger for unique earned tools. Reissued relics carry a server-authoritative owner and serial generation, making every older copy inert even inside unloaded chunks or third-party storage without scanning those inventories. The first replacement is free, later replacements cost `4` or `8 Silvermarks`, each record has a one-Minecraft-day cooldown, and every request requires a second confirmation within ten seconds.
- Added stage-aware free recovery for the temporary Cartographer's Lens and Cracked Shrine Core. A first lost Restored Shrine Core is restored before the first binding; after the network is established, further cores can be recommissioned from renewable materials instead of permanently locking shrine construction.

### Bug fixes and improvements

- Unified the Broken Heartstone ruin and Starreach Ring cache behind a dry-land target selector. Their distant maps now prefer non-ocean, non-river biomes without loading remote chunks; if the exact marked terrain is still waterlogged, obstructed, or unsuitable, the search follows the player at bounded intervals until nearby dry open ground is available instead of retrying the same invalid coordinates forever.
- Protected the unrecovered Broken Heartstone milestone from survival breaking, pistons, and explosions. The protection covers every player who could otherwise lose the unique quest interaction, while creative administrators and the recovered post-quest decoration remain removable.
- Completed the German and Spanish localization pass for all new shrine-story, village-bond, route-event, Wayshrine, item, Satchel, and map text. All three language files retain matching keys and placeholders.
- Wayshrine travel is server-authoritative and rejects forged target indices, remote activation, missing or inactive destinations, unsafe arrival spaces, insufficient funds, active combat, and unresolved route incidents.
- Fixed held-item interaction on Guild Wayshrines so the Wayfarer's Sigil and Magic Shard reach the block reliably instead of depending on Minecraft's empty-hand fallback.
- Replaced the fixed-offset forgotten shrine with a Ring-quest-style trail map and randomized distant search region. On approach, the quest resolves dry, nearly level natural ground away from villages and places the authored `7 x 3 x 7` mossy Heartstone ruin exactly once; its carved Guild Milestone yields the Cracked Shrine Core.
- Added isolated travel-cost boundary coverage and kept the existing route, ferry, and reset regression suite intact.
- Rebuilt the Wayshrine board around its own generated Village Quest asset: the terrain map now sits in a recessed map frame, five destination slots occupy a dedicated ledger, shrine/charge status has its own ribbon, the wallet clears the brass corner, and three fixed footer wells use concise actions with detail tooltips instead of clipped destination names.
- Refined the rebuilt board after fullscreen review: the wallet is centered in the right-hand oak header, the redundant floating destination caption is gone, the charge counter clears its gemstone fitting, and every footer label now sits optically centered in its button well.
- Replaced the regular interpolated lantern and active-shrine pulse with restrained irregular glow timing: long calm holds are interrupted by brief, non-sequential intensity changes instead of a continuous disco-like cycle.
- Replaced the Wayshrine's blocky central heartstone with a compact faceted Magic-Shard crystal that floats freely inside the frame instead of filling both model halves. Full-face crystal UVs keep its cyan-violet facets coherent, its emissive layers pulse on an irregular calm rhythm, and the inactive state remains visibly dormant.
- Repainted the retained Guild Courier's Satchel, Wayfarer's Sigil, Cartographer's Lens, and both Shrine Cores as detailed transparent 32-pixel sprites in the established Village Quest brass, slate, parchment, cyan, and violet visual family. The tall Wayshrine inventory model is also scaled down to clear its slot.
- Refined the Caravan Ledger at distant zoom levels: settlement labels now shrink, shorten, avoid one another, and disappear at the two widest scales instead of piling up at full size, while the percentage badge reports all ten actual zoom steps. The four header tabs and their icons are smaller and centered inside the free left-hand header area so they no longer collide with the page title.
- Scaled the Emberglass Lantern correctly in inventory, hand, frame, and dropped-item contexts instead of rendering the untransformed world model at almost full block size. Clicking a Wayshrine destination card now recenters the embedded map onto that shrine.
- Unified all four new placeable block items to the same compact inventory, hand, frame, and dropped-item transform. The tall Guild Wayshrine and Guild Notice Post now sit centered inside the inventory grid rather than crowding adjacent slots.
- Rebuilt the Wayshrine heart as a small asymmetric three-part Magic-Shard cluster driven by the existing client-only block-entity renderer. Active crystals rotate and bob with a calm irregular cyan/violet emissive pulse; inactive crystals remain dim and static, with no server ticker, chunk loading, packets, or external rendering dependency.
- Made the Wayshrine map use the main Caravan Ledger map's exact ten zoom factors, percentage labels, direct stepped wheel behavior, panning scale, and network-bound coverage. Destination-card clicks now recenter without an extra interpolated zoom animation.
- Active Wayshrines no longer require every traveler to own or hold a Wayfarer's Sigil. The registered owner's Sigil is required only to bind, awaken, and inspect; shared guests can open and use an active network directly for double the coin price, while rename and survival breaking remain owner-only.
- The shrine-specific admin fixture now clears the incidents intentionally seeded by the general route fixture, so `/vq admin shrines testsetup` can always exercise travel. The normal `/vq admin routes testsetup` keeps its incident scenarios.
- Restored hidden compatibility registrations for the removed Roadmender's Mallet and Dormant/Attuned Waystone prototype IDs. Old Unreleased saves load cleanly and migrate carried prototypes to the retained Sigil or placed-Wayshrine flow instead of logging unknown-item decode errors.

## 2.1.2-unreleased.1

Build date: 2026-08-09

### Bug fixes and improvements

- Extended the Caravan Ledger map with six additional zoom-out levels while preserving its previous default scale. A network containing only a Homestead can now show several thousand blocks at once, making villages around 1,000 blocks away practical to locate before registration.
- Fixed Caravan Ledger registration failing from village paths, squares, gaps between structure pieces, and the outer districts of large CTOV settlements. Detection now accepts the complete horizontal footprint of an already-loaded generated village plus a small edge margin; the destination must still contain a living villager and arbitrary villager groups remain ineligible.
- Corrected numbered Sources packaging so its embedded `fabric.mod.json` carries the same resolved Unreleased version as the Runtime instead of the Gradle source placeholder.

## 2.1.1 - Homesteads & Wayfinding

Release date: 2026-08-09

### New features

- A player-built base can now become the home of the trade network as soon as the `Market Charter` grants the Caravan Ledger. Use the ledger twice on safe Overworld ground to confirm a personal `Homestead Trade Post`; existing routes must be removed before moving the home, while every destination must still be a real inhabited vanilla or CTOV village. The later Caravan Yard remains the expansion from one route to five rather than an extra lock on using your own base.
- Added a distinct Homestead map marker, keeping personal bases visually separate from generated village nodes on the ledger and live minimap.
- Added ocean ferry segments to surveyed routes. Mark waypoints while actually traveling through an `#minecraft:is_ocean` biome and the installed route receives a dashed blue sea lane, a bespoke boat marker, and a live arrival timer on both maps. The last safe land point before each sea leg becomes a real boarding anchor: an observed physical group waits there, gathers its formation, and only then transfers into the virtual crossing. Unobserved routes retain immediate background travel without loading chunks. Lakes and rivers never become ferries and must be bypassed or crossed by a constructed bridge.
- Added persistent per-world terrain-map tiles under `.minecraft/village-quest/map-cache`. Singleplayer saves, multiplayer servers, dimensions, and map-quality levels remain separated; live loaded terrain refreshes stale tiles, while retention and a size limit prevent unbounded growth.
- Added safe server and client configuration files under `.minecraft/config/village-quest/`. Reset timezone/day/hour, player-built yards, physical-caravan density, HUD/minimap position and scale, notifications, sounds, marker visibility, map quality/cache limits, and tutorial hints can now be configured without changing saves.
- Added `Reduced` and `Map only` physical-caravan modes. The virtual economy and moving map markers continue normally; reduced mode uses one nearby trader, while map-only mode avoids physical incidents that could not be resolved without NPCs.
- Added the read-only `/vq diagnose` support report. It shows the active reset zone, route/home state, physical-caravan mode, route progress, road quality, waypoints, incidents, loaded traders, possible stuck groups, and orphaned tagged entities. Admins may inspect another player with `/vq diagnose <player>`.
- Added a configurable `.` keybind for toggling the Quest Tracker. It shares the clean `Village Quest` controls category with the existing `,` minimap key.
- Gave all eight active Village Quest tools and relics one coherent Minecraft-style visual family. The Caravan Ledger and Roadwarden Horn now have proper project-specific sprites, while the Magic Shard, Starreach Ring, Merchant's Seal, Shepherd's Flute, Apiarist's Smoker, and Surveyor's Compass received matching hand-finished replacements.

### Bug fixes and improvements

- Extended `Restless Pens: New Pastures` from `930` to `4,800` blocks of horseback travel. Routine distance ticks are now silent, while reaching the full exploration goal keeps its rewarding completion cue.
- Rebalanced `Bakehouse Help` so its fresh-wheat target includes the grain consumed by the required bread. Following the staged instructions now naturally leaves the complete wheat-and-bread delivery bundle for the Questmaster.
- Added a visual milestone bar to every expanded Trust card. It shows current progress toward the next unlock or Mastery rank, including the exact reputation target, instead of leaving long-term advancement as plain prose.
- Corrected the Journal's Prosperity shortcut to follow the same access rule as the server. It now appears after a matching Apiary, Forge, Market, Pasture, or Road Watch project—or existing route access—instead of being exposed by the always-present Village Ledger; the Guide now explains that the Market Charter introduces the provisional first route.
- Anchored terrain-map cells, height shading, shorelines, and decorative marks to a fixed world grid. Panning now keeps a constant viewport footprint and reveals another part of those same cells instead of choosing a new screen-relative sample set, so returning to the same area preserves its exact illustrated terrain pattern.
- The full map and minimap now honor client options for player, village, caravan, and route-line visibility. Tracker and minimap position, scale, and background opacity are also applied at render time.
- Daily and Weekly reset calculations now follow the configured server timezone with daylight-saving-safe boundaries. `AUTO` uses the Java timezone of the integrated or dedicated server, so a US-hosted server no longer inherits a hard-coded Central European reset.
- Quest feedback sounds and availability/caravan notices now respect each connected player's local preferences. These settings do not alter authoritative quest or route progress.
- Added isolated regression tests for daily, weekly, non-European timezone, and daylight-saving transitions without changing existing quest turn-in behavior.
- Rebuilt the Surveyor's Compass as a deterministic 32-frame instrument: only the inset direction dial rotates, the aged-brass housing stays perfectly fixed, and readable cardinal marks preserve the established `N points to the target` behavior.
- Removed verified duplicate or unreachable runtime resources, including superseded board art, duplicate loot-table paths, orphaned item models, and an unused sound registration. Compatibility registrations and save-facing legacy IDs remain untouched.
- Restored the Journal Collection icon after the cleanup audit exposed its dynamically constructed resource path; the Collection tab no longer renders as Minecraft's missing-texture tile.
- Split every special-item description into short translated tooltip lines, keeping Merchant's Seal, the Roadwarden Horn, and the other redesigned items readable across smaller windows and higher GUI scales.
- Made the current player position unmistakable on both route maps. The full map keeps its larger cyan player marker even before a home node is registered, while the minimap uses the matching icon and preserves live X/Z coordinates when its compact footer cannot fit every network statistic.

## 2.1.0 - Prosperity & Prestige

Release date: 2026-07-29

### New features

- Added `Prosperity & Prestige`, a new long-term Crown sink that begins after the matching village projects are complete. The Apiary, Forge, Market, Pasture, and Road Watch each have three permanent ranks: `Foundation`, `Established`, and `Legacy`, priced at `6`, `12`, and `24 Crowns`.
- Each branch now changes the economy instead of only filling a progress bar. Apiary, Market, Pasture, and road-related Pilgrim goods receive up to a `15%` discount; the Forge also discounts permanent route upgrades; and the Road Watch lowers recurring route-incident chance by up to `9` percentage points.
- Added prepaid Pilgrim commissions. An invested Market can order any currently unlocked Pilgrim good for its normal discounted price plus a rank-dependent `3/2/1 Crown` fee. The order is delivered on the first Pilgrim visit after the next dawn, so rare rotations can be planned without making the traveling merchant irrelevant.
- Added five paid village services: a three-journey Road Patrol, a `+15` Survey Report for road quality, an Emergency Recall for stranded caravans, a Village Festival that boosts the next three repeatable quest payouts by `25%`, and a rank-gated Guild Ceremony that boosts the next three freight payouts by `25%`.
- Added a ten-piece prestige collection. Five permanent caravan liveries can be applied to any installed route and keep NPC outfits, full-map lines, and minimap colors synchronized. The Guild Banner, Mapmaker Crest, Market Pavilion, Watchtower Pennant, and final Guild Hall Trophy provide visible long-term milestones up to all `15` prosperity ranks.
- Added a persistent economy ledger for total currency earned and spent, Pilgrim purchases, commissions, services, investments, collection pieces, total prosperity ranks, and remaining Festival or Ceremony bonuses.
- Added a dedicated five-tab Prosperity board with `24` new pixel-art icons, visible investment prices, shared Crown/Silvermark wallets, and an admin test setup for quickly previewing the complete economy.
- Added clearer quest guidance: locked entries explain their exact requirements, multi-stage work reveals one meaningful step at a time, and restrained sounds distinguish acceptance, progress, stage completion, and final rewards.
- The Questmaster now announces genuinely new Daily, Weekly, and Story work once in chat. Complete English, German, and Spanish text and matching wiki guidance are included.

### Bug fixes and improvements

- Fixed inventory-backed quest turn-ins across story, Daily, and Weekly content. Every fixed multi-item bundle is now fully validated before anything is removed, and a claim cannot complete unless all required items are still available for consumption.
- Standardized completion behavior: pure action Daily and Weekly quests auto-complete, while item-consuming Daily and Weekly quests, all Story chapters, and Special commissions wait for an explicit Questmaster claim. Progress events no longer remove delivery items.
- Audited every visible Daily, Weekly, Story, and Special objective against its live tracker and turn-in code. Quest text now distinguishes fresh actions from supply deliveries and hybrid hand-ins, all three languages use the actual authored targets, pure deliveries explicitly allow stored or traded goods, and the wiki matches the same lore and mechanics.
- Rebuilt `The Silent Forge: The Master's Edge` as a sequential six-stage commission: five distinct villager books, four freshly crafted Iron Armor pieces, four flexibly assigned protection enchantments of any level, a freshly crafted Diamond Sword, Sharpness of any level, and the final five-piece hand-in. Enchanted-book trades now read stored enchantments correctly, including Sharpness V, and an upgrade recovery pass recognizes qualifying books still carried by players whose active chapter was affected by the old tracker.
- Extended clear stage progression to every other genuine production chain. `Bakehouse Help`, `Meal from the River`, `Smelting for the Smithy`, `Harvest for the Village`, `Smith's Week`, and `Ledger And Notices` now reveal one dependency stage at a time, advance automatically, ignore processing performed before its prerequisite, and reserve the final item removal for the Questmaster hand-in. Independent bundles such as mixed bakehouse stock remain parallel instead of receiving an artificial order.
- Normalized the German localization to standard German orthography. Remaining Swiss-style `ss` forms such as `Strasse` and `weiss`, plus malformed earlier substitutions such as `Baün`, `Neü`, and `qüstmaster`, now use the correct `ß`, umlauts, or original vowel sequence.
- Fixed `The Failing Harvest`: chapter 1 now counts the actual Wheat and Potato item yields from mature crops instead of one point per block, then requires and consumes `16 Wheat` and `8 Potatoes`; chapter 2 likewise requires and consumes its `3 Honey Bottles` and `1 Honeycomb`.
- Audited every active crop-yield quest and moved `Kitchen Supplies`, `Bakehouse Help`, `Harvest for the Village`, and `The Failing Harvest` onto one mature-crop item-yield path. Multi-item Potato and Carrot harvests now credit the full collected stack, Fortune-compatible drops remain accurate, immature crops grant no progress, and verified right-click-and-replant harvests avoid double counting.
- Fixed normal mature Wheat, Potato, and Carrot harvests at the source. Village Quest now captures Minecraft's exact, already-randomized crop stack when `Block.popResource` spawns it, before item-entity event order or pickup delay can lose the association. This credits the real yield without restoring one-point-per-plant progress; the short-lived entity matcher remains as a compatibility fallback.
- Compacted the tracked-quest turn-in warning. Completed inventory requirements are omitted, only genuinely missing item names and counts remain red, and all tracker content wraps within a fixed readable width instead of stretching the background across the screen. The same behavior covers Daily, Weekly, Story, and Special turn-ins, including the five-item Silent Forge bundle.
- Preserved the red missing-item warning color after the compact tracker wraps the message into plain display lines.
- Colored completed numeric objectives dark green in the tracked-quests HUD. A line turns dark green only when every visible `current/target` pair on that line has reached its target.
- Fixed Fortune-sensitive mining progress, including `The Silent Forge: Cold Hearth`. Tracked Coal, Raw Iron, Redstone, Raw Gold, Diamond, Lapis, and Amethyst progress now uses Minecraft's exact generated block-resource stack instead of a separately randomized loot prediction; the item-entity path remains as a compatibility fallback.
- Cleared completed or locked quests from the Quest Board category badges, so a finished Weekly no longer leaves a misleading `1`, and moved the Weekly reset timer clear of the lower frame.
- Kept zero-badge Quest Board categories selectable whenever they still contain an entry. Completed Weeklies can therefore be reopened to review their completion state and see the next reset timer without restoring the misleading badge.
- Limited reset timers to their relevant Quest Board tabs. Daily and Weekly countdowns no longer leak into the unrelated Special/Relic view.
- Fixed the Story cooldown presentation. `The Questmaster Listens` no longer contributes a misleading `1` badge, its zero-badge Story tab remains selectable, and the footer now shows the live countdown until the next story.
- Made every primary Village Quest board responsive to Minecraft's automatic GUI scale. Journal, Quest Board, Pilgrim Trader, Caravan Ledger/Route Office, and Prosperity now keep a consistent centered footprint between a maximized window and fullscreen instead of expanding to nearly the entire display; hover, scrolling, buttons, and map dragging use the same transformed coordinates.
- Moved terrain-map texture cleanup onto the client render thread when leaving a world, preventing the wrong-thread texture-release warning that could appear during disconnect or shutdown.
- Polished the board after a native fullscreen client review: long commission prices now wrap cleanly in the detail card, all ten economy-ledger rows remain visible, and route selectors sit clear of the lower wood divider in Services and Collection.
- Standardized currency presentation across the Journal, Pilgrim Trader, and Prosperity board. Crown and Silvermark item icons with compact amounts now live in the upper-right wood header, replacing long parchment text such as `500 Crowns 5 Silvermarks` that could clip at the edge.
- Re-centered the Journal and Pilgrim Trader coin displays inside their wooden headers, with separate offsets so both wallets sit naturally clear of the brass corner at the responsive board scale.
- Aligned the Prosperity wallet with the accepted Journal header position, moving both currency icons and amounts into the center of the wooden header and clear of the brass corner.
- Made Prosperity and the full Caravan Ledger map behave as Journal subpages: Prosperity's `Done` button and the map's regular close action now return to the Journal, while starting a route survey still closes directly into gameplay.
- Centered the five Prosperity navigation icons and Collection row art, moved the Pilgrim `Rumor` control into the upper-left wood header, and shifted Journal/Prosperity `Done` buttons clear of the brass corner. Every scrollable primary board now uses one generated brass, oak, and teal double-arrow handle instead of the former narrow bar.
- Improved the unreleased new-player Weekly guard. `Market Week` now appears only when the player can afford the three cheapest currently unlocked Pilgrim goods, rather than using the earlier one-Crown approximation. Invalid unaccepted offers migrate automatically; accepted Weeklies and their progress remain untouched.
- Existing worlds upgrade without a destructive migration. Completed projects and routes immediately expose the appropriate economy branches, while every new rank, commission, service, collection unlock, and livery persists normally.

## 2.0.2 - New Player Weekly Eligibility (superseded by 2.1.0)

Release date: not published

- This initial candidate kept `Market Week` out of the Weekly rotation below one Crown. Its save-safe offer replacement is retained in `2.1.0`, while the eligibility check now measures the cost of three actual unlocked purchases.

## 2.0.1 - Quest Board Frame Hotfix

Release date: 2026-07-21

- Fixed the compact Quest Board rendering only a `392 x 220` crop of its `416 x 234` background texture. The complete wood and brass frame is now scaled into the intended window, restoring the missing right and bottom edges without making the interface larger.
- Closed the transparent inner wood seams along the Pilgrim Trader's upper-left and left frame, and centered both footer labels vertically; the white exit hint also has additional left padding so it no longer touches the border.

## 2.0.0 - Roads Between Villages

Release date: 2026-07-19

- Restored the one-time inventory tutorial pointer for the Journal. Fresh players and existing installations upgrading to this build receive one clear animated pointer, and the same guidance follows the normal bookmark, the status-effect-safe top-right placement, or the compact `J` compatibility fallback.
- Fixed the rare case where the Journal needed two clicks after navigating from it to another Village Quest board. Inventory controls now request an idempotent open, normal closing explicitly clears the server state, and Journal navigation also clears stale state before opening the destination; the existing `/vq journal` toggle remains backward compatible.

### Licensing and packaging

- Adopted a mixed license for `2.0.0`: functional source code is now `LGPL-3.0-only`, while original Village Quest assets, narrative content, branding, and promotional material remain All Rights Reserved under the project licensing notice.
- Added the complete GPLv3 and LGPLv3 texts, preserved the historical MIT notice for already published releases and compatible carried-forward material, and documented third-party software and assets explicitly.
- Recorded every unresolved legacy NPC and caravan skin by shipped filename. Those files are excluded from both the LGPL and the Village Quest ownership claim; the maintainer authorized the `2.0.0` publication with this provenance warning intact, while replacement or source clearance remains a priority.
- Updated Fabric metadata to advertise both the code and protected-asset terms, and embedded the complete licensing package into runtime and sources JARs.

### Unified interfaces and live navigation

- Limited every Pilgrim stock rotation to at most four offers. New spawns and Merchant's Seal rerolls use the four-offer cap, while Pilgrims loaded from older saves automatically trim a previously stored fifth offer instead of carrying it forward.
- Fixed the Pilgrim shop when more than four goods are available. The goods column now has a clipped four-row viewport, mouse-wheel scrolling, a proportional scrollbar, and matching visible-row hitboxes, so later offers no longer render or remain clickable through the footer. The redundant white merchant label in the upper-left header was removed, leaving the centered `Pilgrim Trader` plaque as the single screen title.
- Replaced the cramped Questmaster board with the same modular dashboard language as the Journal. The final `392 x 220` frame leaves a deliberate strip of the world visible even at large GUI scales; dedicated Daily, Weekly, Story, and Special icon tabs drive a separate quest list and a spacious scrollable detail card, while accept, claim, cancel, rewards, reset timers, locked entries, and the multiplayer party drawer remain available without text touching painted borders.
- Added four independent `32 x 32` Questmaster category assets: a daily sun seal, weekly calendar, story scroll, and special relic. Their brass, parchment, teal, and dark-outline pixel treatment matches the existing Journal icon family and keeps the screen extensible without baking navigation into one background image.
- Unified the exterior treatment of every primary UI. Journal, Questmaster, Pilgrim, Caravan Ledger, and Route Office now share the same restrained world shade and one runtime-rendered panel shadow: a faint ambient edge surrounds the frame while a broader soft falloff anchors it toward the lower right. The asymmetric generated shadow mattes and the earlier doubled code-side edges were removed from the board textures.
- Polished caravan identity and lifecycle handling on the `26.2` reference line. Each of the five routes now owns one consistent outfit/color family shared by every merchant in that caravan, the full map, the minimap, and route-card accents; a new muted-violet fifth skin completes the set.
- Removed visible caravan rubber-banding during ordinary travel. Followers now regroup through navigation, hard route recovery waits until it is off-screen, and pausing a route repeatedly sweeps every loaded owner/route-tagged merchant or attacker instead of trusting a single runtime group. Periodic cleanup also rejects mapped duplicates that are not members of the current caravan, preventing stale traders from accumulating.
- Improved Caravan Ledger management: route rows show compact running/paused indicators with short hover help, long route tooltips wrap to a readable width, route text sits clear of its borders, the full-map tabs move together beyond the upper-left brass corner, the Route Office guild/day summary is right-aligned below its divider, and destinations can be renamed from the ledger or with `/vq routes rename <1-5> <name>`.
- Normalized the German localization to real UTF-8 umlauts instead of player-facing `ae/oe/ue` substitutions, with a small curated set of obvious `ß` spelling corrections. Added `design/normalize_de_umlauts.ps1` as a deterministic maintenance check for later ports.
- Rebuilt the main client interfaces as one coherent Village Quest visual system: dark oak framing, warm parchment, muted brass controls, and restrained teal navigation accents now carry across the Quest Board, Pilgrim shop, Journal, Caravan Ledger, and live minimap.
- Corrected the modular layout pass after full-size client review: parchment cards now preserve thin corners and borders instead of stretching them into the text area, Questmaster details use safer inner margins, and the Pilgrim title and compact wallet remain centered and clear of the brass corner. Added admin-only Questmaster and Pilgrim UI test commands for repeatable GUI-scale and translation checks.
- Replaced the Journal's long tutorial-page flow with a compact five-tab dashboard for home, quests, trust, legacy, and guidance. Its cards expand only when needed, keep the important next step visible, and retain direct Map and Questmaster shortcuts.
- Simplified the Journal home page's `Where to go next` guidance in English, German, and Spanish so it only directs players toward available Questmaster work; the redundant instruction about expanding cards was removed and must stay removed when this 26.2 UI layer is ported.
- Replaced the abstract ledger grid with a real top-down terrain view sampled from already loaded client chunks. The full map and corner minimap now show recognizable rivers, forests, roads, villages, the player, route lines, caravans, and incidents without force-loading world chunks; surveyed terrain remains remembered for the current play session.
- Removed the full-map render bottleneck by baking the sampled terrain and its illustrated details into a cached dynamic texture instead of issuing thousands of individual rectangles every frame. The ledger map can now be dragged with the left mouse button, defers terrain resampling until the drag ends, and documents that navigation in English, German, and Spanish.
- Softened the heavy black area outside every unified board: the world tint remains restrained, boundary-connected dark matte pixels are transparent, and the shared runtime shadow supplies a lighter ambient rim with lower-right depth while the wooden frames and internal ink remain crisp.
- Verified CTOV structure-tag compatibility through the shared `#minecraft:village` tag and hardened the living-villager check to use each village's real structure footprint. Large ChoiceTheorem settlements can therefore register without being mistaken for an abandoned vanilla-sized village, while zombie or genuinely empty CTOV villages remain rejected.
- Refined the route-map tooltips and marker hierarchy so villages, caravans, incidents, and the player remain readable when several markers share the same area. The configurable `,` shortcut and `/vq routes minimap` continue to toggle the live corner view.
- Added four deterministic medieval caravan outfits so a traveling group no longer looks like three copies of the same merchant.
- Tightened ordinary caravan formation behavior: followers avoid leaf and powder-snow routing, event stops use the leader's facing when no movement vector exists, and merchants that collapse into the same space are separated onto safe nearby footing.
- Hardened the older `Shadows on the Trade Road` rescue convoys as well: encounters now require a broader stable footprint, never accept partially spawned merchant groups, and recover merchants that fall far below or outside the encounter area.
- Added current real-client UI captures to the repository-level `mod-previews` upload set. The accepted UI/reliability layer was subsequently ported to both maintained target lines for the shared `2.0.0` candidate.

### Trade Guild, rewards, and long-term progression

- Added the `Village Trade Guild`, a five-rank long-term progression built from route count, road quality, resolved incidents, and completed freight contracts.
- Added a rotating board of up to three daily freight contracts. Players assign one to a route, load the requested cargo, and complete it on that route's next arrival; long routes, matching specializations, and a Trade Office increase the payout.
- Added six route specializations (`general`, `provisions`, `forge`, `livestock`, `courier`, and `guarded`) and six permanent Silvermark investments: reinforced wheels, lantern crews, weather covers, escorts, insurance, and trade offices.
- The `Market Charter` now opens the ledger, map, and one provisional route much earlier. Completing `The Empty Caravan` and its `Caravan Yard` expands that network to the full five routes and enables recurring incidents.
- Rebalanced route economics around installed path length, security, and quality. Purposeful detours are now paid for, ordinary route income has network-wide daily limits, and offline proceeds wait in a capped trade-office escrow until the owner returns.
- Route incidents are now limited to one active event per player network, no longer mark a route dangerous merely by starting, occur less often on high-quality/fully upgraded roads, and grant tiered rewards based on difficulty and distance. The first full-network incident remains a guaranteed tutorial.
- Added long-term reputation Mastery levels beyond `200` reputation. Mastery is capped at five per track and unlocks a second daily reroll after three combined Mastery levels rather than stacking unlimited raw power.
- Added `/vq daily reroll`, `/vq routes guild`, `/vq routes contracts`, contract accept/supply actions, route specialization, and route upgrade commands. The ledger map now shows guild rank, daily earnings, route specializations, and installed upgrades.
- Added the `Roadwarden Horn` at `200` Monster Hunting reputation. It posts one daily road watch that prevents the next non-tutorial incident and points the Wayfinder toward live route trouble on later uses.
- Improved the other long-term relic loops: sneak-use of the `Shepherd's Flute` now holds nearby animals in place, while the completed `Merchant's Seal` prioritizes unlocked collectibles the player has not bought before.
- Reworked quest experience into bounded level-bar rewards that remain worthwhile at both low and very high vanilla levels. Normal Dailies grant `1.5/3/4.5` bars, normal Weeklies `5.25/6/6.75`, Pilgrim tiers `3/4.5/6`, story chapters scale up to `7.5`, and the Forge Charter adds another `0.75` bar; light/heavy repeatable profiles still adjust their payout.
- Raised daily wallet rewards to `3/6/12` Silvermarks, changed `Fresh Finery for Your Companions!` from one recolor to a tuned multi-recolor objective, and made every Pilgrim combat offer include an Overworld-safe choice plus a second alternative.
- Repriced all large Pilgrim supply bundles individually, reduced the excessive Provisions Satchel baseline and jackpot odds, raised the underpaid Forge/Market/Pasture story rewards, and accepted gold or diamond horse armor in the `Restless Pens` finale.
- Village-project bonuses now apply consistently to the chapter that unlocks them instead of only some reward types seeing the newly completed project.
- Expanded `/vq admin routes testsetup [player]` with all five specializations, multiple upgrade states, a rank-five guild test profile, contract access, and enough wallet funds to exercise the remaining investments.
- Completed a full native-client polish pass on the `26.2` reference line for the expanded network: all five routes, map tooltips, guild commands, contract loading and arrival, specialization/refit, upgrades, the Roadwarden Horn, daily rerolls, reputation Mastery, low- and high-level XP rewards, and save/reload persistence were verified together.
- Freight contracts now complete on their assigned arrival even after ordinary route income has reached its daily network cap. The reputation admin parser also accepts `monster_hunting` and `monster-hunting`, and the English specialization result no longer repeats the word `freight`.
- Existing saves now receive a one-time progression backfill on login: players who already unlocked the `Market Charter` or `Caravan Yard` receive a missing `Caravan Ledger`, and players who already reached `200` Monster Hunting receive a missing `Roadwarden Horn`. Persistent claim flags and inventory checks prevent repeated grants.

### The roads between villages

- Added the six-chapter late story `The Empty Caravan`, which follows the aftermath of `Shadows on the Trade Road` through an abandoned wreck, a trail of clues, village witnesses, a player-chosen amnesty or justice plan, a bait-caravan defense, and the rebuilding of the roads between villages.
- Completing the new story unlocks the permanent `Caravan Yard` village project and the `Caravan Ledger`, creating a new trade-route endgame rather than ending the road storyline at one final battle.
- Players can register villages and maintain up to five persistent trade routes. Routes continue their journeys without force-loading chunks, make deliveries, earn route income, improve from successful intervention, and can be paused or removed from the ledger map.
- Added persistent route surveying for genuine player-built detours. A route can hold up to `48` marked bends or anchors; the map, virtual travel distance, road-quality sampling, event targets, and visible caravan navigation all follow the installed line instead of a forced straight connection.
- Active routes now materialize as named three-merchant caravans when players are nearby. They visibly travel toward their destination, favor suitable player-built road surfaces, and disappear safely back into the persistent simulation outside observation range.
- Hardened physical caravan travel against ravines, dense forests, blocked routes, and restart leftovers. Groups now choose broad hazard-free footing, keep their formation together, detect failed movement, recover toward the installed route, and fall back to the unloaded simulation after repeated local pathfinding failure instead of remaining trapped forever.
- Road-event caravans now identify their incident directly above the lead merchant, ordinary caravans no longer fill the landscape with permanent nameplates, and solo incidents can only be completed by the route owner.
- Expanded the parchment-style trade-route map for five compact route rows, waypoint polylines, survey/install/cancel controls, and a guarded two-click route-removal action alongside caravan positions, direction, security, road quality, earnings, and emergencies.
- Added a live corner minimap for the trade network. Its configurable default `,` key and `/vq routes minimap` both toggle a real-time view of the player, registered villages, installed route lines, nearby/background caravans, and active incidents; the full ledger map now also gives hover details for caravan and player markers.
- Village registration now requires both a generated village structure and at least one living normal villager nearby, so abandoned or zombie villages cannot silently become trade destinations.
- Roads now matter mechanically: path blocks, gravel, cobblestone, stone bricks, planks, slabs, and lighting improve route quality, while better routes move faster and earn more.
- Added eight recurring road situations: broken wheels, injured pack animals, washed-out bridges, false distress calls, hungry travelers, road tolls, missing couriers, and storm camps.
- The `Wayfinder's Compass` now automatically selects dedicated targets for route emergencies and `The Empty Caravan` investigations.
- Added `/vq routes`, `/vq routes register`, `/vq routes remove <1-5>`, and the `/vq routes survey ...` workflow. `/vq admin routes testsetup [player]` now creates a complete five-route surveyed test network with two live event scenarios; `/vq admin routes reset [player]` removes that route data again.
- Added `/vq admin routes testevent <1-5> <event|clear>` so every recurring caravan situation can be forced independently during QA.
- Hardened caravan and ambusher placement in dense or uneven terrain. Physical groups now search nearby safe surfaces, and a false-distress event can no longer complete successfully when no attackers were actually spawned.
- Polished the new interfaces after an in-client QA pass: route-event help wraps into a compact tooltip, and active story chapters now show a disabled `Still Working` action instead of an empty button area.
- A focused five-route client pass confirmed survey start, waypoint marking, installation, command removal, and guarded map removal; it also tightened the compact row layout and extended the second-click removal window to `30 seconds`.

### Compatibility and maintenance

- Farming quests now recognize successful mature-crop harvests from compatible right-click-and-replant mods, including the event flow used by `RightClickHarvest`. Holding bone meal during that harvest no longer loses progress, while merely clicking a ripe crop still grants nothing.
- Updated the journal, project overview, item reference, complete-reset cleanup, English/German/Spanish localization, and maintainer documentation for the new caravan systems.

## 1.22.8 - Reliable Resets And Cleaner Sessions

Release date: 2026-07-17

- The complete admin reset now also clears quest parties, invites, shared sessions, and reconnect-grace state, so old multiplayer data cannot return on the next server shutdown or restart.
- `Restless Pens` now shows both finale requirements correctly in English, German, and Spanish: the gathered bell call and the required Diamond Horse Armor.
- Player-bound journals, trackers, Questmaster screens, and relic hints now clean up on disconnect, while every transient cache, including late-road encounters, resets at server start and stop so stale session state cannot carry into later worlds.
- Repository checks now build all three maintained Minecraft lines from the root and verify JSON, translation keys, placeholders, and directly referenced localization entries before release artifacts are produced.

## 1.22.7 - Shard Bonus Daily Tracking Fixes

Release date: 2026-07-01

- Fixed action-based `Daily` quest tracking for shard bonus quests, so `Autumn Harvest` and the other event-driven dailies now progress correctly when accepted through the `Questmaster` shard offer.

## 1.22.6 - Quest Tracking Fixes And Questmaster Hover Preview

Release date: 2026-06-23

- Fixed `Fresh Finery for Your Companions!` so the quest now completes from the real successful `Wolf` and `Cat` collar recolor path again instead of a fragile early interaction callback.
- Fixed successful hive-harvest progress tracking for the `Questmaster`, so `The Failing Harvest` chapter 2 and related bee quests now advance on real honey-bottle and honeycomb harvests again.
- Fixed sheep-based quest progress tracking so `Wool Weaver`, `Stall and Pasture`, and `Restless Pens` chapter 2 now count only real successful shearing events and no longer depend on the fragile early entity-use hook.
- Realigned all `The Failing Harvest` chapter targets with the shipped quest text again: `16/8` crops, `3/1` hive goods, `6/4` baked goods, and `2/4` trade proof.
- `Questmaster` descriptions now open in a larger hover preview so long quest text can be read without enlarging the whole board.
- Project-gated `Pilgrim` wares now wait for completed village-project progress consistently again, so `Village Ledger Plaque` and `Village Ledger Desk` no longer leak into the shop before the first real village project is finished.
- Ported the maintained `Village Quest` stable line onto Minecraft `26.2` and shipped it as the first public `26.2` release.
- Updated the modern dependency stack to Minecraft `26.2`, Fabric Loader `0.19.3`, Fabric API `0.153.0+26.2`, Fabric Loom `1.17.12`, and Gradle `9.6.0`.
- Adapted the `26.2` port for the new wool collections, client GUI screen API, knockback helpers, and moved entity classes/packages so the line compiles cleanly again on the latest mappings.

## 1.22.5 - Quest Parties, Variety, Localization, And Client Compat

Release date: 2026-05-14

- The multiplayer quest-party batch is now fully promoted into a stable release on the modern line: dedicated-server quest parties, clickable chat invites, restart-persistent shared sessions, `10 minute` reconnect grace, and shared `Daily`, `Weekly`, core `Story`, and normal `Pilgrim` combat-contract progress now ship as part of the main release.
- The `Questmaster` now has a dedicated brown party button on shareable quests, party management stays hidden on singleplayer or integrated worlds, and the journal keeps its `Questmaster` shortcut visible on every page instead of only the first one.
- Repeatable `Daily`, `Weekly`, and `Pilgrim` quests now use stored `light`, `normal`, or `heavy` target profiles with matching reward scaling, and the non-`Shadows` story arcs plus repeatables now avoid the old obvious stack and half-stack requirement numbers.
- The story follow-up cooldown between completed `4`-chapter arcs was reduced from `3 hours` to `1 hour`.
- A full Spanish `es_es` localization now ships on this line as well, with the original translation provided by `Lutte` and the missing newer quest-party strings and placeholder fixes filled in.
- The reported `Bendable Cuboids` and `MTGCard` client conflicts were addressed directly on this line: humanoid quest NPCs now use a safer held-item renderer when needed, the old inventory journal overlay can disable itself safely, the inventory keeps a small fallback `J` journal button, and the journal/admin journal screens now avoid the crashy arrow and `GuiGraphics` compatibility paths in the reported combo.

## 1.22.1-beta.3 - Multiplayer Quest Party Beta

Release date: 2026-05-14

- Ported the `1.21.11` multiplayer quest-sharing beta batch to `26.1.2`, including dedicated-server-only quest parties, clickable chat invites, and `/vq party ...` commands for `show`, `invite`, `accept`, `decline`, `leave`, `disband`, and shared daily/weekly/story/pilgrim offer acceptance.
- The `Questmaster` UI now exposes the same party drawer on shareable `Daily`, `Weekly`, and core `Story` entries, including current members, invite candidates, and direct invite/leave/disband actions; the party toggle now sits as a dedicated brown button above the right quest header panel and remains hidden on singleplayer or integrated worlds.
- Shareable `Daily`, `Weekly`, core `Story`, and normal `Pilgrim` combat-contract flows now support pooled progress, pooled turn-in where applicable, explicit chat offers for late joiners, `10 minute` disconnect grace, and restart-persistent party and shared-session state on `26.1.2`.
- Repeatable `Daily`, `Weekly`, and `Pilgrim` quests now roll and persist synced `light`, `normal`, or `heavy` target profiles on this line as well, scale their numeric rewards to match that profile, and avoid obvious stack or half-stack target counts; the non-`Shadows` story arcs also received the same authored irregular target numbers.
- The journal now keeps its `Questmaster` shortcut button visible on every page instead of only the first page on `26.1.2`.

## 1.22.0 - Shadows On The Trade Road And Admin Reset

Release date: 2026-04-24

- The Fabric Loader dependency was bumped to `0.19.2` on both maintained lines.
- The modern `26.1.2` line now also uses Fabric API `0.146.0+26.1.2`.
- The modern `26.1.2` line now includes the new late `Questmaster` arc `Shadows on the Trade Road`, ported from the `1.21.11` implementation into the Mojang-mapped codebase.
- The new road-defense arc appears after `Watch Bell` as a locked story entry and unlocks after `3` completed `Pilgrim` combat rumors.
- That arc adds village rumor gathering, village-aware interview tracking, a toolsmith compass calibration step, nighttime caravan rescue encounters, a courier handoff chapter, and a larger final convoy defense with new `Caravan Merchant` and `Traitor` entities.
- Late-road encounter balance uses staggered `3`-second wave pulses with up to `2` hostile spawns per pulse, a `16-26` block hostile spawn ring, `5`-second initial `Glowing` on each wave hostile, and `3` final-wave `Traitor` elites.
- Caravan encounter anchors reject water and other non-solid footing, escaped raiders leash back into the ambush area, and the last `1-2` remaining enemies get delayed `Glowing` markers so hidden mobs cannot stall the quest.
- Caravan merchants now have `45` hearts, roughly `1/3` of each convoy can weakly defend with wooden swords, and convoy spawn spacing avoids overlap glitches.
- Active rescue and final-convoy quest status lines now show how many hostile mobs remain in the current encounter.
- The `Wayfinder's Compass` gained story-bound modes for `Caravan Distress` and `Guild Convoy` while the late trade-road arc is active.
- New admin helpers were added for the late-road batch: `/vq admin story shadows unlock`, `/vq admin story shadows testrescue`, and `/vq admin story shadows testfinal`.
- A new admin command, `/vq admin reset complete`, now wipes the saved Village Quest state for every player, including wallet, reputation, story/project progress, special quest data, pilgrim contracts, cooldown state, live quest sessions, and spawned Village Quest NPCs.
- Villager talk objectives now speak back with context-sensitive lore lines in `Market Rounds`, the villager-facing `Market Road Troubles` chapters, and the new `Shadows on the Trade Road` rumor steps instead of progressing silently.
- Villager, toolsmith, and courier dialogue lines were retuned toward a more medieval low-fantasy tone in German and English.
- `Questmaster` and `Pilgrim` screens can now also be closed again through the player's current inventory keybind.
- The `Wayfinder's Compass` now keeps a chunkier pixel-art outer ring while preserving the original readable inner dial and cardinal letters.
- Remaining Wolkensprung extraction scripts, templates, comments, and leftover lang keys were removed from this line.
- README and wiki command/install notes were refreshed for the current dependency stack and admin surface.

## 1.21.1 - First-Use Journal Onboarding And Minecraft 26.1.2 Update

Release date: 2026-04-10

- Village Quest has been updated for Minecraft `26.1.2`.
- The Fabric API dependency was bumped to `0.145.4+26.1.2`.
- The current modern line now lives in the renamed `26.1.2` folder, and README/wiki install docs were refreshed to match the new jar and path names.
- The inventory journal tab now shows a one-time first-use hint with a small arrow in the inventory until it has been clicked once.
- After opening the journal, the `Questmaster` button on the first page now also gets its own one-time guided highlight.

## 1.21.0 - Pilgrim Shop Expansion And Story Cooldown Update

Release date: 2026-04-09

- The pilgrim wallet header now expands its coin spacing so multi-digit `Crown` and `Silvermark` counts no longer overlap the icons.
- Pilgrim shop prices were raised sharply across the full catalog with a central `3x` pricing pass.
- Story wallet rewards were reduced to `70%` of their former values so main-story completion no longer funds the shop as quickly by itself.
- Player and admin commands now also live under the new roots `/villagequest ...` and `/vq ...`.
- The old direct roots such as `/questadmin`, `/questmaster`, `/dailyquest`, `/wallet`, `/reputation`, `/journal`, and `/questtracker` were removed, so commands now start only with `/villagequest ...` or `/vq ...`.
- Journal help pages, clickable chat actions, tracker hints, README notes, and command docs were updated to use the new `/vq` command structure.
- The inventory journal tab now also opens the journal through `/vq journal`, and the remaining live command links were checked so they match the new root structure.
- The `Merchant's Seal` hover text was shortened in English and German so it fits more cleanly in inventory tooltips.
- The first Pilgrim shop-overhaul slice added `Road Camp Kit`, `Village Ledger Desk`, `Apiary Supply Crate`, `Smithing Supply Rack`, `Market Stall Kit`, `Pasture Tack Bundle`, and `Watch Post Kit` as new themed bundle wares.
- `Apiary Charter Plaque` was rebuilt into a real wall-mounted decorative plaque block with depth, custom front art, and proper block placement instead of a flat hanging item.
- The first plaque follow-up fixed wall placement, restored the proper localized item name, and removed the visible transparent border artifact from the front texture.
- The second plaque follow-up restored a visible wood backing/frame, forces wood particles for breaking, and adds a second loot-table path safeguard so the plaque should drop back as an item reliably.
- A second custom-art shop batch added `Village Ledger Plaque`, `Forge Charter Plaque`, `Market Charter Plaque`, `Pasture Charter Plaque`, and `Watch Bell Reliquary`.
- The temporary standing decor experiment was rolled back, so `Pilgrim Trade Chest`, `Milestone Marker`, and `Weathered Signpost` are no longer part of the active unreleased batch.
- The pilgrim now offers a broader premium wave of decorative custom heads, including barrels, shelves, utility pieces, and plushies.
- Decorative custom head wares now cost `5 Crowns`, and each pilgrim can offer at most one of them per visit.
- The `Skeleton Plushie`, `Zombie Plushie`, and `Creeper Plushie` now unlock only after completing their matching Pilgrim combat contract once.
- Decorative custom head items now use proper English and German item names plus short hover lore, so bought heads match the shop presentation more cleanly.
- `Apiary Charter Plaque` now uses the same full plaque artwork style as the later charter plaques so the whole series reads more consistently.
- Plaque and reliquary wares now cost `3 Crowns 5 Silvermarks`, and their shop text plus item lore were shortened into cleaner trophy-style descriptions.
- Completing a full four-chapter village story now starts a real `3 hour` cooldown before the next story appears, and the `Story` tab shows both a waiting note and a live countdown timer during that pause.
- The shared plaque block model now adds a consistent outer wood frame across the whole plaque/reliquary line, so the series reads more uniformly in-world and in inventory.
- Pilgrim detail prices now shrink to stay on one line, so mixed-currency prices such as `3 Crowns 5 Silvermarks` no longer collide or wrap awkwardly.
- Plaque art was downscaled to a chunkier raster size, and `Starreach Ring`, `Merchant's Seal`, plus `Shepherd's Flute` were reduced to `32x32` item textures for a more Minecraft-like look.
- Legacy compatibility junk items such as the old mini blocks, test items, and decorative leftovers were removed from the active registry, and the remaining coin item IDs now use English registry paths: `legacy_copper_penny`, `silvermark`, and `crown`.
- Bundle quantities were adjusted explicitly so `Bed`, `Spyglass`, `Book and Quill`, and `Lectern` now use the intended `1x` counts without a global bundle rule.
- All multi-item pilgrim bundles now use the same price as the `Provisions Satchel`, so satchels, kits, crates, desks, and similar bundle wares stay aligned.
- Pilgrim shop payloads now also shorten long decor-head profile names on `26.1.1`, so custom-head offers no longer risk a packet-encoding disconnect when opening the trader.
- Pilgrim offer locking now blocks both reputation-gated and village-project-gated wares correctly, including direct-buy paths.

## 1.20.4 - Quest Balance And Systems Update

Release date: 2026-04-03

- Daily, weekly, story, and special quests received a broad balance pass across the full `26.1.1` content set.
- Story rewards were normalized by chapter and several story chapters were redesigned or expanded:
  - `The Silent Forge` now includes a much larger mining/smithing ramp and a full enchanted sword-and-armor finale.
  - `Market Road Troubles` now leans harder into crafted ledgers, named professions, and a large village bell finale.
  - `Restless Pens` now pivots its third chapter into horseback exploration and its finale into a herd-and-horse-armor readiness check.
- Special quests were expanded:
  - `Merchant's Seal` now separates villager trading, villager buying, and pilgrim buying requirements.
  - `Shepherd's Flute` now uses breeding plus taming objectives for `Wolf`, `Cat`, and `Parrot`.
  - `Apiarist's Smoker` now tracks bee breeding and crafted honey blocks and consumes all required hive goods at turn-in.
  - `Wayfinder's Compass` now also requires mined `Lapis Lazuli` alongside `Redstone`.
- Tracked mining drops were expanded so `Raw Gold` and `Lapis Lazuli` count correctly with the same self-earned mining logic.
- Pilgrim offers are now single-use per spawned pilgrim: once bought, that offer stays unavailable until a new pilgrim appears.
- The internal `Admin: Core Systems Test` was expanded to cover the new risky hook paths such as taming, crafted honey blocks, villager purchases, and the added mining-drop routes.
- The admin wallet commands no longer print duplicate chat feedback when an admin adjusts their own wallet.
- English and German quest texts were updated to match the new objectives and progress labels.

## 1.20.3 - Minecraft 26.1.1 Hotfix Update

Release date: 2026-04-03

- Village Quest has been updated for Minecraft `26.1.1`.
- The Fabric stack was refreshed to the latest stable loader and Fabric API builds for `26.1.1`.
- Internal release files and documentation were refreshed for the `26.1.1` line.

## 1.20.2 - Story Turn-In And Painting Fixes

Release date: 2026-04-01

- `The Failing Harvest` now truly requires and consumes the final `Bread` and `Baked Potato` turn-in items.
- `The Silent Forge` received several hand-in fixes:
  - chapter 2 now also consumes the required `Iron Ingots`
  - chapter 3 now only accepts fresh undamaged crafted tools instead of also taking used ones
  - chapter 4 now recognizes bought `Sharpness` enchanted books reliably again
- The `Surveyor's Compass` netherite pickaxe hand-in was tightened as well so only undamaged tools count.
- Custom `Village Quest` paintings now preserve their correct custom item when broken instead of turning back into a normal vanilla painting.
- `Questmaster` summon placement now scores nearby spawn spots by both horizontal and vertical distance so indoor summons stay closer to the player's actual floor.
- Interacting with the `Questmaster` now refreshes his full 30 second despawn timer so he does not vanish immediately after closing the UI.
- The `Magic Shard` item texture was reduced from the old oversized render to a smaller, more vanilla-like icon.

## 1.20.1 - Minecraft 26.1 Update

Release date: 2026-03-30

- Village Quest has been updated for Minecraft `26.1`.
- The inventory journal bookmark now renders more cleanly again.
- `Smoke over Blackstone` now shows its real objectives directly instead of the old unclear wording.
- Several delivery quests now check and consume all of their listed hand-in materials consistently again.
- `Smelting for the Smithy` now labels its ingot progress more clearly as `Iron Ingots`.
- Many daily, weekly, story, and pilgrim quest texts were cleaned up for clearer progress labels and more consistent item wording in both English and German.
- Several gathering, mining, and shearing quests were updated:
  - self-earned progress is tracked more reliably
  - `Fortune` bonus yield now counts properly where it makes sense
  - several hand-in quests now truly require their materials again at turn-in
- Relevant hand-in quests now show a clear red hint when progress is finished but the required turn-in items are no longer in the inventory.

## 1.20.0 - Village Stories Foundation And Progression Clarity Rework

Release date: 2026-03-29

- The `Questmaster` received a major `Story` expansion with four visible village storylines:
  - `The Failing Harvest`
  - `The Silent Forge`
  - `Market Road Troubles`
  - `Restless Pens`
- New permanent `Village Projects` were added:
  - `Village Ledger`
  - `Apiary Charter`
  - `Forge Charter`
  - `Market Charter`
  - `Pasture Charter`
  - `Watch Bell`
- `Market Rounds` was added as a second trade-focused daily.
- `Story` now unlocks after the first normal daily, while `Special` unlocks after the first real reputation gain.
- Relic quests now feel more like earned follow-up commissions because they are tied to both reputation and story progress.
- Village progression was split more clearly:
  - the `Questmaster` focuses more strongly on village-core work
  - late dangerous road jobs were moved toward the `Pilgrim`
  - `Watch Bell` now unlocks automatically once the four village-core stories are completed
- The `Pilgrim` was streamlined:
  - `Roadmarks for the Compass` is now a one-time special contract
  - later on, there is exactly one dangerous road rumor per day
- The `Wayfinder's Compass` was expanded into a staged relic with `Home`, `Field Bearings`, and `Roadmarks`.
- Quest rewards were changed from raw XP values to direct level gains.
- The journal, quest tracker, Questmaster UI, Pilgrim UI, and NPC presentation all received a major polish pass.
- Many smaller progress, turn-in, and UI issues were cleaned up.

## 1.19.5 - Quest Progress Hotfixes

- Villager trade quests now count reliably again, even when results are taken quickly with `Shift`-click.
- The anvil step of the shard quest is now tracked more reliably as well.
- `Pet Collar` became stricter and now only counts real recolors on the player's own tamed wolf or cat.

## 1.19.4 - NPC Self-Defense Update

- The `Questmaster` and `Pilgrim` can now defend themselves when attacked by players.
- Both gained warning lines, combat lines, and visible weapon behavior.
- Killing the `Questmaster` now gives the attacker a personal summon cooldown.

## 1.19.3 - Furnace Quest Hook Fixes

- Furnace-based quests now count correctly again, even when finished items are taken quickly from the output slot.
- Several cooking and smithing quests now properly consume their turn-in items.
- The `Provisions Satchel` was upgraded into a better travel reward.

## 1.19.2 - Inventory Compat Follow-Up

- The journal bookmark now sits more naturally on the inventory edge again.
- A better fallback was added for mods that use status-effect sidebars.

## 1.19.1 - Questmaster And Inventory Polish

- The `Questmaster` can now appear more sensibly in caves and underground bases.
- Breeding quests now count real animal pairs more cleanly.
- The journal access in the inventory was rebuilt into an animated bookmark tab.

## 1.19.0 - Reputation Expansion Batch

- The new `Monster Hunting` reputation track was added.
- Four monster-hunting dailies were added:
  - `Zombie Cull`
  - `Skeleton Patrol`
  - `Spider Sweep`
  - `Creeper Watch`
- The relic quests `Apiarist's Smoker` and `Surveyor's Compass` were added.
- Relic unlock thresholds were raised to `200` reputation.
- `Surveyor's Compass` later evolved into `Wayfinder's Compass`.
- Special and relic items were presented more clearly in the journal and UI.

## 1.18.0 - Weekly Quest System

- The weekly quest system was introduced.
- Seven weekly quests were added:
  - `Harvest for the Village`
  - `Bakehouse Stock`
  - `Smith Week`
  - `Stall and Pasture`
  - `Market Week`
  - `Night Watch`
  - `Road Warden`
- Weeklies were integrated into the Questmaster, journal, and quest tracker.

## 1.17.5-1.17.2 - Questmaster And Pilgrim UI Hotfixes

- Several UI passes improved text, buttons, scrolling, timers, and the overall layout of the `Questmaster` and `Pilgrim`.

## 1.17.1 - Guaranteed First Magic Shard

- The first completed quest now always guarantees one `Magic Shard`.
- After that, the normal shard chance applies again.

## 1.17.0 - Questmaster UI V2 And Decline Removal

- The `Questmaster` received its second major board UI.
- Daily accept and cancel flow was simplified.
- The old decline lockout was removed.

## 1.16.9-1.16.1 - Questmaster UI Polish Cycle

- The first Questmaster UI received many layout, tooltip, scrolling, and presentation improvements.
- The `Questmaster` no longer despawns while someone still has the UI open.

## 1.16.0 - Questmaster UI Foundation

- The old chat-only `Questmaster` interaction was replaced with a real quest window.
- The `Daily`, `Weekly`, and `Special` structure was established.

## 1.15.7-1.15.1 - Merchant's Seal Stabilization

- `Merchant's Seal` was stabilized and later made usable with the wandering trader as well.

## 1.15.0 - Relic Questlines Pack 1

- The first two relic questlines were added:
  - `Merchant's Seal`
  - `Shepherd's Flute`

## 1.14.0 - Reputation Unlocks And Pilgrim Progression

- Reputation gained clearer unlock thresholds.
- The `Pilgrim` began tying offers to player progress and reputation.

## 1.13.0 - Village Reputation Foundation

- The first four reputation tracks were added:
  - `Farming`
  - `Crafting`
  - `Animals`
  - `Village Trade`

## 1.12.9 - Leather Daily Removal

- The leather daily was removed from the active quest pool.

## 1.12.8-1.12.2 - Inventory Journal Button Rollout

- The journal received its first direct button in the inventory.
- Position, art, and stability were improved several times.

## 1.12.1 - Journal Player Command Overhaul

- The journal was reorganized and its player command overview was improved.

## 1.12.0 - Quest Progress Feedback And Tracker

- Quest progress now appears directly in the action bar.
- Milestone feedback and the quest tracker were added as well.

## 1.11.7-1.11.5 - Starreach Ring Texture Iteration

- The `Starreach Ring` received multiple art and polish passes.

## 1.11.4-1.11.1 - Secret Shard Quest Debug And Fix Pass

- The hidden shard quest and its cache behavior were made more robust.
- The `Magic Shard` text was shortened.

## 1.11.0 - Secret Shard Quest And Starreach Ring

- The hidden quest `Whispers of the Shards` was added.
- The `Starreach Ring` was introduced as its reward.

## 1.10.2-1.10.1 - Magic Shard Bonus Daily And Daily Reopen

- `Magic Shards` can now unlock an extra daily on the same day.
- Dailies can be reopened later on the same day instead of being lost immediately.

## 1.10.0 - Questmaster NPC Replaces Quest Block

- The old quest block was replaced by the summonable `Questmaster`.

## 1.9.15-1.9.14 - Magic Shard Introduction

- The `Magic Shard` was introduced.

## 1.9.13-1.9.10 - Release Cleanup And Command Pruning

- Standard daily rewards were streamlined toward wallet currency and XP.
- Old release leftovers and unnecessary commands were cleaned up.

## 1.9.9-1.9.1 - Pilgrim Economy And Presentation Polish

- The `Pilgrim` received departure and respawn timers plus several presentation improvements.

## 1.9.0 - Painting Size Rebalance And Two-Currency Overhaul

- The wallet economy was rebuilt around `Silvermark` and `Crown`.
- Pilgrim paintings and prices were rebalanced.

## 1.8.2-1.8.0 - Painting Expansion And Currency Naming Cleanup

- More Pilgrim paintings were added.
- Sizes, prices, and currency naming were cleaned up.

## 1.7.8-1.7.1 - Pilgrim UI And Trade Polish

- The Pilgrim trade screen received several layout and readability improvements.

## 1.7.0 - Pilgrim Trader

- The traveling `Pilgrim` merchant was introduced.

## 1.6.2-1.6.0 - Digital Wallet And Journal Basics

- Coin items were replaced by the digital wallet.
- Wallet display and early journal basics were added.

## 1.5.10-1.5.8 - Wolkensprung Split

- `Wolkensprung` was split out of `Village Quest` into its own separate mod project.

## 1.5.7-1.5.1 - Daily Admin, Balance, And Selection Polish

- Daily testing tools, balancing, and rotation logic were improved.

## 1.5.0 - Daily Wave 2

- Five more action-based dailies were added:
  - `River Meal`
  - `Autumn Harvest`
  - `Smith Smelting`
  - `Stall Breeding`
  - `Village Trading`

## 1.4.4-1.4.1 - Project Continuity And Cleanup

- Project continuity helpers and early cleanup work were introduced.

## 1.4.0 - Daily Wave 1

- The first action-based dailies entered the game.

## 1.3.1-1.3.0 - Daily Refactor And Reset Command

- The daily system was restructured.

## 1.2.1-1.2.0 - Compatibility And Localization

- Early compatibility and localization work was added.

## 1.1.2-1.1.0 - Early Daily-State And Peace-Armor Cleanup

- Early quest-state and text cleanup was implemented.

## 1.0.5-1.0.1 - Early 1.21.11 Compatibility Fixes

- Early compatibility issues from the first `1.21.11` line were fixed.

## 1.0.0 - Baseline

- Baseline release on Minecraft `1.21.11`.
