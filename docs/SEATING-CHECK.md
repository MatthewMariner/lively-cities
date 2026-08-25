# Seating check

**Status: not yet walked. No pose or tile on this list has been changed, and none
should be until it is.**

**Re-verified after the nine-city cut (2026-08-24), and unchanged.** The cut deleted
eighteen region files, so this list was recomputed from the reduced dataset rather
than assumed to still hold. All thirty-one figures survive: every one of them stands
in Varrock, Lumbridge, the Grand Exchange, Edgeville, Ardougne, Catherby or Draynor,
and none of the fifteen removed places held a seated or leaning figure at all. The
"scenery this dataset places nearby" lines were recomputed too — no deleted record
was within three tiles of any tile below, so not one of those lines moved. Grimefang,
in the open question at the end, is in Lumbridge and also survives.

Five of the figures below — Thalindra, the Dark wizard, Nightfire, Dofur and Simon —
did have their `moveAnimation` corrected in the same pass that produced this file,
because it named a different creature's skeleton from their pose and their models. That
is a different defect and it is recorded in `NOTICE`. None of the five moves, none
changed tile, and none changed pose, so nothing on this list is affected by it — but
"nothing here has been changed" would have been the wrong headline.

`Sitting` in this dataset is `AnimationID.CHAIR_SIT_READY_THRONE_4` (4114) — a
human sitting on something. Played on a tile with no bench, stool, chair, log,
crate or step under it, the figure squats in mid-air, and that is one of the few
defects in this plugin that looks like a bug from any angle.

**It cannot be checked from outside the game.** The dataset stores a tile, not
what is on it; the game's own collision and scenery data would have to be read at
that tile to know whether a seat is there, and this project deliberately does not
read the live cache offline. So this is a list, not a fix. Twenty-nine seated
figures were vendored in from the predecessor plugin, most of them placed by hand
by someone who was standing there at the time — mass-changing them sight-unseen
would very likely make it worse, because a citizen genuinely sitting on a pub
bench is good content and there is no way to tell those apart from here.

**What to do with it:** walk the list, look at each figure, tick the box. A ticked
box means "there is something under them and they look seated on it". An unticked
one with a note is a work item.

## How to find them

Every tile below is given as `x, y, plane`. The fastest way to stand on one is
RuneLite's own **Ground Markers** plugin: copy the JSON block at the bottom of
this file, then right-click the Ground Markers plugin in the sidebar and choose
**Import**. All thirty-one tiles — the twenty-nine seated figures and the two who
lean — appear as yellow marked squares labelled with the citizen's name, so the check
becomes "walk to the yellow tile, look at who is on it".

Turn **Lively Cities** on, set **Crowd density** to `Full` or `Crowded` so nobody
is thinned out from under you, and set **Render distance** high enough that the
figure is drawn before you arrive.

**"Scenery this dataset places nearby" is the single source for what is under a
figure**, and it is stated once, on that figure's own entry. Nothing further down this
file restates it — a summary that repeated the fact in its own words is exactly how this
file came to say both "none" and "a prop on the neighbouring tile" about the same cat.
The figure it names is only ever scenery *this plugin* places; whatever the game itself
has on that tile is the thing the walk is for.

## Ardougne — 1

*Getting there: Ardougne cloak / Ardougne teleport, then the monastery south-west of the city.*

- [ ] **Brother Keptic** — `2597, 3216, 0` (region 10290), pose `Sitting`
      Examine: "An elderly monk."
      Scenery this dataset places nearby: nothing within three tiles

## Catherby — 1

*Getting there: Camelot teleport, then east to Catherby.*

- [ ] **Joanne** — `2854, 3435, 0` (region 11317), pose `Sitting`
      Examine: "A Citizen of Gielinor."
      Scenery this dataset places nearby: nothing within three tiles

## Draynor — 1

*Getting there: Draynor Village lodestone / Amulet of glory to Draynor.*

- [ ] **Sailor** — `3099, 3260, 0` (region 12338), pose `Sitting`
      Examine: "A sailor."
      Scenery this dataset places nearby: nothing within one tile; the nearest is 3101,3259 models [19268], 2 tiles away

## Edgeville — 1

*Getting there: Amulet of glory to Edgeville.*

- [ ] **Andre** — `3090, 3496, 0` (region 12342), pose `Sitting`
      Examine: "A citizen of Gielinor."
      Scenery this dataset places nearby: nothing within one tile; the nearest is 3090,3499 models [2384], 3 tiles away

## Lumbridge — 5

*Getting there: Lumbridge Home teleport.*

