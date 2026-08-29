# City top-up check

**Status: not yet walked. Every tile on this list is unverified, and none of them
should be moved until somebody has stood on it.**

On 2026-08-29 the five thin cities were brought up to ten citizens each — 33 new
figures across 13 region files. Al Kharid, Catherby, Falador and Ardougne went
from three or four figures to ten; Draynor went from two citizens (plus one
scenery prop) to ten.

**Revised the same day, after review.** Two records in the Ardougne monastery
were re-authored — "Brother Alfric" became "Anselm" on a kit that can actually be
re-dealt, and "Brother Edwy" moved off a Zamorakian monk's robes — and several
examine lines and remarks were rewritten. **No tile moved and no wander box
changed**, so every marker in the import block below is still the marker it was;
only two names and a good deal of text are different. The rest of this file has
been brought back into line with the data, including four numbers that were
wrong: see the notes marked *2026-08-29* below.

**Nothing offline can tell you whether a tile is walkable.** The dataset stores a
tile; the game stores what is on it. So every one of the 33 placements below was
chosen by the only rule that is honest from outside the game:

1. **Stand near a tile that already works.** Each new figure is placed within a
   few tiles of a shipped citizen whose own tile is proven — somebody stood on it
   in game when the predecessor's dataset was authored. The "placed near" column
   names that figure and the distance.
2. **Or stand inside a wander box a human drew.** **Twenty** of the 33 sit inside
   an existing `WanderingCitizen`'s authored box, which is ground somebody
   already decided a citizen could pace across.

   This said "fourteen" until 2026-08-29, and fourteen is what you get by
   subtracting the six whose box was drawn for something that is not a walking
   human: Hesper (Plopper, a pig), Peri (Bees), Marta (a squirrel), Tobias (the
   Brassican Mage), and Marlow and Cuthbert (the Ghost). That is a defensible
   refinement — a pig's pen is weaker evidence for a farmhand than a knight's
   patrol is for a townsman — but it is a different claim from the one the
   sentence was making, and it was never stated. Both numbers are here now: **20
   stand inside somebody's box, 14 inside a box drawn for a walking human.**

Both are inferences, not facts. A figure standing inside a wall is exactly the
defect this list exists to find.

### The thirteen with no box under them at all

These stand on ground whose only evidence is a nearby figure — no
`WanderingCitizen`'s authored rectangle covers them, so "somebody decided a
citizen could walk here" is not available for any of them, only "somebody stood
three tiles away". **This is the set to walk first**, and it is derivable from
the table below but was never stated:

**Anselm · Brother Edwy · Aldous · Ilsa · Bram · Odell · Corliss · Maud ·
Perrin · Hollis · Tarik · Nadir · Halima**

Two notes on reading that list. Odell, Nadir and Ilsa carry a box of *their own*
— invented for this pass, not inherited — so they have a rectangle to walk but no
prior human judgement behind it, which is why they are here rather than with the
twenty. And Aldous is the one whose **pose** needs scenery as well as ground: he
swings a `Woodcutting` axe and wants a tree.

The other twenty stand inside a box somebody drew, which is the stronger of the
two inferences this pass had available. Fourteen of those twenty are inside a box
drawn for a walking human; the other six borrow a pig's, a bee swarm's, a
squirrel's, a ghost's (twice) and the Brassican Mage's.

**Twelve of the 33 are wanderers, and their boxes are unverified too.** A box is
a rectangle of tiles, and the walk visits all of it — so a box with a tree in the
middle produces a citizen who walks through the tree. The Ground Markers block at
the bottom therefore carries **57** markers, not 33: one yellow marker per figure,
plus a cyan marker on the south-west and north-east corner of each of the twelve
new boxes. Walk the rectangle, not just the middle of it.

## How to find them

Every tile below is given as `x, y, plane`. Copy the JSON block at the bottom of
this file, then right-click the **Ground Markers** plugin in the sidebar and
choose **Import**.

- **Yellow** squares are the 33 figures, labelled with the citizen's name.
- **Cyan** squares are the 24 wander-box corners, labelled `<name> box SW` and
  `<name> box NE`.

Turn **Lively Cities** on, set **Crowd density** to `Full` (not `Crowded` — see
below), and set **Render distance** high enough that a figure is drawn before you
arrive.

