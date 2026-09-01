# The Plugin Hub: shipping and updating

**Lively Cities is live.** Merged into `runelite/plugin-hub` on 2026-08-29 as
[PR #15685](https://github.com/runelite/plugin-hub/pull/15685), automatically, with both
checks green and no maintainer changes requested. Listing:
<https://runelite.net/plugin-hub/show/lively-cities>.

```
plugins/lively-cities  @  runelite/plugin-hub@master
repository=https://github.com/MatthewMariner/lively-cities.git
commit=76f14938836e9e9cdfcefa81884a4d3d625c6f0f
authors=MatthewMariner
```

**This document is now the update runbook, and that is the job that matters.** Filing once
was the easy part. The hub rebuilds every plugin on its roughly weekly client bump, and a
plugin that stops compiling keeps serving its last-good jar with a failure timestamp — so
nothing on your end turns red. You fix the bug, commit, push, and the hub is still serving
the old jar, because the manifest still names the old SHA.

That gap is not hypothetical. It is exactly how this plugin's predecessor stayed dead for
fourteen months with a working fix sitting in its repository: someone fixed the cache ids and
nobody ever bumped `commit=`. **An update is a new PR against `plugins/lively-cities` changing
one line.** Everything below is how to be confident before you send it.

*(The Toolchain app's OSRS page watches this for you — it compares the `commit=` on hub master
against your pushed HEAD and shows the plugin as **outdated** the moment they diverge.)*

---

## Before you file an update

| Check | Command | Expected |
|---|---|---|
| Tests | `./gradlew clean test` | all green (506) |
| Every new placement walked in game | `docs/CITY-TOP-UP-CHECK.md` · `docs/CITY-LIVERY-CHECK.md` | 57 + 151 markers imported, all 33 + 127 boxes ticked — **still outstanding** |
| Offline dataset audit | *(part of the above)* | green |
| No filesystem API in `src/main` | *(part of the above — `ShippedSourceTest`)* | green; see [below](#no-filesystem-writes-in-the-shipped-jar) |
| Cache ids still resolve | `./run-windows.sh --audit` | no failing ids outside the known-permanent-null section |
| Frame cost still inside its thresholds | `./run-windows.sh --timings`, then play for a few minutes | measured 2026-08-29 at 184 entities: per-frame p99 **8µs**, per-tick p99 **5.50ms**. The dataset is now 311 entities after the 2026-09-01 livery pass — **re-measure outstanding, needs a live client** |
| Hub file-level preflight | `yarn workspace @toolchain/server osrs:preflight ~/Workspaces/osrs/lively-cities` | `Result: PASS` |
| Compiles under the hub's own build | see [Verifying the hub build](#verifying-the-hub-build) | `BUILD SUCCESSFUL` |
| Screenshots in the README | — | *deliberately deferred (2026-08-24) — the page ships with placeholders* |
| Jagex third-party client guidelines | read them in a browser | unchanged from your last read |

That last row is not ceremony. The guidelines page was revised once without changing its
visible dateline, so "I read it months ago" is not the same as having read it. Do it per
submission, updates included.

**Use `run-windows.sh`, not the Gradle tasks, on a WSL machine.** The two rows above name it
deliberately. `./gradlew auditCacheIds` and `./gradlew runWithTimings` do the same work, but
they launch a *Linux-side* client whose `user.home` is `~`, so they read
`~/.runelite/credentials.properties` — a different file from the one the Jagex Launcher
writes at `C:\Users\<you>\.runelite\`. The client then logs in as whatever stale character
that WSL copy names, and no amount of relaunching in the launcher changes it, because nothing
carries the Windows file across the boundary. RuneLite refreshes the stale token at startup,
so the file even looks freshly written. `run-windows.sh` copies the Windows credentials in on
every run; that is why it gets the character you picked.

### Verifying the hub build

Under `build=standard` the hub **discards this repo's `build.gradle` entirely** and substitutes
its own — client, lombok, jetbrains-annotations, nothing else. So a dependency that works
locally can fail there. Simulate it exactly:

```bash
S=$(mktemp -d)
git archive HEAD src/main | tar -x -C "$S"
curl -so "$S/build.gradle" \
  https://raw.githubusercontent.com/runelite/plugin-hub-tooling/master/package/src/main/resources/net/runelite/pluginhub/packager/standard-build.gradle
sed -i 's|compileOnly "net.runelite:client"|compileOnly "net.runelite:client:1.12.37"|' "$S/build.gradle"
echo "rootProject.name = 'lively-cities'" > "$S/settings.gradle"
cp -r gradle gradlew "$S/"
( cd "$S" && ./gradlew compileJava )
```

Last verified **2026-08-29 against the released commit: BUILD SUCCESSFUL, 48 classes**. Our source uses Gson and Guice,
which look like third-party dependencies but arrive transitively through the client — worth
re-proving rather than assuming, so **re-run this after any new import.**

The 63 → 67 → 66 → 63 → 48 accounting, since a class count that cannot be explained is not
evidence of anything:

- **63 → 67.** `NpcAppearance` arrived with the cameos; `FrameTimings` and its two nested
  types arrived with the stopwatch. (`ReportWriter` was a rename of
  `CacheAuditReportWriter`, so it was never a new class.)
- **67 → 66.** `ReportWriter` left `src/main` for the test source set.
- **66 → 63.** `CacheIdAudit` and its two nested types followed it. Nothing in the shipped
  jar called them once the reporting moved, and a cache-walker with no reachable caller is
  weight the hub builds, serves and reviews for nobody.
- **63 → 48.** The nine-city cut. `City` is an enum whose every constant has a body — each
  one overrides `enabledIn` — so javac emits one anonymous subclass per constant: `City$1`
  … `City$24`. Removing fifteen constants removed fifteen classes, and nothing else. The
  compiled output confirms it: exactly nine `City$N.class` files where there were
  twenty-four, and every other class name in the jar unchanged.

Landing back on 63 before that was a coincidence worth stating rather than a target: the jar
was the size it was before any of the developer tooling existed, and it contained none of it.
48 is not a coincidence — it is 63 minus the fifteen checkboxes that no longer exist. See
[No filesystem writes in the shipped jar](#no-filesystem-writes-in-the-shipped-jar).

Note the recipe above uses `git archive HEAD`, which silently omits uncommitted changes — if
you are verifying work in progress, copy `src/main` from the working tree instead, or the
build you prove is not the build you are filing.

---

## No filesystem writes in the shipped jar

**What changed (2026-08-24).** Everything that wrote a file moved out of `src/main` and into
`src/test/java`. `ReportWriter` moved verbatim — same `.part` draft, same `ATOMIC_MOVE` with a
plain-replace fallback — and the two things that called it are now a second RuneLite plugin,
`LivelyCitiesDevReportsPlugin`, which lives in the test source set. `src/main` keeps the
measuring and the auditing: `FrameTimings` still owns the histograms and the cadence,
`CacheIdAudit` still owns the cache walk, and both still produce the same plain text. They
just hand out a `String` and stop there.

Nothing about the tooling changed. `./gradlew runWithTimings` and `./gradlew auditCacheIds`
both already ran on `sourceSets.test.runtimeClasspath`, so the reporter is on the classpath of
exactly those launches and of nothing a hub user can start. Both still produce their reports in
`~/.runelite/lively-cities/`.

**Why.** riktenx, reviewing [plugin-hub#12366](https://github.com/runelite/plugin-hub/pull/12366):

> file i/o will make your plugin require manually review. if you can not use it your plugin can
> be automatically reviewed.

and, on [plugin-hub#13208](https://github.com/runelite/plugin-hub/pull/13208), the shape to use
instead:

> you can either add a separate debug plugin in the test source set (which won't ship with your
> plugin and won't get looked at but you can use it during development) or just remove it

Both diagnostics are gated behind `--developer-mode` **and** a JVM system property, so no hub
user could ever reach them. The jar was paying manual-review latency, and a smaller pool of
reviewers, for capabilities its users cannot invoke.

**What we can and cannot claim.** The claim is narrow and literal: **no class in `src/main`
names a filesystem API** — no `java.io.File`, no `java.nio.file.*`, no `FileWriter` or
`FileOutputStream`, nothing that opens a path. `ShippedSourceTest` scans every shipped source
file and fails the build if one reappears, so it stays true rather than having been true once.

It is **not** a claim that the submission will pass automated review. The reviewer's rule set is
private — riktenx again, on 12366: *"i cannot share that code"* — so whether it treats a
classpath `InputStream` as file I/O cannot be checked from outside this repo. `RegionDataLoader`
still imports `java.io` and still calls `getClassLoader().getResourceAsStream(...)`, because a
plugin that ships a dataset has to read it and there is no other way to; that pattern is close to
universal on the hub. If the reviewer flags it, the fallback is the same as it always was: a
maintainer reads the diff. An unverifiable claim about clearing an automated gate would be the
predecessor's unmeasured-performance mistake in new clothes.

**Do not put this in the PR body as a selling point.** It is a property of the code, and the
build either proves it or does not.

---

## The manifest

One file, no extension, at `plugins/lively-cities` in a fork of
[`runelite/plugin-hub`](https://github.com/runelite/plugin-hub). Filename must match
`^[a-z0-9-]+$`.

```
repository=https://github.com/MatthewMariner/lively-cities.git
commit=<the 40-hex sha you are shipping — 76f1493… as of the first release>
authors=MatthewMariner
```

- `commit` is a plain SHA. Tags are not a thing here.
- Only `repository`, `commit`, `authors`, `jarSizeLimitMiB`, `warning`, `disabled` and
  `unavailable` are accepted keys — **an unknown key is a hard build failure.**
- **No `warning=` line is needed.** That policy covers plugins that communicate with
  third-party servers; this one makes no network calls at all. (Had we shipped the
  live-query design considered for a different plugin, it would have needed one *and* would
  have shipped disabled by default.)

## Sending an update

The fork already exists. An update is one line changing in one file.

```bash
cd /tmp && rm -rf plugin-hub
gh repo fork runelite/plugin-hub --clone --remote
cd plugin-hub
git checkout -B lively-cities-update upstream/master        # always branch off fresh master
printf 'repository=https://github.com/MatthewMariner/lively-cities.git\ncommit=%s\nauthors=MatthewMariner\n' \
  "$(git -C ~/Workspaces/osrs/lively-cities rev-parse HEAD)" > plugins/lively-cities
git diff --stat                                             # expect: 1 file, 1 insertion, 1 deletion
git commit -am "Update Lively Cities"
git push -f -u origin lively-cities-update
gh pr create --repo runelite/plugin-hub --base master --fill
```

PR targets **`master`**. Two things that are easy to get wrong:

- **Branch off `upstream/master`, not your fork's stale one.** Your fork does not follow the
  hub, and a branch cut from a months-old copy carries every unrelated change since.
- **Push the plugin repo first.** The `commit=` line names a SHA the hub will clone; if it is
  only on your machine the build fails with something unhelpful. `git -C … status` should be
  clean and `origin/main..HEAD` empty before you run the above.

The body can be short for an update — what changed and why. The long-form pitch below was for
the first submission and is kept as a record of what was claimed.

### What the first submission looked like

Filed 2026-08-29 as [#15685](https://github.com/runelite/plugin-hub/pull/15685) and merged
about half an hour later by `runelite-github-app`, with `build: SUCCESS` and
`RuneLite Plugin Hub Checks: SUCCESS`. **No maintainer asked for anything**, and the only
comments were the two bots. That is the automatic lane, and the plugin qualified for it
because the shipped jar does no file I/O — see
[No filesystem writes in the shipped jar](#no-filesystem-writes-in-the-shipped-jar), which is
the single change that bought it.

## The PR body

The middle paragraph is the part that matters. The phrasing is the recognised safe pattern and
appears verbatim in merged hub PRs — it answers the reviewer's actual question before they ask.

> **Lively Cities** — cosmetic townsfolk that make cities feel populated. A successor to the
> abandoned [Citizens](https://github.com/gc/citizens) plugin, shipping its BSD-2 licensed
> placement dataset with the upstream notice retained (see `NOTICE` for exactly what is
> derived).
>
> **It is purely cosmetic and local: it uses RuneLiteObjects only, adds RUNELITE-type menu
> entries only, and sends nothing to the server.** It does not generate input, does not
> interact with real NPCs, objects or scene tiles, and — unlike its predecessor — never
> removes anything from the loaded scene. The `removedObject` field the vendored dataset
> carries is parsed and deliberately discarded.
>
> Distinct from the "Dynamic Puro-Puro Spawns" rejection: nothing here reveals game state.
> Every figure is static authored content shipped in the jar.
>
> On the failure that got Citizens disabled — an OSRS update renumbering model cache ids, and
> a player reporting a figure with no legs — two things are different here. Partial model
> builds never render, so a half-loaded figure cannot appear; and `./gradlew auditCacheIds`
> walks every cache id the dataset depends on and reports what no longer resolves, so the
> next renumbering is a diffable text file rather than a support thread. There is a runbook in
> the README.
>
> **That paragraph was only ever half the answer, and the missing half shipped.** Both
> defences are about a figure that *fails to build*. A figure whose record never named a
> trouser in the first place builds perfectly and still has no legs, and 47 of the 98
> kit-built human citizens were in exactly that state at launch — the largest single cause
> being a hood model pasted where legs and boots belong on twenty of them. Repaired on
> 2026-08-30 (`NOTICE` item 9), and `BodySlotLintTest` is now the third defence: it asks the
> dataset itself whether every human figure has geometry at the shin, at the hand and on the
> floor.
>
> **The geometry half and the colour half are both closed now.** A trouser
> model painted the colour of a face looks the same in a screenshot as no trouser at all, and
> six citizens this project authored were doing exactly that — answering a `find` slot aimed
> at trousers, a tunic, hair, a boot or a collar with `4550`, the value the client
> substitutes for a player's face. They were repaletted on 2026-08-30 and a second rule in `BodySlotLintTest`
> now refuses it categorically.
>
> That rule was one value wide and the fault was a gamut wide. Playing at `Full` on
> 2026-08-30 — where no derived figure exists — still showed trouserless figures, because
> seventeen records painted the legs base a flesh-*class* tan and the nearest of them was one
> hue step and two lightness steps from the face colour. A third rule now refuses any
> flesh-gamut colour on a legs slot, using the plugin's own `CitizenEcho.isFlesh` rather than
> a second copy of it, and all seventeen were repainted — ten upstream's, disclosed as
> `NOTICE` item 11. It is deliberately not widened to the other garment slots: the hair and
> boots bases are inside the gamut themselves, so the rule there would refuse the game's own
> colours. Six upstream records paint the face colour onto arm, hand and head geometry, which
> is where skin belongs; they are counted and named by a test so the figure cannot grow in
> silence.
>
> The same fault existed in the derivation and shipped: two of the 51 echoes at `Crowded`
> wore the face colour on a garment, one of them on the legs. The re-deal rule compared
> flesh-*classes*, so a dark leather brown swapping places with the face colour looked
> class-preserving. The face colour is now its own class and cannot be moved off the slot the
> author put it on. The derived half of the trousers rule follows from the authored half
> rather than being enforced separately — a surviving deal keeps every colour on its own side
> of the flesh boundary, so no echo can wear a complexion on its legs while no authored record
> does — and `CitizenEchoTest` asserts it over both populations rather than reasoning about it.
>
> Fake-vs-real legibility was treated as the licence to exist rather than polish: menu entries
> are always deprioritised, no clickbox is generated while an item or spell is on the cursor,
> the menu target uses a colour the game never uses for a real one, and Examine says what the
> figure is. Overhead chatter ships with a global off switch, a per-citizen mute, and
> configurable cadence — the predecessor's most-complained-about behaviour, whose promised
> toggle never arrived.
>
> On frame cost: the clickbox hull is computed in `MenuOpened` rather than per tick or per
> frame, so the only per-frame work is interpolating walking figures between tiles. That is
> measured rather than asserted, with the acceptance thresholds written down before any
> number existed. Over 19,000 frames and 300 game ticks of ordinary play in Varrock, at up
> to the 80-object cap: **the per-frame pass is 0µs median and 8µs at p99, worst frame 95µs**
> — about half a percent of a 60fps frame. Per game tick the work splits three ways, because
> a steady-state tick and the tick you cross a region boundary on are different events:
> deciding who is on screen is **151µs median, 5.50ms p99**; the models a tick builds are
> capped so a crossing tick fits inside one frame; and the region load itself is the
> expensive part, at roughly 3ms. `./gradlew runWithTimings` reproduces all of it, with the
> active-object count beside every figure.
>
> One number is worth flagging rather than burying: the per-tick p99 of 5.50ms sits between
> this project's own "acceptable" line (2ms) and its "a problem" line (8ms). The first
> measurement failed outright at ≥11ms p99 and a 53.73ms worst tick; splitting the meters
> showed that spike was a region load being averaged in with ordinary ticks rather than the
> model-building burst it was assumed to be. The README carries the full before-and-after.

**The frame-cost figures above are as submitted, and are now stale by entity count.**
They were measured 2026-08-29 at 184 entities; the dataset has since grown to 311 after
the 2026-09-01 livery pass. The table in [Before you file an
update](#before-you-file-an-update) carries the re-measure as outstanding rather than
guessing a new number — that needs a live client, which this document cannot supply.

## After you send one

Two status checks, read **separately**:

1. **The Actions build** — compiles and packages. Strict mode is on for PR builds.
2. **The external "RuneLite Plugin Hub Checks" bot** — act only if it literally says
   *"Changes are needed"*.

A stale-PR bot closes "waiting for author" PRs after 7 days, so watch it. The first
submission was merged in about half an hour with both checks green; that is an observation,
not an SLA.

**Do not let the manifest go stale.** A fixed plugin whose `commit=` was never bumped is why
Citizens stayed dead for fourteen months after it worked again — and it is invisible from
this side, because the hub keeps serving the last-good jar rather than showing an error. The
Toolchain OSRS page exists to make that visible: it compares hub master's `commit=` against
your pushed HEAD and marks the plugin **outdated** the moment they diverge.

## Known at release, and deliberately shipped

Neither blocks anything; both are disclosed in the README rather than hidden. They are the
first candidates for an update.

- **A hundred and sixty placements have never been walked** — 33 from 2026-08-29 and 127 from 2026-09-01. `docs/CITY-TOP-UP-CHECK.md` is the
  walk — 57 Ground Markers, five stops, one circuit — plus the livery pass's own 151 markers over nine stops in `docs/CITY-LIVERY-CHECK.md`. Nothing offline can tell whether a tile
  is walkable, so a figure could be standing in a wall. Riskiest: **Adela**, **Sybilla**,
  **Thurstan** and **Gervase**, the Saradominist group inside Falador's church, 24 to 27
  tiles from the nearest proven tile — and the thirteen with no wander box beneath them.
- **Two re-kitted figures nobody has seen.** *Anselm* and *Brother Edwy* in the Ardougne
  monastery were given different bodies late; the ids are verified against the cache but how
  they read is not.
- ~~**The East bank workman** is missing leggings, and at `Crowded` his derived twin is
  examined as an anonymous "Passer-by" while dressed in the same uniform.~~ Fixed on
  2026-08-30, and he was not alone: 47 of the 98 kit-built human citizens were shipping
  without legs, hands or footwear. He is "City workman" (13109), and his leggings had been
  replaced by an Elder Chaos druid hood — the same paste that had cost twenty citizens a
  body part. `BodySlotLintTest` holds it now. The `npcAppearanceId` route suggested here was
  *not* taken: it would have moved the pinned distinct-model-id count and made him
  ineligible to seed. See `NOTICE` item 9.
