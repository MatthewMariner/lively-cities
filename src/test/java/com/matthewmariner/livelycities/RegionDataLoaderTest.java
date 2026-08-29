package com.matthewmariner.livelycities;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Fixtures live in {@code src/test/resources/TestRegionData/} so the loader can
 * be pointed at deliberately broken data without touching the 27 shipped files.
 */
public class RegionDataLoaderTest
{
	private RegionDataLoader loader;

	private static Map<String, EntityDefinition> byName(List<EntityDefinition> entities)
	{
		Map<String, EntityDefinition> map = new HashMap<>();
		for (EntityDefinition e : entities)
		{
			map.put(e.getName(), e);
		}
		return map;
	}

	@Before
	public void setUp()
	{
		loader = new RegionDataLoader(TestGson.injected(), "TestRegionData/");
	}

	@Test
	public void parsesEveryFieldTheRenderCoreUses()
	{
		RegionDefinition region = loader.loadRegion(1001);
		assertNotNull(region);

		assertEquals(1001, region.getRegionId());
		assertEquals(0.8f, region.getVersion(), 0.0001f);
		assertEquals(0, region.getSkippedRecords());
		assertEquals(3, region.getEntityCount());
		assertEquals(2, region.getCitizenCount());
		assertEquals(1, region.getSceneryCount());

		Map<String, EntityDefinition> named = byName(region.getEntities());

		EntityDefinition alder = named.get("Alder the fletcher");
		assertNotNull(alder);
		assertEquals(EntityType.StationaryCitizen, alder.getType());
		assertEquals("Whittling a bow stave.", alder.getExamineText());
		assertEquals(new WorldPoint(3238, 3425, 0), alder.getWorldLocation());
		assertEquals(512, alder.getOrientation());
		assertArrayEqualsInt(new int[]{235, 248}, alder.getModelIds());
		// 54397 -> 33694 is a real pair out of 12850.json (Fisherman), and both
		// halves are past 32767 — the shipped data has 94 such values. The
		// client's recolor() takes the wrapped 16-bit value, so the loader must
		// preserve the negative rather than clamp it. Spelled out as literals:
		// asserting (short) 54397 against a cast of the same expression would
		// pass however the loader behaved.
		assertEquals(-11139, alder.getRecolorFind()[0]);
		assertEquals(-31842, alder.getRecolorReplace()[0]);
		assertEquals((short) 54397, alder.getRecolorFind()[0]);
		assertEquals((short) 33694, alder.getRecolorReplace()[0]);
		// And a value that does fit is left alone.
		assertEquals(8741, alder.getRecolorFind()[1]);
		assertEquals(8493, alder.getRecolorReplace()[1]);
		assertEquals(2, alder.getRecolorFind().length);
		assertEquals(LivelyAnimation.Fletching, alder.getIdleAnimation());
		assertNull(alder.getMoveAnimation());
		assertNull(alder.getScale());
		assertNull(alder.getTranslate());
		assertTrue(alder.getMergedObjects().isEmpty());

		EntityDefinition bryn = named.get("Bryn the wanderer");
		assertNotNull(bryn);
		assertEquals(EntityType.WanderingCitizen, bryn.getType());
		assertEquals(new WorldPoint(3111, 3222, 1), bryn.getWorldLocation());
		assertEquals(1, bryn.getPlane());
		assertEquals(1536, bryn.getOrientation());
		assertEquals(0, bryn.getRecolorFind().length);
		assertEquals(LivelyAnimation.HumanIdle, bryn.getIdleAnimation());
		assertEquals(LivelyAnimation.HumanWalk, bryn.getMoveAnimation());
		assertNotNull(bryn.getScale());
		assertEquals(-0.8f, bryn.getScale()[0], 0.0001f);
		assertEquals(-0.7f, bryn.getScale()[1], 0.0001f);
		assertEquals(-0.6f, bryn.getScale()[2], 0.0001f);

		// Scenery carries no name, so it is the one entry keyed under null.
		EntityDefinition scenery = named.get(null);
		assertNotNull(scenery);
		assertEquals(EntityType.Scenery, scenery.getType());
		assertTrue(!scenery.getType().isCitizen());
		assertEquals(new WorldPoint(3300, 3399, 2), scenery.getWorldLocation());
		assertEquals(1024, scenery.getOrientation());
		assertArrayEqualsInt(new int[]{1569, 2491, 2468}, scenery.getModelIds());
		assertNull(scenery.getIdleAnimation());
		assertNotNull(scenery.getTranslate());
		assertEquals(0.7f, scenery.getTranslate()[1], 0.0001f);
		assertEquals(1, scenery.getMergedObjects().size());
		assertEquals(7719, scenery.getMergedObjects().get(0).getObjectId());
		assertEquals(2, scenery.getMergedObjects().get(0).getRotations());
	}