**Do the first pass at `Full`, then repeat at `Crowded`.** These 33 citizens seed
**63 of the 184** derived "Passer-by" figures the `Crowded` setting adds, and a
derived figure stands either inside its source's wander box or on a ring two
tiles out from it. Those tiles are checked against the live collision map before
anything spawns, so a `Crowded` extra on bad ground simply never appears — which
means `Crowded` cannot show you a new fault, but it *can* show you an authored
tile that turns out to be surrounded by nothing standable.

## What a tick means

A ticked box means **all** of:

- the figure is standing on the ground, not inside a wall, counter, hedge, fence
  or water, and not floating;
- it is not inside a doorway or on a staircase;
- it is not standing inside a real NPC or a shop table;
- its pose reads as deliberate from a few tiles away;
- and for a wanderer, the same is true everywhere inside its cyan rectangle.

An unticked box with a note is a work item: move the tile a few squares, or trim
the box. Both are one-line edits to the record in
`src/main/resources/RegionData/<region>.json`.

**Do not "fix" a figure by deleting it.** Each of the five cities is pinned at
ten citizens by
`RegionDataLoaderTest.everyCityHoldsTheNumberOfCitizensItIsSupposedTo`, and a
deletion also moves the echo, remark and skeleton counts. Move it instead.

(That sentence named a test that did not exist until the review pass on
2026-08-29. Nothing was checking any per-city count: deleting a Draynor figure
and adding one to Varrock left the 142 total intact and the whole suite green.
The assertion is real now, and it covers all nine cities rather than the five
that moved.)

---

## Al Kharid — 6

*Getting there: Al Kharid lodestone. 13105 is the goat pen and the road south to
the Shantay Pass; 13106 is the road north of the toll gate; 13361 is the open
desert east of the city.*

### Region 13105 — the goat pen, south of the city

- [ ] **Tarik** — `3310, 3143, 0`, stationary, facing east
      Examine: "Twice a day, every goat."
      Kit: "Ali the wanderer" (13106), palette re-dealt · `HumanIdle` / `HumanWalk`
      Placed near: Ali the goat herder, 3 tiles

- [ ] **Nadir** — `3309, 3139, 0`, **wanders** `3306,3137 .. 3311,3142`, facing east
      Examine: "He walks the road to the pass and back."
      Kit: "Ali the goat herder" (13105), palette re-dealt · `HumanIdle` / `HumanWalk`
      Placed near: Ali the goat herder, 4 tiles
      Says: "Water is a long way off." / "Mind the goats." / "Two hours to the pass,
      if you keep moving."
      **Box note:** Ali's own box is `3311,3138 .. 3313,3140`. Nadir's reaches
      **five tiles west, one south and two north** of it, and stops two short of
      its eastern edge — so most of his rectangle is ground nobody has vouched
      for, not an overlap with ground somebody has. If the pen is fenced, this is
      the most likely of the three to cross it. (This note said "two tiles west
      and two south" until 2026-08-29, which understated the west reach by three
      tiles on the one entry the doc singles out as most likely to cross a
      fence.)

- [ ] **Halima** — `3316, 3143, 0`, stationary, facing west
      Examine: "Waiting for the shade."
      Kit: "Afrah" (12853), palette re-dealt · `HumanIdle` / `HumanWalk`
      Placed near: Ali the goat herder, 3 tiles (and Abex the goat, 3 tiles)

### Region 13106 — the road north of the toll gate

Both of these sit inside "Ali the wanderer"'s own 14x9 box, which is the largest
piece of vouched-for ground in Al Kharid.

- [ ] **Fahd** — `3284, 3244, 0`, **wanders** `3281,3242 .. 3287,3246`, facing north
      Examine: "On this road since dawn."
      Kit: "Ali the spy" (13361), palette re-dealt · `HumanIdle` / `HumanWalk`
      Placed near: Ali the wanderer, 8 tiles

- [ ] **Reza** — `3287, 3241, 0`, **wanders** `3283,3240 .. 3290,3245`, facing south
      Examine: "Heading north, if the gate is open."
      Kit: "Toren" (12853), palette re-dealt · `HumanIdle` / `HumanWalk`
      Placed near: Ali the wanderer, 7 tiles

### Region 13361 — the desert east of the city

- [ ] **Yusra** — `3339, 3151, 0`, stationary, facing east
      Examine: "The caravan left without her, and she has decided to take that
      personally."
      Kit: "Ava" (12595), palette re-dealt · `HumanIdle` / `HumanWalk`
      Placed near: Ali the spy, 4 tiles — inside his authored box

