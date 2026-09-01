# City livery check

**Status: not yet walked. Every tile on this list is unverified, and none of them
should be moved until somebody has stood on it.**

On 2026-09-01 the dataset gained **127 citizens** across **21 of the 27 region
files**, taking the roster from 142 to 269 and the whole dataset from 184 records
to 311. They are generic, reusable townsfolk: nobody here is a character. What
tells one from the next is the kit, the pose, the palette, the name and the way it
is facing; what tells one *city* from the next is a livery.

This is a companion to `docs/CITY-TOP-UP-CHECK.md`, not a replacement for it. That
file's 33 figures are still unwalked and still need walking.

## The livery

Each colour is the game's own 16-bit HSL word — six bits of hue, three of
saturation, seven of lightness — and each was measured off a real game asset
rather than chosen. `NOTICE` item 12 says which asset, city by city.

| City | Torso | Legs | Trim |
|---|---|---|---|
| Varrock | `10034` h9/s6/l50 | `39` h0/s0/l39 | `10050` h9/s6/l66 † |
| Falador | `107` h0/s0/l107 | `90` h0/s0/l90 | `43818` h42/s6/l42 |
| Lumbridge | `40500` h39/s4/l52 | `40484` h39/s4/l36 | `99` h0/s0/l99 |
| Ardougne | `1965` h1/s7/l45 | `1950` h1/s7/l30 | `99` h0/s0/l99 |
| Al Kharid | `55085` h53/s6/l45 | `55072` h53/s6/l32 | `99` h0/s0/l99 |
| Draynor | `23351` h22/s6/l55 | `23334` h22/s6/l38 | `54049` h52/s6/l33 |
| Catherby | `34490` h33/s5/l58 | `34472` h33/s5/l40 | `12845` h12/s4/l45 |
| Grand Exchange | `48695` h47/s4/l55 | `48678` h47/s4/l38 | `1945` h1/s7/l25 |
| Edgeville | — | — | — |

**† Varrock's trim is measured and is worn by nobody.** Varrock's eight figures use
three cuts, and the trim only ever appears on the fourth, so `10050` is a colour this
table publishes and no record carries. It is left in the table because it is the
measurement — the Varrock guard's own kiteshield highlight — and deleting it would
lose the fact rather than correct the claim. Every other value above is worn by at
least one shipped record, and
`CityLiveryCheckTest.everyPublishedLiveryColourIsWornExceptTheOneNamedHere` is what
stops that list of exceptions growing quietly. If Varrock ever gains a fourth cut,
that test goes red and this footnote comes out.

**Each city keeps its own colour on the torso in every cut.** That is the invariant
the walk should judge, and it is the one the data actually holds: Falador's torso is
white in all four of its cuts and Lumbridge's is blue in all four of its.

**What is *not* true is that the trim stays trim.** Cut 3 promotes the trim to the
whole legs slot, so a cut-3 figure is a 50/50 split rather than a dominant colour
with an accent — and for Falador and Lumbridge the two cut-3 outfits are mirror
images of each other:

| | torso | legs |
|---|---|---|
| Falador cut 3 (Berengar, Editha) | `107` white | `43818` royal blue |
| Lumbridge cut 3 (Lisbeth, Peveril) | `40500` blue | `99` white |

Four records, and until 2026-08-30 this section claimed the white-first/blue-first
split was "enforced by construction", which those four are the counterexample to.
They are kept rather than repainted, because each still wears its own city's colour
on the torso and cut 3 does the same thing in all eight liveried cities — but the
safety argument is now the measurement rather than the construction. **The two blues
are three hue rungs of sixty-four apart — 16.9°, not the nineteen degrees this
document used to claim** — which is a real but narrow separation, so *whether a
white-over-blue Falador figure and a blue-over-white Lumbridge figure read as two
cities is a question for the walk*, and it is the first thing to look at in each.
The separation is pinned by
`CityLiveryCheckTest.theFaladorAndLumbridgeBluesStayApartAndTheMirrorIsExactlyFourRecords`.

**Edgeville deliberately has none.** See its section.

Within a city, four "cuts" vary the lightness of the torso and legs, and cut 3
swaps the darker leg shade for the city's trim. **They do not vary the boots.** Only
**8 of the 127** carry a boot slot at all, because a record repaints a slot only
where its donor kit already aimed at one, and 24 of the 26 donor kits never aim at
the boots base `4626`. The eight come from two donor kits — "Butler Jarvis" (5) and
"Mysterious Old Man" (3) — and their boot colour is written on their `Livery:` line
alongside the torso and the legs. Everybody else's footwear is whatever their donor
kit's boot model is authored in, untouched by this pass.

`noEcho` is set on all 127, so the `Crowded` density derives nothing from them — a
derived figure wears its source's palette *dealt onto different slots*, which is
precisely what a livery cannot survive.

## How each tile was chosen, and what that is worth

**Nothing offline can tell you whether a tile is walkable.** The dataset stores a
tile; the game stores what is on it. So each of the 127 placements below sits a
stated distance from a figure already in the dataset, and the `Placed near:` line on
every entry names that figure and the distance. **The distance is Chebyshev** — the
larger of the two axis gaps, which is what "tiles away" means when you walk it —
and `CityLiveryCheckTest.everyPlacedNearLineNamesARecordThatShipsAtTheDistanceItClaims`
checks all 127 against the data.

**Not every anchor is proven, and this section used to say they all were.** Until
2026-08-30 the sentence above read "a figure whose own tile is proven — somebody
stood on it in game". That is true of 72 of the 127. The other **55 name one of the
33 citizens added on 2026-08-29** — the ones this file's own opening says are "still
unwalked and still need walking". An unwalked figure two tiles away is not evidence
about ground, it is a second guess resting on the first. Verified examples: Farid→Tarik,
Ingrid→Aldous, Gaultier→Coren, Gorden→Ilsa, Sancie→Sela.

So there are two questions per entry, not one, and the entries separate them:

- **`Placed near: <name>, N tiles`** — where the tile was reasoned from. If the
  anchor is one of the 2026-08-29 records the line says so, in the form
  `Placed near: Tarik (unwalked), 2 tiles`.
- **the reach note**, on the entries where the nearest *genuinely* proven tile is
  far away. "Genuinely proven" means the authored tile of a citizen that came from
  upstream's dataset, on the same plane — the only tiles in this repository somebody
  is known to have stood on.

**Twenty-one of the 127 are nine tiles or more from the nearest genuinely proven
tile** — not seven. Seven was the figure this file published while measuring
distance to the nearest anchor of *either* kind; measured against upstream tiles
only it is 21 by Chebyshev and 24 by Euclid. The list is the QA order: work down it,
because it is sorted by the only risk this document can measure offline.

| # | Figure | City | Tiles to nearest proven | That tile |
|---|---|---|---|---|
| 1 | **Adela** | Falador | 27 | Sir Wendes |
| 2 | **Sybilla** | Falador | 26 | Sir Wendes |
| 3 | **Thurstan** | Falador | 25 | Sir Wendes |
| 4 | **Gervase** | Falador | 24 | Sir Wendes |
| 5 | **Ferrand** | Ardougne | 15 | Legends' Guard |
| 6 | **Gorse** | Draynor | 13 | Sailor |
| 7 | **Gorden** | Catherby | 11 | Joanne |
| 8 | **Thistle** | Draynor | 11 | Sailor |
| 9 | **Editha** | Falador | 11 | Sir Wendes |
| 10 | **Rashid** | Al Kharid | 10 | Ali the wanderer |
| 11 | **Basma** | Al Kharid | 10 | Ali the wanderer |
| 12 | **Isabeau** | Ardougne | 10 | Legends' Guard |
| 13 | **Thomasin** | Catherby | 10 | Charlie |
| 14 | **Bertram** | Draynor | 10 | Sailor |
| 15 | **Thackeray** | Grand Exchange | 9 | Child |
| 16 | **Zaid** | Al Kharid | 9 | Ali the goat herder |
| 17 | **Sancie** | Ardougne | 9 | Legends' Guard |
| 18 | **Lark** | Draynor | 9 | Sailor |
| 19 | **Rushen** | Draynor | 9 | Sailor |
| 20 | **Isolde** | Falador | 9 | Sir Wendes |
| 21 | **Amice** | Falador | 9 | Sir Wendes |

Rows 1 to 4 are the four inside Falador's Saradomin church. They are 24 to 27 tiles
from Sir Wendes, who stands in the park — the only vouched-for ground in region
11828 — and there is no proven tile in that building or anywhere near it. Their own
`Placed near` lines point at Nessa, who is one of the 2026-08-29 records and has
never been walked either, so they are two inferences deep rather than one. They are
here because the owner asked for a Saradominist group in the church; they are the
weakest four placements in the pass by a wide margin, and if any of them is standing
in a pew or an altar the fix is to shift it into the aisle rather than to delete it.

Rows 5 to 14 are the ten this file did not previously mention at all. Seven of them
(**Ferrand, Gorse, Gorden, Editha, Rashid, Basma, Isabeau**) looked close only
because the figure they were measured against is itself unwalked.

Rows 15 to 21 are at exactly nine. One of them — **Thackeray** — sits on open ground
*between* two proven tiles rather than out past one: the Child is nine tiles
north-east of him and Peter and Richard are fourteen to the south-west. That is the
better of the two ways to be far away, but it is still far away. The other six are out
past their nearest proven tile rather than sitting between two: Zaid has a second at
twelve tiles, but it is further along the same bearing rather than opposite, and
Sancie, Lark, Rushen, Isolde and Amice have nothing else proven within thirty tiles in
any direction at all.

Thomasin, one row up at ten, is bracketed the same way Thackeray is — Charlie ten
tiles north-west, Joanne fifteen east. The paragraph above named her as one of the
rows at nine until 2026-08-31, and named Rushen with her, who is out past a single
tile like most of the band.
`CityLiveryCheckTest.theProseAboveTheReachTableNamesFiguresFromTheRowsItClaims`
now checks that every figure named in this section belongs to the band the sentence
claims, which is the half of the mistake a test can hold.

**Twelve of the 127 are wanderers, and their boxes are unverified too.** A box is a
rectangle and the walk visits all of it, so a box with a tree in it produces a
citizen who walks through the tree. The Ground Markers block therefore carries
**151** markers, not 127: one yellow marker per figure, plus a cyan marker on the
south-west and north-east corner of each of the twelve new boxes.

## How to find them

Every tile below is `x, y, plane`. Copy the JSON block at the bottom of this file,
then right-click the **Ground Markers** plugin in the sidebar and choose **Import**.

- **Yellow** squares are the 127 figures, labelled with the citizen's name.
- **Cyan** squares are the 24 wander-box corners, labelled `<name> box SW` and
  `<name> box NE`.

Turn **Lively Cities** on, set **Crowd density** to `Full`, and set **Render
distance** high enough that a figure is drawn before you arrive.

**`Crowded` adds nothing to this walk and can be left alone.** Every one of these
127 records carries `"noEcho": true`, so the setting derives nothing from any of
them; it still adds the same 51 derived figures it added before this pass, all of
them hanging off records `docs/CITY-TOP-UP-CHECK.md` already covers.

