# Submitting to the Plugin Hub

Everything needed to file the submission, prepared in advance. **Deliberately not filed yet** —
the decision (2026-08-23) is to wait until the content pass is done, because a first impression
at 109 citizens is a first impression at 109 citizens.

The dataset was cut from 24 places to 9 on 2026-08-24 (181 entities to 151, 135 citizens to
109). That decision cuts the other way from the one above and is meant to: the wait is for
*density*, and shipping twelve one-figure towns was buying breadth that read as breakage. The
nine that remain are the ones worth topping up.

Nothing below requires new work. When the content lands, re-run the checks and file it.

---

## Before you file

| Check | Command | Expected |
|---|---|---|
| Tests | `./gradlew clean test` | all green (446) |
| Offline dataset audit | *(part of the above)* | green |
| No filesystem API in `src/main` | *(part of the above — `ShippedSourceTest`)* | green; see [below](#no-filesystem-writes-in-the-shipped-jar) |
| Cache ids still resolve | `./run-windows.sh --audit` | no failing ids outside the known-permanent-null section |
| Frame cost measured | `./run-windows.sh --timings`, then play for a few minutes | a real figure in `frame-timings.txt`, inside the thresholds the README states — and **written into the README and the PR body below**, replacing the placeholder sentence |
| Hub file-level preflight | `yarn workspace @toolchain/server osrs:preflight ~/Workspaces/osrs/lively-cities` | `Result: PASS` |
| Compiles under the hub's own build | see [Verifying the hub build](#verifying-the-hub-build) | `BUILD SUCCESSFUL` |
| Screenshots in the README | — | *deliberately deferred (2026-08-24) — the page ships with placeholders* |
| Jagex third-party client guidelines | read them in a browser | unchanged from your last read |

That last row is not ceremony. The guidelines page was revised once without changing its
visible dateline, so "I read it months ago" is not the same as having read it. Do it per
submission.

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
sed -i 's|compileOnly "net.runelite:client"|compileOnly "net.runelite:client:1.12.36"|' "$S/build.gradle"
echo "rootProject.name = 'lively-cities'" > "$S/settings.gradle"
cp -r gradle gradlew "$S/"
( cd "$S" && ./gradlew compileJava )
```

Last verified **2026-08-24: BUILD SUCCESSFUL, 48 classes**, run against the working tree
rather than a commit so the uncommitted work was included. Our source uses Gson and Guice,
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
commit=<the 40-hex sha you are submitting>
authors=MatthewMariner
```

- `commit` is a plain SHA. Tags are not a thing here.
- Only `repository`, `commit`, `authors`, `jarSizeLimitMiB`, `warning`, `disabled` and
  `unavailable` are accepted keys — **an unknown key is a hard build failure.**
- **No `warning=` line is needed.** That policy covers plugins that communicate with
  third-party servers; this one makes no network calls at all. (Had we shipped the
  live-query design considered for a different plugin, it would have needed one *and* would
  have shipped disabled by default.)

## Filing it

```bash
gh repo fork runelite/plugin-hub --clone --remote
cd plugin-hub
git checkout -B lively-cities upstream/master
printf 'repository=https://github.com/MatthewMariner/lively-cities.git\ncommit=%s\nauthors=MatthewMariner\n' \
  "$(git -C ~/Workspaces/osrs/lively-cities rev-parse HEAD)" > plugins/lively-cities
git add plugins/lively-cities
git commit -m "Add Lively Cities"
git push -f -u origin lively-cities
gh pr create --web
```

PR targets **`master`**.

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
> Fake-vs-real legibility was treated as the licence to exist rather than polish: menu entries
> are always deprioritised, no clickbox is generated while an item or spell is on the cursor,
> the menu target uses a colour the game never uses for a real one, and Examine says what the
> figure is. Overhead chatter ships with a global off switch, a per-citizen mute, and
> configurable cadence — the predecessor's most-complained-about behaviour, whose promised
> toggle never arrived.
>
> On frame cost: the clickbox hull is computed in `MenuOpened` rather than per tick or per
> frame, so the only per-frame work is interpolating walking figures between tiles. That is
> instrumented rather than asserted — `./gradlew runWithTimings` reports median/p95/p99 for
> the per-tick pass, model building and the per-frame pass, with the active-object count
> beside them. **<<FILL IN before filing: the measured p99 for the per-frame pass and for
> the per-tick pass, at N active objects. Do not file this paragraph with the placeholder
> still in it — an unmeasured performance claim is exactly what got the predecessor
> dismissed.>>**

## After filing

Two status checks, read **separately**:

1. **The Actions build** — compiles and packages. Strict mode is on for PR builds.
2. **The external "RuneLite Plugin Hub Checks" bot** — act only if it literally says
   *"Changes are needed"*.

A stale-PR bot closes "waiting for author" PRs after 7 days, so watch it. Observed turnaround
for simple submissions is under an hour, but that is an observation and not an SLA.

**Updates forever after:** bump the `commit=` line, new PR. The hub rebuilds every plugin on
its roughly weekly client bump; a plugin that stops compiling keeps serving its last-good jar
with a failure timestamp, so a break is something to fix calmly rather than urgently.

**Do not let the manifest go stale.** That single omission — a fixed plugin whose `commit=`
was never bumped — is why Citizens stayed dead for fourteen months after it worked again.