---

## Catherby — 6

*Getting there: Camelot teleport, then east. 11317 is the eastern half of the
town and the beach; 11061 is the woods and the western half.*

### Region 11317 — the town

Merryn, Osric and Wynn are all inside Charlie the dog's authored box, which is
the town-centre ground the predecessor vouched for.

- [ ] **Merryn** — `2827, 3444, 0`, stationary, facing south
      Examine: "Waiting on the charter ship."
      Kit: "Joanne" (11317), palette re-dealt · `Think` / `HumanWalk`
      Placed near: Charlie, 3 tiles
      Says: "It was due at noon." / "The wind is wrong for it."

- [ ] **Osric** — `2826, 3441, 0`, stationary, facing west
      Examine: "Mending the same net he mended yesterday."
      Kit: "Fisherman" (12850), palette re-dealt · `HumanIdle` / `HumanWalk`
      Placed near: Charlie, 6 tiles
      **Note:** this is the south-west corner of Charlie's box, so it is the tile
      in this group most likely to be against a wall.

- [ ] **Wynn** — `2829, 3442, 0`, **wanders** `2826,3441 .. 2829,3446`, facing north
      Examine: "She is looking for someone who was here a moment ago."
      Kit: "Mary" (12852), palette re-dealt · `HumanIdle` / `HumanWalk`
      Placed near: Charlie, 5 tiles — her whole box is inside his

- [ ] **Ilsa** — `2851, 3435, 0`, **wanders** `2849,3433 .. 2853,3437`, facing east
      Examine: "Out for the view, she says."
      Kit: "Grace" (12850), palette re-dealt · `HumanIdle` / `HumanWalk`
      Placed near: Joanne, 3 tiles
      Says: "You can see Entrana on a clear day." / "Not today, mind."
      **Box note:** Joanne is the only proven tile out here on the eastern shore,
      and her box is invented rather than inherited. Walk all four corners.

### Region 11061 — the woods west of the town

- [ ] **Aldous** — `2755, 3431, 0`, stationary, facing west
      Examine: "The yews are further in, apparently."
      Kit: "Forester" (11061), palette re-dealt · `Woodcutting` / `HumanWalk`
      Placed near: Forester, 3 tiles
      **Pose note:** `Woodcutting` swings an axe. If there is no tree on or beside
      his tile it will read the way a `Sitting` figure with no chair reads — the
      same class of defect `SEATING-CHECK.md` is about. He is three tiles from a
      figure already doing it, so the odds are good, but this is the one to look
      at first in Catherby.

- [ ] **Peri** — `2757, 3443, 0`, **wanders** `2755,3441 .. 2758,3445`, facing south
      Examine: "Well clear of the bees, and staying that way."
      Kit: "Elara" (12853), palette re-dealt · `SuzieIdle` / `HumanWalk`
      Placed near: Bees, 3 tiles — her box overlaps the swarm's
      Says: "They only sting if you flap."

---

## Falador — 6

*Getting there: Falador lodestone. 11828 is the city and the park; 11829 is north
of the wall; 12083 is the cabbage field to the south-east; 11571 is the Crafting
Guild.*

### Region 11828 — the park

**All four of these stand inside Sir Wendes' authored box.** That box is the only
vouched-for ground anywhere in region 11828, which is why Falador's top-up is a
park scene rather than a street scene. If the park turns out to hold trees or
benches on these tiles, the fix is to shuffle within the box rather than to leave
the city thin.

- [ ] **Alwin** — `2965, 3386, 0`, stationary, facing north
      Examine: "In armour, off duty, and adamant that this is not a contradiction."
      Kit: "Sir Wendes" (11828), palette re-dealt · `HumanIdle` / `HumanWalk`
      Placed near: Sir Wendes, 5 tiles
      Says: "It is easier to wear than to carry." / "The park is quieter than the courtyard."

- [ ] **Berta** — `2962, 3383, 0`, stationary, facing west
      Examine: "She comes to the park to get away from the forge."
      Kit: "Lily" (12854), palette re-dealt · `HumanIdle` / `HumanWalk`
      Placed near: Sir Wendes, 8 tiles — the south-west corner of his box