## What a tick means

A ticked box means **all** of:

- the figure is standing on the ground, not inside a wall, counter, hedge, fence
  or water, and not floating;
- it is not inside a doorway or on a staircase;
- it is not standing inside a real NPC or a shop table;
- its pose reads as deliberate from a few tiles away;
- **its clothes are its city's colour, and read as cloth rather than as skin**;
- and for a wanderer, the same is true everywhere inside its cyan rectangle.

An unticked box with a note is a work item: move the tile a few squares, or trim
the box. Both are one-line edits to the record in
`src/main/resources/RegionData/<region>.json`.

**Do not "fix" a figure by deleting it.** Every city is pinned at its exact count
by `RegionDataLoaderTest.everyCityHoldsTheNumberOfCitizensItIsSupposedTo`, and a
deletion also moves the roster total, the remarks partition, the wander-box count
and this file's own marker count. Move it instead.

---

## Edgeville — 18

*Getting there: Amulet of glory, or the lodestone. Everything below is in one
region.*

**Edgeville is the one city here with no livery, and that is the thing to look
at.** It has no crest, no banner and no guard model of its own — its guards use
Falador's. Rather than invent a colour for it, its eighteen figures keep their
donor's own colours pushed towards the dark end, each by a different amount. The
question for the walk is whether that reads as *deliberately unbranded* next to
a liveried Varrock, or just as a set of muddy townsfolk.

**"Dark" here is relative to the donor, not absolute, and two figures show why that
matters.** The rule is a lightness drop applied to whatever the donor was wearing —
17 of the 18 torsos are strictly darker than their donor's and the drops run from 4
to 29 rungs — so a figure copied from somebody already in black comes out black, and
a figure copied from somebody in white comes out light grey. The two to look at:

- **Torvig** — donor "Brother Edwy", a monk in white. His legs are `91` (h0/s0/l91),
  down 12 rungs from the donor's `103`, and still one rung off Falador's own leg
  white `90`. He is the lightest figure in Edgeville and he is not a mistake; he is
  the rule working on a pale donor. If he reads as a Falador knight who wandered
  north, say so.
- **Dagg** — donor "Wilhelm". Torso `7232` at lightness 64, down 8 rungs and
  desaturated two more. Second lightest, same reason.

Two of the eighteen are at the floor and could not move: **Bregg**'s legs and boots
and **Sten**'s torso and legs are their donor's exact colours because the donor was
already at lightness 6. Sten's boot is the one slot in Edgeville that is *lighter*
than its donor — `43910` against `43906`, four rungs, both of them black. Neither is
visible; both are written down so the rule is not read as tidier than it is.
`CityLiveryCheckTest.everyEdgevilleFigureIsItsDonorsColoursDarkened` pins all of it.

**Nobody here is holding a weapon, and "that is impossible" was the wrong reason.**
The owner asked for Edgeville to read as a place PKers pass through. The claim
recorded against that was that the dataset has no armed kit to dress one from,
because the shipped `Guard` kit carries no recolour pairs at all and so cannot take
a livery. The first half is true; the conclusion is not. A read-only decode of the
1.12.36 cache says two shipped kits are armed *and* dyeable:

| record | file | held model | exposes |
|---|---|---|---|
| `Dark wizard` | 12853 | `4591` | `8741` torso, `25238` legs, `6798` hair |
| `Saradomin wizard` | 12854 | `539` | `8741` torso, `25238` legs |

Neither is among the 26 donor kits this pass used. What the two props actually are
can be settled offline and now has been:

- **`539` is a weapon** — it is the wielded model of five items: 1215 "Dragon
  dagger", 1231 "(p)", 5680 "(p+)", 5698 "(p++)" and 20407, a *second* unpoisoned
  "Dragon dagger". That is three poisoned variants and two plain ones, not the "four
  poisoned variants" this file said until 2026-08-31. The NPCs carrying it are 685
  "Stranger", 2955 "Saradomin wizard", 7309 "Ancient Wizard", 10940 "Assassin", 14193
  "Soldier" and 15689 "Warrior" — the first three were missing from the list too, and
  2955 is the same name as the shipped record that holds the model.
- **`4591` is a book, not a staff.** It is the wield model of item 3850, and the
  NPCs carrying it are NPC 4242 "Reldo" — Varrock's librarian — 7064 and 7065 "Dark
  wizard", 7066 "Wizard", 7611 "Meleti", 10617 "Lauretta" and 11446 "Apprentice
  Felix". The two "Dark wizard"s were missing from this list until 2026-08-31, which
  is worth noticing: the shipped record holding `4591` is itself named "Dark wizard",
  so the omission hid the one NPC that names the kit. The record plays
  `StandingWithBook` / `WalkingWithBook`, which is the same answer from the other
  direction.

So the honest statement is **not attempted**, not impossible. It was not attempted
here for two reasons, and both are about this pass rather than about the models. A
dagger and a book are not the staves the request implies — most Edgeville PKers are
mages — so the one kit that is genuinely PK gear is a melee kit. And adding any
figure moves the count of records carrying `noEcho`, which is pinned at exactly the
127, along with the roster total, the remarks partition, the marker count and this
file's own totals. That is a content pass with its own walk, not a correction to
this one. If somebody does it, `Saradomin wizard`'s kit dyed dark is where to start.

### Region 12342 — the bank, the road east and the ditch approach

- [ ] **Gorm** — `3092, 3494, 0`, stationary, facing east
      Examine: "Waiting on somebody who is late, and getting later."
      Kit: "Sergeant Damien" (12853) · `ArmsCrossedReady` / `HumanWalk`
      Livery: none — donor's own colours, darkened
      Placed near: Andre, 2 tiles

- [ ] **Sable** — `3094, 3497, 0`, stationary, facing south
      Examine: "She has counted her supplies twice and is starting again."
      Kit: "Marta" (11829) · `Think` / `HumanWalk`
      Livery: none — donor's own colours, darkened
      Placed near: Andre, 4 tiles

- [ ] **Hesk** — `3093, 3500, 0`, stationary, facing west
      Examine: "Fully equipped, entirely unhurried."
      Kit: "Perrin" (12338) · `HumanLeanReady` / `HumanWalk`
      Livery: none — donor's own colours, darkened
      Placed near: Andre, 4 tiles

- [ ] **Rand** — `3088, 3493, 0`, stationary, facing north
      Examine: "He says he is only going as far as the ditch."
      Kit: "Idris" (10804) · `HumanIdle` / `HumanWalk`
      Livery: none — donor's own colours, darkened
      Placed near: Andre, 3 tiles
      Says: "Only as far as the ditch."

- [ ] **Vorn** — `3087, 3500, 0`, stationary, facing east
      Examine: "Reading the same notice board he read on the way out."
      Kit: "Emme" (12853) · `Think` / `HumanWalk`
      Livery: none — donor's own colours, darkened
      Placed near: Damien, 3 tiles

- [ ] **Dagg** — `3095, 3492, 0`, stationary, facing west
      Examine: "Somebody told him the north was quiet today."
      Kit: "Wilhelm" (10804) · `HumanSmugIdle` / `HumanWalk`
      Livery: none — donor's own colours, darkened
      Placed near: Andre, 5 tiles

- [ ] **Mirek** — `3091, 3503, 0`, stationary, facing south
      Examine: "He has not decided, and the deciding is the whole afternoon."
      Kit: "Corliss" (12338) · `NervousIdle` / `HumanWalk`
      Livery: none — donor's own colours, darkened
      Placed near: Damien, 5 tiles

- [ ] **Ysolde** — `3096, 3501, 0`, **wanders** `3094,3500 .. 3098,3503`, facing west
      Examine: "Back already, and not saying why."
      Kit: "Ilsa" (11317) · `SuzieIdle` / `HumanWalk`
      Livery: none — donor's own colours, darkened
      Placed near: Andre, 6 tiles

- [ ] **Brack** — `3103, 3495, 0`, stationary, facing north
      Examine: "Between the bank and the gate, as usual."
      Kit: "Bram" (12338) · `ArmsCrossedReady` / `HumanWalk`
      Livery: none — donor's own colours, darkened
      Placed near: Eugene, 3 tiles

- [ ] **Krell** — `3108, 3499, 0`, stationary, facing west
      Examine: "Warming his hands and putting off the walk."
      Kit: "Hollis" (12338) · `HumanIdle` / `HumanWalk`
      Livery: none — donor's own colours, darkened
      Placed near: Eugene, 2 tiles

- [ ] **Torvig** — `3110, 3495, 0`, stationary, facing south
      Examine: "He came south with less than he went north with."
      Kit: "Brother Edwy" (10290) · `HumanLeanReady` / `HumanWalk`
      Livery: none — donor's own colours, darkened
      Placed near: Eugene, 4 tiles

- [ ] **Sefa** — `3104, 3501, 0`, **wanders** `3102,3499 .. 3107,3503`, facing east
      Examine: "Doing a slow circuit and watching the road."
      Kit: "Wynn" (11317) · `HumanIdle` / `HumanWalk`
      Livery: none — donor's own colours, darkened
      Placed near: Eugene, 4 tiles
      Says: "Nothing on the road."

- [ ] **Ulf** — `3112, 3500, 0`, stationary, facing west
      Examine: "He has been talked out of it once already."
      Kit: "Eugene" (12342) · `Think` / `HumanWalk`
      Livery: none — donor's own colours, darkened
      Placed near: Eugene, 6 tiles

- [ ] **Rask** — `3100, 3494, 0`, stationary, facing north
      Examine: "Checking a strap that does not need checking."
      Kit: "Perrin" (12338) · `HumanIdle` / `HumanWalk`
      Livery: none — donor's own colours, darkened
      Placed near: Eugene, 6 tiles

- [ ] **Nimm** — `3127, 3515, 0`, stationary, facing west
      Examine: "She is here for the walk back, not the walk out."
      Kit: "Hesper" (10548) · `HumanIdle` / `HumanWalk`
      Livery: none — donor's own colours, darkened
      Placed near: Nicholson, 3 tiles

- [ ] **Bregg** — `3132, 3513, 0`, stationary, facing south
      Examine: "Standing where he can see both ways at once."
      Kit: "Butler Jarvis" (12853) · `ArmsCrossedReady` / `HumanWalk`
      Livery: none — donor's own colours, darkened; boots `33030`
      Placed near: Nicholson, 4 tiles

- [ ] **Halla** — `3124, 3512, 0`, **wanders** `3122,3510 .. 3127,3514`, facing north
      Examine: "Pacing the last dry ground before the ditch."
      Kit: "Nessa" (11828) · `HumanIdle` / `HumanWalk`
      Livery: none — donor's own colours, darkened
      Placed near: Nicholson, 6 tiles

- [ ] **Sten** — `3133, 3515, 0`, stationary, facing west
      Examine: "He has done this often enough not to hurry."
      Kit: "Mysterious Old Man" (12853) · `HumanLeanReady` / `HumanWalk`
      Livery: none — donor's own colours, darkened; boots `43910`
      Placed near: Nicholson, 3 tiles

---

## Grand Exchange — 15

*Getting there: the Grand Exchange lodestone or a ring of wealth.*

