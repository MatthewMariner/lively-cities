# Lively Cities
Cosmetic townsfolk and scenery that make cities feel lived-in

## After an OSRS update: checking the dataset still resolves

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

### 1. The offline dataset audit — runs on every `./gradlew test`

No client needed. `ModelIdAuditTest`, `LivelyAnimationTest`, `CacheIdAuditTest`
and `RegionDataLoaderTest` already assert, over the shipped JSON alone:

- every `modelIds` entry is positive and not implausibly large (see
  `CacheIdPlausibility` for where the ceiling comes from)
- no entity ships an empty `modelIds` array
- the dataset's distinct-model-id count is pinned (currently 384) — if this
  test fails after you *intentionally* changed the dataset, update the pinned
  number in `ModelIdAuditTest`; if you did not touch the dataset, something
  else changed it
- every animation name the dataset uses resolves in `LivelyAnimation`
- the whole dataset loads with zero skipped records

These catch authoring mistakes (a stray digit, an empty array, an unknown
animation name), but **they cannot tell you whether an id still resolves in
the current game cache** — that needs a live client, which the normal test
suite deliberately never has.

### 2. The cache-backed validator — `./gradlew auditCacheIds`

This is the real check, and the one to run after a suspected renumbering:

```
./gradlew auditCacheIds
```

This launches the same dev client `./gradlew run` does, with one extra system
property set. On startup, `LivelyCitiesPlugin` walks every distinct model id,
merged-object id, and animation id the shipped dataset references and asks the
live client (`client.loadModelData(id)` / `client.loadAnimation(id)`) whether
each one still resolves — these two calls are the only real ground truth,
which is why this cannot be a unit test.

The client does not need to be logged into a world; the cache is loaded before
the login screen. Watch the client log for a summary line
(`Lively Cities cache id audit: N model id(s) checked (M failing), ...`), then
open the full report:

```
~/.runelite/lively-cities/model-id-audit.txt
```

The report is a small, diffable, sorted plain-text file — commit it (or just
compare it by eye against a previous run) to see exactly what changed. It has
four sections: failing model ids, failing merged-object ids, failing animation
ids, and a **known-permanent-null** section for animation ids that are
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