- [ ] **Dunstan** — `2967, 3383, 0`, **wanders** `2963,3382 .. 2970,3387`, facing south
      Examine: "Pacing out a measurement, and losing count."
      Kit: "Craftsman Jim" (11571), palette re-dealt · `HumanIdle` / `HumanWalk`
      Placed near: Sir Wendes, 7 tiles — his box is inside Sir Wendes' box

- [ ] **Nessa** — `2968, 3388, 0`, stationary, facing east
      Examine: "She has read the same page four times."
      Kit: "Jofridr" (12853), palette re-dealt · `HumanIdle` / `HumanWalk`
      Placed near: Sir Wendes, 2 tiles

### Region 12083 — the cabbage field

- [ ] **Tobias** — `3059, 3292, 0`, stationary, facing west
      Examine: "Somebody has to count the cabbages."
      Kit: "Mike" (12850), palette re-dealt · `HumanIdle` / `HumanWalk`
      Placed near: Brassican Mage, 4 tiles — inside his 17x8 box
      Says: "Two hundred and six." / "It was two hundred and eight yesterday."

### Region 11829 — north of the wall

- [ ] **Marta** — `2960, 3433, 0`, **wanders** `2958,3432 .. 2962,3435`, facing south
      Examine: "Walks this way whatever the weather."
      Kit: "Nicholson" (12342), palette re-dealt · `HumanIdle` / `HumanWalk`
      Placed near: Squirrel, 3 tiles — her box is inside the squirrel's

---

## Ardougne — 7

*Getting there: Ardougne cloak or teleport. 10290 is the monastery south-west of
the city; 10548 is the farm north of it; 10804 is the Legends' Guild path.*

**Read this before walking Ardougne.** None of the three regions this plugin
ships for "Ardougne" is East Ardougne's market square — that is region 10547, and
there is no region file for it. So the seven figures below make the monastery, the
farm and the guild path feel inhabited, and the market square is exactly as empty
as it was. Fixing that means a new region file, which is a bigger decision than a
top-up pass.

### Region 10290 — the monastery

- [ ] **Anselm** — `2594, 3216, 0`, stationary, facing north
      Examine: "Not a monk. He is here about the roof, and has been since Tuesday."
      Kit: "Morten" (12853), palette re-dealt · `HumanIdle` / `HumanWalk`
      Placed near: Brother Keptic, 3 tiles
      **Re-authored 2026-08-29.** This tile shipped as "Brother Alfric" wearing the
      "Saradomin priest"'s kit, which carries no recolour arrays at all — so there
      was nothing to rotate and the figure was a pixel-for-pixel copy of a citizen
      who walks Varrock's church, 661 tiles east. The name was also one letter from
      **Brother Althric**, a real NPC standing in this same monastery. Same uuid,
      new name, new kit, palette actually re-dealt.
      He is deliberately **not** a monk. Every robed donor this dataset holds is
      either un-re-dealable or the wrong god: the Saradomin priest carries no
      recolour at all, and the Saradomin wizard and Zethrus each carry two colours
      spread across their slots, so no rotation moves either of them anywhere. Both
      figures in this room being un-re-dealt was the defect; making him a layman is
      how the room gets two different bodies. **What to look for:** a
      plainly-dressed man among monks should read as a visitor, which is what the
      examine text says he is. If he reads as a trespasser instead, write that down.

- [ ] **Brother Edwy** — `2597, 3213, 0`, stationary, facing east
      Examine: "The abbot asked for one clean copy by Friday."
      Kit: "Assistant Apothecary" (12597), palette re-dealt · `Think` / `HumanWalk`
      Placed near: Brother Keptic, 3 tiles
      Says: "The ink is running thin." / "Do not lean on the desk."
      **Re-kitted 2026-08-29.** He shipped wearing "Zethrus", whose own examine text
      is "A zamorakian monk" — the wrong god for a Saradominist monastery — and
      whose palette holds two colours across three slots, so no rotation could move
      him far from what he was. The Assistant Apothecary's is six distinct colours
      across six slots, which is a re-deal that can actually be seen. **What to look
      for:** whether he still reads as somebody who belongs in a monastery. His
      donor is an apothecary's apprentice from Varrock, chosen for having a palette
      worth rotating rather than for the look, and nothing offline can say what a
      rotated version of it looks like. He and Anselm are the two figures in this
      pass most likely to need a different kit after the walk.

### Region 10548 — the farm

