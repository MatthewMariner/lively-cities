# Submitting to the Plugin Hub

Everything needed to file the submission, prepared in advance. **Deliberately not filed yet** —
the decision (2026-08-23) is to wait until the content pass is done, because a first impression
at 135 citizens is a first impression at 135 citizens.

Nothing below requires new work. When the content lands, re-run the checks and file it.

---

## Before you file

| Check | Command | Expected |
|---|---|---|
| Tests | `./gradlew clean test` | all green |
| Offline dataset audit | *(part of the above)* | green |
| Cache ids still resolve | `./gradlew auditCacheIds` | no failing ids outside the known-permanent-null section |
| Hub file-level preflight | `yarn workspace @toolchain/server osrs:preflight ~/Workspaces/osrs/lively-cities` | `Result: PASS` |
| Compiles under the hub's own build | see [Verifying the hub build](#verifying-the-hub-build) | `BUILD SUCCESSFUL` |
| Screenshots in the README | — | *deliberately deferred (2026-08-24) — the page ships with placeholders* |
| Jagex third-party client guidelines | read them in a browser | unchanged from your last read |

That last row is not ceremony. The guidelines page was revised once without changing its
visible dateline, so "I read it months ago" is not the same as having read it. Do it per
submission.

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

Last verified 2026-08-23: **BUILD SUCCESSFUL, 63 classes.** Our source uses Gson and Guice,
which look like third-party dependencies but arrive transitively through the client — worth
re-proving rather than assuming after any new import.

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
