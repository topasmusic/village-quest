# Markets, Trader Places and Visits

Part of [Living Kingdoms 2.6](living-kingdoms.md).

## Build and assign a trader place

The **Market Desk** controls the market area. Craft a **Trader Stall** from
**3 white wool, 3 oak planks and 2 sticks**. Leave safe reachable standing space
directly at it and place an ordinary chest or barrel beside it. The trader space
must be within one horizontal block of the stall and two of its container, with
at most one block vertical difference. Each place needs its own resources;
sharing standing space, counter or container with another place is rejected.

A single real stall satisfies the market counter requirement. The supported
legacy alternative is two connected counter blocks. The stall needs neither
daylight nor a lamp within five blocks; roofs are allowed. The market building
still needs its general light source and two desk access spaces.

Each placed stall randomly keeps green, red, blue, yellow or lime cloth, with
identical function and one shared item. Older stalls without a variant stay green.

Confirm/register the official Market Desk. Open **Market places**, choose one of
five numbered cards, highlight an available verified location, then explicitly
assign it. The list distinguishes standing position, counter and container.
Highlight previews all three. Release a resource at its old place before assigning
it elsewhere. Existing assigned legacy places remain supported; new assignments
use the custom stall. Recheck places after rebuilding or relocating the market.
Removing a place preserves the trader identity and visit history.

One stall supports one trader. [Village recognition](kingdom-buildings.md#five-recognition-projects)
controls the capacity of the next regular visit, from one up to five.

## Buying and selling

Every click trades **one fixed displayed lot** at the shown total coin price.
There are no one-lot/two-lot buttons. For more goods, wait for confirmation and
click again. **You have** counts matching plain goods in the main inventory;
named/decorated variants and equipment are excluded from selling.

The screen keeps stock, home demand, your wallet and the trader's remaining
buying budget separate. Click a goods row for paged price details; goods and
details have separate page controls. Amount tooltips show full totals.
Waiting buttons retain their artwork while their input stays locked. Real
balance, budget and stock changes remain visible. **Refresh** requests current
server data, including changes made by other players. Escape/X closes the market.

If a trade is awaiting save confirmation, wait for it to finish. Reopening or
refreshing does not reset goods, bypass a held trade or repeat a payment.

## Assortments and real home needs

Trader home identity and earned stage determine supplies: granaries offer basic
supplies, forges infrastructure, pastures travel and animal care, apiaries honey
and waxed copper, and archives planning/building materials. Young stages usually
have 3–4 sale rows, middle stages 4–6 and higher stages 5–8. A new visit can import
up to two useful specialties from direct active trade or completed friendship
partners with confirmed home profiles. The origin's earned stage controls these
imports. Indirect links do not qualify. Players cannot reroll a saved visit by
reopening it or discovering the trader separately.

One buying request may appear when a verified current home need matches:

| Home | Possible requested lot |
|---|---|
| Granary | 32 wheat for seed reserves, or 32 bread for pantry needs. |
| Apiary | 16 honey bottles for forage, or 8 honeycombs for wax stock. |
| Pasture | 8 hay blocks for fodder, 16 white wool, or 8 leather for husbandry. |
| Forge | 32 coal, 16 copper ingots, or 8 iron ingots. |
| Archive | 32 paper or 8 ink sacs. |

Payment is usually 1–2 Silvermarks, capped by verified acquisition/conversion
prices. A request is not guaranteed. Seeds, potatoes, flowers and feathers are not
buying requests. Ordinary crop/bee production remains normal gameplay; bought ripe
planting data or stored honey cannot use a fresh/empty source proof.

At most four lots per trader are requested, with at most 128 items of each kind
accepted per home need cycle across players and traders. A request occupies one
of at most eight rows and has no sale stock. Each trader's buying budget is
16 Silvermarks. Need/budget exhaustion is shown. Demand alone cannot allow selling
while the global acquisition-price proof is unavailable.

Forge powered-rail lots contain eight rails; piston, observer and comparator lots
contain four components at their quoted prices. Archives can supply signs,
ordinary/glow frames and other stage-eligible planning materials. Read the live
server-issued goods and price; the current saved visit is authoritative.

## Price adjustments

Personality is combined with stock, need, permanent home-village bond and personal
social reputation. Half the initial visit stock or less adds three percentage
points to purchase prices. Remaining verified home need adds three points to
both adjustments. Trusted/Allied bonds with the trader's actual home improve
buying/selling by three/six points. Host-village social trust has twice the weight
of guild standing; the weighted score gives:

| Score | Purchase adjustment from social reputation |
|---|---|
| At most −60 | +12 percentage points |
| At most −20 | +6 |
| At least +20 | −3 |
| At least +60 | −6 |
| Otherwise | 0 |

Selling uses the opposite social-reputation adjustment. The final combined
adjustment stays within ±15%. Prosperity discounts, whole-Silvermark rounding
and global buyback ceilings still apply. Small amounts can round to the same
price despite a changed factor. The exact quoted lot total is used for payment.

## Visits and direct-route travel

Morning/day-market traders use assigned usable places and one saved shared visit.
Night, unloading, missing places, new observers and a new day cannot refill it.
A later regular visit needs its observed restocking interval and completed prior
trade recovery. [Recognition](kingdom-buildings.md#five-recognition-projects)
changes later capacity, not the present stock or budget.

Visitors travel on real direct village connections. Trading opens only after
arrival at a verified place and closes on departure/return. The same logical
person cannot appear both at home and as a visitor. A brief evening conversation
may keep a guest at the stall after trade closes; there is no night market.
The departure display uses the server's actual plan, including that evening stay.
Temporary route pauses/unknown observations pause travel. A permanently removed
or replaced route ends the affected virtual trip safely without refilling goods.
Changes to village stages and agreements affect later visits.

Trader greetings can remember you, name their real home, mention actual freight
and respond to local standing. Traders have no separate quest chain. Repeated
small attacks, personal warnings and service restrictions follow
[Social reputation](social-reputation.md); right-clicking does not extend them.

## Inn opening period and special guests — 2.6

A newly recognized inn has a two Minecraft day opening period (40 minutes at
20 ticks/second). Only loaded valid-inn time counts. Unloading/restarting pauses
this clock; an invalidated or newly registered replacement inn starts again.
Traveling market traders and the four special guests appear only after it ends.
Ordinary village staff and already saved identities are preserved.

Lutte, Simon, Marius and Raphael occasionally visit populated player-managed
villages with a valid inn and a nearby managing player. Each eligible Minecraft
day has one saved 10% draw for the village; extra inns, joins and reloads cannot
reroll it. At most one special guest stays near the inn for 6–10 loaded minutes,
then a two-day cooldown follows. The next guest avoids repeating the last one.
Arrivals are daytime only, with no raid or local hostile threat. Invalid inns end
visits; unloading pauses them without creating a replacement body.

Right-click uses existing private chat and speech bubbles, without trade or a new
menu. They remember meeting the player and discuss actual known routes, building
needs or recorded village milestones. These conversations grant no money, items,
project completion or other free progress. Raphael also speaks of composing new
tunes and performing them soon. Raphael has no quest line in this release.

New caravan surnames and these four fixed bynames follow each client's DE/EN/ES
language. First names and logical identities are stable. Existing/manual names
are kept verbatim. The four visitor names never enter the ordinary random pools.