- [ ] **Hesper** — `2662, 3373, 0`, **wanders** `2662,3373 .. 2665,3376`, facing south
      Examine: "Pigs, barley, and a strong opinion about which belongs where."
      Kit: "Thalindra" (12850), palette re-dealt · `HumanIdle` / `HumanWalk`
      Placed near: Plopper, 3 tiles — her box **is** Plopper's box, exactly
      **Box note:** if that box is a fenced pen, a farmhand inside it is fine and
      a farmhand *stuck* in it is not. Watch her for a minute.

### Region 10804 — the Legends' Guild path

The Legends' Guard paces a 2x15 strip that is the path up to the guild gate.
These four stand along it, which reads as a queue.

- [ ] **Coren** — `2728, 3362, 0`, stationary, facing south
      Examine: "The other guard. He has the gate at night."
      Kit: "Legends' Guard" (10804), palette re-dealt · `HumanWithStickIdle` /
      `HumanWithStickWalk`
      Placed near: Legends' Guard, 3 tiles

- [ ] **Idris** — `2729, 3358, 0`, stationary, facing west
      Examine: "He has been told to come back with a better letter."
      Kit: "Ak-Haranu" (12853), palette re-dealt · `HumanIdle` / `HumanWalk`
      Placed near: Legends' Guard, 7 tiles

- [ ] **Wilhelm** — `2728, 3354, 0`, stationary, facing south
      Examine: "Somebody's luggage came up this hill on his back, and he is still
      getting his breath."
      Kit: "Strongman" (12853), palette re-dealt · `HumanIdle` / `HumanWalk`
      Placed near: Legends' Guard, 11 tiles
      Says: "It was heavier than it looked."

- [ ] **Sela** — `2729, 3352, 0`, **wanders** `2728,3351 .. 2729,3356`, facing north
      Examine: "Silver, mostly. She says the guild pays better than the market."
      Kit: "Silver merchant" (12853), palette re-dealt · `HumanIdle` / `HumanWalk`
      Placed near: Legends' Guard, 13 tiles — her box is the southern third of his
      **Note:** 13 tiles is the longest reach in this pass. The guard's box is
      2 tiles wide and 15 long, so the far end of it is much less certain than
      the end he actually starts on. Walk the strip.

---

## Draynor — 8

*Getting there: Draynor Village lodestone or an Amulet of glory. 12338 is the
village; 12340 is Draynor Manor's grounds.*

### Region 12338 — the village, north end

**The thinnest evidence in the whole pass.** Region 12338 shipped exactly two
entities before this — the Sailor at `3099,3260` and a scenery prop at
`3101,3259` — so six new figures are hung off two proven tiles. They are spread
between `x 3093..3106` and `y 3256..3261`, which is the ground north of Ned's
house and the road east toward the manor gate. Every one of the six is a genuine
guess about a building edge. **Expect to move one or two.**

Nothing here is placed above `y 3261`, deliberately: `y 3264` leaves region 12338
for region 12339, which ships no file and no city claims, and a `Crowded` extra
derived from a citizen at `y 3262` can land there. **That fix has zero margin.**
`Bram` and `Odell` sit at `y 3261`, which is the last value that keeps every ring
candidate a `Crowded` extra could use inside 12338. Do not move either of them
north, and if the walk says one of them has to move, move it west, east or south.

**Walk them in this order**, which is nearest-proven-tile distance, furthest
first — the ranking the walk should follow, and not the same as the order they
are listed in:

| | Figure | Nearest proven tile | Box? |
|---|---|---|---|
| 1 | Maud | 6 (Sailor) | no |
| 2 | Corliss | 5 (prop) | no |
| 3 | Bram | 3 (Sailor) | no |
| 4 | Perrin | 3 (prop) | no |
| 5 | Odell | 2 (prop) | his own, unverified |
| 6 | Hollis | 2 (Sailor) | no |

Distances are Chebyshev (the number of steps a player takes), measured from the
two proven tiles named above.

- [ ] **Bram** — `3096, 3261, 0`, stationary, facing north
      Examine: "Minding somebody else's cart, and taking it seriously."
      Kit: "Charlie" (12852), palette re-dealt · `HumanIdle` / `HumanWalk`
      Placed near: Sailor, 3 tiles

- [ ] **Odell** — `3103, 3261, 0`, **wanders** `3101,3260 .. 3105,3262`, facing south
      Examine: "He starts up the manor path and thinks better of it."
      Kit: "Krazok" (12852), palette re-dealt · `HumanIdle` / `HumanWalk`
      Placed near: the scenery prop at 3101,3259, 2 tiles
      Says: "Not today."