- [ ] **Prisoner** — `3182, 3219, 0` (region 12594), pose `Sitting`
      Examine: "A prisoner being transported. I wonder what he did."
      Scenery this dataset places nearby: 3183,3219 models [2260, 3818] (within one tile)

- [ ] **Sue** — `3182, 3220, 0` (region 12594), pose `Sitting`
      Examine: "Ready for action."
      Scenery this dataset places nearby: 3183,3219 models [2260, 3818] (within one tile)

- [ ] **Thalindra** — `3228, 3236, 0` (region 12850), pose `Sitting`
      Examine: "She's not sure witch drink to order."
      Scenery this dataset places nearby: 3228,3237 models [2491] (within one tile)

- [ ] **Dark wizard** — `3228, 3238, 0` (region 12850), pose `Sitting`
      Examine: "A wizard of the evil kind."
      Scenery this dataset places nearby: 3228,3237 models [2491] (within one tile)

- [ ] **Limping Locke** — `3231, 3237, 0` (region 12850), pose `Sitting`
      Examine: "Don't ask him how he lost the leg."
      Scenery this dataset places nearby: nothing within one tile; the nearest is 3228,3237 models [2491], 3 tiles away

## Lumbridge Swamp caves — 1

*Getting there: not a teleport. Region 12697 is underground — the y of 9825 is what
says so, the plane is still 0 — reached through the Lumbridge Swamp cave entrance
south of the castle. A light source is needed.*

- [ ] **Goblin** — `3191, 9825, 0` (region 12697), pose `GoblinPull`
      Examine: "A desperate goblin."
      Scenery this dataset places nearby: nothing within three tiles

## Varrock — 19

*Getting there: Varrock teleport.*

- [ ] **Nick** — `3203, 3387, 0` (region 12852), pose `Sitting`
      Examine: "Up to no good."
      Scenery this dataset places nearby: nothing within one tile; the nearest is 3203,3385 models [13446], 2 tiles away

- [ ] **Jo** — `3204, 3386, 0` (region 12852), pose `Sitting`
      Examine: "An inexperienced, young thief."
      Scenery this dataset places nearby: 3203,3385 models [13446] (within one tile)

- [ ] **Charlie** — `3243, 3381, 0` (region 12852), pose `Sitting`
      Examine: "A Citizen of Gielinor"
      Scenery this dataset places nearby: nothing within three tiles

- [ ] **Afrah** — `3215, 3407, 0` (region 12853), pose `Sitting`
      Examine: "She looks like she's from Al-Kharid."
      Scenery this dataset places nearby: nothing within three tiles

- [ ] **Nightfire** — `3215, 3419, 0` (region 12853), pose `CatSit`
      Examine: "Are you kitten me right meow?"
      Scenery this dataset places nearby: nothing within three tiles

- [ ] **Alexander** — `3216, 3402, 0` (region 12853), pose `Sitting`
      Examine: "Looks like a nice chap."
      Scenery this dataset places nearby: nothing within three tiles

- [ ] **Darren** — `3217, 3404, 0` (region 12853), pose `Sitting`
      Examine: "He has a nice moustache."
      Scenery this dataset places nearby: nothing within three tiles

- [ ] **Ali** — `3217, 3408, 0` (region 12853), pose `Sitting`
      Examine: "He looks like he's from Al-Kharid."
      Scenery this dataset places nearby: nothing within three tiles

- [ ] **H.A.M. Member** — `3218, 3399, 0` (region 12853), pose `Sitting`
      Examine: "A member of H.A.M."
      Scenery this dataset places nearby: nothing within one tile; the nearest is 3221,3398 models [2468], 3 tiles away

- [ ] **Mysterious Old Man** — `3218, 3401, 0` (region 12853), pose `Sitting`
      Examine: "A man, who is old, and mysterious."
      Scenery this dataset places nearby: nothing within one tile; the nearest is 3221,3398 models [2468], 3 tiles away

- [ ] **Demon Butler** — `3221, 3397, 0` (region 12853), pose `Sitting`
      Examine: "He's on his day off."
      Scenery this dataset places nearby: 3221,3398 models [2468]; 3221,3398 models [2491] (within one tile)

- [ ] **Butler Jarvis** — `3221, 3399, 0` (region 12853), pose `Sitting`
      Examine: "He's on his day off."
      Scenery this dataset places nearby: 3221,3398 models [2468]; 3221,3398 models [2491] (within one tile)

