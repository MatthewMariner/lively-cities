package com.matthewmariner.livelycities;

import com.matthewmariner.livelycities.data.EntityRecord;
import com.matthewmariner.livelycities.data.PointRecord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Thinning has to be proportional, deterministic, and nested. Each of those is a
 * separate way for it to be quietly wrong.
 */
public class CrowdDensityTest
{
	private static final int REGION = 12853;

	/**
	 * The bucket arithmetic, pinned directly on the hash rather than through a
	 * uuid, so the boundaries are exact.
	 *
	 * <p>The negative cases are the point. Half of all 64-bit hashes are negative,
	 * and {@code hash % 100 < keepPercent} is true for <i>every</i> negative hash
	 * — which would keep half the roster whatever the dial said, and would look
	 * plausible in a proportion test tuned to a 66% level.
	 */
	@Test
	public void theBucketIsAFloorModSoNegativeHashesAreNotAllKept()
	{
		assertTrue("bucket 0", CrowdDensity.SPARSE.keeps(0L));
		assertTrue("bucket 32 is the last SPARSE keeps", CrowdDensity.SPARSE.keeps(32L));
		assertFalse("bucket 33 is the first it drops", CrowdDensity.SPARSE.keeps(33L));
		assertTrue("bucket 65 is the last NORMAL keeps", CrowdDensity.NORMAL.keeps(65L));
		assertFalse("bucket 66 is the first it drops", CrowdDensity.NORMAL.keeps(66L));

		// floorMod(-1, 100) is 99; the remainder operator would give -1.
		assertFalse("a hash of -1 lands in bucket 99, not bucket -1",
			CrowdDensity.SPARSE.keeps(-1L));
		assertFalse(CrowdDensity.NORMAL.keeps(-1L));
		assertFalse(CrowdDensity.SPARSE.keeps(-101L));
		assertFalse(CrowdDensity.SPARSE.keeps(Long.MIN_VALUE + 91L));
		// And a negative hash in a low bucket is still kept.
		assertTrue("floorMod(-200, 100) is 0", CrowdDensity.SPARSE.keeps(-200L));
	}

	@Test
	public void fullKeepsEveryoneWhateverTheHash()
	{
		assertTrue(CrowdDensity.FULL.keeps(0L));
		assertTrue(CrowdDensity.FULL.keeps(99L));
		assertTrue(CrowdDensity.FULL.keeps(-1L));
		assertTrue(CrowdDensity.FULL.keeps(Long.MIN_VALUE));
		assertTrue(CrowdDensity.FULL.keeps(Long.MAX_VALUE));
		assertEquals(100, CrowdDensity.FULL.getKeepPercent());
	}

	/**
	 * {@link CrowdDensity#CROWDED} keeps the whole authored roster too, and is the
	 * only level that admits {@link CitizenEcho}'s derived citizens.
	 *
	 * <p>Both halves matter and they are different claims. "Keeps everyone" is what
	 * makes the level purely additive: it is {@link CrowdDensity#FULL} plus something,
	 * never a different crowd. "Only one" is what makes the feature opt-in — a second
	 * level that admitted echoes would mean a user who had chosen SPARSE for a quieter
	 * city could be handed extra people.
	 */
	@Test
	public void crowdedKeepsEveryoneAndIsTheOnlyLevelThatAdmitsEchoes()
	{
		assertTrue(CrowdDensity.CROWDED.keeps(0L));
		assertTrue(CrowdDensity.CROWDED.keeps(99L));
		assertTrue(CrowdDensity.CROWDED.keeps(-1L));
		assertTrue(CrowdDensity.CROWDED.keeps(Long.MIN_VALUE));
		assertTrue(CrowdDensity.CROWDED.keeps(Long.MAX_VALUE));
		assertEquals("the thinning percentage is 100 — the extra people are not a percentage",
			100, CrowdDensity.CROWDED.getKeepPercent());

		assertTrue(CrowdDensity.CROWDED.includesEchoes());
		for (CrowdDensity density : CrowdDensity.values())
		{
			if (density != CrowdDensity.CROWDED)
			{
				assertFalse(density + " must not admit derived citizens", density.includesEchoes());
			}
		}
	}

