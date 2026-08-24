package com.matthewmariner.livelycities;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The part of the durability tooling that can be tested without a real client:
 * given a client's answers, does {@link CacheIdAudit} bucket them correctly?
 * {@link FakeClient} stands in for the client — {@code setUnloadable} /
 * {@code setThrowing} / {@code setUnloadableAnimations} are exactly the
 * fixtures {@code LivelyEntityTest} already uses for the same client contract,
 * reused here rather than re-invented.
 *
 * <p>What this file does <b>not</b> claim: that any of these ids resolve against
 * the <i>real</i> game cache. That question belongs to
 * {@code ./gradlew auditCacheIds} against a live client, which is exactly why
 * {@link CacheIdAudit} takes a {@code Client} rather than owning one.
 */
public class CacheIdAuditTest
{
	// --- collect(): the no-client half, against the real shipped dataset -----

	/**
	 * Cross-checks {@link CacheIdAudit#collect} against
	 * {@link ShippedModelIds#distinct()} — two independent readings of the same
	 * dataset, one through {@link EntityDefinition} (validated, what the render
	 * core actually uses) and one straight off the raw JSON
	 * ({@link ModelIdAuditTest} pins the raw one at 376). They have to agree,
	 * because the shipped dataset has zero skipped records and zero non-positive
	 * ids — if they ever disagree, either a record started being skipped or a
	 * non-positive id crept into the corpus, and this is what would notice
	 * either one.
	 */
	@Test
	public void collectsExactlyTheDistinctModelIdsTheOfflineAuditSees()
	{
		RegionDataLoader loader = new RegionDataLoader(TestGson.injected());
		CacheIdAudit.DatasetIds dataset = CacheIdAudit.collect(loader);

		assertEquals("every City-claimed region has to load", 45, dataset.regionsLoaded);
		assertEquals("collect() must see exactly the raw dataset's distinct model ids",
			ShippedModelIds.distinct(), dataset.modelIds);
		assertEquals(376, dataset.modelIds.size());
	}

	/**
	 * The one merged object in the shipped dataset (object id 7719, see
	 * {@code RegionDataLoaderTest}'s fixture for the equivalent test-data shape)
	 * has to turn up as a distinct id of its own, not folded into the model ids.
	 */
	@Test
	public void collectsMergedObjectIdsSeparatelyFromModelIds()
	{
		RegionDataLoader loader = new RegionDataLoader(TestGson.injected());
		CacheIdAudit.DatasetIds dataset = CacheIdAudit.collect(loader);

		assertFalse("the shipped dataset has at least one mergedObjects entry",
			dataset.mergedObjectIds.isEmpty());
		assertFalse("a merged-object id must not also be counted as a model id",
			dataset.modelIds.containsAll(dataset.mergedObjectIds));
	}

	/**
	 * The other half of the appearance surface: the {@code npcAppearanceId}s.
	 *
	 * <p>This is the whole justification for preferring an NPC id to a raw model id.
	 * An entity dressed from a composition contributes <b>no</b> model ids at all, so
	 * without this bucket the seven entities dressed that way would be the one part of
	 * the dataset the durability tooling could not see — and "we chose the more
	 * auditable mechanism" would be a claim rather than a fact.
	 *
	 * <p>Cross-checked against {@link ShippedModelIds#distinctNpcAppearanceIds()},
	 * which reads the raw JSON, exactly as the model ids are.
	 */
	@Test
	public void collectsEveryNpcAppearanceIdTheDatasetReferences()
	{
		RegionDataLoader loader = new RegionDataLoader(TestGson.injected());
		CacheIdAudit.DatasetIds dataset = CacheIdAudit.collect(loader);

		assertEquals("collect() must see exactly the raw dataset's npcAppearanceIds",
			ShippedModelIds.distinctNpcAppearanceIds(), dataset.npcAppearanceIds);
		assertEquals("the six cameos' NPC ids plus the one \"Rufus\" wears", 7,
			dataset.npcAppearanceIds.size());
		assertFalse("an NPC id is not a model id and must not be counted as one",
			dataset.modelIds.containsAll(dataset.npcAppearanceIds));
	}

