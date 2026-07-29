# Prosperity, Services, and the Village Economy

`Prosperity & Prestige` is the long-term Crown economy introduced in Village Quest `2.1.0`. It gives established players permanent investments, planned purchases, short-term services, and prestige goals without replacing quests, the traveling Pilgrim, or physical trade routes.

Open the board from the Journal after relevant village progression exists, or use `/vq prosperity`.

Existing saves do not restart. Completed charter projects and installed routes are read directly, while all new ranks, commissions, bonuses, collection unlocks, and liveries use persistent player state.

## Prosperity Branches

Each branch requires its matching permanent village project. Every branch has three ranks:

| Rank | Cost |
|---|---:|
| `Foundation` | `6 Crowns` |
| `Established` | `12 Crowns` |
| `Legacy` | `24 Crowns` |

There are `15` ranks across the complete village:

| Branch | Required project | Permanent effect |
|---|---|---|
| Apiary | Apiary Charter | `5/10/15%` off Pilgrim bee and apiary goods |
| Forge | Forge Charter | `5/10/15%` off forge goods and permanent route upgrades |
| Market | Market Charter | `5/10/15%` off general market goods; unlocks commissions and lowers their fee |
| Pasture | Pasture Charter | `5/10/15%` off animal and pasture goods |
| Road Watch | Watch Bell | `5/10/15%` off road goods and `3/6/9` percentage points less route-incident chance |

The selected next rank shows its exact Crown price above `Invest`.

## Pilgrim Commissions

Market `Foundation` unlocks prepaid commissions for every currently unlocked Pilgrim offer.

- goods use their normal Prosperity-discounted price;
- the fee is `3 Crowns` at Foundation, `2` at Established, and `1` at Legacy;
- only one commission can wait at a time;
- delivery happens on the first Pilgrim visit after the next dawn; and
- delivery counts as a real Pilgrim purchase for compatible progression.

## Paid Village Services

| Service | Cost | Effect |
|---|---:|---|
| Road Patrol | `8 Crowns` | prevents incidents on the selected route for its next three journeys |
| Survey Report | `5 Crowns` | adds `15` road-quality points to the selected route, up to `100` |
| Emergency Recall | `10 Crowns` | clears an incident and returns the selected caravan to its nearest endpoint |
| Village Festival | `15 Crowns` | adds `25%` currency to the next three Daily, Weekly, or normal Pilgrim-contract payouts |
| Guild Ceremony | `25 Crowns` | at Trade Guild rank `3`, adds `25%` currency to the next three completed freight contracts |

Festival and Ceremony uses are bounded; purchasing one resets its remaining uses to three.

## Prestige Collection

The collection provides ten permanent milestones:

- five route liveries: Crimson, Forest, Azure, Ochre, and Violet;
- Guild Banner at `4` total Prosperity ranks;
- Mapmaker Crest at `7` total ranks;
- Market Pavilion at Market `Established`;
- Watchtower Pennant at Road Watch `Established`; and
- Guild Hall Trophy after all `15` ranks.

A selected livery synchronizes the caravan NPC outfits, full-map line, minimap line, and route accent. The five non-livery milestones deliver named keepsakes.

## Economy Ledger and Market Week

The final tab records lifetime currency earned and spent, Pilgrim purchases, commissions, services, investments, collection pieces, total ranks, and remaining Festival or Ceremony uses.

`Market Week` is offered only when the wallet can cover the three cheapest currently unlocked Pilgrim goods. Invalid unaccepted offers are replaced safely; accepted Weeklies and their progress are never changed.

## Focused Admin Test

`/vq admin economy testsetup [player]` resets the target player's unreleased economy state, unlocks the five matching projects and Caravan Yard, prepares the five-route QA network, grants `500 Crowns`, and opens the board.
