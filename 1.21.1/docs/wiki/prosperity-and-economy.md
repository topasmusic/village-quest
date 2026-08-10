# Prosperity, Services, and the Village Economy

`Prosperity & Prestige` is the long-term Crown economy introduced for Village Quest `2.1.0`. It gives established players useful permanent investments, planned purchases, short-term services, and prestige goals without replacing quests, the traveling Pilgrim, or physical trade routes.

Open the board from the Journal after relevant village progression exists, or use:

\`\`\`mcfunction
/vq prosperity
\`\`\`

Existing saves do not restart. Completed charter projects and installed routes are read directly, while all new ranks, commissions, bonuses, collection unlocks, and liveries use persistent player state.

Prosperity is project-gated, not globally trade-route-gated. Completing any matching `Apiary Charter`, `Forge Charter`, `Market Charter`, `Pasture Charter`, or `Watch Bell` project opens the board and its relevant branch. Apiary, Forge, and Pasture investment can therefore begin before a trade route exists. The Market path is the route tutorial: `Market Road Troubles` awards the `Market Charter`, ledger map, and one provisional route; `The Empty Caravan` later expands that preview to the full five-route Caravan Yard.

The board uses the same visual rules as the Journal and Pilgrim Trader. The five section symbols and Collection artwork are centered in their controls, the current Crown/Silvermark balance is always shown with item icons in the upper-right wood header, and every long list uses the same brass-and-oak double-arrow scroll handle.

## Prosperity Branches

Each branch requires its matching permanent village project. Every branch has three ranks:

| Rank | Cost |
|---|---:|
| `Foundation` | `6 Crowns` |
| `Established` | `12 Crowns` |
| `Legacy` | `24 Crowns` |

The currently selected rank shows this Crown price directly above `Invest`, so the charge is visible before confirmation.

There are `15` ranks across the complete village:

| Branch | Required project | Permanent effect |
|---|---|---|
| Apiary | Apiary Charter | `5/10/15%` off Pilgrim bee and apiary goods |
| Forge | Forge Charter | `5/10/15%` off forge goods and permanent route upgrades |
| Market | Market Charter | `5/10/15%` off general market goods; unlocks commissions and lowers their fee |
| Pasture | Pasture Charter | `5/10/15%` off animal and pasture goods |
| Road Watch | Watch Bell | `5/10/15%` off road goods and `3/6/9` percentage points less route-incident chance |

The board always shows the current effect and the exact next-rank effect before money is spent.

## Pilgrim Commissions

Market `Foundation` unlocks prepaid commissions for every currently unlocked Pilgrim offer.

- the goods use their normal Prosperity-discounted price
- the commission fee is `3 Crowns` at Foundation, `2` at Established, and `1` at Legacy
- only one commission can wait at a time
- delivery happens on the first Pilgrim visit after the next dawn
- delivery counts as a real Pilgrim purchase for compatible quest and collection progress

Commissions make a known good planable, but the player still has to meet and visit the traveling Pilgrim.

## Paid Village Services

Services are repeatable Crown sinks with bounded effects:

| Service | Cost | Effect |
|---|---:|---|
| Road Patrol | `8 Crowns` | prevents incidents on the selected route for its next three journeys |
| Survey Report | `5 Crowns` | adds `15` road-quality points to the selected route, up to `100` |
| Emergency Recall | `10 Crowns` | clears an incident and returns the selected caravan to its nearest endpoint |
| Village Festival | `15 Crowns` | adds `25%` currency to the next three Daily, Weekly, or normal Pilgrim-contract payouts |
| Guild Ceremony | `25 Crowns` | at Trade Guild rank `3`, adds `25%` currency to the next three completed freight contracts |

These effects do not stack without limit. Buying a new Festival or Ceremony resets its remaining uses to three.

## Prestige Collection

The collection provides ten permanent milestones:

- five route liveries: Crimson, Forest, Azure, Ochre, and Violet
- Guild Banner at `4` total Prosperity ranks
- Mapmaker Crest at `7` total ranks
- Market Pavilion at Market `Established`
- Watchtower Pennant at Road Watch `Established`
- Guild Hall Trophy after all `15` ranks

A purchased livery can be applied to any installed route. Its caravan NPC outfits, full-map line, minimap line, and route accent use the same color. Ownership remains permanent even when it is no longer the active livery.

The five non-livery milestones also deliver named keepsakes to the player. If the inventory is full, the keepsake drops safely beside the player.

## Economy Ledger

The final board tab records:

- total currency earned and spent after the `2.1.0` system begins tracking
- Pilgrim purchases and delivered commissions
- services and Prosperity investments
- collection pieces and total ranks
- remaining Festival and Ceremony uses

These are informational lifetime counters. They do not create a second score requirement.

## Market Week Protection

`Market Week` requires three Pilgrim purchases. A new unaccepted offer is therefore allowed only when the wallet can cover the three cheapest currently unlocked Pilgrim goods. This is more accurate than the earlier one-Crown candidate check.

If an invalid `Market Week` was offered but not accepted, the game replaces it safely. Once accepted, it is never rerolled or removed underneath the player.

## Focused Admin Test

\`\`\`mcfunction
/vq admin economy testsetup
\`\`\`

The test setup:

- resets only the player's existing `2.1.0` economy ranks, commissions, services, collection, and ledger counters
- unlocks the five matching charter projects and Caravan Yard
- prepares the complete five-route QA network
- sets the wallet to `500 Crowns`
- opens `Prosperity & Prestige`

Use the ranks progressively rather than buying everything at once. Verify prices before and after each relevant rank, place one commission and advance to a later day, buy route-targeted services on different routes, apply all five liveries, and inspect the economy ledger after each action.