**The thinnest city in the dataset at the default settings**, and the reason is
not obvious from the roster: nine of its ten entities were citizens, but six of
those are cameos, which are off by default. A player with a stock install saw
three figures in the busiest place in the game.

### Region 12598 — the Grand Exchange

- [ ] **Havelock** — `3166, 3487, 0`, stationary, facing north
      Examine: "A clerk, off the desk and no happier for it."
      Kit: "Butler Jarvis" (12853) · `ArmsCrossedReady` / `HumanWalk`
      Livery: Grand Exchange cut 0 — torso `48695`, legs `48678`, boots `1945`
      Placed near: Richard, 3 tiles

- [ ] **Prudence** — `3172, 3491, 0`, stationary, facing west
      Examine: "She has been quoted a price and is thinking about it."
      Kit: "Joanne" (11317) · `Think` / `HumanWalk`
      Livery: Grand Exchange cut 1 — torso `48707`, legs `48690`
      Placed near: Richard, 3 tiles

- [ ] **Merribold** — `3167, 3492, 0`, stationary, facing south
      Examine: "Holding a place in a queue that has stopped existing."
      Kit: "Corliss" (12338) · `HumanIdle` / `HumanWalk`
      Livery: Grand Exchange cut 2 — torso `48685`, legs `48668`
      Placed near: Richard, 3 tiles

- [ ] **Quill** — `3173, 3486, 0`, stationary, facing west
      Examine: "Every figure agrees except one, and he has found it."
      Kit: "Brother Edwy" (10290) · `Think` / `HumanWalk`
      Livery: Grand Exchange cut 0 — torso `48695`, legs `48678`
      Placed near: Richard, 4 tiles

- [ ] **Tallow** — `3165, 3484, 0`, stationary, facing east
      Examine: "He buys nothing and knows every price."
      Kit: "Emme" (12853) · `HumanSmugIdle` / `HumanWalk`
      Livery: Grand Exchange cut 3 — torso `48695`, legs `1945`
      Placed near: Richard, 5 tiles

- [ ] **Winnifred** — `3161, 3490, 0`, stationary, facing north
      Examine: "Waiting on an offer that will not fill today."
      Kit: "Ilsa" (11317) · `SuzieIdle` / `HumanWalk`
      Livery: Grand Exchange cut 1 — torso `48707`, legs `48690`
      Placed near: Gunnar, 3 tiles

- [ ] **Bosley** — `3157, 3491, 0`, stationary, facing east
      Examine: "He came for one thing and is leaving with four."
      Kit: "Hollis" (12338) · `HumanIdle` / `HumanWalk`
      Livery: Grand Exchange cut 0 — torso `48695`, legs `48678`
      Placed near: Rob, 3 tiles

- [ ] **Crispin** — `3156, 3497, 0`, stationary, facing south
      Examine: "Counting the same coins for the third time."
      Kit: "Bram" (12338) · `Think` / `HumanWalk`
      Livery: Grand Exchange cut 2 — torso `48685`, legs `48668`
      Placed near: Rob, 3 tiles
      Says: "It was more than this yesterday."

- [ ] **Deverell** — `3160, 3499, 0`, **wanders** `3158,3497 .. 3163,3501`, facing east
      Examine: "Walking the arches because standing still is worse."
      Kit: "H.A.M. Member" (12853) · `HumanIdle` / `HumanWalk`
      Livery: Grand Exchange cut 0 — torso `48695`, legs `48678`
      Placed near: Cazh, 4 tiles

- [ ] **Marchant** — `3164, 3499, 0`, stationary, facing west
      Examine: "He is explaining the market to somebody who left."
      Kit: "Idris" (10804) · `HumanLeanReady` / `HumanWalk`
      Livery: Grand Exchange cut 1 — torso `48707`, legs `48690`
      Placed near: Peter, 4 tiles

- [ ] **Ottoline** — `3170, 3496, 0`, stationary, facing south
      Examine: "She has the look of somebody who bought at the top."
      Kit: "Nessa" (11828) · `SuzieIdle` / `HumanWalk`
      Livery: Grand Exchange cut 3 — torso `48695`, legs `1945`
      Placed near: Richard, 7 tiles

- [ ] **Fitch** — `3175, 3494, 0`, stationary, facing west
      Examine: "Runner for somebody who does not come here himself."
      Kit: "Wilhelm" (10804) · `NervousIdle` / `HumanWalk`
      Livery: Grand Exchange cut 0 — torso `48695`, legs `48678`
      Placed near: Richard, 6 tiles

- [ ] **Thackeray** — `3178, 3503, 0`, stationary, facing north
      Examine: "He has read the whole board and is starting over."
      Kit: "Demon Butler" (12853) · `Think` / `HumanWalk`
      Livery: Grand Exchange cut 2 — torso `48685`, legs `48668`
      Placed near: Child, 9 tiles
      **Reach note:** 9 tiles from the nearest proven tile, with nothing else under it. Walk this one early.

- [ ] **Rowe** — `3183, 3509, 0`, stationary, facing west
      Examine: "Minding the north arch, or standing near it."
      Kit: "Sergeant Damien" (12853) · `ArmsCrossedReady` / `HumanWalk`
      Livery: Grand Exchange cut 1 — torso `48707`, legs `48690`
      Placed near: Child, 3 tiles

- [ ] **Pettigrew** — `3175, 3463, 0`, stationary, facing east
      Examine: "Out of the crowd on purpose."
      Kit: "Mysterious Old Man" (12853) · `HumanLeanReady` / `HumanWalk`
      Livery: Grand Exchange cut 0 — torso `48695`, legs `48678`, boots `1945`
      Placed near: Squirrel, 4 tiles

---

## Al Kharid — 14

*Getting there: Al Kharid lodestone. 13105 is the goat pen and the road south,
13106 the road north of the toll gate, 13361 the open desert east.*

**Al Kharid's own market square is still empty and this pass did not change
that.** Every proven tile this plugin has in Al Kharid is at the goat pen, on
the toll road or out in the desert, so that is where the fourteen went. The
market would have meant placing figures twenty-five tiles from the nearest tile
anybody has stood on.

**Al Kharid's magenta is one of three purples in the set, and they are the closest
colours in it.** `NOTICE` item 12 has the measurements; the short version is that
Al Kharid's `55085` (h53, `#A310A0`), Draynor's trim `54049` (h52, `#6F0C77`) and the
Grand Exchange's `48695` (h47, `#6830AB`) are one, six and five hue rungs from each
other. **Only the one-rung gap is tighter than the Falador/Lumbridge blue pair** every
other paragraph in this file worries about; at six and five rungs the other two are
wider than it, and this paragraph claimed all three were tighter until 2026-08-31.

**And the pair that actually collides is on the legs, which this file never looked
at.** Al Kharid's cut-0 legs `55072` (`#740C72`, h53/s6/l32) and Draynor's trim
`54049` (`#6F0C77`, h52/s6/l33) are one rung apart on hue *and* one on lightness — an
RGB distance of **7.1**, against 66.3 for the torso pair named above. For any
practical purpose they are the same colour. Draynor's trim is a cut-3 leg colour, worn
by Hessa and Thistle; Al Kharid's `55072` is its cut-0 leg colour, worn by five. So it
is the same body part in two different cities rather than a torso against a hem. Set
aside the white `99` that Ardougne, Al Kharid and Lumbridge share as their trim on
purpose — one value published three times, which is what makes the twenty-four
published colours twenty-two distinct ones — and this is the tightest collision in the
pass. It is still nothing a walk can see, for the reason below.

None of the three was separated, because no two of them are ever on screen together —
three cities, three checkboxes, hundreds of tiles apart. **That is an argument about
geography, not about colour**, so if you ever see an Al Kharid figure and a Grand
Exchange figure in one frame, this is the pair to report.

### Region 13105 — the goat pen, south of the city

- [ ] **Farid** — `3308, 3145, 0`, stationary, facing south
      Examine: "He is paid by the goat and counts carefully."
      Kit: "Ali" (12853) · `HumanIdle` / `HumanWalk`
      Livery: Al Kharid cut 0 — torso `55085`, legs `55072`
      Placed near: Tarik (unwalked), 2 tiles

- [ ] **Layla** — `3313, 3145, 0`, stationary, facing west
      Examine: "Out of the sun for as long as it lasts."
      Kit: "Afrah" (12853) · `SuzieIdle` / `HumanWalk`
      Livery: Al Kharid cut 1 — torso `55097`, legs `55084`
      Placed near: Halima (unwalked), 3 tiles

- [ ] **Zaid** — `3304, 3141, 0`, stationary, facing east
      Examine: "Minding the gap in the fence, which is his whole job."
      Kit: "Hollis" (12338) · `ArmsCrossedReady` / `HumanWalk`
      Livery: Al Kharid cut 2 — torso `55075`, legs `55062`
      Placed near: Nadir (unwalked), 5 tiles

- [ ] **Amina** — `3311, 3147, 0`, stationary, facing south
      Examine: "She has the water and everybody knows it."
      Kit: "Ilsa" (11317) · `HumanIdle` / `HumanWalk`
      Livery: Al Kharid cut 0 — torso `55085`, legs `55072`
      Placed near: Tarik (unwalked), 4 tiles
      Says: "Not until the shade moves."

- [ ] **Karim** — `3318, 3140, 0`, stationary, facing west
      Examine: "Waiting for the herd to come back to him."
      Kit: "Bram" (12338) · `HumanLeanReady` / `HumanWalk`
      Livery: Al Kharid cut 3 — torso `55085`, legs `99`
      Placed near: Abex, 2 tiles

- [ ] **Nour** — `3305, 3138, 0`, stationary, facing north
      Examine: "She has walked in from the pass and is not walking back yet."
      Kit: "Wynn" (11317) · `Think` / `HumanWalk`
      Livery: Al Kharid cut 1 — torso `55097`, legs `55084`
      Placed near: Nadir (unwalked), 4 tiles

### Region 13106 — the road north of the toll gate

- [ ] **Hakim** — `3285, 3248, 0`, stationary, facing west
      Examine: "Toll paid, patience nearly spent."
      Kit: "Idris" (10804) · `NervousIdle` / `HumanWalk`
      Livery: Al Kharid cut 0 — torso `55085`, legs `55072`
      Placed near: Ali the wanderer, 7 tiles

- [ ] **Samira** — `3289, 3245, 0`, stationary, facing south
      Examine: "She has done this road often enough to know the shade."
      Kit: "Afrah" (12853) · `HumanIdle` / `HumanWalk`
      Livery: Al Kharid cut 2 — torso `55075`, legs `55062`
      Placed near: Ali the wanderer, 3 tiles

- [ ] **Rashid** — `3282, 3244, 0`, stationary, facing east
      Examine: "Second in the queue, and certain the first is stalling."
      Kit: "Ali" (12853) · `ArmsCrossedReady` / `HumanWalk`
      Livery: Al Kharid cut 1 — torso `55097`, legs `55084`
      Placed near: Fahd (unwalked), 2 tiles

- [ ] **Basma** — `3288, 3238, 0`, stationary, facing north
      Examine: "Carrying somebody else's news north."
      Kit: "Nessa" (11828) · `SuzieIdle` / `HumanWalk`
      Livery: Al Kharid cut 0 — torso `55085`, legs `55072`
      Placed near: Reza (unwalked), 3 tiles