	/**
	 * The default is unchanged, and that is the opt-in.
	 *
	 * <p>A new level at the top of the enum is one {@code @ConfigItem} default away
	 * from being on for everybody who has never touched the dial — including the
	 * users who never asked for extra citizens. RuneLite serialises an enum config
	 * value by name, so a profile with no {@code crowdDensity} key falls through to
	 * this method.
	 */
	@Test
	public void theDefaultIsStillFullSoTheExtraCitizensAreOptIn()
	{
		assertEquals(CrowdDensity.FULL, new FakeConfig().crowdDensity());
		assertFalse(new FakeConfig().crowdDensity().includesEchoes());
	}

	/**
	 * Turning the dial up must only ever add people. It falls out of comparing one
	 * bucket against a rising threshold — but a version that hashed the uuid
	 * together with the level would pass a proportion test and swap the whole
	 * crowd for a different one on every change.
	 */
	@Test
	public void turningTheDialUpOnlyEverAddsPeople()
	{
		List<UUID> uuids = shippedUuids();

		Set<UUID> sparse = kept(CrowdDensity.SPARSE, uuids);
		Set<UUID> normal = kept(CrowdDensity.NORMAL, uuids);
		Set<UUID> full = kept(CrowdDensity.FULL, uuids);
		Set<UUID> crowded = kept(CrowdDensity.CROWDED, uuids);

		assertTrue("everyone sparse keeps, normal keeps too", normal.containsAll(sparse));
		assertTrue("and everyone normal keeps, full keeps too", full.containsAll(normal));
		assertTrue("and everyone full keeps, crowded keeps too", crowded.containsAll(full));
		assertEquals(uuids.size(), full.size());
		assertEquals("crowded adds people, it never swaps them", uuids.size(), crowded.size());
		assertTrue("the levels have to actually differ", sparse.size() < normal.size());
	}

	/**
	 * The same uuid always gets the same answer, and the answer comes from the
	 * uuid's <i>value</i> rather than from the object.
	 *
	 * <p>Parsing the string twice is what makes this a real test: identity hashing
	 * or a per-instance {@code Random} would give two freshly-parsed UUIDs
	 * different verdicts, and that is exactly the bug — a crowd that changes
	 * membership every time the wrapper cache is rebuilt.
	 */
	@Test
	public void theVerdictComesFromTheUuidsValueNotTheObject()
	{
		String raw = "44444444-4444-4444-8444-444444444444";
		long first = definitionWithUuid(raw).stableHash();
		long second = definitionWithUuid(raw).stableHash();

		assertEquals("two definitions built from the same uuid must hash the same",
			first, second);
		assertNotEquals("and a different uuid must hash differently",
			first, definitionWithUuid("55555555-5555-4555-8555-555555555555").stableHash());

		for (CrowdDensity density : CrowdDensity.values())
		{
			assertEquals(density + " must agree with itself across instances",
				density.keeps(first), density.keeps(second));
		}
	}

	/**
	 * An anchor value, so the hash cannot be changed by accident.
	 *
	 * <p>Not a test of the arithmetic — the test above covers that — but of its
	 * <i>stability</i>. The whole promise of this feature is that the same people
	 * are kept across sessions, and a "harmless" tweak to the mixing function
	 * reshuffles every thinned city on everyone's install. Changing this number on
	 * purpose is fine; changing it without noticing is what this catches.
	 */
	@Test
	public void theHashIsPinnedSoAThinnedCityStaysTheSameCityAcrossVersions()
	{
		assertEquals(-6027941731549618587L,
			definitionWithUuid("44444444-4444-4444-8444-444444444444").stableHash());
		assertEquals(7473597206274394059L,
			definitionWithUuid("55555555-5555-4555-8555-555555555555").stableHash());
	}