	@Test
	public void skipsUnusableRecordsAndKeepsTheRest()
	{
		RegionDefinition region = loader.loadRegion(1002);
		assertNotNull(region);

		// Six records cannot render: unknown type, no location, empty models,
		// all-unusable model ids, absent type, location missing its plane.
		assertEquals(6, region.getSkippedRecords());
		assertEquals(6, region.getEntityCount());
		assertEquals(5, region.getCitizenCount());
		assertEquals(1, region.getSceneryCount());

		Map<String, EntityDefinition> named = byName(region.getEntities());
		assertNotNull(named.get("Survivor"));
		assertNull(named.get("Skip: unknown type"));
		assertNull(named.get("Skip: no location"));
		assertNull(named.get("Skip: empty models"));
		assertNull(named.get("Skip: all model ids unusable"));
		assertNull(named.get("Skip: type absent"));
		assertNull(named.get("Skip: plane missing from location"));
	}

	@Test
	public void degradesRatherThanSkipsWhenTheRecordCanStillRender()
	{
		RegionDefinition region = loader.loadRegion(1002);
		assertNotNull(region);
		Map<String, EntityDefinition> named = byName(region.getEntities());

		// An unknown animation name means a static model, not a lost entity.
		EntityDefinition degraded = named.get("Degraded animation");
		assertNotNull(degraded);
		assertNull(degraded.getIdleAnimation());

		// Three finds, one replace: only the matched pair survives.
		EntityDefinition lopsided = named.get("Lopsided recolours");
		assertNotNull(lopsided);
		assertEquals(1, lopsided.getRecolorFind().length);
		assertEquals(1, lopsided.getRecolorReplace().length);
		assertEquals((short) 11, lopsided.getRecolorFind()[0]);
		assertEquals((short) 99, lopsided.getRecolorReplace()[0]);

		// 2560 jau is 512 once wrapped into a full rotation.
		EntityDefinition spun = named.get("Over-spun");
		assertNotNull(spun);
		assertEquals(EntityType.ScriptedCitizen, spun.getType());
		assertEquals(512, spun.getOrientation());

		// Non-positive model ids are dropped individually.
		EntityDefinition mixed = named.get("Mixed model ids");
		assertNotNull(mixed);
		assertArrayEqualsInt(new int[]{42}, mixed.getModelIds());

		// A two-component translate is ignored, not applied half-way.
		EntityDefinition scenery = named.get(null);
		assertNotNull(scenery);
		assertNull(scenery.getTranslate());
	}

	/**
	 * The named anti-pattern: the predecessor returned null for the whole file
	 * when {@code version != 0.8f}, so one bumped number emptied a city.
	 */
	@Test
	public void loadsEverythingDespiteAnUnexpectedSchemaVersion()
	{
		RegionDefinition region = loader.loadRegion(1003);
		assertNotNull("an unexpected version must not discard the region", region);
		assertEquals(9.9f, region.getVersion(), 0.0001f);
		assertEquals(2, region.getEntityCount());
		assertEquals(1, region.getCitizenCount());
		assertEquals(1, region.getSceneryCount());
		assertEquals(0, region.getSkippedRecords());
	}