	/**
	 * Every idle/move animation the render core resolves has to appear here by
	 * name, matching {@code LivelyAnimationTest}'s independently-pinned count of
	 * 84 distinct names.
	 */
	@Test
	public void collectsEveryResolvedAnimationNameUsedByTheDataset()
	{
		RegionDataLoader loader = new RegionDataLoader(TestGson.injected());
		CacheIdAudit.DatasetIds dataset = CacheIdAudit.collect(loader);

		assertEquals(84, dataset.animationIdsByName.size());
		assertTrue("BeeIdle is used by the dataset and must be one of the collected names",
			dataset.animationIdsByName.containsKey("BeeIdle"));
		assertEquals(0, (int) dataset.animationIdsByName.get("BeeIdle"));
	}

	// --- run(): bucketing a client's answers ----------------------------------

	@Test
	public void everyIdThatResolvesProducesNoFailures()
	{
		FakeClient client = new FakeClient();
		CacheIdAudit.DatasetIds dataset = dataset(
			ids(100, 200, 300),
			ids(7719),
			animations("HumanIdle", 808, "HumanWalk", 819));

		CacheIdAudit.Report report = CacheIdAudit.run(client, dataset);

		assertTrue(report.failingModelIds.isEmpty());
		assertTrue(report.failingMergedObjectIds.isEmpty());
		assertTrue(report.failingAnimations.isEmpty());
		assertTrue(report.knownPermanentNullAnimations.isEmpty());
		assertFalse(report.hasUnexpectedFailures());
	}

	@Test
	public void modelIdsThatDoNotResolveAreReportedAsFailing()
	{
		FakeClient client = new FakeClient();
		client.setUnloadable(200, 400);

		CacheIdAudit.DatasetIds dataset = dataset(
			ids(100, 200, 300, 400),
			ids(),
			animations());

		CacheIdAudit.Report report = CacheIdAudit.run(client, dataset);

		assertEquals(Arrays.asList(200, 400), report.failingModelIds);
		assertTrue(report.hasUnexpectedFailures());
	}

	/**
	 * Merged-object ids go through {@code loadModelData} too (see
	 * {@code LivelyEntity.loadParts}), so a failure there must land in its own
	 * bucket rather than being silently merged into — or lost among — the plain
	 * model id failures.
	 */
	@Test
	public void mergedObjectIdsThatDoNotResolveAreReportedInTheirOwnBucket()
	{
		FakeClient client = new FakeClient();
		client.setUnloadable(7719);

		CacheIdAudit.DatasetIds dataset = dataset(
			ids(100),
			ids(7719),
			animations());

		CacheIdAudit.Report report = CacheIdAudit.run(client, dataset);

		assertTrue("a merged-object failure must not appear as a model id failure",
			report.failingModelIds.isEmpty());
		assertEquals(Collections.singletonList(7719), report.failingMergedObjectIds);
	}

	/**
	 * {@code LivelyAnimation.BeeIdle} is id 0, and {@code client.loadAnimation(0)}
	 * returns null by design, always — a real regression on another animation
	 * must not be swallowed by that expected null, and the expected null must not
	 * be reported as though it were one.
	 */
	@Test
	public void theKnownPermanentNullAnimationIsSeparatedFromRealFailures()
	{
		FakeClient client = new FakeClient();
		client.setUnloadableAnimations(0, 6539); // BeeIdle, and WerewolfIdle failing for real

		CacheIdAudit.DatasetIds dataset = dataset(
			ids(),
			ids(),
			animations("BeeIdle", 0, "WerewolfIdle", 6539, "HumanIdle", 808));

		CacheIdAudit.Report report = CacheIdAudit.run(client, dataset);

		assertEquals(Collections.singletonList("WerewolfIdle=6539"), report.failingAnimations);
		assertEquals(Collections.singletonList("BeeIdle=0"), report.knownPermanentNullAnimations);
		assertTrue("a permanent, expected null alone must not count as an unexpected failure — "
				+ "only WerewolfIdle should trip this",
			report.hasUnexpectedFailures());
	}