	/**
	 * The proportion, on enough samples that the sampling noise is smaller than any
	 * bug worth having.
	 *
	 * <p>Ten thousand uuids: for a 33% level the standard deviation of the kept
	 * share is under half a percentage point, so a ±3 point band is six sigma. A
	 * filter that keeps everyone, keeps nobody, or ignores the level cannot fit
	 * inside it.
	 */
	@Test
	public void thinningKeepsRoughlyTheAdvertisedShareOfALargeRoster()
	{
		// A fixed seed, so this is the same ten thousand uuids on every run. The
		// determinism the feature promises is asserted above; this is about spread.
		Random random = new Random(20260823L);
		List<UUID> uuids = new ArrayList<>(10_000);
		for (int i = 0; i < 10_000; i++)
		{
			uuids.add(new UUID(random.nextLong(), random.nextLong()));
		}

		for (CrowdDensity density : CrowdDensity.values())
		{
			double share = 100.0 * kept(density, uuids).size() / uuids.size();
			assertTrue(density + " kept " + String.format("%.1f", share) + "% of "
					+ uuids.size() + ", expected about " + density.getKeepPercent() + "%",
				Math.abs(share - density.getKeepPercent()) <= 3.0);
		}
	}

	/**
	 * And the same thing on the roster that actually ships, where 151 samples make
	 * the band much wider — one standard deviation is already ~3.6 points at the
	 * sparse level. Loose on purpose: what this catches is the dataset's uuids
	 * clustering pathologically, not a small deviation.
	 */
	@Test
	public void thinningIsNotPathologicalOnTheShippedRoster()
	{
		List<UUID> uuids = shippedUuids();
		assertEquals("the whole shipped roster", 151, uuids.size());

		for (CrowdDensity density : CrowdDensity.values())
		{
			double share = 100.0 * kept(density, uuids).size() / uuids.size();
			assertTrue(density + " kept " + String.format("%.1f", share) + "% of the shipped "
					+ uuids.size() + ", expected about " + density.getKeepPercent() + "%",
				Math.abs(share - density.getKeepPercent()) <= 12.0);
		}
	}

	@Test
	public void theLabelsAreWhatTheConfigPanelWillShow()
	{
		assertEquals("Crowded", CrowdDensity.CROWDED.toString());
		assertEquals("Full", CrowdDensity.FULL.toString());
		assertEquals("Normal", CrowdDensity.NORMAL.toString());
		assertEquals("Sparse", CrowdDensity.SPARSE.toString());

		// Declaration order is dropdown order, densest first.
		assertEquals(0, CrowdDensity.CROWDED.ordinal());
		assertEquals(CrowdDensity.values().length - 1, CrowdDensity.SPARSE.ordinal());
	}

	private static Set<UUID> kept(CrowdDensity density, List<UUID> uuids)
	{
		Set<UUID> out = new HashSet<>();
		for (UUID uuid : uuids)
		{
			if (density.keeps(hash(uuid)))
			{
				out.add(uuid);
			}
		}
		return out;
	}

	/**
	 * The same value {@link EntityDefinition#stableHash()} produces, reached
	 * through a definition rather than reimplemented — a local copy of the mixer
	 * here would agree with a broken one there.
	 */
	private static long hash(UUID uuid)
	{
		return definitionWithUuid(uuid.toString()).stableHash();
	}

	private static List<UUID> shippedUuids()
	{
		RegionDataLoader loader = new RegionDataLoader(TestGson.injected());
		List<UUID> uuids = new ArrayList<>();

		for (int regionId : ShippedRegions.ids())
		{
			RegionDefinition region = loader.loadRegion(regionId);
			assertNotNull("region " + regionId + " failed to load", region);
			for (EntityDefinition entity : region.getEntities())
			{
				uuids.add(entity.getUuid());
			}
		}

		return uuids;
	}

	private static EntityDefinition definitionWithUuid(String uuid)
	{
		EntityRecord record = new EntityRecord();
		record.entityType = "StationaryCitizen";
		record.uuid = uuid;
		record.modelIds = new int[]{217};

		PointRecord point = new PointRecord();
		point.x = 3238;
		point.y = 3425;
		point.plane = 0;
		record.worldLocation = point;

		EntityDefinition definition = EntityDefinition.fromRecord(record, REGION);
		assertNotNull(definition);
		return definition;
	}
}