	@Test
	public void oneUnbindableRecordDoesNotTakeItsNeighbourDown()
	{
		RegionDefinition region = loader.loadRegion(1007);
		assertNotNull(region);
		assertEquals(1, region.getSkippedRecords());
		assertEquals(1, region.getEntityCount());
		assertEquals("Neighbour survives", region.getEntities().get(0).getName());
	}

	@Test
	public void malformedJsonYieldsNullRatherThanAnException()
	{
		assertNull(loader.loadRegion(1004));
	}

	@Test
	public void rootThatIsNotAnObjectYieldsNull()
	{
		assertNull(loader.loadRegion(1005));
	}

	@Test
	public void rostersOfTheWrongShapeLoadAsEmpty()
	{
		RegionDefinition region = loader.loadRegion(1006);
		assertNotNull(region);
		assertEquals(0, region.getEntityCount());
		assertEquals(0, region.getSkippedRecords());
		// The file name is authoritative even though the body says 6006.
		assertEquals(1006, region.getRegionId());
	}

	@Test
	public void absentRegionFileYieldsNull()
	{
		assertNull(loader.loadRegion(999999));
	}

	/**
	 * The shipped dataset, loaded through the real resource prefix. Audited by
	 * hand: 27 files, 142 citizens (103 vendored, plus the six cameos and the 33
	 * townsfolk authored for the five thin cities on 2026-08-29), 42 scenery, every
	 * record valid.
	 */
	@Test
	public void loadsTheWholeShippedDatasetWithoutSkippingAnything()
	{
		RegionDataLoader shipped = new RegionDataLoader(TestGson.injected());

		int files = 0;
		int citizens = 0;
		int scenery = 0;
		int skipped = 0;

		for (int regionId : ShippedRegions.ids())
		{
			RegionDefinition region = shipped.loadRegion(regionId);
			assertNotNull("region " + regionId + " failed to load", region);
			files++;
			citizens += region.getCitizenCount();
			scenery += region.getSceneryCount();
			skipped += region.getSkippedRecords();
			assertEquals("region " + regionId + " has the wrong id", regionId, region.getRegionId());
			assertEquals(RegionDataLoader.EXPECTED_VERSION, region.getVersion(), 0.0001f);
		}

		assertEquals("region file count", 27, files);
		assertEquals("citizen count", 142, citizens);
		assertEquals("scenery count", 42, scenery);
		assertEquals("nothing in the shipped data should be skipped", 0, skipped);
	}

	/**
	 * The three citizen flavours, counted — the row the README's headline table quotes.
	 *
	 * <p>That row said "51 wander, 86 stand still, 5 follow a script" until 2026-08-29,
	 * and the last third of it was false: {@code startScript} is parsed and executed
	 * nowhere (see {@code ShippedSourceTest.noShippedClassReadsTheStartScriptField}), so
	 * the five {@code ScriptedCitizen}s stand as still as the 86. The row now reads "51
	 * wander, 91 stand still" with the five named as a limitation, and this is what stops
	 * the numbers behind it drifting again. It is also the count that would notice a
	 * {@code ScriptedCitizen} quietly becoming something else, which the 142 total would
	 * not.
	 */
	@Test
	public void theShippedRosterSplitsIntoTheThreeCitizenFlavoursTheReadmeQuotes()
	{
		RegionDataLoader shipped = new RegionDataLoader(TestGson.injected());

		Map<EntityType, Integer> byType = new EnumMap<>(EntityType.class);
		for (EntityType type : EntityType.values())
		{
			byType.put(type, 0);
		}

		for (int regionId : ShippedRegions.ids())
		{
			RegionDefinition region = shipped.loadRegion(regionId);
			assertNotNull("region " + regionId + " failed to load", region);
			for (EntityDefinition entity : region.getEntities())
			{
				byType.merge(entity.getType(), 1, Integer::sum);
			}
		}

		assertEquals("wandering citizens", 51, (int) byType.get(EntityType.WanderingCitizen));
		assertEquals("stationary citizens", 86, (int) byType.get(EntityType.StationaryCitizen));
		assertEquals("scripted citizens, which behave exactly like stationary ones because "
			+ "nothing runs their script", 5, (int) byType.get(EntityType.ScriptedCitizen));
		assertEquals("scenery", 42, (int) byType.get(EntityType.Scenery));

		assertEquals("the four flavours have to be the whole dataset", 184,
			byType.values().stream().mapToInt(Integer::intValue).sum());
		assertEquals("and the README's \"91 stand still\" is the two motionless flavours "
				+ "added together", 91,
			byType.get(EntityType.StationaryCitizen) + byType.get(EntityType.ScriptedCitizen));
	}