- [ ] **Tahir** — `3291, 3243, 0`, stationary, facing west
      Examine: "He would rather pay the toll than walk around."
      Kit: "Corliss" (12338) · `HumanSmugIdle` / `HumanWalk`
      Livery: Al Kharid cut 3 — torso `55085`, legs `99`
      Placed near: Reza (unwalked), 4 tiles

### Region 13361 — the desert east of the city

- [ ] **Jamila** — `3340, 3148, 0`, stationary, facing south
      Examine: "Watching the desert for something that is not there."
      Kit: "Marta" (11829) · `Think` / `HumanWalk`
      Livery: Al Kharid cut 0 — torso `55085`, legs `55072`
      Placed near: Yusra (unwalked), 3 tiles

- [ ] **Salim** — `3336, 3153, 0`, stationary, facing east
      Examine: "He says the caravan is a day out. He said that yesterday."
      Kit: "Wilhelm" (10804) · `HumanIdle` / `HumanWalk`
      Livery: Al Kharid cut 2 — torso `55075`, legs `55062`
      Placed near: Yusra (unwalked), 3 tiles
      Says: "A day out. No more."

- [ ] **Rania** — `3343, 3153, 0`, stationary, facing west
      Examine: "Well out of the wind, and staying there."
      Kit: "Ava" (12595) · `HumanLeanReady` / `HumanWalk`
      Livery: Al Kharid cut 1 — torso `55097`, legs `55084`
      Placed near: Ali the spy, 2 tiles

---

## Ardougne — 14

*Getting there: Ardougne cloak or teleport.*

**None of the three regions this plugin ships for Ardougne is the market
square** — that is region 10547 and there is no file for it. So this is the
monastery, the farm and the guild path, more densely, and the market square is
exactly as empty as it was.

### Region 10290 — the monastery

- [ ] **Aurele** — `2600, 3215, 0`, stationary, facing west
      Examine: "Here about the bell, and has been all week."
      Kit: "Brother Keptic" (10290) · `ArmsCrossedReady` / `HumanWalk`
      Livery: Ardougne cut 0 — torso `1965`, legs `1950`
      Placed near: Brother Keptic, 3 tiles

- [ ] **Gisele** — `2592, 3213, 0`, stationary, facing east
      Examine: "She brought the bread and is waiting to be thanked."
      Kit: "Joanne" (11317) · `SuzieIdle` / `HumanWalk`
      Livery: Ardougne cut 1 — torso `1977`, legs `1962`
      Placed near: Anselm (unwalked), 3 tiles

- [ ] **Rennard** — `2597, 3210, 0`, stationary, facing north
      Examine: "Copying badly, and slowly, and in the wrong ink."
      Kit: "Brother Edwy" (10290) · `Think` / `HumanWalk`
      Livery: Ardougne cut 2 — torso `1955`, legs `1940`
      Placed near: Brother Edwy (unwalked), 3 tiles

- [ ] **Blaise** — `2601, 3212, 0`, stationary, facing west
      Examine: "He has been shown the door twice and found his way back."
      Kit: "Zethrus" (12853) · `MageReady` / `HumanWalk`
      Livery: Ardougne cut 0 — torso `1965`, legs `1950`
      Placed near: Brother Keptic, 4 tiles

- [ ] **Odile** — `2593, 3219, 0`, stationary, facing south
      Examine: "Reading the notice on the gate, which has not changed."
      Kit: "Nessa" (11828) · `Think` / `HumanWalk`
      Livery: Ardougne cut 3 — torso `1965`, legs `99`
      Placed near: Anselm (unwalked), 3 tiles

- [ ] **Thibault** — `2604, 3216, 0`, **wanders** `2602,3214 .. 2606,3218`, facing south
      Examine: "Walking the cloister because he was told to sit still."
      Kit: "Craftsman Jim" (11571) · `HumanIdle` / `HumanWalk`
      Livery: Ardougne cut 1 — torso `1977`, legs `1962`
      Placed near: Brother Keptic, 7 tiles

### Region 10548 — the farm

- [ ] **Cerise** — `2660, 3376, 0`, stationary, facing east
      Examine: "Pigs, mud, and a fixed opinion about both."
      Kit: "Hesper" (10548) · `HumanIdle` / `HumanWalk`
      Livery: Ardougne cut 0 — torso `1965`, legs `1950`
      Placed near: Hesper (unwalked), 3 tiles

- [ ] **Alard** — `2666, 3372, 0`, stationary, facing north
      Examine: "The gate is shut and he is fairly sure he shut it."
      Kit: "Hollis" (12338) · `NervousIdle` / `HumanWalk`
      Livery: Ardougne cut 2 — torso `1955`, legs `1940`
      Placed near: Plopper, 4 tiles

- [ ] **Perrine** — `2659, 3371, 0`, **wanders** `2657,3369 .. 2661,3374`, facing north
      Examine: "She has the barley counted and nobody has asked."
      Kit: "Marta" (11829) · `HumanIdle` / `HumanWalk`
      Livery: Ardougne cut 1 — torso `1977`, legs `1962`
      Placed near: Hesper (unwalked), 3 tiles
      Says: "Six sacks. Six."

### Region 10804 — the Legends' Guild path

- [ ] **Gaultier** — `2726, 3360, 0`, stationary, facing east
      Examine: "The other other guard. He has the afternoon."
      Kit: "Sergeant Damien" (12853) · `ArmsCrossedReady` / `HumanWalk`
      Livery: Ardougne cut 0 — torso `1965`, legs `1950`
      Placed near: Coren (unwalked), 2 tiles

- [ ] **Sancie** — `2731, 3356, 0`, stationary, facing west
      Examine: "Third in the queue and the calmest of the three."
      Kit: "Ilsa" (11317) · `SuzieIdle` / `HumanWalk`
      Livery: Ardougne cut 1 — torso `1977`, legs `1962`
      Placed near: Sela (unwalked), 4 tiles

- [ ] **Ferrand** — `2727, 3350, 0`, stationary, facing north
      Examine: "He has the letter. He is less sure about the seal."
      Kit: "Bram" (12338) · `NervousIdle` / `HumanWalk`
      Livery: Ardougne cut 2 — torso `1955`, legs `1940`
      Placed near: Sela (unwalked), 2 tiles

- [ ] **Amaury** — `2731, 3364, 0`, stationary, facing south
      Examine: "Sent up with a message and no instructions about waiting."
      Kit: "Wilhelm" (10804) · `HumanIdle` / `HumanWalk`
      Livery: Ardougne cut 3 — torso `1965`, legs `99`
      Placed near: Legends' Guard, 2 tiles

- [ ] **Isabeau** — `2726, 3355, 0`, stationary, facing east
      Examine: "She has been up this hill before and paced herself."
      Kit: "Ava" (12595) · `HumanLeanReady` / `HumanWalk`
      Livery: Ardougne cut 0 — torso `1965`, legs `1950`
      Placed near: Wilhelm (unwalked), 2 tiles

---

## Catherby — 14

*Getting there: Camelot teleport, then east.*

**Spread rather than clumped, on purpose.** The 2026-08-29 pass put four of its
six Catherby figures inside one dog's wander box. These fourteen sit in four
groups — the woods, the town centre, the eastern shore, and two figures out on
the beach between the town and the shore, which is the longest reach in
Catherby and is flagged below.

### Region 11061 — the woods west of the town

- [ ] **Selby** — `2761, 3433, 0`, stationary, facing west
      Examine: "Further in for the good wood, he says. He is not going further in."
      Kit: "Corliss" (12338) · `HumanIdle` / `HumanWalk`
      Livery: Catherby cut 0 — torso `34490`, legs `34472`
      Placed near: Forester, 3 tiles

- [ ] **Ingrid** — `2754, 3434, 0`, stationary, facing east
      Examine: "She has an axe and a strong view about lending it."
      Kit: "Marta" (11829) · `ArmsCrossedReady` / `HumanWalk`
      Livery: Catherby cut 1 — torso `34502`, legs `34484`
      Placed near: Aldous (unwalked), 3 tiles

- [ ] **Halvor** — `2760, 3440, 0`, stationary, facing south
      Examine: "Keeping the long way round between himself and the bees."
      Kit: "Idris" (10804) · `NervousIdle` / `HumanWalk`
      Livery: Catherby cut 2 — torso `34480`, legs `34462`
      Placed near: Peri (unwalked), 3 tiles

- [ ] **Netta** — `2754, 3438, 0`, **wanders** `2754,3436 .. 2757,3440`, facing north
      Examine: "Gathering, mostly. Some of it on purpose."
      Kit: "Joanne" (11317) · `HumanIdle` / `HumanWalk`
      Livery: Catherby cut 0 — torso `34490`, legs `34472`
      Placed near: Peri (unwalked), 5 tiles

### Region 11317 — the town and the eastern shore

- [ ] **Corwin** — `2831, 3444, 0`, stationary, facing west
      Examine: "He has been promised a berth and is holding out for a better one."
      Kit: "Bram" (12338) · `HumanLeanReady` / `HumanWalk`
      Livery: Catherby cut 1 — torso `34502`, legs `34484`
      Placed near: Merryn (unwalked), 4 tiles

- [ ] **Brenna** — `2824, 3445, 0`, stationary, facing east
      Examine: "Down for the tide and early by two hours."
      Kit: "Wynn" (11317) · `SuzieIdle` / `HumanWalk`
      Livery: Catherby cut 0 — torso `34490`, legs `34472`
      Placed near: Merryn (unwalked), 3 tiles

- [ ] **Tarrant** — `2830, 3439, 0`, stationary, facing north
      Examine: "Mending nothing, watching everything."
      Kit: "Hollis" (12338) · `Think` / `HumanWalk`
      Livery: Catherby cut 2 — torso `34480`, legs `34462`
      Placed near: Osric (unwalked), 4 tiles

- [ ] **Maren** — `2823, 3439, 0`, **wanders** `2821,3437 .. 2825,3442`, facing south
      Examine: "Doing the round of the town, twice a day, whatever the weather."
      Kit: "Nessa" (11828) · `HumanIdle` / `HumanWalk`
      Livery: Catherby cut 3 — torso `34490`, legs `12845`
      Placed near: Osric (unwalked), 3 tiles
      Says: "The wind will turn."

- [ ] **Rowena** — `2851, 3439, 0`, stationary, facing north
      Examine: "Out on the shore because the town is full of people."
      Kit: "Ilsa" (11317) · `SuzieIdle` / `HumanWalk`
      Livery: Catherby cut 0 — torso `34490`, legs `34472`
      Placed near: Ilsa (unwalked), 4 tiles

- [ ] **Alric** — `2856, 3433, 0`, stationary, facing west
      Examine: "He has seen the boat and is not impressed by it."
      Kit: "Wilhelm" (10804) · `HumanSmugIdle` / `HumanWalk`
      Livery: Catherby cut 1 — torso `34502`, legs `34484`
      Placed near: Joanne, 2 tiles

