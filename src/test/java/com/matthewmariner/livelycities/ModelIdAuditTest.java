package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The offline half of the durability tooling: pure data invariants over the
 * shipped {@code RegionData/*.json}, no client and no cache. Whether an id
 * actually <i>resolves</i> is answered only by {@link CacheIdAudit}, which needs
 * a live client and is kept out of this run — see that class's javadoc.
 *
 * <p>What lives here instead is everything a JSON diff can catch before a
 * broken id ever reaches a player: an implausible number, an empty
 * {@code modelIds}, and a pinned count so a dataset change that adds or removes
 * ids shows up as a red assertion rather than a silent drift.
 */
public class ModelIdAuditTest
{
	/**
	 * Positive and not wrong by orders of magnitude. See
	 * {@link CacheIdPlausibility} for where the ceiling comes from and why it is
	 * deliberately loose — the precise question is the cache-backed check's job,
	 * not this one's.
	 */
	@Test
	public void everyModelIdIsPositiveAndWithinThePlausibleCacheRange()
	{
		List<String> violations = new ArrayList<>();

		for (ShippedModelIds.Entry entry : ShippedModelIds.perEntity())
		{
			for (int id : entry.modelIds)
			{
				if (!CacheIdPlausibility.isPlausible(id))
				{
					violations.add(entry + ": modelId " + id);
				}
			}
		}

		assertTrue("implausible model id(s) in the shipped dataset: " + violations, violations.isEmpty());
	}

	/**
	 * An entity with nothing to build cannot render anything — and there are now two
	 * ways to have something to build, so this is the qualified form of what used to
	 * be "no entity ships an empty {@code modelIds} array".
	 *
	 * <p>The six cameo records in {@code 12598.json} deliberately carry no
	 * {@code modelIds} at all: they are dressed from an {@code npcAppearanceId}
	 * instead (see {@code EntityRecord.npcAppearanceId}). So the invariant is
	 * <b>exactly one of the two</b> has to be present, which is a stronger statement
	 * than the old one rather than a relaxation of it — the old test could not have
	 * caught a record with neither, because a record with neither also has an empty
	 * {@code modelIds} array and would have been reported as the same violation.
	 *
	 * <p>{@code EntityDefinition} already skips a record with neither at load time
	 * (and {@code RegionDataLoaderTest.loadsTheWholeShippedDatasetWithoutSkippingAnything}
	 * asserts nothing in the shipped data is skipped at all), but this checks the raw
	 * JSON directly so the claim does not depend on that filtering staying in place,
	 * or on skip-counting logic that has other jobs too.
	 */
	@Test
	public void everyEntityHasEitherModelIdsOrAnNpcAppearanceIdAndNeverNeither()
	{
		List<String> violations = new ArrayList<>();
		int fromModelIds = 0;
		int fromNpcAppearance = 0;

		for (ShippedModelIds.Entry entry : ShippedModelIds.perEntity())
		{
			boolean hasModels = entry.modelIds.length > 0;
			boolean hasNpc = entry.npcAppearanceId != 0;

			if (!hasModels && !hasNpc)
			{
				violations.add(entry + ": neither modelIds nor npcAppearanceId");
			}
			else if (hasModels && hasNpc)
			{
				// Legal — the NPC appearance wins and EntityDefinition warns — but
				// nothing in the shipped data does it, and it is worth knowing if
				// that changes, because the ignored half is the half a human typed.
				violations.add(entry + ": carries both modelIds and npcAppearanceId "
					+ entry.npcAppearanceId + ", so the modelIds are dead weight");
			}

			if (hasNpc)
			{
				fromNpcAppearance++;
			}
			else
			{
				fromModelIds++;
			}
		}

		assertTrue("entities with nothing to build, or with two ways to build: " + violations,
			violations.isEmpty());

		// Both halves have to be non-empty or this test is only checking one rule.
		assertEquals("entities dressed from raw model ids", 175, fromModelIds);
		assertEquals("entities dressed from an NPC appearance", 6, fromNpcAppearance);
	}

	/**
	 * The {@code npcAppearanceId}s get the same offline bound as the model ids, and
	 * it bites harder here: {@code gameval.NpcID}'s highest constant in 1.12.36 is
	 * 16346, so a transposed digit or a pasted hashcode in this field is caught
	 * before a client is ever asked about it.
	 */
	@Test
	public void everyNpcAppearanceIdIsPositiveAndWithinThePlausibleCacheRange()
	{
		List<String> violations = new ArrayList<>();

		for (ShippedModelIds.Entry entry : ShippedModelIds.perEntity())
		{
			if (entry.npcAppearanceId != 0 && !CacheIdPlausibility.isPlausible(entry.npcAppearanceId))
			{
				violations.add(entry + ": npcAppearanceId " + entry.npcAppearanceId);
			}
		}

		assertTrue("implausible npcAppearanceId(s) in the shipped dataset: " + violations,
			violations.isEmpty());
		assertEquals("the dataset has to actually use the mechanism for this to mean anything",
			6, ShippedModelIds.distinctNpcAppearanceIds().size());
	}

	/**
	 * The load-bearing pin. 384 is the precise size of the fragility surface the
	 * plan's L0 audit measured — the exact input list the cache-backed validator
	 * has to check. A dataset change that adds or removes model ids has to move
	 * this number, which is what makes the change visible in review rather than
	 * silently changing what durability tooling covers.
	 */
	@Test
	public void the384DistinctModelIdFigureIsPinned()
	{
		assertEquals("distinct model ids across the shipped dataset — see the L0 audit in "
				+ "plans/osrs-lively-cities.md; a change here means the durability tooling's "
				+ "input list changed and should be re-reviewed",
			384, ShippedModelIds.distinct().size());
	}

	/**
	 * A per-region summary, printed so a human reviewing a dataset change (or
	 * this test's own output) can see what moved without re-deriving it by hand.
	 * Backed by a real assertion — a summary nobody can break is documentation,
	 * not verification — so the printed total is cross-checked against the
	 * independently-computed dataset-wide distinct count.
	 */
	@Test
	public void printsAPerRegionModelIdSummaryForHumanReview()
	{
		TreeMap<Integer, java.util.TreeSet<Integer>> byRegion = new TreeMap<>();
		for (ShippedModelIds.Entry entry : ShippedModelIds.perEntity())
		{
			java.util.TreeSet<Integer> ids = byRegion.computeIfAbsent(entry.regionId, k -> new java.util.TreeSet<>());
			for (int id : entry.modelIds)
			{
				ids.add(id);
			}
		}

		java.util.TreeSet<Integer> unionAcrossRegions = new java.util.TreeSet<>();
		System.out.println("Lively Cities model id audit — per-region summary");
		System.out.println("region     distinct model ids");
		for (java.util.Map.Entry<Integer, java.util.TreeSet<Integer>> region : byRegion.entrySet())
		{
			unionAcrossRegions.addAll(region.getValue());
			System.out.println(String.format("%-10d %d", region.getKey(), region.getValue().size()));
		}
		System.out.println("TOTAL distinct model ids across " + byRegion.size() + " region(s): "
			+ unionAcrossRegions.size());

		assertEquals("every shipped region has a summary row", ShippedRegions.ids().size(), byRegion.size());
		assertEquals("the printed union has to equal the independently-computed distinct count "
				+ "or the summary is lying about what changed",
			ShippedModelIds.distinct(), unionAcrossRegions);
	}
}