	/**
	 * Isolates the claim above from
	 * {@link #theKnownPermanentNullAnimationIsSeparatedFromRealFailures}: with
	 * <b>only</b> the known-permanent-null id failing, the report must not read
	 * as a failure at all.
	 */
	@Test
	public void onlyTheKnownPermanentNullFailingIsNotAnUnexpectedFailure()
	{
		FakeClient client = new FakeClient();
		client.setUnloadableAnimations(0);

		CacheIdAudit.DatasetIds dataset = dataset(ids(), ids(), animations("BeeIdle", 0));

		CacheIdAudit.Report report = CacheIdAudit.run(client, dataset);

		assertTrue(report.failingAnimations.isEmpty());
		assertEquals(Collections.singletonList("BeeIdle=0"), report.knownPermanentNullAnimations);
		assertFalse("only the expected null failed, so this must not read as a real problem",
			report.hasUnexpectedFailures());
	}

	/**
	 * A client call that throws — a real possibility; {@code FakeClient} models
	 * it because {@code LivelyEntityTest} needs it too — must count as that one
	 * id failing, not abort the walk. Proven by checking every other id was still
	 * asked about.
	 */
	@Test
	public void aClientCallThatThrowsIsTreatedAsFailingWithoutAbortingTheWalk()
	{
		FakeClient client = new FakeClient();
		client.setThrowing(200);

		CacheIdAudit.DatasetIds dataset = dataset(ids(100, 200, 300), ids(), animations());

		CacheIdAudit.Report report = CacheIdAudit.run(client, dataset);

		assertEquals(Collections.singletonList(200), report.failingModelIds);
		assertEquals("the throw on 200 must not have stopped 300 from being asked about",
			3, client.loadModelDataCalls());
	}

	/**
	 * An NPC id whose composition is gone lands in its own bucket, and counts as a
	 * real failure — that is what makes a renumbered NPC id after a game update
	 * visible in the report instead of as six invisible citizens.
	 */
	@Test
	public void npcAppearanceIdsThatDoNotResolveAreReportedInTheirOwnBucket()
	{
		FakeClient client = new FakeClient();
		// 1798 resolves; 4214 is not registered, so FakeClient throws for it exactly
		// as the real client does for a missing archive entry.
		client.withNpc(1798, FakeNpcComposition.of("White Knight", 217, 305));

		CacheIdAudit.DatasetIds dataset = dataset(
			ids(100), ids(), ids(1798, 4214), animations());

		CacheIdAudit.Report report = CacheIdAudit.run(client, dataset);

		assertEquals(Collections.singletonList(4214), report.failingNpcAppearanceIds);
		assertTrue("an NPC id failure must not appear as a model id failure",
			report.failingModelIds.isEmpty());
		assertTrue(report.hasUnexpectedFailures());
	}

	/**
	 * An NPC id that resolves to a composition with <b>no models</b> is a failure
	 * too, and this is the case a naive audit would miss: the lookup succeeded, so
	 * "does {@code getNpcDefinition} return something?" is green while the citizen is
	 * invisible. Going through {@link NpcAppearance} is what makes the audit ask the
	 * same question the renderer asks.
	 */
	@Test
	public void anNpcIdThatResolvesToNothingDrawableCountsAsFailing()
	{
		FakeClient client = new FakeClient();
		client.withNpc(1798, FakeNpcComposition.withoutModels("Bodyless", new int[0]));

		CacheIdAudit.Report report = CacheIdAudit.run(
			client, dataset(ids(), ids(), ids(1798), animations()));

		assertEquals("a lookup that works but draws nothing is still broken",
			Collections.singletonList(1798), report.failingNpcAppearanceIds);
		assertTrue(report.hasUnexpectedFailures());
	}

	/** Nothing failing anywhere, with all four buckets populated. */
	@Test
	public void everyKindOfIdResolvingProducesACleanReport()
	{
		FakeClient client = new FakeClient();
		client.withNpc(1798, FakeNpcComposition.of("White Knight", 217));

		CacheIdAudit.Report report = CacheIdAudit.run(client, dataset(
			ids(100, 200), ids(7719), ids(1798), animations("HumanIdle", 808)));

		assertTrue(report.failingModelIds.isEmpty());
		assertTrue(report.failingMergedObjectIds.isEmpty());
		assertTrue(report.failingNpcAppearanceIds.isEmpty());
		assertTrue(report.failingAnimations.isEmpty());
		assertFalse(report.hasUnexpectedFailures());
	}