- [ ] **Sigrun** — `2847, 3431, 0`, stationary, facing east
      Examine: "Counting sails, and getting a different answer each time."
      Kit: "Ava" (12595) · `Think` / `HumanWalk`
      Livery: Catherby cut 2 — torso `34480`, legs `34462`
      Placed near: Ilsa (unwalked), 4 tiles

- [ ] **Edrick** — `2857, 3438, 0`, **wanders** `2855,3436 .. 2859,3440`, facing south
      Examine: "Walking the headland for the view and telling everybody so."
      Kit: "Perrin" (12338) · `HumanIdle` / `HumanWalk`
      Livery: Catherby cut 0 — torso `34490`, legs `34472`
      Placed near: Joanne, 3 tiles

- [ ] **Thomasin** — `2839, 3441, 0`, stationary, facing west
      Examine: "Between the beach and the bank, and in no hurry about either."
      Kit: "Emme" (12853) · `HumanLeanReady` / `HumanWalk`
      Livery: Catherby cut 1 — torso `34502`, legs `34484`
      Placed near: Charlie, 10 tiles
      **Reach note:** 10 tiles from the nearest proven tile, with nothing else under it. Walk this one early.

- [ ] **Gorden** — `2843, 3436, 0`, stationary, facing north
      Examine: "He has the nets in and the afternoon to himself."
      Kit: "Butler Jarvis" (12853) · `ArmsCrossedReady` / `HumanWalk`
      Livery: Catherby cut 3 — torso `34490`, legs `12845`, boots `34472`
      Placed near: Ilsa (unwalked), 8 tiles

---

## Draynor — 14

*Getting there: Draynor Village lodestone or an Amulet of glory.*

**Nothing here is placed above `y 3261`, for the same reason as last time:**
`y 3264` leaves region 12338 for 12339, which ships no file and no city claims.
That constraint has not moved and neither has the margin.

**Draynor's green is the loudest colour in the pass, and that is a question for the
walk rather than a defect.** `23351` renders `#14C728`; its lightest cut `23363`
renders `#22E938`. Measured as chroma — the gap between the strongest and weakest
RGB channel — the base is 179 of 255 and the light cut is 199, both above every
other city's torso (Ardougne's red is 169, Varrock's gold 163, Catherby's teal 159).
It is a near-neon green on a farming village. It is kept because it is the value
`NOTICE` item 12 names as its source and because nothing offline can settle a
question of tone: guessing a second time before anybody has seen the first guess in
daylight would be the same move that produced it. **Look at Draynor in daylight and
say whether the village reads as farmers or as a lit sign**; if it is wrong, one
lightness rung down across all four cuts is the change, and it is a fourteen-record
edit.

### Region 12338 — the village

- [ ] **Nettie** — `3097, 3256, 0`, stationary, facing east
      Examine: "She has willows of her own and opinions about everyone else's."
      Kit: "Ilsa" (11317) · `ArmsCrossedReady` / `HumanWalk`
      Livery: Draynor cut 0 — torso `23351`, legs `23334`
      Placed near: Hollis (unwalked), 2 tiles

- [ ] **Corbin** — `3095, 3253, 0`, stationary, facing north
      Examine: "He is the third person today to ask where the bank is."
      Kit: "Bram" (12338) · `NervousIdle` / `HumanWalk`
      Livery: Draynor cut 1 — torso `23363`, legs `23346`
      Placed near: Hollis (unwalked), 5 tiles

- [ ] **Wilda** — `3091, 3255, 0`, stationary, facing south
      Examine: "Out where she can see who is coming up the road."
      Kit: "Marta" (11829) · `HumanIdle` / `HumanWalk`
      Livery: Draynor cut 2 — torso `23341`, legs `23324`
      Placed near: Maud (unwalked), 3 tiles

- [ ] **Bertram** — `3089, 3259, 0`, stationary, facing east
      Examine: "He came for the market and is a day early."
      Kit: "Corliss" (12338) · `Think` / `HumanWalk`
      Livery: Draynor cut 0 — torso `23351`, legs `23334`
      Placed near: Maud (unwalked), 4 tiles

- [ ] **Hessa** — `3101, 3253, 0`, stationary, facing west
      Examine: "She has been told about the manor and went anyway."
      Kit: "Wynn" (11317) · `SuzieIdle` / `HumanWalk`
      Livery: Draynor cut 3 — torso `23351`, legs `54049`
      Placed near: Perrin (unwalked), 3 tiles

- [ ] **Orrick** — `3106, 3254, 0`, stationary, facing north
      Examine: "Waiting on a cart that is coming from Lumbridge, apparently."
      Kit: "Hollis" (12338) · `HumanLeanReady` / `HumanWalk`
      Livery: Draynor cut 1 — torso `23363`, legs `23346`
      Placed near: Perrin (unwalked), 3 tiles

- [ ] **Lark** — `3108, 3258, 0`, stationary, facing west
      Examine: "He has the road east to himself and prefers it that way."
      Kit: "Eugene" (12342) · `HumanIdle` / `HumanWalk`
      Livery: Draynor cut 2 — torso `23341`, legs `23324`
      Placed near: Odell (unwalked), 5 tiles

- [ ] **Mabb** — `3094, 3260, 0`, stationary, facing south
      Examine: "Minding a barrow with nothing in it."
      Kit: "Nessa" (11828) · `HumanIdle` / `HumanWalk`
      Livery: Draynor cut 0 — torso `23351`, legs `23334`
      Placed near: Bram (unwalked), 2 tiles

- [ ] **Rushen** — `3099, 3251, 0`, stationary, facing north
      Examine: "Down from the north end, and glad of it."
      Kit: "Wilhelm" (10804) · `Think` / `HumanWalk`
      Livery: Draynor cut 1 — torso `23363`, legs `23346`
      Placed near: Sailor, 9 tiles
      Says: "It is quieter down here."
      **Reach note:** 9 tiles from the nearest proven tile, with nothing else under it. Walk this one early.

- [ ] **Gorse** — `3086, 3256, 0`, stationary, facing east
      Examine: "She has walked the whole village and found nobody in."
      Kit: "Ava" (12595) · `SuzieIdle` / `HumanWalk`
      Livery: Draynor cut 2 — torso `23341`, legs `23324`
      Placed near: Maud (unwalked), 7 tiles

- [ ] **Thistle** — `3103, 3249, 0`, **wanders** `3101,3247 .. 3105,3251`, facing south
      Examine: "Doing the rounds, slowly, and stopping for everyone."
      Kit: "Hesper" (10548) · `HumanIdle` / `HumanWalk`
      Livery: Draynor cut 3 — torso `23351`, legs `54049`
      Placed near: Perrin (unwalked), 7 tiles

### Region 12340 — Draynor Manor grounds

- [ ] **Pell** — `3117, 3379, 0`, stationary, facing east
      Examine: "He keeps the hedge and will tell you about it."
      Kit: "Idris" (10804) · `HumanIdle` / `HumanWalk`
      Livery: Draynor cut 0 — torso `23351`, legs `23334`
      Placed near: Cuthbert (unwalked), 3 tiles

- [ ] **Sowerby** — `3122, 3375, 0`, stationary, facing west
      Examine: "Here to value something, and putting it off."
      Kit: "Mysterious Old Man" (12853) · `Think` / `HumanWalk`
      Livery: Draynor cut 1 — torso `23363`, legs `23346`, boots `54049`
      Placed near: Ghost, 2 tiles

- [ ] **Bryn** — `3114, 3374, 0`, stationary, facing north
      Examine: "She will go up to the door and no further."
      Kit: "Joanne" (11317) · `NervousIdle` / `HumanWalk`
      Livery: Draynor cut 2 — torso `23341`, legs `23324`
      Placed near: Marlow (unwalked), 3 tiles

---

## Falador — 16

*Getting there: Falador lodestone.*

**Read the church section before walking Falador.** Eleven of the sixteen are in
region 11828 and four of those eleven are inside the Saradomin church, which is
roughly twenty-six tiles east of the nearest tile anybody has stood on. They are
here because the owner asked for a Saradominist group in Falador's church; they
are the least-evidenced placements in the whole pass and they are the ones to
walk first.

### Region 11828 — the park, and the church

- [ ] **Aymer** — `2964, 3388, 0`, stationary, facing south
      Examine: "In white, off duty, and standing as though he were not."
      Kit: "Sergeant Damien" (12853) · `ArmsCrossedReady` / `HumanWalk`
      Livery: Falador cut 0 — torso `107`, legs `90`
      Placed near: Alwin (unwalked), 2 tiles

- [ ] **Godric** — `2968, 3385, 0`, stationary, facing west
      Examine: "He has walked the park twice and will walk it again."
      Kit: "Idris" (10804) · `HumanIdle` / `HumanWalk`
      Livery: Falador cut 1 — torso `119`, legs `102`
      Placed near: Alwin (unwalked), 3 tiles

- [ ] **Isolde** — `2961, 3387, 0`, stationary, facing east
      Examine: "She is waiting for the bell and pretending not to be."
      Kit: "Nessa" (11828) · `SuzieIdle` / `HumanWalk`
      Livery: Falador cut 2 — torso `97`, legs `80`
      Placed near: Alwin (unwalked), 4 tiles

- [ ] **Ranulf** — `2966, 3389, 0`, stationary, facing north
      Examine: "The park is his beat and he takes it seriously."
      Kit: "Wilhelm" (10804) · `HumanLeanReady` / `HumanWalk`
      Livery: Falador cut 0 — torso `107`, legs `90`
      Placed near: Alwin (unwalked), 3 tiles

- [ ] **Editha** — `2959, 3384, 0`, stationary, facing south
      Examine: "She has the afternoon and no plans for it."
      Kit: "Ilsa" (11317) · `Think` / `HumanWalk`
      Livery: Falador cut 3 — torso `107`, legs `43818`
      Placed near: Berta (unwalked), 3 tiles

- [ ] **Osbert** — `2972, 3387, 0`, stationary, facing west
      Examine: "Explaining the rules of the park to nobody in particular."
      Kit: "Corliss" (12338) · `HumanSmugIdle` / `HumanWalk`
      Livery: Falador cut 1 — torso `119`, legs `102`
      Placed near: Nessa (unwalked), 4 tiles

- [ ] **Amice** — `2965, 3381, 0`, **wanders** `2963,3379 .. 2967,3383`, facing east
      Examine: "Pacing out the flowerbed and losing count, like everyone here."
      Kit: "Marta" (11829) · `HumanIdle` / `HumanWalk`
      Livery: Falador cut 2 — torso `97`, legs `80`
      Placed near: Alwin (unwalked), 5 tiles

- [ ] **Thurstan** — `2995, 3383, 0`, stationary, facing north
      Examine: "He has knelt, stood, and is now simply standing."
      Kit: "Brother Keptic" (10290) · `MageReady` / `HumanWalk`
      Livery: Falador cut 0 — torso `107`, legs `90`
      Placed near: Nessa (unwalked), 27 tiles
      **Reach note:** 27 tiles from the nearest proven tile, with nothing else under it. Walk this one early.