- [ ] **Corliss** — `3106, 3259, 0`, stationary, facing west
      Examine: "Somebody gave him directions and he is still working through them."
      Kit: "Sefton" (12853), palette re-dealt · `HumanIdle` / `HumanWalk`
      Placed near: the scenery prop at 3101,3259, 5 tiles
      **Note:** the furthest east of the six, and **the second-weakest placement in
      the group** — five tiles from the nearest proven tile, with no box under him.
      Walk him before Perrin. (Until 2026-08-29 this entry carried no note at all
      while Perrin, three tiles out, carried one; the ranking was by how alarming
      the neighbour sounded rather than by how far the evidence reached.)

- [ ] **Maud** — `3093, 3258, 0`, stationary, facing east
      Examine: "Out at the west end, where it is quiet."
      Kit: "Jo" (12852), palette re-dealt · `HumanIdle` / `HumanWalk`
      Placed near: Sailor, 6 tiles
      **Note:** the furthest west of the six, the closest to Aggie's house, and
      **the weakest placement in the group** — six tiles from the Sailor and eight
      from the prop, with no box under her. The likeliest of the six to be inside a
      wall. Walk her first.
      (Her examine text used to read "Keeping an eye on the market from a safe
      distance", which was fiction invented to justify a tile chosen for lack of
      vouched-for ground. She is at the west end because that is where the evidence
      ran out, and the note above is where that belongs.)

- [ ] **Perrin** — `3103, 3256, 0`, stationary, facing south
      Examine: "Counting windows. He says he likes the architecture."
      Kit: "Master Thief" (12852), palette re-dealt · `HumanIdle` / `HumanWalk`
      Placed near: the scenery prop at 3101,3259, 3 tiles
      **Note:** the furthest south of the six, and the closest to Ned's house — but
      three tiles from a proven tile, so **fourth of six by evidence**, not second.
      (His examine used to read "He has not moved for an hour and is not resting",
      which is a description of what a `StationaryCitizen` *is* rather than of who
      he is. Every figure in this pass has not moved for an hour.)

- [ ] **Hollis** — `3099, 3258, 0`, stationary, facing north
      Examine: "He is here about the willow trees."
      Kit: "Andre" (12342), palette re-dealt · `HumanIdle` / `HumanWalk`
      Placed near: Sailor, 2 tiles
      Says: "They are not all mine."

### Region 12340 — Draynor Manor grounds

Both stand inside the Ghost's authored box or one tile off it.

- [ ] **Marlow** — `3116, 3377, 0`, **wanders** `3116,3376 .. 3119,3377`, facing west
      Examine: "Keeps the grounds. Never after dark."
      Kit: "Gardener" (12597), palette re-dealt · `HumanIdle` / `HumanWalk`
      Placed near: Ghost, 4 tiles — his box is the western part of the Ghost's

- [ ] **Cuthbert** — `3118, 3376, 0`, stationary, facing east
      Examine: "He came to value the manor, and now cannot get the smell of it out
      of his coat."
      Kit: "Nick" (12852), palette re-dealt · `HumanIdle` / `HumanWalk`
      Placed near: Ghost, 2 tiles

---

## What this pass deliberately did not do

- **No new model ids.** Every one of the 33 wears a `modelIds` array copied whole
  from a shipped citizen, so the dataset still contains exactly **324** distinct
  model ids — the same number it had before. That is the figure that decides how
  much of this plugin an OSRS cache renumbering can break, and it did not move.
  (`ModelIdAuditTest.theDistinctModelIdFigureIsPinned` is where it is held.)
- **No new animations.** The dataset still uses exactly **72** distinct animation
  names. Each figure plays either its donor's own pair or another animation on the
  same framemap as the body it is wearing, which is what
  `AnimationSkeletonTest` checks record by record.
- **No new scenery.** Scenery is still 42 records, untouched.
- **No new region files, and no changes to the `City` enum.** Ardougne's market
  square, Falador's streets and Draynor's market are still unpopulated because
  this plugin ships no data for the regions they are in — see the Ardougne note
  above.
- **Nothing sits down.** Not one of the 33 is posed `Sitting`, `DwarfSit`,
  `CatSit` or `ChurchSitting`, because `SEATING-CHECK.md` is a list of 31 figures
  whose seats nobody has verified and there was no reason to lengthen it. The one
  pose here that needs scenery under it is Aldous' `Woodcutting`, and it is
  flagged on his entry.

