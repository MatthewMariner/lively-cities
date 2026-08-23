package com.matthewmariner.livelycities;

/**
 * A coarse, offline sanity bound for a raw cache id — {@code modelIds} and
 * {@code mergedObjects[].objectID} in the region dataset.
 *
 * <p><b>This is not, and cannot be, the real check.</b> Whether an id actually
 * resolves is a question only a live client can answer — that is the whole
 * reason {@link CacheIdAudit} exists. What this class catches is the class of
 * mistake a live client would never be asked about: a negative sentinel, a
 * transposed digit, a pasted hashcode or hour-of-epoch that landed in a
 * {@code modelIds} array by accident. Those are wrong by orders of magnitude,
 * not by one renumbering, and they are exactly the kind of authoring slip a
 * dataset audit should catch before the cache-backed check ever runs.
 *
 * <p><b>Where {@link #MAX_PLAUSIBLE_ID} comes from.</b> There is no offline way
 * to ask the real cache how many model archives it has — that would mean either
 * a live client (banned from the normal test run by design) or parsing the raw
 * game cache under {@code ~/.runelite/jagexcache} (which is real cache access
 * with the same problem: environment-dependent, and not what "pure data
 * invariants" means). So the bound below is not a claim about the model
 * archive's true size. It is grounded in the one piece of hard, offline,
 * reproducible evidence available: {@code runelite-api-1.12.36.jar}'s own
 * generated {@code net.runelite.api.gameval} constant classes, which are scraped
 * from this exact client version's cache and ship inside the dependency this
 * project already compiles against. Measured directly from that jar on
 * 2026-08-23 (via {@code javap -p -constants}, highest field value per class):
 *
 * <ul>
 *   <li>{@code AnimationID} — highest id 14496, ~14489 constants</li>
 *   <li>{@code NpcID} — highest id 16346, ~16347 constants</li>
 *   <li>{@code ObjectID} + {@code ObjectID1} (split across two classes — the JVM
 *       constant-pool limit forces the generator to spill into a second class
 *       once one archive's names would not fit in one) — highest id 62430</li>
 * </ul>
 *
 * <p>There is no {@code ModelID} gameval class at all — models are exactly the
 * unnamed, easy-to-typo raw numbers the predecessor plugin hardcoded, which is
 * the whole reason this durability tooling exists. But models are not a
 * different order of magnitude from objects and NPCs in the same client
 * version; the shipped dataset's own highest {@code modelIds} entry, 56218, already
 * sits inside the 62430 ceiling measured on ObjectID above. So the bound here is
 * set well clear of every one of those measured maxima — generous enough that
 * years of future cache growth will not need this number touched — while still
 * being nowhere near {@link Integer#MAX_VALUE}, so a value that is wrong by
 * orders of magnitude still gets caught.
 *
 * <p><b>Deliberately not tied to a config or a live measurement.</b> Tightening
 * this to chase the cache's real size would make the offline audit's pass/fail
 * depend on facts the audit cannot see, which is precisely the failure mode
 * {@link CacheIdAudit} exists to own instead.
 */
final class CacheIdPlausibility
{
	static final int MAX_PLAUSIBLE_ID = 200_000;

	private CacheIdPlausibility()
	{
	}

	/**
	 * @return true if {@code id} is positive and not implausibly large. Says
	 * nothing about whether the id actually resolves in any real cache.
	 */
	static boolean isPlausible(int id)
	{
		return id > 0 && id <= MAX_PLAUSIBLE_ID;
	}
}