- [ ] **Sybilla** — `2996, 3385, 0`, stationary, facing west
      Examine: "She came in out of the rain and stayed for the rest of it."
      Kit: "Joanne" (11317) · `Think` / `HumanWalk`
      Livery: Falador cut 1 — torso `119`, legs `102`
      Placed near: Nessa (unwalked), 28 tiles
      **Reach note:** 28 tiles from the nearest proven tile, with nothing else under it. Walk this one early.

- [ ] **Gervase** — `2994, 3381, 0`, stationary, facing east
      Examine: "Here for the bell, the sermon, or the quiet. He has not said which."
      Kit: "Zethrus" (12853) · `MageReady` / `HumanWalk`
      Livery: Falador cut 2 — torso `97`, legs `80`
      Placed near: Nessa (unwalked), 26 tiles
      **Reach note:** 26 tiles from the nearest proven tile, with nothing else under it. Walk this one early.

- [ ] **Adela** — `2997, 3382, 0`, stationary, facing south
      Examine: "She has read the plaque and is working out who is under it."
      Kit: "Ava" (12595) · `SuzieIdle` / `HumanWalk`
      Livery: Falador cut 0 — torso `107`, legs `90`
      Placed near: Nessa (unwalked), 29 tiles
      **Reach note:** 29 tiles from the nearest proven tile, with nothing else under it. Walk this one early.

### Region 11829 — north of the wall

- [ ] **Hamon** — `2957, 3434, 0`, stationary, facing east
      Examine: "Outside the wall because inside it is full of knights."
      Kit: "Bram" (12338) · `ArmsCrossedReady` / `HumanWalk`
      Livery: Falador cut 1 — torso `119`, legs `102`
      Placed near: Marta (unwalked), 3 tiles

- [ ] **Eudo** — `2964, 3438, 0`, stationary, facing south
      Examine: "He has followed the squirrel this far and is committed now."
      Kit: "Hollis" (12338) · `NervousIdle` / `HumanWalk`
      Livery: Falador cut 2 — torso `97`, legs `80`
      Placed near: Squirrel, 2 tiles

### Region 12083 — the cabbage field

- [ ] **Rowan** — `3056, 3294, 0`, stationary, facing west
      Examine: "Two hundred and eleven, and she has not finished the row."
      Kit: "Wynn" (11317) · `HumanIdle` / `HumanWalk`
      Livery: Falador cut 0 — torso `107`, legs `90`
      Placed near: Tobias (unwalked), 3 tiles
      Says: "Two hundred and eleven."

- [ ] **Berengar** — `3061, 3289, 0`, stationary, facing north
      Examine: "He disputes the count and has not offered a better one."
      Kit: "H.A.M. Member" (12853) · `HumanSmugIdle` / `HumanWalk`
      Livery: Falador cut 3 — torso `107`, legs `43818`
      Placed near: Tobias (unwalked), 3 tiles

### Region 11571 — the Crafting Guild

- [ ] **Constance** — `2933, 3285, 0`, stationary, facing east
      Examine: "Waiting on the guild to open, which it already has."
      Kit: "Butler Jarvis" (12853) · `Think` / `HumanWalk`
      Livery: Falador cut 1 — torso `119`, legs `102`, boots `43818`
      Placed near: Craftsman Jim, 3 tiles

---

## Lumbridge — 14

*Getting there: Lumbridge lodestone or a home teleport.*

*Lumbridge already had the most citizens of any city but Varrock, and the widest
spread of proven tiles, so this is the easiest walk in the pass — every figure
is within seven tiles of a shipped one.*

### Region 12850 — the town and the castle

- [ ] **Tam** — `3222, 3230, 0`, stationary, facing east
      Examine: "New here, and it shows."
      Kit: "Hollis" (12338) · `NervousIdle` / `HumanWalk`
      Livery: Lumbridge cut 0 — torso `40500`, legs `40484`
      Placed near: Mike, 3 tiles

- [ ] **Nell** — `3229, 3230, 0`, stationary, facing west
      Examine: "She has given the same directions four times this morning."
      Kit: "Ilsa" (11317) · `HumanIdle` / `HumanWalk`
      Livery: Lumbridge cut 1 — torso `40512`, legs `40496`
      Placed near: Mike, 4 tiles
      Says: "It is north. Keep going."

- [ ] **Roderick** — `3232, 3234, 0`, stationary, facing north
      Examine: "Between the shop and the castle, and stuck there."
      Kit: "Bram" (12338) · `Think` / `HumanWalk`
      Livery: Lumbridge cut 2 — torso `40490`, legs `40474`
      Placed near: Limping Locke, 3 tiles

- [ ] **Bramwell** — `3225, 3239, 0`, stationary, facing east
      Examine: "He has been warned about the wizard and is looking anyway."
      Kit: "Corliss" (12338) · `HumanLeanReady` / `HumanWalk`
      Livery: Lumbridge cut 0 — torso `40500`, legs `40484`
      Placed near: Dark wizard, 3 tiles

- [ ] **Lisbeth** — `3231, 3244, 0`, stationary, facing west
      Examine: "She is not with the dwarf, and would like that understood."
      Kit: "Nessa" (11828) · `ArmsCrossedReady` / `HumanWalk`
      Livery: Lumbridge cut 3 — torso `40500`, legs `99`
      Placed near: Drunken dwarf, 2 tiles

- [ ] **Ordric** — `3234, 3255, 0`, stationary, facing south
      Examine: "Waiting on the ferry, or on somebody who knows about the ferry."
      Kit: "Idris" (10804) · `HumanIdle` / `HumanWalk`
      Livery: Lumbridge cut 1 — torso `40512`, legs `40496`
      Placed near: Zack, 7 tiles

- [ ] **Fenna** — `3218, 3253, 0`, stationary, facing north
      Examine: "She has crossed the bridge twice and is thinking about a third."
      Kit: "Marta" (11829) · `SuzieIdle` / `HumanWalk`
      Livery: Lumbridge cut 2 — torso `40490`, legs `40474`
      Placed near: Scruffy, 4 tiles

- [ ] **Wat** — `3209, 3250, 0`, **wanders** `3207,3248 .. 3212,3252`, facing east
      Examine: "Walking the north bank because the south bank is busy."
      Kit: "Craftsman Jim" (11571) · `HumanIdle` / `HumanWalk`
      Livery: Lumbridge cut 0 — torso `40500`, legs `40484`
      Placed near: Scruffy, 5 tiles

- [ ] **Gilda** — `3241, 3228, 0`, stationary, facing west
      Examine: "She has the good spot on the bank and is not moving."
      Kit: "Wynn" (11317) · `Think` / `HumanWalk`
      Livery: Lumbridge cut 1 — torso `40512`, legs `40496`
      Placed near: Fisherman, 4 tiles

### Region 12594 — west of the road

- [ ] **Symon** — `3185, 3222, 0`, stationary, facing south
      Examine: "He has been to see the prisoner and wishes he had not."
      Kit: "Demon Butler" (12853) · `NervousIdle` / `HumanWalk`
      Livery: Lumbridge cut 2 — torso `40490`, legs `40474`
      Placed near: Sue, 3 tiles

- [ ] **Alys** — `3179, 3217, 0`, stationary, facing east
      Examine: "She brings the bread down on a Tuesday."
      Kit: "Joanne" (11317) · `HumanIdle` / `HumanWalk`
      Livery: Lumbridge cut 0 — torso `40500`, legs `40484`
      Placed near: Prisoner, 3 tiles

- [ ] **Peveril** — `3187, 3252, 0`, stationary, facing west
      Examine: "Well clear of the goblins, and checking."
      Kit: "Wilhelm" (10804) · `ArmsCrossedReady` / `HumanWalk`
      Livery: Lumbridge cut 3 — torso `40500`, legs `99`
      Placed near: Grimefang, 2 tiles

### Region 12595 — the swamp road

- [ ] **Hulda** — `3171, 3267, 0`, stationary, facing north
      Examine: "She has come down from the swamp and would rather not discuss it."
      Kit: "Ava" (12595) · `Think` / `HumanWalk`
      Livery: Lumbridge cut 1 — torso `40512`, legs `40496`
      Placed near: Rick, 3 tiles

- [ ] **Doggett** — `3174, 3266, 0`, stationary, facing south
      Examine: "He is going as far as the fence and no further."
      Kit: "Butler Jarvis" (12853) · `HumanLeanReady` / `HumanWalk`
      Livery: Lumbridge cut 0 — torso `40500`, legs `40484`, boots `99`
      Placed near: Rick, 6 tiles

---

## Varrock — 8

*Getting there: Varrock lodestone.*

**Only eight, and the reason is arithmetic rather than taste.** The densest
thirty-tile window in the dataset sits on Varrock square and already holds 76
authored entities against an object cap of 80. Adding to regions 12852, 12853 or
12597 would spend a budget the city has already spent, so all eight are north of
the city or outside its east gate, where the window is empty.

### Region 12854 — north Varrock and the church

- [ ] **Aldric** — `3253, 3477, 0`, stationary, facing east
      Examine: "Early for the service, and early on purpose."
      Kit: "Brother Keptic" (10290) · `MageReady` / `HumanWalk`
      Livery: Varrock cut 0 — torso `10034`, legs `39`
      Placed near: Saradomin priest, 5 tiles

- [ ] **Bettany** — `3257, 3477, 0`, stationary, facing west
      Examine: "She has swept this floor and would like it noticed."
      Kit: "Nessa" (11828) · `Think` / `HumanWalk`
      Livery: Varrock cut 1 — torso `10046`, legs `51`
      Placed near: Saradomin wizard, 2 tiles

- [ ] **Cordell** — `3254, 3486, 0`, stationary, facing south
      Examine: "He has been given a candle and no further instructions."
      Kit: "Zethrus" (12853) · `HumanIdle` / `HumanWalk`
      Livery: Varrock cut 2 — torso `10024`, legs `29`
      Placed near: Saradomin priest, 4 tiles

- [ ] **Rosalind** — `3252, 3480, 0`, stationary, facing north
      Examine: "Sitting would be easier. She is not going to."
      Kit: "Joanne" (11317) · `SuzieIdle` / `HumanWalk`
      Livery: Varrock cut 0 — torso `10034`, legs `39`
      Placed near: Saradomin priest, 3 tiles

- [ ] **Halvard** — `3229, 3462, 0`, stationary, facing west
      Examine: "In from the north field, and still in his boots."
      Kit: "Hollis" (12338) · `HumanIdle` / `HumanWalk`
      Livery: Varrock cut 1 — torso `10046`, legs `51`
      Placed near: Lily, 3 tiles

- [ ] **Wenna** — `3224, 3458, 0`, stationary, facing east
      Examine: "She has the north gate in view and likes it that way."
      Kit: "Marta" (11829) · `ArmsCrossedReady` / `HumanWalk`
      Livery: Varrock cut 2 — torso `10024`, legs `29`
      Placed near: Lily, 3 tiles

- [ ] **Garrick** — `3216, 3501, 0`, stationary, facing south
      Examine: "Here on business, like everybody on this street."
      Kit: "Corliss" (12338) · `HumanLeanReady` / `HumanWalk`
      Livery: Varrock cut 0 — torso `10034`, legs `39`
      Placed near: Kors Aertsen, 3 tiles

### Region 13109 — east of the east gate

