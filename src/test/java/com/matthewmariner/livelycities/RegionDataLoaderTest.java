package com.matthewmariner.livelycities;

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
 * be pointed at deliberately broken data without touching the 45 shipped files.
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
	 * hand: 45 files, 135 citizens (129 vendored plus the six cameos), 46 scenery,
	 * every record valid.
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

		assertEquals("region file count", 45, files);
		assertEquals("citizen count", 135, citizens);
		assertEquals("scenery count", 46, scenery);
		assertEquals("nothing in the shipped data should be skipped", 0, skipped);
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
