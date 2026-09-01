package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import net.runelite.api.gameval.NpcID;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
		assertEquals("entities dressed from raw model ids", 304, fromModelIds);
		assertEquals("entities dressed from an NPC appearance", 7, fromNpcAppearance);
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
			7, ShippedModelIds.distinctNpcAppearanceIds().size());
	}

	/**
	 * <b>GitHub issue #1: "Rufus renders without boots."</b>
	 *
	 * <p>Not a rendering bug — the client logged no model-load failure for him, and a
	 * partial build cannot spawn at all (see {@code LivelyEntity}). His record carried
	 * twelve model ids, the most of any entity in the corpus, and none of them was
	 * footwear. It sat unfixed because the only fix available was to guess a raw model
	 * id, and a wrong guess puts a hat where the boots should be.
	 *
	 * <p>{@code npcAppearanceId} removed the guess: he now wears a <i>named</i>
	 * constant. The id is asserted through {@link NpcID#FARMER1} rather than as 3114,
	 * and the second assertion is the one that makes the choice checkable rather than
	 * asserted — the 1.12.36 jar's other, independently generated id table names the
	 * same number {@code FARMER}, i.e. an NPC whose in-game name is literally "Farmer".
	 * That is the whole evidence for "the id maps to the thing we think it does".
	 *
	 * <p><b>That first line is a compile-time pin, not a runtime one</b>, and an earlier
	 * revision of this javadoc oversold it as "a mechanical check". Both names are
	 * {@code static final int}, so javac inlines them (JLS 4.12.4) and what actually
	 * executes is {@code assertEquals(3114, 3114)} — two literals that cannot disagree.
	 * It still earns its keep, because {@code runeLiteVersion = 'latest.release'} means
	 * a jar bump recompiles this file and a disagreement between the two tables would be
	 * baked in as two <i>different</i> literals and go red on the next run. But what it
	 * pins is the jar this file was compiled against, not the jar the suite is running
	 * on, and the only way to make it a genuine runtime read is reflection, which this
	 * project does not use.
	 *
	 * <p>The rest is the trade, written down: he is emphatically <b>not</b> a cameo — an
	 * ordinary townsperson must not become opt-in content by way of a bug fix, see
	 * {@code EntityRecord.cameo} — and he is the only non-cameo citizen dressed this
	 * way. He is looked up <b>by name across the whole roster</b> rather than taken out
	 * of the list filtered on {@code !cameo}, because an earlier revision did the
	 * latter and its {@code assertFalse(rufus.cameo)} was then a restatement of the
	 * filter that had selected him: it could not fail. Flip {@code cameo} to true on his
	 * record now and it does.
	 *
	 * <p>That his authored body is <b>replaced, not patched</b> — the appearance wins,
	 * so the twelve ids and their six recolour pairs left the record entirely — used to
	 * be a fourth loop here with no counter in it, which would have passed having
	 * asserted nothing if its {@code if} had stopped matching.
	 * {@link #everyEntityHasEitherModelIdsOrAnNpcAppearanceIdAndNeverNeither} already
	 * makes exactly that claim over every entity in the dataset, and reports carrying
	 * both as a violation, so the loop was deleted rather than decorated.
	 */
	@Test
	public void theCitizenWhoseAuthoredModelsHadNoBootsWearsANamedNpcInstead()
	{
		assertEquals("the two independently generated id tables in runelite-api-1.12.36 have to "
				+ "agree that this number is the NPC named \"Farmer\" — that agreement is the only "
				+ "evidence anybody has that the id means what the dataset says it means",
			net.runelite.api.NpcID.FARMER, NpcID.FARMER1);

		ShippedCitizens.Entry rufus = null;
		for (ShippedCitizens.Entry citizen : ShippedCitizens.all())
		{
			if ("Rufus".equals(citizen.name))
			{
				assertNull("two citizens named Rufus would make every assertion below ambiguous",
					rufus);
				rufus = citizen;
			}
		}

		assertNotNull("the citizen this entire test is about is no longer in the dataset", rufus);
		assertEquals(NpcID.FARMER1, rufus.npcAppearanceId);
		assertFalse("a townsperson fixed by re-dressing him must not become opt-in content",
			rufus.cameo);

		List<ShippedCitizens.Entry> dressedFromAnNpc = new ArrayList<>();
		for (ShippedCitizens.Entry citizen : ShippedCitizens.all())
		{
			if (citizen.npcAppearanceId != 0 && !citizen.cameo)
			{
				dressedFromAnNpc.add(citizen);
			}
		}

		assertEquals("exactly one non-cameo citizen is dressed from a composition: " + dressedFromAnNpc,
			1, dressedFromAnNpc.size());
		assertEquals("and it is him", "Rufus", dressedFromAnNpc.get(0).name);
	}

	/**
	 * The load-bearing pin. The plan's L0 audit measured 384 — the exact input list
	 * the cache-backed validator had to check. It went to 376 when "Rufus" in Varrock
	 * was moved onto an {@code npcAppearanceId} (GitHub issue #1) and eight of his
	 * twelve model ids, used by nobody else, left the corpus with him. It is 324 now:
	 * the nine-city cut on 2026-08-24 removed eighteen region files, and 52 model ids
	 * went with them because nothing in the surviving nine places wears them. A dataset
	 * change that adds or removes model ids has to move this number, which is what makes
	 * the change visible in review rather than silently changing what the durability
	 * tooling covers.
	 */
	@Test
	public void theDistinctModelIdFigureIsPinned()
	{
		assertEquals("distinct model ids across the shipped dataset — see the L0 audit in "
				+ "plans/osrs-lively-cities.md; a change here means the durability tooling's "
				+ "input list changed and should be re-reviewed",
			324, ShippedModelIds.distinct().size());
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