- [ ] **Tobin** — `3270, 3428, 0`, stationary, facing west
      Examine: "Working hard, or standing near somebody who is."
      Kit: "Bram" (12338) · `HumanSmugIdle` / `HumanWalk`
      Livery: Varrock cut 1 — torso `10046`, legs `51`
      Placed near: City workman, 2 tiles

---

## What this pass deliberately did not do

- **No new model ids.** Every one of the 127 wears a `modelIds` array copied whole
  from a shipped citizen, so the dataset still contains exactly **324** distinct
  model ids. That is the figure that decides how much of this plugin an OSRS cache
  renumbering can break, and it did not move.
  (`ModelIdAuditTest.theDistinctModelIdFigureIsPinned` is where it is held.)
- **No new animations.** The dataset still uses exactly **72** distinct animation
  names. Every pose here is one the dataset already carried, on the human framemap,
  and every figure walks on `HumanWalk`.
- **No new scenery.** Scenery is still 42 records, untouched.
- **No new region files, and no changes to the `City` enum.** Ardougne's market
  square, Al Kharid's market and Falador's streets are still unpopulated because
  this plugin ships no proven ground in them.
- **Nothing sits down, though fifteen figures lean.** Every pose here is a standing
  one from the set `CitizenEcho.NOT_AN_ANONYMOUS_POSE` does *not* refuse — sitting
  never appears — but `HumanLeanReady` is on fifteen of the 127, and a lean has the
  same "nothing under it" risk sitting does. `docs/SEATING-CHECK.md` is now a list
  of 46 figures rather than 31, with all fifteen added to its Leaning section, and
  no figure here is holding an invisible axe.
- **Nothing was added to Varrock's centre.** Regions 12852, 12853 and 12597 gained
  nothing: the densest thirty-tile window in the dataset is on Varrock square and
  already holds 76 authored entities against a cap of 80. That window still holds
  76 after this pass, which is why every density sentence in the source still reads
  the way it did.
- **No existing record was touched.** The diff against the region files is
  insertions only.

## Ground markers

Copy this whole line, then Ground Markers → Import. **151 tiles**: the 127 figures
in yellow, and the south-west and north-east corners of the twelve new wander
boxes in cyan.

