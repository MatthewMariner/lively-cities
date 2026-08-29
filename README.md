<div align="center">

# Lively Cities

**Gielinor's cities are empty. This fills them.**

Cosmetic townsfolk who stand, sit, work and wander through the streets of Old School
RuneScape — client-side, purely visual, and gone the moment you switch it off.

[![RuneLite](https://img.shields.io/badge/RuneLite-1.12.36-blue)](https://runelite.net)
[![Java](https://img.shields.io/badge/Java-11-orange)](https://runelite.net)
[![License](https://img.shields.io/badge/license-BSD--2--Clause-green)](LICENSE)
[![Tests](https://img.shields.io/badge/tests-460-brightgreen)](#development)

</div>

> [!TIP]
> **On the Plugin Hub.** Install it from inside RuneLite — the wrench icon, then **Plugin
> Hub**, then search for *Lively Cities*. There is also a
> [listing page](https://runelite.net/plugin-hub/show/lively-cities). To build it yourself
> instead, see [Development](#development).

<!-- SCREENSHOT: hero — Varrock square at Crowded density, mid-afternoon, no interface panels
     open. Wide, 16:9, showing several citizens at different distances. Save as
     docs/img/hero.png and replace this comment with:  ![Varrock square](docs/img/hero.png) -->

---

## What it does

Varrock square has a handful of guards and a general store. Falador's streets are bare.
Lumbridge is a castle with nobody in it. The world is beautifully built and almost entirely
unpopulated, and once you notice it you cannot stop noticing it.

Lively Cities adds **184 hand-placed figures** across **27 regions** — a fletching apprentice
working at her bench, a drunken peasant near the tavern, two thieves sitting on a wall in
Varrock, a squirrel, a rat, someone cooking over a fire. Some stand, some sit, some walk a
route. They talk occasionally. They are entirely local to your client: no packets, no server
load, and **nothing another player can see**.

| | |
|---|---|
| **184 entities** | 142 citizens + 42 pieces of scenery |
| **51 wander**, 91 stand still | 5 of the 91 are `ScriptedCitizen` records whose script nothing runs — [see below](#known-limitations) |
| **9 places** | Varrock (97), Lumbridge (21), Al Kharid (10), Ardougne (10), Catherby (10), Falador (10), the Grand Exchange (10), Draynor (11), Edgeville (5) |
| **326 at Crowded** | an optional density that roughly doubles the streets |

<!-- SCREENSHOT: a close-up of two or three citizens with distinct appearances, ideally one
     mid-walk. Save as docs/img/citizens.png and replace with:
     ![Citizens](docs/img/citizens.png) -->

---

## Settings

Everything is a dial, because the thing this plugin's predecessor got most complained about
was having none.

<!-- SCREENSHOT: the RuneLite config panel for Lively Cities, Cities section expanded so the
     checkboxes are visible. Save as docs/img/config.png and replace with:
     ![Settings](docs/img/config.png) -->

**Crowd density** — `Sparse` · `Normal` · `Full` · `Crowded`. Thinning is deterministic: the
same people are always the ones kept, so a street looks the same every time you walk down it
rather than reshuffling every login. `Crowded` goes the other way and adds derived extras.

**Render distance** — 5 to 30 tiles, default 25. This is the dial that actually changes what
you see. Above about 16 tiles distant figures will sometimes pop in as the client recentres
its scene; that is a limit of how much world the game keeps loaded, not a bug, and the setting
says so.

**Overhead text** — a hard off switch, plus how often anyone speaks and how long a line
stays up. You can also mute one individual by right-clicking them.

**9 city checkboxes** — turn any place off and its citizens vanish on the click, not on the
next region crossing.

**Friend cameos** — off by default, and staying that way. Six named, human-looking figures
posing as a group on the north-west side of the Grand Exchange: caricatures of the author's
friends, dressed as a wizard, a sailor, a Shayzien soldier, a rogue, a butler and a White
Knight. Everything else in this plugin is a townsperson; a cluster of named humans at the
busiest bank in the game is the one thing here that could be mistaken for real players, so nobody
sees them unless they tick the box. They obey the Grand Exchange checkbox too, Examine says
outright that each is a likeness and not a player, and any of them can be hidden or muted
individually like any other citizen.

---

## It stays out of your way

This is the part that matters more than the citizens, and it is deliberate.

- **They never steal a click.** Every menu entry is deprioritised, so anything real sorts
  above them. Hold an item or a spell on your cursor and citizens produce no clickbox at
  all — you cannot misclick one while using an item on something.
- **Fake is obvious.** Their right-click target is a colour the game never uses for a real
  one, and Examine says outright what they are.
- **Nothing reaches the server.** No packets, no input generation, no interaction with real
  NPCs, objects or scene tiles. That is enforced structurally in the tests, not just
  asserted: the test client throws on every real-action call, so any code path reaching for
  one fails the build.
- **Don't like someone?** Right-click → **Hide**. It persists. "Unhide all" brings them back.
- **It measures its own cost rather than promising there isn't one.** The clickbox — the
  expensive part — is computed when you right-click and never per frame, so the only
  per-frame work is sliding walking figures between tiles.
  Measured over 19,000 frames of ordinary play: **8µs at the 99th percentile**, about
  half a percent of one frame. `./gradlew runWithTimings` reproduces it.

<!-- SCREENSHOT: right-click menu on a citizen showing Examine / Hide / Mute below the real
     options, with the coloured target text visible. Save as docs/img/menu.png and replace
     with:  ![Right-click menu](docs/img/menu.png) -->

---

## Credits, and why this exists

Lively Cities is a successor to **[Citizens](https://github.com/gc/citizens)** by Magnaboy,
skeldoor, jebscape and Diabolickal — BSD-2 licensed, and genuinely good. It reached ~7,700
installs and a Jagex moderator publicly called the idea great.

Then an August 2024 game update renumbered player-model cache ids. Its citizens broke
visually, someone posted *"Who is this man? Why does he not have any legs?"*, the hub disabled
it, and although a contributor fixed the ids fourteen months later the listing was never
revived. The demand outlived the maintenance by well over a year.

**This plugin ships their placement dataset**, under their licence and with their notice
retained — that data is hundreds of hours of walking around Gielinor deciding where a person
should stand, and throwing it away would have been vandalism. See [NOTICE](NOTICE) for exactly
what is derived: the dataset, the animation-name table, the model lighting constants, and the
eight modifications we have made to their data.

What is new is everything that stops it dying the same way:

- **`./gradlew auditCacheIds`** walks every cache id the dataset depends on and reports what
  no longer resolves. The failure that killed Citizens is now a diffable text file.
- **Partial models never render.** If some of a figure's parts fail to load, it does not
  spawn half-built — that is what "no legs" actually was.
- **New figures are dressed by NPC id, not by raw model id.** A record can carry
  `npcAppearanceId` and clone an existing NPC's models and colours instead of listing cache
  numbers nobody can look up. That is one indirection further from the geometry an artist
  reworks, it is a named constant in `gameval.NpcID`, and the audit above covers it — which
  is the whole reason to prefer it. The vendored figures keep their `modelIds`; the one
  exception is Rufus, who had no boots in his (see below).
- **New figures introduce no new cache ids at all.** The 33 citizens added to the thin
  cities on 2026-08-29 each wear a `modelIds` array copied whole out of a record already
  in the dataset, with that record's own recolour palette re-dealt so the copy is not its
  donor twice. Distinct model ids: 324 before, 324 after. A new number here is a new thing
  that can break on a game update, and this pass added none — see [NOTICE](NOTICE) item 8.
- **A placement lint** checks each figure's theme against the region it stands in. It caught
  six citizens impersonating the Barrows Brothers above their own crypts; they were renamed
  to anonymous barrow wights, and the Barrows has since left the dataset entirely.
- **460 tests**, and every guard has been broken on purpose and watched fail. A test nobody
  has seen fail is a hypothesis.

---

## Known limitations

Stated plainly, because you will find them anyway.

- **The dataset is still lopsided, and it is nine places rather than twenty-four.**
  142 citizens for the whole game, 40 of them in a single Varrock region and 63 in Varrock
  altogether. Varrock is the flagship and everywhere else is a town rather than a city.

  The five thinnest survivors were brought up to ten citizens each on 2026-08-29 — Al
  Kharid, Catherby, Falador, Ardougne and Draynor had three or four figures apiece before
  that, which reads as a quiet town rather than a populated one. **None of those 33 tiles
  has been walked in game.** Nothing offline can tell you whether a tile is walkable, so
  each new figure was placed within a few tiles of a placement the predecessor's authors
  made, or inside a wander box they drew; both are inferences, and a figure standing in a
  wall is the way they fail. Every one is listed with its tile in
  [docs/CITY-TOP-UP-CHECK.md](docs/CITY-TOP-UP-CHECK.md), with a Ground Markers import
  block, and that walk is the next thing this plugin needs.

  Even topped up, three of those five are populated somewhere other than where a player
  would look for them. This plugin ships no region file for East Ardougne's market square,
  so Ardougne's seven are at the monastery, the farm and the Legends' Guild path. Falador's
  four new park figures all stand inside Sir Wendes' authored wander box, which is the only
  vouched-for ground anywhere in region 11828 — so Falador is a park scene rather than a
  street scene. Draynor's eight are in two groups: six on the ground north of Ned's house
  and east toward the manor gate, and two in the manor grounds (region 12340) beside the
  Ghost's box. That first group is the thinnest evidence in the whole pass rather than
  vouched-for ground — region 12338 shipped exactly two entities before this, so six
  figures hang off two proven tiles, and `docs/CITY-TOP-UP-CHECK.md` says which two.

  Fifteen thinner places were removed outright on 2026-08-24 rather than shipped as they
  were: thirteen of the original twenty-four held one or two figures, and ticking "Canifis"
  to find one person there reads as a broken plugin, not as a sparse one. Those were
  Barrows, Piscatoris, the Ranging Guild, Camelot, Taverley, Castle Wars, the Farming Guild,
  the Lumber Yard, the Motherlode Mine, Otto's Grotto, Trollheim, Paterdomus, Canifis, Musa
  Point and Rimmington. Earning those places back with enough content to deserve a checkbox
  is content authoring, and it is the main work left.
- **Five figures are typed `ScriptedCitizen` and no script runs.** The vendored format has
  a `startScript` field naming a behaviour — `edgevilleSmith`, `gardenerScript`,
  `apothecaryScript`, `lumbridgeGardenerScript`, `testScript` — and this plugin parses it and
  executes nothing. There is no script engine here and none is planned. The five records
  (Eugene in Edgeville, the Assistant Apothecary and the Gardener in Varrock, Mike in
  Lumbridge, Emme in Varrock) therefore stand exactly where they were placed, playing their
  idle animation, indistinguishable in behaviour from the 86 `StationaryCitizen`s. The field
  is carried through the loader rather than dropped so that the shipped files stay a faithful
  copy of the format they came from — see `EntityRecord`, which says the same thing at the
  field. Anything a reader might think those five do, they do not.
- **A figure whose record was short a model is re-dressed rather than patched.** Rufus in
  Varrock square had no footwear model in his twelve — the record simply never carried one.
  Guessing which raw id to add could have given him a hat for feet, so he now wears
  `NpcID.FARMER1` (the game's own generic "Farmer") through `npcAppearanceId` instead. That
  replaces his whole appearance rather than patching it, so the twelve hand-picked ids and
  their six recolour pairs are gone; he reads as an ordinary farming trader now, complete,
  rather than as a distinctive barefoot one. He also stops seeding `Crowded` extras, because
  an NPC-dressed citizen has no record palette to re-deal — two ambient bodies fewer at
  that density than he would otherwise have contributed.
- **Twenty-nine figures sit down and nobody has checked what they sit on.** `Sitting`
  played on a tile with no bench under it renders as squatting in mid-air, and whether
  a seat is there is a question only a live client can answer — the dataset stores a
  tile, not what is on it. Their **poses** are deliberately left exactly as the
  predecessor authored them, because most were placed by someone standing in the room and
  a citizen genuinely sitting on a pub bench is good content. (Five of them — Thalindra,
  the Dark wizard, Nightfire, Dofur and Simon — did have their *walk* corrected, for the
  skeleton reason in [NOTICE](NOTICE); none of them moves, and none of them changed
  position or pose.) Every one is listed with its tile, pose and examine text in
  [docs/SEATING-CHECK.md](docs/SEATING-CHECK.md), together with two more who *lean*
  rather than sit and have the same problem in a different shape — thirty-one figures on
  one walk, with a Ground Markers import block covering all of them so the walk is a walk
  rather than a search.
- **One cameo's costume is an approximation, and it is the one wearing armour.** Peter
  wears `NpcID.CORSCURS_LORD_MARSHAL` — Lord Marshal Brogan's full Shayzien plate —
  because it is imposing with no bare skin anywhere in it. It is not a portrait: the
  brief was "a soldier", the constant is a named quest NPC, and nothing offline can ask
  a composition what it looks like. He replaced `NpcID.BARBARIAN`, which was picked for
  being the most muscular human body in the named constants and read on video as a naked
  man standing in the busiest bank in the game. If Brogan's plate turns out to read as
  "that specific quest NPC" rather than "a soldier", that is a costume change, not a bug.
- **Distant figures pop in** past ~16 tiles. See Render distance above.
- **Smoothing needs RuneLite's own Animation Smoothing plugin** turned on. With it off,
  nothing in the game interpolates — real NPCs included — so our figures look equally steppy.
- **The right-click guard has one layout it cannot see.** It suppresses citizen entries on
  interface clicks by looking for a "Walk here" option, and the minimap carries one of those
  too — so the minimap panel is excluded by its own on-screen rectangle instead, using
  RuneLite's own three component ids for it. The fourth layout, `TOPLEVEL_OSM`, has a
  different id and is not covered; RuneLite's own minimap-anchored overlays do not handle it
  either. On that layout a citizen projecting under the minimap can still be offered a
  (harmless, local) Examine.
- **Figures fill in over a few seconds rather than all at once**, and that is deliberate.
  Walking into a busy square asks the client to build a lot of models in one go, which used
  to cause a visible stutter, so the work is spread across ticks — nearest figures first.
  The densest corner of Varrock takes about five seconds to finish arriving from cold.
  The measurements behind that trade, and the thresholds they were judged against, are in
  `FrameTimings`' javadoc and in `docs/SUBMISSION.md`.
- **Crowded adds derived figures, not authored ones.** They are silent, they do not wander,
  and they wear their source's colours rearranged. They are ambience, not characters.
- **The cameo tiles have not been walked on.** The six were placed off the Grand Exchange's
  wiki map, not by standing there. To stop that becoming a figure inside a bank booth, a cameo
  is the one kind of authored figure whose tile has to pass the game's own collision map before
  it will render — so a bad tile means an absent cameo rather than a broken-looking one. If one
  of them never appears with the setting on, that is what happened; please report the tile.

## Found a bug?

Please open an issue — there are templates for [bug reports and feature
requests](../../issues/new/choose). The most useful bug report names **where you were
standing** and **what you expected instead**, since almost everything here is positional.

If it is a rendering problem, `~/.runelite/logs/client.log` filtered to `livelycities` is
usually decisive, and pasting it saves a round trip.

---

## Development

Built against RuneLite client **1.12.36**, targeting Java 11 bytecode. Requires a JDK ≥ 11;
the Gradle wrapper handles the rest.

```bash
./gradlew build            # compile and run the 460 tests
./gradlew run              # a dev client with the plugin loaded
./gradlew auditCacheIds    # dev client + walk every cache id (see below)
./gradlew runWithTimings   # dev client + measure our own frame cost (see below)

./run-windows.sh --audit    # the same walk, on a Windows-side client
./run-windows.sh --timings  # the same measurement, on a Windows-side client
```

**On WSL, prefer the `run-windows.sh` forms.** The Gradle tasks launch a Linux-side client
whose `user.home` is `~`, so they read `~/.runelite/credentials.properties` rather than the
one the Jagex Launcher writes under `C:\Users\<you>\`. The result is a client logged in as a
stale character that relaunching in the launcher cannot fix — and since RuneLite refreshes
that token on startup, the file looks current. `run-windows.sh` copies the Windows
credentials in every run, and is faster besides.

`run-windows.sh` builds in WSL and launches the client natively on Windows using RuneLite's
own bundled JRE — faster than WSLg, and no Windows JDK needed. It defaults to an isolated
`user.home` so your real profiles are never written to.

### Where the two reports are written from

Both `auditCacheIds` and `runWithTimings` write a file, and neither of them ships. The
*measuring* and the *auditing* are in `src/main` — counters, the cache walk, and the plain
text they produce. The *writing* is in `src/test/java`, in a second RuneLite plugin
(`LivelyCitiesDevReportsPlugin`) that only the dev tasks above ever load, because all three
run on `sourceSets.test.runtimeClasspath`. Nothing changes about the tooling; it simply is
not in the jar the Plugin Hub builds.

That split is there because the hub's automated reviewer will not look at a plugin that does
file I/O — see [docs/SUBMISSION.md](docs/SUBMISSION.md#no-filesystem-writes-in-the-shipped-jar)
for the maintainer's own words and the two PRs they come from. What can be checked, and is
checked by a test, is narrow and literal: **no class in `src/main` names a filesystem API.**
The dataset is still read with `getResourceAsStream` off the classpath, which is not a
filesystem access and which the plugin cannot ship without.

### After an OSRS update: checking the dataset still resolves

The predecessor to this plugin ("Citizens") died this way: an August 2024 OSRS
update renumbered player-model cache ids, most of its citizens broke visually,
the hub disabled the plugin over the resulting "who is this man with no legs"
confusion, and — although a contributor quietly fixed the ids over a year
later — the hub listing was never revived. The dataset here addresses game
content the same way theirs did: by raw numeric cache id (`modelIds`) and by
animation name resolved to a numeric id (`idleAnimation`/`moveAnimation` via
`LivelyAnimation`). **Run this after every OSRS update that could renumber
cache content** — new NPC/model/animation releases, and especially anything
described as reworking existing models.

There are two checks, because only one of them can run without a live client.

#### 1. The offline dataset audit — runs on every `./gradlew test`

No client needed. `ModelIdAuditTest`, `LivelyAnimationTest`, `CacheIdAuditTest`
and `RegionDataLoaderTest` already assert, over the shipped JSON alone:

- every `modelIds` entry is positive and not implausibly large (see
  `CacheIdPlausibility` for where the ceiling comes from)
- every entity has either a `modelIds` array or an `npcAppearanceId`, and never
  neither — and never both, which would leave a hand-typed model list as dead
  weight (the `npcAppearanceId` wins when a record carries both)
- every `npcAppearanceId` is inside the same plausible range, which bites
  harder there: `gameval.NpcID`'s highest constant in 1.12.36 is 16346
- the dataset's distinct-model-id count is pinned (currently 324) and its
  distinct-`npcAppearanceId` count is pinned (currently 7) — if either test
  fails after you *intentionally* changed the dataset, update the pinned
  number in `ModelIdAuditTest`; if you did not touch the dataset, something
  else changed it. (The crowd at `Crowded` is 326 — 142 authored citizens
  plus 184 derived ones. It briefly read 324 as well, and a note here called
  that a coincidence; it is not even that any more. The two are unrelated
  quantities and neither should ever be "corrected" to agree with the other:
  one is a property of the game cache and the other of the echo derivation)
- every animation name the dataset uses resolves in `LivelyAnimation`
- the whole dataset loads with zero skipped records

These catch authoring mistakes (a stray digit, an empty array, an unknown
animation name), but **they cannot tell you whether an id still resolves in
the current game cache** — that needs a live client, which the normal test
suite deliberately never has.

#### 2. The cache-backed validator — `./gradlew auditCacheIds`

This is the real check, and the one to run after a suspected renumbering:

```
./gradlew auditCacheIds
```

This launches the same dev client `./gradlew run` does, with one extra system
property set. On startup, `LivelyCitiesPlugin` walks every distinct model id,
merged-object id, `npcAppearanceId` and animation id the shipped dataset
references and asks the live client (`client.loadModelData(id)`,
`client.getNpcDefinition(id)` and `client.loadAnimation(id)`) whether each one
still resolves — these calls are the only real ground truth, which is why this
cannot be a unit test.

An `npcAppearanceId` is checked through `NpcAppearance.resolve`, i.e. the same
code path the renderer uses, so "the lookup worked but the composition has no
models" counts as a failure rather than as a green id in front of an invisible
citizen.

The client does not need to be logged into a world; the cache is loaded before
the login screen. Watch the client log for a summary line
(`Lively Cities cache id audit: N model id(s) checked (M failing), ...`), then
open the full report:

```
~/.runelite/lively-cities/model-id-audit.txt
```

The report is a small, diffable, sorted plain-text file — commit it (or just
compare it by eye against a previous run) to see exactly what changed. It has
five sections: failing model ids, failing merged-object ids, failing NPC
appearance ids, failing animation ids, and a **known-permanent-null** section
for animation ids that are
*expected* to fail — currently just `BeeIdle=0`, because
`client.loadAnimation(0)` returns null by design (no frame lengths, not a Maya
animation), not because of a broken cache entry. A real regression never shows
up in that section; if `BeeIdle=0` starts appearing under failing ids instead,
that would itself be worth a second look.

**What a failure looks like:** one or more ids listed under "failing" that are
not in the known-permanent-null section. That id no longer resolves in the
current cache — the exact failure mode that killed Citizens.

**What to do about it:**

1. Note every failing id and which entities used it (grep `RegionData/*.json`
   for the id).
2. Work out the replacement id — usually by finding the equivalent NPC/object
   in-game and checking what it uses now (the in-game examine/right-click
   tools, or a cache browser, are the fastest way; this plugin does not ship
   its own id-lookup tool).
3. Update the affected `modelIds`/animation names in the region JSON.
4. Re-run `./gradlew test` (the offline audit will catch anything now
   implausible) and `./gradlew auditCacheIds` again to confirm the report is
   clean.
5. Commit the fix with the region files and a note of which ids changed and
   why — this is exactly the historical record the predecessor's manifest
   never got updated to reflect.

Never ship a fix without re-running `auditCacheIds`: gc's own fix for Citizens
was self-described as comprehensive only "for the most part," which is why
that check exists at all rather than trusting a manual diff.

---

<div align="center">
<sub>Cosmetic and local. Sends nothing to the server. Not affiliated with Jagex.</sub>
</div>