- [ ] **Guard** — `3224, 3399, 0` (region 12853), pose `Sitting`
      Examine: "He's on a break."
      Scenery this dataset places nearby: nothing within one tile; the nearest is 3221,3398 models [2468], 3 tiles away

- [ ] **Jofridr** — `3238, 3432, 0` (region 12853), pose `Sitting`
      Examine: "A Citizen of Gielinor."
      Scenery this dataset places nearby: nothing within three tiles

- [ ] **Morten** — `3239, 3432, 0` (region 12853), pose `Sitting`
      Examine: "A Citizen of Gielinor."
      Scenery this dataset places nearby: nothing within three tiles

- [ ] **Dofur** — `3246, 3407, 0` (region 12853), pose `DwarfSit`
      Examine: "A dwarf."
      Scenery this dataset places nearby: nothing within one tile; the nearest is 3248,3404 models [2408], 3 tiles away

- [ ] **Simon** — `3250, 3408, 0` (region 12853), pose `DwarfSit`
      Examine: "A dwarf."
      Scenery this dataset places nearby: nothing within three tiles

- [ ] **Kors Aertsen** — `3214, 3504, 0` (region 12854), pose `Sitting`
      Examine: "Visiting Varrock for a few weeks, for business."
      Scenery this dataset places nearby: nothing within three tiles

- [ ] **Saradomin wizard** — `3256, 3479, 0` (region 12854), pose `ChurchSitting`
      Examine: "He looks familiar."
      Scenery this dataset places nearby: nothing within three tiles

## What "pass" means

A figure passes if there is a seat under it and it reads as sitting on that seat.
Three things fail, and they are different work items:

1. **Nothing under them.** They squat in mid-air. Fix: either move the tile onto a
   real seat, or change the pose to something standing. Prefer moving — the author
   put them there for a reason, and the reason is usually a conversation group.
2. **Something under them, but they float above it or sink into it.** The seat is
   the wrong height. The record's `translate` field is the lever (several already
   carry one, e.g. `[0.0, 0.0, -1.0]`).
3. **Something under them, but facing the wrong way.** `baseOrientation` is the
   lever: `0` = south, `512` = west, `1024` = north, `1536` = east.

Twenty-four of the twenty-nine are on `Sitting`. The other five are seated too, on
different animations, and want a slightly different eye. Each one's "scenery nearby"
line is above, on its own entry, and is not repeated here:

- **Dofur** and **Simon** are `DwarfSit`, which is
  `AnimationID.DWARF_READY_SITTING_AND_DRINKING` — a dwarf seated with a tankard.
  Same question, dwarf-height seat.
- **Nightfire** is `CatSit`, `AnimationID.CAT_ON_STOOL_READY`. That pose wants a stool
  specifically, and it is the one on this list to look at first: read its entry above
  for what the dataset puts near it, and if the game has nothing there either then a
  cat sitting on a stool with no stool is the most conspicuous way any of these can
  fail.
- **The Saradomin wizard** is `ChurchSitting`, `AnimationID.ROMEO_JULIET_PEW_READY`
  — a pew, not a chair. He is inside the Varrock church, so the pews are real; the
  question is whether he is on one or between two.
- **The goblin in 12697** is `GoblinPull`, gameval `_100_GOB_SIT`. It is on this list
  because it is a sit; getting to it is a trip rather than a teleport, which is why it
  has its own section above rather than sitting under Varrock.

Two more figures are *leaning* rather than sitting, and they have exactly the same
problem in a different shape — a lean with no wall behind it reads as falling
over. They are on the same walk and in the same Ground Markers block, which is why
that block has thirty-one entries and not twenty-nine.

## Leaning — 2

- [ ] **Kaldrik** — `3214, 3400, 0` (region 12853), pose `DwarfLean`
      Examine: "A dwarf, possibly waiting for someone?"
      Scenery this dataset places nearby: nothing within three tiles

- [ ] **MrCream** — `3163, 3493, 0` (region 12598), pose `HumanLeanReady`
      Examine: "A cameo — one of the plugin author's friends, drawn as the best-dressed
      man at the Exchange. A likeness, not a player."
      Scenery this dataset places nearby: nothing within three tiles
      He is a cameo, so tick **Friend cameos** on first, and note that the cameo tiles
      have never been walked either.

## One more to decide, while you are in Lumbridge

