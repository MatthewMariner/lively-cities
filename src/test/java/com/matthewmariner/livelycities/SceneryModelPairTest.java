package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A scenery prop that is one model short of every other copy of itself.
 *
 * <h2>The defect this exists for</h2>
 *
 * <p>The brazier at the Grand Exchange ({@code 12598.json}, tile 3169,3488) shipped
 * as {@code modelIds: [2260]}. Every other placement of the same prop in the dataset
 * — two in 12594, one in 12595, one in 12853, all four on {@code FireIdle} — is
 * {@code [2260, 3818]}. On video the Grand Exchange one renders with a flat,
 * untextured green face where the missing half should be.
 *
 * <h2>Why the cache audit cannot see this</h2>
 *
 * <p>{@code CacheIdAudit} asks the live client whether each id resolves, and 2260
 * resolves perfectly. "The id is valid" and "the model is complete" are different
 * questions, and until this test only the first had ever been asked. Nothing about
 * a single valid id says a second one was meant to be beside it.
 *
 * <h2>The rule, and why it is scenery only</h2>
 *
 * <p>Call {@code B} <b>exclusive to</b> {@code A} when every record carrying B also
 * carries A, and B appears at least twice — i.e. the dataset only ever uses B as
 * half of the pair {@code (A, B)}. If A then turns up in exactly one further record
 * <i>without</i> B, that record is the odd one out and the pair has been split.
 *
 * <p><b>Citizens are deliberately excluded, and the exclusion is measured rather
 * than assumed.</b> Run the same rule over the citizen roster and it produces
 * thirteen findings, none of them defects: a citizen is six to twelve individually
 * chosen wardrobe models, so two citizens sharing five parts and differing in the
 * sixth is the authoring working, not failing. Scenery is the opposite — a prop is
 * a fixed set of models copied off the game's own object, so a partial copy is a
 * broken prop, and the same rule over the 46 scenery records produced exactly one
 * finding: the brazier above. A rule that fires thirteen times on correct data is
 * not a guard, it is noise somebody will delete; this one is narrow on purpose.
 */
public class SceneryModelPairTest
{
	/** Scenery records in the shipped dataset. Pinned; see {@code RegionDataLoaderTest}. */
	private static final int SHIPPED_SCENERY_RECORDS = 46;

	/**
	 * The one that would have caught the Grand Exchange brazier.
	 */
	@Test
	public void noSceneryPropCarriesOnlyHalfOfAPairEveryOtherCopyHasWhole()
	{
		List<ShippedModelIds.Entry> scenery = sceneryRecords();
		Map<Integer, TreeSet<Integer>> recordsById = recordsByModelId(scenery);

		List<String> violations = new ArrayList<>();

		for (Map.Entry<Integer, TreeSet<Integer>> whole : recordsById.entrySet())
		{
			int a = whole.getKey();
			TreeSet<Integer> carryingA = whole.getValue();
			if (carryingA.size() < 3)
			{
				// Two records cannot tell "a pair with one broken copy" from "two
				// different props that happen to share a model".
				continue;
			}

			for (Map.Entry<Integer, TreeSet<Integer>> part : recordsById.entrySet())
			{
				int b = part.getKey();
				TreeSet<Integer> carryingB = part.getValue();
				if (a == b || carryingB.size() < 2 || !carryingA.containsAll(carryingB))
				{
					continue;
				}

				TreeSet<Integer> missingB = new TreeSet<>(carryingA);
				missingB.removeAll(carryingB);
				if (missingB.size() != 1)
				{
					continue;
				}

				ShippedModelIds.Entry odd = scenery.get(missingB.first());
				violations.add("model " + b + " is only ever used beside " + a + " ("
					+ carryingB.size() + " record(s)), but " + odd + " carries " + a
					+ " alone — a prop one model short renders with the missing face open");
			}
		}

		assertEquals("shipped scenery records", SHIPPED_SCENERY_RECORDS, scenery.size());
		assertTrue("scenery prop(s) missing half of a fixed model pair: " + violations,
			violations.isEmpty());
	}

	/**
	 * The rule has something to be true about.
	 *
	 * <p>Without this the test above passes on a dataset with no pairs in it at all,
	 * including one where somebody deleted the second model from all five braziers
	 * instead of adding it to the sixth. So the pair itself is asserted: 3818 is used
	 * only ever beside 2260, and the two appear in the same five records.
	 */
	@Test
	public void theBrazierIsAFixedPairAndAllFiveCopiesAreWhole()
	{
		Map<Integer, TreeSet<Integer>> recordsById = recordsByModelId(sceneryRecords());

		TreeSet<Integer> withBrazierBase = recordsById.get(2260);
		TreeSet<Integer> withBrazierTop = recordsById.get(3818);

		assertEquals("the brazier base is in five scenery records", 5,
			withBrazierBase == null ? 0 : withBrazierBase.size());
		assertEquals("and its companion model is in exactly the same five, not four",
			withBrazierBase, withBrazierTop);
	}

	/**
	 * A printed inventory of every model a scenery prop is built from, so the
	 * pairings are reviewable by eye rather than only by assertion.
	 */
	@Test
	public void printsTheSceneryModelInventoryForHumanReview()
	{
		TreeMap<String, Integer> byShape = new TreeMap<>();
		for (ShippedModelIds.Entry entry : sceneryRecords())
		{
			TreeSet<Integer> ids = new TreeSet<>();
			for (int id : entry.modelIds)
			{
				ids.add(id);
			}
			byShape.merge(ids.toString(), 1, Integer::sum);
		}

		System.out.println("Lively Cities scenery model sets — models, placements");
		int total = 0;
		for (Map.Entry<String, Integer> row : byShape.entrySet())
		{
			System.out.println(String.format("%-24s %d", row.getKey(), row.getValue()));
			total += row.getValue();
		}

		assertEquals("every scenery record has to appear in the inventory",
			SHIPPED_SCENERY_RECORDS, total);
	}

	// --- fixtures ------------------------------------------------------------

	/**
	 * @return which record indices carry each model id, keyed by id. Indices are into
	 * the list {@link #sceneryRecords()} returned, so the same list has to be used for
	 * both or the reported record is the wrong one.
	 */
	private static Map<Integer, TreeSet<Integer>> recordsByModelId(List<ShippedModelIds.Entry> records)
	{
		Map<Integer, TreeSet<Integer>> out = new TreeMap<>();
		for (int i = 0; i < records.size(); i++)
		{
			for (int id : records.get(i).modelIds)
			{
				out.computeIfAbsent(id, k -> new TreeSet<>()).add(i);
			}
		}
		return out;
	}

	/**
	 * Scenery only, read raw out of the JSON — {@link ShippedModelIds} exists so this
	 * sees what was authored rather than what survived validation, and
	 * {@link EntityDefinition} would have dropped a non-positive id before this test
	 * could notice a prop was short one.
	 */
	private static List<ShippedModelIds.Entry> sceneryRecords()
	{
		List<ShippedModelIds.Entry> out = new ArrayList<>();
		for (ShippedModelIds.Entry entry : ShippedModelIds.perEntity())
		{
			if ("Scenery".equals(entry.label))
			{
				out.add(entry);
			}
		}
		return out;
	}
}