```json
[{"regionId":12342,"regionX":20,"regionY":38,"z":0,"color":"#FFFFFF00","label":"Gorm"},{"regionId":12342,"regionX":22,"regionY":41,"z":0,"color":"#FFFFFF00","label":"Sable"},{"regionId":12342,"regionX":21,"regionY":44,"z":0,"color":"#FFFFFF00","label":"Hesk"},{"regionId":12342,"regionX":16,"regionY":37,"z":0,"color":"#FFFFFF00","label":"Rand"},{"regionId":12342,"regionX":15,"regionY":44,"z":0,"color":"#FFFFFF00","label":"Vorn"},{"regionId":12342,"regionX":23,"regionY":36,"z":0,"color":"#FFFFFF00","label":"Dagg"},{"regionId":12342,"regionX":19,"regionY":47,"z":0,"color":"#FFFFFF00","label":"Mirek"},{"regionId":12342,"regionX":24,"regionY":45,"z":0,"color":"#FFFFFF00","label":"Ysolde"},{"regionId":12342,"regionX":22,"regionY":44,"z":0,"color":"#FF00FFFF","label":"Ysolde box SW"},{"regionId":12342,"regionX":26,"regionY":47,"z":0,"color":"#FF00FFFF","label":"Ysolde box NE"},{"regionId":12342,"regionX":31,"regionY":39,"z":0,"color":"#FFFFFF00","label":"Brack"},{"regionId":12342,"regionX":36,"regionY":43,"z":0,"color":"#FFFFFF00","label":"Krell"},{"regionId":12342,"regionX":38,"regionY":39,"z":0,"color":"#FFFFFF00","label":"Torvig"},{"regionId":12342,"regionX":32,"regionY":45,"z":0,"color":"#FFFFFF00","label":"Sefa"},{"regionId":12342,"regionX":30,"regionY":43,"z":0,"color":"#FF00FFFF","label":"Sefa box SW"},{"regionId":12342,"regionX":35,"regionY":47,"z":0,"color":"#FF00FFFF","label":"Sefa box NE"},{"regionId":12342,"regionX":40,"regionY":44,"z":0,"color":"#FFFFFF00","label":"Ulf"},{"regionId":12342,"regionX":28,"regionY":38,"z":0,"color":"#FFFFFF00","label":"Rask"},{"regionId":12342,"regionX":55,"regionY":59,"z":0,"color":"#FFFFFF00","label":"Nimm"},{"regionId":12342,"regionX":60,"regionY":57,"z":0,"color":"#FFFFFF00","label":"Bregg"},{"regionId":12342,"regionX":52,"regionY":56,"z":0,"color":"#FFFFFF00","label":"Halla"},{"regionId":12342,"regionX":50,"regionY":54,"z":0,"color":"#FF00FFFF","label":"Halla box SW"},{"regionId":12342,"regionX":55,"regionY":58,"z":0,"color":"#FF00FFFF","label":"Halla box NE"},{"regionId":12342,"regionX":61,"regionY":59,"z":0,"color":"#FFFFFF00","label":"Sten"},{"regionId":12598,"regionX":30,"regionY":31,"z":0,"color":"#FFFFFF00","label":"Havelock"},{"regionId":12598,"regionX":36,"regionY":35,"z":0,"color":"#FFFFFF00","label":"Prudence"},{"regionId":12598,"regionX":31,"regionY":36,"z":0,"color":"#FFFFFF00","label":"Merribold"},{"regionId":12598,"regionX":37,"regionY":30,"z":0,"color":"#FFFFFF00","label":"Quill"},{"regionId":12598,"regionX":29,"regionY":28,"z":0,"color":"#FFFFFF00","label":"Tallow"},{"regionId":12598,"regionX":25,"regionY":34,"z":0,"color":"#FFFFFF00","label":"Winnifred"},{"regionId":12598,"regionX":21,"regionY":35,"z":0,"color":"#FFFFFF00","label":"Bosley"},{"regionId":12598,"regionX":20,"regionY":41,"z":0,"color":"#FFFFFF00","label":"Crispin"},{"regionId":12598,"regionX":24,"regionY":43,"z":0,"color":"#FFFFFF00","label":"Deverell"},{"regionId":12598,"regionX":22,"regionY":41,"z":0,"color":"#FF00FFFF","label":"Deverell box SW"},{"regionId":12598,"regionX":27,"regionY":45,"z":0,"color":"#FF00FFFF","label":"Deverell box NE"},{"regionId":12598,"regionX":28,"regionY":43,"z":0,"color":"#FFFFFF00","label":"Marchant"},{"regionId":12598,"regionX":34,"regionY":40,"z":0,"color":"#FFFFFF00","label":"Ottoline"},{"regionId":12598,"regionX":39,"regionY":38,"z":0,"color":"#FFFFFF00","label":"Fitch"},{"regionId":12598,"regionX":42,"regionY":47,"z":0,"color":"#FFFFFF00","label":"Thackeray"},{"regionId":12598,"regionX":47,"regionY":53,"z":0,"color":"#FFFFFF00","label":"Rowe"},{"regionId":12598,"regionX":39,"regionY":7,"z":0,"color":"#FFFFFF00","label":"Pettigrew"},{"regionId":13105,"regionX":44,"regionY":9,"z":0,"color":"#FFFFFF00","label":"Farid"},{"regionId":13105,"regionX":49,"regionY":9,"z":0,"color":"#FFFFFF00","label":"Layla"},{"regionId":13105,"regionX":40,"regionY":5,"z":0,"color":"#FFFFFF00","label":"Zaid"},{"regionId":13105,"regionX":47,"regionY":11,"z":0,"color":"#FFFFFF00","label":"Amina"},{"regionId":13105,"regionX":54,"regionY":4,"z":0,"color":"#FFFFFF00","label":"Karim"},{"regionId":13105,"regionX":41,"regionY":2,"z":0,"color":"#FFFFFF00","label":"Nour"},{"regionId":13106,"regionX":21,"regionY":48,"z":0,"color":"#FFFFFF00","label":"Hakim"},{"regionId":13106,"regionX":25,"regionY":45,"z":0,"color":"#FFFFFF00","label":"Samira"},{"regionId":13106,"regionX":18,"regionY":44,"z":0,"color":"#FFFFFF00","label":"Rashid"},{"regionId":13106,"regionX":24,"regionY":38,"z":0,"color":"#FFFFFF00","label":"Basma"},{"regionId":13106,"regionX":27,"regionY":43,"z":0,"color":"#FFFFFF00","label":"Tahir"},{"regionId":13361,"regionX":12,"regionY":12,"z":0,"color":"#FFFFFF00","label":"Jamila"},{"regionId":13361,"regionX":8,"regionY":17,"z":0,"color":"#FFFFFF00","label":"Salim"},{"regionId":13361,"regionX":15,"regionY":17,"z":0,"color":"#FFFFFF00","label":"Rania"},{"regionId":10290,"regionX":40,"regionY":15,"z":0,"color":"#FFFFFF00","label":"Aurele"},{"regionId":10290,"regionX":32,"regionY":13,"z":0,"color":"#FFFFFF00","label":"Gisele"},{"regionId":10290,"regionX":37,"regionY":10,"z":0,"color":"#FFFFFF00","label":"Rennard"},{"regionId":10290,"regionX":41,"regionY":12,"z":0,"color":"#FFFFFF00","label":"Blaise"},{"regionId":10290,"regionX":33,"regionY":19,"z":0,"color":"#FFFFFF00","label":"Odile"},{"regionId":10290,"regionX":44,"regionY":16,"z":0,"color":"#FFFFFF00","label":"Thibault"},{"regionId":10290,"regionX":42,"regionY":14,"z":0,"color":"#FF00FFFF","label":"Thibault box SW"},{"regionId":10290,"regionX":46,"regionY":18,"z":0,"color":"#FF00FFFF","label":"Thibault box NE"},{"regionId":10548,"regionX":36,"regionY":48,"z":0,"color":"#FFFFFF00","label":"Cerise"},{"regionId":10548,"regionX":42,"regionY":44,"z":0,"color":"#FFFFFF00","label":"Alard"},{"regionId":10548,"regionX":35,"regionY":43,"z":0,"color":"#FFFFFF00","label":"Perrine"},{"regionId":10548,"regionX":33,"regionY":41,"z":0,"color":"#FF00FFFF","label":"Perrine box SW"},{"regionId":10548,"regionX":37,"regionY":46,"z":0,"color":"#FF00FFFF","label":"Perrine box NE"},{"regionId":10804,"regionX":38,"regionY":32,"z":0,"color":"#FFFFFF00","label":"Gaultier"},{"regionId":10804,"regionX":43,"regionY":28,"z":0,"color":"#FFFFFF00","label":"Sancie"},{"regionId":10804,"regionX":39,"regionY":22,"z":0,"color":"#FFFFFF00","label":"Ferrand"},{"regionId":10804,"regionX":43,"regionY":36,"z":0,"color":"#FFFFFF00","label":"Amaury"},{"regionId":10804,"regionX":38,"regionY":27,"z":0,"color":"#FFFFFF00","label":"Isabeau"},{"regionId":11061,"regionX":9,"regionY":41,"z":0,"color":"#FFFFFF00","label":"Selby"},{"regionId":11061,"regionX":2,"regionY":42,"z":0,"color":"#FFFFFF00","label":"Ingrid"},{"regionId":11061,"regionX":8,"regionY":48,"z":0,"color":"#FFFFFF00","label":"Halvor"},{"regionId":11061,"regionX":2,"regionY":46,"z":0,"color":"#FFFFFF00","label":"Netta"},{"regionId":11061,"regionX":2,"regionY":44,"z":0,"color":"#FF00FFFF","label":"Netta box SW"},{"regionId":11061,"regionX":5,"regionY":48,"z":0,"color":"#FF00FFFF","label":"Netta box NE"},{"regionId":11317,"regionX":15,"regionY":52,"z":0,"color":"#FFFFFF00","label":"Corwin"},{"regionId":11317,"regionX":8,"regionY":53,"z":0,"color":"#FFFFFF00","label":"Brenna"},{"regionId":11317,"regionX":14,"regionY":47,"z":0,"color":"#FFFFFF00","label":"Tarrant"},{"regionId":11317,"regionX":7,"regionY":47,"z":0,"color":"#FFFFFF00","label":"Maren"},{"regionId":11317,"regionX":5,"regionY":45,"z":0,"color":"#FF00FFFF","label":"Maren box SW"},{"regionId":11317,"regionX":9,"regionY":50,"z":0,"color":"#FF00FFFF","label":"Maren box NE"},{"regionId":11317,"regionX":35,"regionY":47,"z":0,"color":"#FFFFFF00","label":"Rowena"},{"regionId":11317,"regionX":40,"regionY":41,"z":0,"color":"#FFFFFF00","label":"Alric"},{"regionId":11317,"regionX":31,"regionY":39,"z":0,"color":"#FFFFFF00","label":"Sigrun"},{"regionId":11317,"regionX":41,"regionY":46,"z":0,"color":"#FFFFFF00","label":"Edrick"},{"regionId":11317,"regionX":39,"regionY":44,"z":0,"color":"#FF00FFFF","label":"Edrick box SW"},{"regionId":11317,"regionX":43,"regionY":48,"z":0,"color":"#FF00FFFF","label":"Edrick box NE"},{"regionId":11317,"regionX":23,"regionY":49,"z":0,"color":"#FFFFFF00","label":"Thomasin"},{"regionId":11317,"regionX":27,"regionY":44,"z":0,"color":"#FFFFFF00","label":"Gorden"},{"regionId":12338,"regionX":25,"regionY":56,"z":0,"color":"#FFFFFF00","label":"Nettie"},{"regionId":12338,"regionX":23,"regionY":53,"z":0,"color":"#FFFFFF00","label":"Corbin"},{"regionId":12338,"regionX":19,"regionY":55,"z":0,"color":"#FFFFFF00","label":"Wilda"},{"regionId":12338,"regionX":17,"regionY":59,"z":0,"color":"#FFFFFF00","label":"Bertram"},{"regionId":12338,"regionX":29,"regionY":53,"z":0,"color":"#FFFFFF00","label":"Hessa"},{"regionId":12338,"regionX":34,"regionY":54,"z":0,"color":"#FFFFFF00","label":"Orrick"},{"regionId":12338,"regionX":36,"regionY":58,"z":0,"color":"#FFFFFF00","label":"Lark"},{"regionId":12338,"regionX":22,"regionY":60,"z":0,"color":"#FFFFFF00","label":"Mabb"},{"regionId":12338,"regionX":27,"regionY":51,"z":0,"color":"#FFFFFF00","label":"Rushen"},{"regionId":12338,"regionX":14,"regionY":56,"z":0,"color":"#FFFFFF00","label":"Gorse"},{"regionId":12338,"regionX":31,"regionY":49,"z":0,"color":"#FFFFFF00","label":"Thistle"},{"regionId":12338,"regionX":29,"regionY":47,"z":0,"color":"#FF00FFFF","label":"Thistle box SW"},{"regionId":12338,"regionX":33,"regionY":51,"z":0,"color":"#FF00FFFF","label":"Thistle box NE"},{"regionId":12340,"regionX":45,"regionY":51,"z":0,"color":"#FFFFFF00","label":"Pell"},{"regionId":12340,"regionX":50,"regionY":47,"z":0,"color":"#FFFFFF00","label":"Sowerby"},{"regionId":12340,"regionX":42,"regionY":46,"z":0,"color":"#FFFFFF00","label":"Bryn"},{"regionId":11828,"regionX":20,"regionY":60,"z":0,"color":"#FFFFFF00","label":"Aymer"},{"regionId":11828,"regionX":24,"regionY":57,"z":0,"color":"#FFFFFF00","label":"Godric"},{"regionId":11828,"regionX":17,"regionY":59,"z":0,"color":"#FFFFFF00","label":"Isolde"},{"regionId":11828,"regionX":22,"regionY":61,"z":0,"color":"#FFFFFF00","label":"Ranulf"},{"regionId":11828,"regionX":15,"regionY":56,"z":0,"color":"#FFFFFF00","label":"Editha"},{"regionId":11828,"regionX":28,"regionY":59,"z":0,"color":"#FFFFFF00","label":"Osbert"},{"regionId":11828,"regionX":21,"regionY":53,"z":0,"color":"#FFFFFF00","label":"Amice"},{"regionId":11828,"regionX":19,"regionY":51,"z":0,"color":"#FF00FFFF","label":"Amice box SW"},{"regionId":11828,"regionX":23,"regionY":55,"z":0,"color":"#FF00FFFF","label":"Amice box NE"},{"regionId":11828,"regionX":51,"regionY":55,"z":0,"color":"#FFFFFF00","label":"Thurstan"},{"regionId":11828,"regionX":52,"regionY":57,"z":0,"color":"#FFFFFF00","label":"Sybilla"},{"regionId":11828,"regionX":50,"regionY":53,"z":0,"color":"#FFFFFF00","label":"Gervase"},{"regionId":11828,"regionX":53,"regionY":54,"z":0,"color":"#FFFFFF00","label":"Adela"},{"regionId":11829,"regionX":13,"regionY":42,"z":0,"color":"#FFFFFF00","label":"Hamon"},{"regionId":11829,"regionX":20,"regionY":46,"z":0,"color":"#FFFFFF00","label":"Eudo"},{"regionId":12083,"regionX":48,"regionY":30,"z":0,"color":"#FFFFFF00","label":"Rowan"},{"regionId":12083,"regionX":53,"regionY":25,"z":0,"color":"#FFFFFF00","label":"Berengar"},{"regionId":11571,"regionX":53,"regionY":21,"z":0,"color":"#FFFFFF00","label":"Constance"},{"regionId":12850,"regionX":22,"regionY":30,"z":0,"color":"#FFFFFF00","label":"Tam"},{"regionId":12850,"regionX":29,"regionY":30,"z":0,"color":"#FFFFFF00","label":"Nell"},{"regionId":12850,"regionX":32,"regionY":34,"z":0,"color":"#FFFFFF00","label":"Roderick"},{"regionId":12850,"regionX":25,"regionY":39,"z":0,"color":"#FFFFFF00","label":"Bramwell"},{"regionId":12850,"regionX":31,"regionY":44,"z":0,"color":"#FFFFFF00","label":"Lisbeth"},{"regionId":12850,"regionX":34,"regionY":55,"z":0,"color":"#FFFFFF00","label":"Ordric"},{"regionId":12850,"regionX":18,"regionY":53,"z":0,"color":"#FFFFFF00","label":"Fenna"},{"regionId":12850,"regionX":9,"regionY":50,"z":0,"color":"#FFFFFF00","label":"Wat"},{"regionId":12850,"regionX":7,"regionY":48,"z":0,"color":"#FF00FFFF","label":"Wat box SW"},{"regionId":12850,"regionX":12,"regionY":52,"z":0,"color":"#FF00FFFF","label":"Wat box NE"},{"regionId":12850,"regionX":41,"regionY":28,"z":0,"color":"#FFFFFF00","label":"Gilda"},{"regionId":12594,"regionX":49,"regionY":22,"z":0,"color":"#FFFFFF00","label":"Symon"},{"regionId":12594,"regionX":43,"regionY":17,"z":0,"color":"#FFFFFF00","label":"Alys"},{"regionId":12594,"regionX":51,"regionY":52,"z":0,"color":"#FFFFFF00","label":"Peveril"},{"regionId":12595,"regionX":35,"regionY":3,"z":0,"color":"#FFFFFF00","label":"Hulda"},{"regionId":12595,"regionX":38,"regionY":2,"z":0,"color":"#FFFFFF00","label":"Doggett"},{"regionId":12854,"regionX":53,"regionY":21,"z":0,"color":"#FFFFFF00","label":"Aldric"},{"regionId":12854,"regionX":57,"regionY":21,"z":0,"color":"#FFFFFF00","label":"Bettany"},{"regionId":12854,"regionX":54,"regionY":30,"z":0,"color":"#FFFFFF00","label":"Cordell"},{"regionId":12854,"regionX":52,"regionY":24,"z":0,"color":"#FFFFFF00","label":"Rosalind"},{"regionId":12854,"regionX":29,"regionY":6,"z":0,"color":"#FFFFFF00","label":"Halvard"},{"regionId":12854,"regionX":24,"regionY":2,"z":0,"color":"#FFFFFF00","label":"Wenna"},{"regionId":12854,"regionX":16,"regionY":45,"z":0,"color":"#FFFFFF00","label":"Garrick"},{"regionId":13109,"regionX":6,"regionY":36,"z":0,"color":"#FFFFFF00","label":"Tobin"}]
```

## Suggested route

**This is the cheap order, not the risk order.** The two are different lists and both
are worth having: the reach table above is what to settle first if you only have half
an hour, and this is the shortest circuit if you are doing all 127. Where they
disagree, the reach table wins — it is the one sorted by how likely a tile is to be
wrong.

One circuit, nine stops, west to east:

1. **Ardougne** — cloak to the monastery (6), north to the farm (3), east to the
   Legends' Guild path (5). 14.
2. **Catherby** — Camelot teleport. Woods first (4), then the town (4), the beach
   (2) and the eastern shore (4). 14.
3. **Falador** — lodestone. **The church first (4)** — those are the four to settle
   before anything else in this file — then the park (7), north of the wall (2),
   the Crafting Guild (1), and the cabbage field on the way out. 16.
4. **Draynor** — lodestone. The village (11), then north to the manor grounds (3).
   14.
5. **Edgeville** — glory. Bank (8), the road east (6), the ditch approach (4). 18.
6. **Grand Exchange** — one stop, but a crowded one. 15.
7. **Lumbridge** — home teleport. The castle and town (9), then west of the road
   (3) and the swamp road (2). 14.
8. **Varrock** — lodestone, north to the church (4), the north gate (2), the north
   street (1), then out of the east gate (1). 8.
9. **Al Kharid** — lodestone. The toll road (5), the goat pen (6), the desert (3).
   14.