**Grimefang** in 12594 (`3185, 3254, 0`) is posed `GoblinChill`, gameval
`QIP_OBSERVATORY_HUCK_FIN_GOBLIN_READY_TIMER` (6837) — 24 frames, 152 client ticks,
goblin framemap 1415, which is the right skeleton for his models. What cannot be
settled offline is whether it is a *sit*: no NPC in the cache installs 6837 as its
`stand` or `walk`, so there is no posed example to read it off, and the name says
"Huck Finn" without saying whether the boy is on a riverbank or on his feet. If he
turns out to be sitting, he is a thirtieth entry on this list and wants the same
question asked of his tile. He is a short walk from the Prisoner and Sue, so he costs
nothing to look at on the Lumbridge leg.

## Ground markers

Copy this whole line, then Ground Markers → Import. Thirty-one tiles: the twenty-nine
seated figures and the two who lean.

```json
[{"regionId":10290,"regionX":37,"regionY":16,"z":0,"color":"#FFFFFF00","label":"Brother Keptic"},{"regionId":11317,"regionX":38,"regionY":43,"z":0,"color":"#FFFFFF00","label":"Joanne"},{"regionId":12338,"regionX":27,"regionY":60,"z":0,"color":"#FFFFFF00","label":"Sailor"},{"regionId":12342,"regionX":18,"regionY":40,"z":0,"color":"#FFFFFF00","label":"Andre"},{"regionId":12594,"regionX":46,"regionY":19,"z":0,"color":"#FFFFFF00","label":"Prisoner"},{"regionId":12594,"regionX":46,"regionY":20,"z":0,"color":"#FFFFFF00","label":"Sue"},{"regionId":12850,"regionX":28,"regionY":36,"z":0,"color":"#FFFFFF00","label":"Thalindra"},{"regionId":12850,"regionX":28,"regionY":38,"z":0,"color":"#FFFFFF00","label":"Dark wizard"},{"regionId":12850,"regionX":31,"regionY":37,"z":0,"color":"#FFFFFF00","label":"Limping Locke"},{"regionId":12697,"regionX":55,"regionY":33,"z":0,"color":"#FFFFFF00","label":"Goblin"},{"regionId":12852,"regionX":3,"regionY":59,"z":0,"color":"#FFFFFF00","label":"Nick"},{"regionId":12852,"regionX":4,"regionY":58,"z":0,"color":"#FFFFFF00","label":"Jo"},{"regionId":12852,"regionX":43,"regionY":53,"z":0,"color":"#FFFFFF00","label":"Charlie"},{"regionId":12853,"regionX":15,"regionY":15,"z":0,"color":"#FFFFFF00","label":"Afrah"},{"regionId":12853,"regionX":15,"regionY":27,"z":0,"color":"#FFFFFF00","label":"Nightfire"},{"regionId":12853,"regionX":16,"regionY":10,"z":0,"color":"#FFFFFF00","label":"Alexander"},{"regionId":12853,"regionX":17,"regionY":12,"z":0,"color":"#FFFFFF00","label":"Darren"},{"regionId":12853,"regionX":17,"regionY":16,"z":0,"color":"#FFFFFF00","label":"Ali"},{"regionId":12853,"regionX":18,"regionY":7,"z":0,"color":"#FFFFFF00","label":"H.A.M. Member"},{"regionId":12853,"regionX":18,"regionY":9,"z":0,"color":"#FFFFFF00","label":"Mysterious Old Man"},{"regionId":12853,"regionX":21,"regionY":5,"z":0,"color":"#FFFFFF00","label":"Demon Butler"},{"regionId":12853,"regionX":21,"regionY":7,"z":0,"color":"#FFFFFF00","label":"Butler Jarvis"},{"regionId":12853,"regionX":24,"regionY":7,"z":0,"color":"#FFFFFF00","label":"Guard"},{"regionId":12853,"regionX":38,"regionY":40,"z":0,"color":"#FFFFFF00","label":"Jofridr"},{"regionId":12853,"regionX":39,"regionY":40,"z":0,"color":"#FFFFFF00","label":"Morten"},{"regionId":12853,"regionX":46,"regionY":15,"z":0,"color":"#FFFFFF00","label":"Dofur"},{"regionId":12853,"regionX":50,"regionY":16,"z":0,"color":"#FFFFFF00","label":"Simon"},{"regionId":12854,"regionX":14,"regionY":48,"z":0,"color":"#FFFFFF00","label":"Kors Aertsen"},{"regionId":12854,"regionX":56,"regionY":23,"z":0,"color":"#FFFFFF00","label":"Saradomin wizard"},{"regionId":12853,"regionX":14,"regionY":8,"z":0,"color":"#FFFFFF00","label":"Kaldrik"},{"regionId":12598,"regionX":27,"regionY":37,"z":0,"color":"#FFFFFF00","label":"MrCream"}]
```