	/**
	 * How many citizens each city holds, city by city.
	 *
	 * <p><b>The total is not this claim.</b> The test above pins 142 citizens across
	 * 27 files, and 142 is a sum: deleting a figure from Draynor and adding one to
	 * Varrock leaves it at 142 and leaves every other count in the suite — the echo
	 * figures, the remarks partition, the wander-box count — reachable by luck. What
	 * the 2026-08-29 top-up actually claims is that five thin cities were brought to
	 * <b>ten citizens each</b>, and that is a per-city claim which nothing was
	 * checking. {@code docs/CITY-TOP-UP-CHECK.md} told a reader this class held it,
	 * and it did not; this is the assertion that makes the sentence true.
	 *
	 * <p>All nine cities are pinned rather than the five that moved, because "ten
	 * each" only means anything alongside the ones that are deliberately not ten:
	 * Varrock is 63 and the Grand Exchange is 9 (its tenth entity is scenery, and
	 * six of the nine are cameos that are off by default). A row that changes says
	 * which city changed, which the total never could.
	 *
	 * <p>Counted by the <b>file</b> a record ships in, which is how {@link City}
	 * resolves a checkbox — not by the {@code regionId} written inside the record.
	 * The two disagree for one shipped citizen ("Dark wizard", who claims 12853 and
	 * stands in 12852), and it is the file that decides which city's checkbox turns
	 * a figure off.
	 */
	@Test
	public void everyCityHoldsTheNumberOfCitizensItIsSupposedTo()
	{
		RegionDataLoader shipped = new RegionDataLoader(TestGson.injected());

		Map<City, Integer> expected = new EnumMap<>(City.class);
		expected.put(City.AL_KHARID, 10);
		expected.put(City.ARDOUGNE, 10);
		expected.put(City.CATHERBY, 10);
		expected.put(City.DRAYNOR, 10);
		expected.put(City.EDGEVILLE, 4);
		expected.put(City.FALADOR, 10);
		expected.put(City.GRAND_EXCHANGE, 9);
		expected.put(City.LUMBRIDGE, 16);
		expected.put(City.VARROCK, 63);

		assertEquals("every city has to be listed, or one could empty out unnoticed",
			City.values().length, expected.size());

		int total = 0;
		int regions = 0;
		for (City city : City.values())
		{
			int citizens = 0;
			for (int regionId : city.getRegionIds())
			{
				RegionDefinition region = shipped.loadRegion(regionId);
				assertNotNull("region " + regionId + " failed to load", region);
				citizens += region.getCitizenCount();
				regions++;
			}

			assertEquals("citizens in " + city.getLabel(),
				(int) expected.get(city), citizens);
			total += citizens;
		}

		// Both halves, because either alone can be satisfied by an accident: the
		// per-city rows would still pass if a tenth region file went unclaimed by any
		// city, and the totals would still pass if a figure moved between two cities.
		assertEquals("the nine cities between them claim every shipped region file",
			27, regions);
		assertEquals("and hold every shipped citizen", 142, total);
	}

	private static void assertArrayEqualsInt(int[] expected, int[] actual)
	{
		assertEquals("length", expected.length, actual.length);
		for (int i = 0; i < expected.length; i++)
		{
			assertEquals("element " + i, expected[i], actual[i]);
		}
	}
}