	// --- the report text ------------------------------------------------------

	@Test
	public void theReportTextIsSortedAndCarriesTheCounts()
	{
		FakeClient client = new FakeClient();
		client.setUnloadable(500, 100);
		client.setUnloadableAnimations(0);

		CacheIdAudit.DatasetIds dataset = dataset(
			ids(100, 500, 900),
			ids(),
			animations("BeeIdle", 0, "HumanIdle", 808));

		CacheIdAudit.Report report = CacheIdAudit.run(client, dataset);
		String text = report.toReportText();

		assertTrue(text.contains("model ids checked: 3"));
		assertTrue(text.contains("model ids failing: 2"));
		// Sorted ascending — a TreeSet under the hood — not authoring order (500
		// before 100) and not call order.
		assertTrue("expected ascending order 100 then 500, got:\n" + text,
			text.indexOf("\n100\n") < text.indexOf("\n500\n"));
		assertTrue(text.contains("known-permanent-null (expected, not a failure): 1"));
		assertTrue(text.contains("BeeIdle=0"));
	}

	/**
	 * The NPC appearance section has to be in the text, or a failing id would be
	 * counted by {@code hasUnexpectedFailures()} and then invisible in the file a
	 * human actually reads.
	 */
	@Test
	public void theReportTextCarriesTheNpcAppearanceSection()
	{
		FakeClient client = new FakeClient();
		client.withNpc(1798, FakeNpcComposition.of("White Knight", 217));

		CacheIdAudit.Report report = CacheIdAudit.run(
			client, dataset(ids(), ids(), ids(512, 1798), animations()));
		String text = report.toReportText();

		assertTrue(text, text.contains("npc appearance ids checked: 2"));
		assertTrue(text, text.contains("npc appearance ids failing: 1"));
		assertTrue("the failing id itself has to be listed", text.contains("\n512\n"));
	}

	@Test
	public void runningTheSameDatasetAndAnswersTwiceProducesByteIdenticalText()
	{
		FakeClient client = new FakeClient();
		client.setUnloadable(500, 100, 900);

		CacheIdAudit.DatasetIds dataset = dataset(ids(100, 500, 900), ids(), animations());

		String first = CacheIdAudit.run(client, dataset).toReportText();
		String second = CacheIdAudit.run(client, dataset).toReportText();

		assertEquals("a diffable report has to be stable across identical runs, or a real "
				+ "regression would be indistinguishable from run-to-run noise",
			first, second);
	}

	// --- fixtures --------------------------------------------------------------

	private static Set<Integer> ids(int... values)
	{
		Set<Integer> set = new LinkedHashSet<>();
		for (int v : values)
		{
			set.add(v);
		}
		return set;
	}

	private static TreeMap<String, Integer> animations(Object... namesThenIds)
	{
		TreeMap<String, Integer> map = new TreeMap<>();
		for (int i = 0; i < namesThenIds.length; i += 2)
		{
			map.put((String) namesThenIds[i], (Integer) namesThenIds[i + 1]);
		}
		return map;
	}

	private static CacheIdAudit.DatasetIds dataset(
		Set<Integer> modelIds, Set<Integer> mergedObjectIds, TreeMap<String, Integer> animationIdsByName)
	{
		return dataset(modelIds, mergedObjectIds, ids(), animationIdsByName);
	}

	private static CacheIdAudit.DatasetIds dataset(
		Set<Integer> modelIds,
		Set<Integer> mergedObjectIds,
		Set<Integer> npcAppearanceIds,
		TreeMap<String, Integer> animationIdsByName)
	{
		List<String> summaries = Collections.emptyList();
		return new CacheIdAudit.DatasetIds(
			new TreeSet<>(modelIds), new TreeSet<>(mergedObjectIds), new TreeSet<>(npcAppearanceIds),
			animationIdsByName, summaries, 0);
	}
}