## Ground markers

Copy this whole line, then Ground Markers → Import. **57 tiles**: the 33 figures
in yellow, and the south-west and north-east corners of the twelve new wander
boxes in cyan.

```json
[{"regionId":13105,"regionX":46,"regionY":7,"z":0,"color":"#FFFFFF00","label":"Tarik"},{"regionId":13105,"regionX":45,"regionY":3,"z":0,"color":"#FFFFFF00","label":"Nadir"},{"regionId":13105,"regionX":52,"regionY":7,"z":0,"color":"#FFFFFF00","label":"Halima"},{"regionId":13106,"regionX":20,"regionY":44,"z":0,"color":"#FFFFFF00","label":"Fahd"},{"regionId":13106,"regionX":23,"regionY":41,"z":0,"color":"#FFFFFF00","label":"Reza"},{"regionId":13361,"regionX":11,"regionY":15,"z":0,"color":"#FFFFFF00","label":"Yusra"},{"regionId":11317,"regionX":11,"regionY":52,"z":0,"color":"#FFFFFF00","label":"Merryn"},{"regionId":11317,"regionX":10,"regionY":49,"z":0,"color":"#FFFFFF00","label":"Osric"},{"regionId":11317,"regionX":13,"regionY":50,"z":0,"color":"#FFFFFF00","label":"Wynn"},{"regionId":11317,"regionX":35,"regionY":43,"z":0,"color":"#FFFFFF00","label":"Ilsa"},{"regionId":11061,"regionX":3,"regionY":39,"z":0,"color":"#FFFFFF00","label":"Aldous"},{"regionId":11061,"regionX":5,"regionY":51,"z":0,"color":"#FFFFFF00","label":"Peri"},{"regionId":11828,"regionX":21,"regionY":58,"z":0,"color":"#FFFFFF00","label":"Alwin"},{"regionId":11828,"regionX":18,"regionY":55,"z":0,"color":"#FFFFFF00","label":"Berta"},{"regionId":11828,"regionX":23,"regionY":55,"z":0,"color":"#FFFFFF00","label":"Dunstan"},{"regionId":11828,"regionX":24,"regionY":60,"z":0,"color":"#FFFFFF00","label":"Nessa"},{"regionId":12083,"regionX":51,"regionY":28,"z":0,"color":"#FFFFFF00","label":"Tobias"},{"regionId":11829,"regionX":16,"regionY":41,"z":0,"color":"#FFFFFF00","label":"Marta"},{"regionId":10290,"regionX":34,"regionY":16,"z":0,"color":"#FFFFFF00","label":"Anselm"},{"regionId":10290,"regionX":37,"regionY":13,"z":0,"color":"#FFFFFF00","label":"Brother Edwy"},{"regionId":10548,"regionX":38,"regionY":45,"z":0,"color":"#FFFFFF00","label":"Hesper"},{"regionId":10804,"regionX":40,"regionY":34,"z":0,"color":"#FFFFFF00","label":"Coren"},{"regionId":10804,"regionX":41,"regionY":30,"z":0,"color":"#FFFFFF00","label":"Idris"},{"regionId":10804,"regionX":40,"regionY":26,"z":0,"color":"#FFFFFF00","label":"Wilhelm"},{"regionId":10804,"regionX":41,"regionY":24,"z":0,"color":"#FFFFFF00","label":"Sela"},{"regionId":12338,"regionX":24,"regionY":61,"z":0,"color":"#FFFFFF00","label":"Bram"},{"regionId":12338,"regionX":31,"regionY":61,"z":0,"color":"#FFFFFF00","label":"Odell"},{"regionId":12338,"regionX":34,"regionY":59,"z":0,"color":"#FFFFFF00","label":"Corliss"},{"regionId":12338,"regionX":21,"regionY":58,"z":0,"color":"#FFFFFF00","label":"Maud"},{"regionId":12338,"regionX":31,"regionY":56,"z":0,"color":"#FFFFFF00","label":"Perrin"},{"regionId":12338,"regionX":27,"regionY":58,"z":0,"color":"#FFFFFF00","label":"Hollis"},{"regionId":12340,"regionX":44,"regionY":49,"z":0,"color":"#FFFFFF00","label":"Marlow"},{"regionId":12340,"regionX":46,"regionY":48,"z":0,"color":"#FFFFFF00","label":"Cuthbert"},{"regionId":13105,"regionX":42,"regionY":1,"z":0,"color":"#FF00FFFF","label":"Nadir box SW"},{"regionId":13105,"regionX":47,"regionY":6,"z":0,"color":"#FF00FFFF","label":"Nadir box NE"},{"regionId":13106,"regionX":17,"regionY":42,"z":0,"color":"#FF00FFFF","label":"Fahd box SW"},{"regionId":13106,"regionX":23,"regionY":46,"z":0,"color":"#FF00FFFF","label":"Fahd box NE"},{"regionId":13106,"regionX":19,"regionY":40,"z":0,"color":"#FF00FFFF","label":"Reza box SW"},{"regionId":13106,"regionX":26,"regionY":45,"z":0,"color":"#FF00FFFF","label":"Reza box NE"},{"regionId":11317,"regionX":10,"regionY":49,"z":0,"color":"#FF00FFFF","label":"Wynn box SW"},{"regionId":11317,"regionX":13,"regionY":54,"z":0,"color":"#FF00FFFF","label":"Wynn box NE"},{"regionId":11317,"regionX":33,"regionY":41,"z":0,"color":"#FF00FFFF","label":"Ilsa box SW"},{"regionId":11317,"regionX":37,"regionY":45,"z":0,"color":"#FF00FFFF","label":"Ilsa box NE"},{"regionId":11061,"regionX":3,"regionY":49,"z":0,"color":"#FF00FFFF","label":"Peri box SW"},{"regionId":11061,"regionX":6,"regionY":53,"z":0,"color":"#FF00FFFF","label":"Peri box NE"},{"regionId":11828,"regionX":19,"regionY":54,"z":0,"color":"#FF00FFFF","label":"Dunstan box SW"},{"regionId":11828,"regionX":26,"regionY":59,"z":0,"color":"#FF00FFFF","label":"Dunstan box NE"},{"regionId":11829,"regionX":14,"regionY":40,"z":0,"color":"#FF00FFFF","label":"Marta box SW"},{"regionId":11829,"regionX":18,"regionY":43,"z":0,"color":"#FF00FFFF","label":"Marta box NE"},{"regionId":10548,"regionX":38,"regionY":45,"z":0,"color":"#FF00FFFF","label":"Hesper box SW"},{"regionId":10548,"regionX":41,"regionY":48,"z":0,"color":"#FF00FFFF","label":"Hesper box NE"},{"regionId":10804,"regionX":40,"regionY":23,"z":0,"color":"#FF00FFFF","label":"Sela box SW"},{"regionId":10804,"regionX":41,"regionY":28,"z":0,"color":"#FF00FFFF","label":"Sela box NE"},{"regionId":12338,"regionX":29,"regionY":60,"z":0,"color":"#FF00FFFF","label":"Odell box SW"},{"regionId":12338,"regionX":33,"regionY":62,"z":0,"color":"#FF00FFFF","label":"Odell box NE"},{"regionId":12340,"regionX":44,"regionY":48,"z":0,"color":"#FF00FFFF","label":"Marlow box SW"},{"regionId":12340,"regionX":47,"regionY":49,"z":0,"color":"#FF00FFFF","label":"Marlow box NE"}]
```

## Suggested route

One circuit, west to east, five stops:

1. **Ardougne** — cloak to the monastery (Anselm, Edwy), north to the farm
   (Hesper), east to the Legends' Guild path (Coren, Idris, Wilhelm, Sela). 7.
2. **Catherby** — Camelot teleport, west into the woods first (Aldous, Peri),
   then east into the town (Osric, Merryn, Wynn) and out along the shore (Ilsa). 6.
3. **Falador** — lodestone, north wall first (Marta), then the park
   (Berta, Dunstan, Alwin, Nessa). 5 here, plus Tobias in the cabbage field on
   the way out south-east. 6.
4. **Draynor** — lodestone. The village six (Maud, Bram, Hollis, Perrin, Odell,
   Corliss) are all within ten tiles of each other; then north to the manor
   grounds for Marlow and Cuthbert. 8.
5. **Al Kharid** — lodestone, north of the toll gate first (Fahd, Reza), then
   south to the goat pen (Nadir, Tarik, Halima), then east into the desert for
   Yusra. 6.
