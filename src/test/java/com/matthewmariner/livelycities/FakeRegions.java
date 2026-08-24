package com.matthewmariner.livelycities;

import com.matthewmariner.livelycities.data.EntityRecord;
import com.matthewmariner.livelycities.data.PointRecord;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.runelite.api.coords.WorldPoint;
import static org.junit.Assert.assertNotNull;

/**
 * A {@link RegionDataLoader} that serves region files built in memory.
 *
 * <p>The scene tests are about lifecycle, not parsing: they need ninety entities
 * in one region, or one entity six tiles the wrong side of a border, and writing
 * those as JSON fixtures would put the interesting part of each test in a
 * different file from the assertion. Definitions still come out of the real
 * {@link EntityDefinition#fromRecord} — only the bytes on disk are skipped.
 */
final class FakeRegions extends RegionDataLoader
{
	private final Map<Integer, RegionDefinition> regions = new HashMap<>();
	private final List<Integer> loadCalls = new ArrayList<>();
	private int uuids;

	FakeRegions()
	{
		super(TestGson.injected(), "TestRegionData/");
	}

	@Override
	@Nullable
	public RegionDefinition loadRegion(int regionId)
	{
		loadCalls.add(regionId);
		return regions.get(regionId);
	}

	/**
	 * Files entities into {@code <fileRegionId>.json}, wherever their tiles
	 * happen to be — which is the whole point of some of these tests.
	 */
	FakeRegions file(int fileRegionId, List<EntityDefinition> entities)
	{
		regions.put(fileRegionId, new RegionDefinition(
			fileRegionId, RegionDataLoader.EXPECTED_VERSION, entities, 0));
		return this;
	}

	FakeRegions file(int fileRegionId, EntityDefinition... entities)
	{
		return file(fileRegionId, new ArrayList<>(Arrays.asList(entities)));
	}

	/**
	 * One citizen, filed under {@code fileRegionId}, standing on the given tile.
	 */
	EntityDefinition citizen(int fileRegionId, int x, int y, int plane, int... modelIds)
	{
		EntityRecord record = new EntityRecord();
		record.entityType = "StationaryCitizen";
		record.name = "Citizen " + (++uuids);
		record.uuid = String.format("00000000-0000-4000-8000-%012d", uuids);
		record.worldLocation = point(x, y, plane);
		record.modelIds = modelIds.length == 0 ? new int[]{100} : modelIds;

		EntityDefinition definition = EntityDefinition.fromRecord(record, fileRegionId);
		assertNotNull("the fake built an unusable definition", definition);
		return definition;
	}

	/**
	 * One citizen carrying a recolour palette, which is what makes it seed
	 * {@link CitizenEcho} echoes.
	 *
	 * <p>Needed rather than an extra argument on {@link #citizen}, because "a citizen
	 * that seeds echoes" and "a citizen that seeds none" are two fixtures the crowd
	 * tests need side by side: 49 of the 129 shipped citizens have too little palette
	 * to re-deal, so a fixture where everybody seeds echoes could not tell "the dial
	 * added the derived citizens" from "the dial doubled everything".
	 *
	 * <p>{@code pairs} is what decides how many echoes it seeds, and the numbers here
	 * are chosen so the relationship is visible in the fixture rather than buried:
	 * {@code find} is {@code 1..pairs} and {@code replace} is
	 * {@code 101..100+pairs}, all distinct, so every rotation of the palette is a
	 * distinct re-deal and the citizen seeds {@code min(pairs - 1,
	 * CitizenEcho.MAX_ECHOES_PER_CITIZEN)} echoes. Two pairs therefore seeds one echo
	 * and three or more seeds two.
	 */
	EntityDefinition recoloured(int fileRegionId, int x, int y, int pairs)
	{
		EntityRecord record = new EntityRecord();
		record.entityType = "StationaryCitizen";
		record.name = "Recoloured citizen " + (++uuids);
		record.uuid = String.format("00000000-0000-4000-8000-%012d", uuids);
		record.examineText = "A citizen with a wardrobe.";
		record.worldLocation = point(x, y, 0);
		record.modelIds = new int[]{100};

		record.modelRecolorFind = new int[pairs];
		record.modelRecolorReplace = new int[pairs];
		for (int i = 0; i < pairs; i++)
		{
			record.modelRecolorFind[i] = i + 1;
			record.modelRecolorReplace[i] = 101 + i;
		}

		EntityDefinition definition = EntityDefinition.fromRecord(record, fileRegionId);
		assertNotNull("the fake built an unusable recoloured citizen", definition);
		return definition;
	}

	/**
	 * One citizen that both carries a recolour palette and has something to say.
	 *
	 * <p><b>This fixture exists because of a mutation test.</b> Making
	 * {@code EntityDefinition.echoOf} hand an echo its source's {@code remarks} array
	 * instead of the empty one left the whole suite green: {@link #recoloured} has
	 * nothing to say and {@link #talker} has no palette, so no source in any fixture
	 * had both, and "an echo never speaks" was a claim no test could tell from a
	 * fixture coincidence. 33 of the 129 shipped citizens have both.
	 */
	EntityDefinition recolouredTalker(int fileRegionId, int x, int y, int pairs, String... remarks)
	{
		EntityRecord record = new EntityRecord();
		record.entityType = "StationaryCitizen";
		record.name = "Recoloured talker " + (++uuids);
		record.uuid = String.format("00000000-0000-4000-8000-%012d", uuids);
		record.examineText = "A citizen with a wardrobe and an opinion.";
		record.remarks = remarks;
		record.worldLocation = point(x, y, 0);
		record.modelIds = new int[]{100};

		record.modelRecolorFind = new int[pairs];
		record.modelRecolorReplace = new int[pairs];
		for (int i = 0; i < pairs; i++)
		{
			record.modelRecolorFind[i] = i + 1;
			record.modelRecolorReplace[i] = 101 + i;
		}

		EntityDefinition definition = EntityDefinition.fromRecord(record, fileRegionId);
		assertNotNull("the fake built an unusable recoloured talker", definition);
		assertNotNull("the fixture is pointless if the remarks were dropped", definition.getRemarks());
		return definition;
	}

	/**
	 * A row of {@link #recoloured} citizens on consecutive tiles, spaced so their
	 * echoes have somewhere to go.
	 *
	 * <p>Three tiles apart rather than adjacent: an echo stands
	 * {@link CitizenEcho#MIN_SEPARATION_TILES} from its source, so a shoulder-to-
	 * shoulder row would put every echo on top of the next citizen and the fixture
	 * would be testing tile collisions instead of the crowd dial.
	 */
	List<EntityDefinition> recolouredCrowd(int fileRegionId, int x, int y, int count, int pairs)
	{
		List<EntityDefinition> out = new ArrayList<>(count);
		for (int i = 0; i < count; i++)
		{
			out.add(recoloured(fileRegionId, x + (i % 4) * 3, y + (i / 4) * 3, pairs));
		}
		return out;
	}

	/**
	 * One wandering citizen carrying a recolour palette, so it seeds echoes that
	 * stand on the authored ground of its own box.
	 */
	EntityDefinition recolouredWanderer(
		int fileRegionId, WorldPoint base, WorldPoint bl, WorldPoint tr, int pairs)
	{
		EntityRecord record = new EntityRecord();
		record.entityType = "WanderingCitizen";
		record.name = "Recoloured wanderer " + (++uuids);
		record.uuid = String.format("00000000-0000-4000-8000-%012d", uuids);
		record.worldLocation = point(base.getX(), base.getY(), base.getPlane());
		record.wanderBoxBL = point(bl.getX(), bl.getY(), bl.getPlane());
		record.wanderBoxTR = point(tr.getX(), tr.getY(), tr.getPlane());
		record.baseOrientation = 512;
		record.idleAnimation = "HumanIdle";
		record.moveAnimation = "HumanWalk";
		record.modelIds = new int[]{100};

		record.modelRecolorFind = new int[pairs];
		record.modelRecolorReplace = new int[pairs];
		for (int i = 0; i < pairs; i++)
		{
			record.modelRecolorFind[i] = i + 1;
			record.modelRecolorReplace[i] = 101 + i;
		}

		EntityDefinition definition = EntityDefinition.fromRecord(record, fileRegionId);
		assertNotNull("the fake built an unusable recoloured wanderer", definition);
		assertNotNull("the fake's wanderer lost its box in validation", definition.getWanderBox());
		return definition;
	}

	/**
	 * One citizen that stands still but has an idle animation.
	 *
	 * <p>Needed rather than convenient: a wanderer's animation gets re-selected by
	 * {@code advanceTick} every game tick, so it could not tell "the visibility
	 * pass asked again" from "the walk asked again". A {@code StationaryCitizen}
	 * has no other clock at all, which is exactly the case a latched animation
	 * would strand for the session.
	 */
	EntityDefinition animatedCitizen(int fileRegionId, int x, int y, String idleAnimation)
	{
		EntityRecord record = new EntityRecord();
		record.entityType = "StationaryCitizen";
		record.name = "Animated citizen " + (++uuids);
		record.uuid = String.format("00000000-0000-4000-8000-%012d", uuids);
		record.worldLocation = point(x, y, 0);
		record.idleAnimation = idleAnimation;
		record.modelIds = new int[]{100};

		EntityDefinition definition = EntityDefinition.fromRecord(record, fileRegionId);
		assertNotNull("the fake built an unusable definition", definition);
		return definition;
	}

	/**
	 * One citizen with something to say.
	 *
	 * <p>Separate from {@link #citizen} rather than an extra argument to it, because
	 * "a citizen with remarks" and "a citizen with none" are two fixtures the chatter
	 * tests need side by side in the same scene: 96 of the 129 shipped citizens have
	 * nothing authored, so a fixture where everybody talks would not be able to tell
	 * "the chatter skipped the silent ones" from "the chatter iterated everybody".
	 *
	 * <p>{@code examineText} is filled in as well. Every shipped citizen has one, and
	 * a fixture without it could not distinguish the Examine line's two halves.
	 */
	EntityDefinition talker(int fileRegionId, int x, int y, String... remarks)
	{
		EntityRecord record = new EntityRecord();
		record.entityType = "StationaryCitizen";
		record.name = "Talker " + (++uuids);
		record.uuid = String.format("00000000-0000-4000-8000-%012d", uuids);
		record.examineText = "A talkative citizen.";
		record.remarks = remarks;
		record.worldLocation = point(x, y, 0);
		record.modelIds = new int[]{100};

		EntityDefinition definition = EntityDefinition.fromRecord(record, fileRegionId);
		assertNotNull("the fake built an unusable talker", definition);
		return definition;
	}

	/**
	 * A row of talkers on consecutive tiles, every one saying the same thing.
	 *
	 * <p>The remark is uniform on purpose here and only here: these fixtures exist to
	 * test the <i>cap</i> and the <i>radius</i>, where what is being said is not the
	 * variable. Their tiles are not uniform, which is the axis those tests read.
	 */
	List<EntityDefinition> talkingCrowd(int fileRegionId, int x, int y, int count)
	{
		List<EntityDefinition> out = new ArrayList<>(count);
		for (int i = 0; i < count; i++)
		{
			out.add(talker(fileRegionId, x + (i % 6), y + (i / 6), "Busy today."));
		}
		return out;
	}

	/**
	 * One piece of scenery — which the dataset gives no name, examine or remarks.
	 *
	 * <p>{@code remarks} is settable anyway, so that "scenery is silenced at the
	 * validation gate" is a test about {@link EntityDefinition} rather than a test
	 * about the shipped files happening not to contain the case.
	 */
	EntityDefinition scenery(int fileRegionId, int x, int y, String... remarks)
	{
		EntityRecord record = new EntityRecord();
		record.entityType = "Scenery";
		record.uuid = String.format("00000000-0000-4000-8000-%012d", ++uuids);
		record.remarks = remarks;
		record.worldLocation = point(x, y, 0);
		record.modelIds = new int[]{100};

		EntityDefinition definition = EntityDefinition.fromRecord(record, fileRegionId);
		assertNotNull("the fake built unusable scenery", definition);
		return definition;
	}

	/**
	 * One wandering citizen, filed under {@code fileRegionId}, standing on
	 * {@code base} and pacing the inclusive box {@code bl}..{@code tr}.
	 *
	 * <p>{@code baseOrientation} is a required argument rather than a default: 0
	 * is a perfectly good travel orientation (due south), so a fixture that left
	 * it at 0 could not tell "returned to its base orientation" from "never set
	 * an orientation at all".
	 */
	EntityDefinition wanderer(
		int fileRegionId,
		WorldPoint base,
		WorldPoint bl,
		WorldPoint tr,
		int baseOrientation)
	{
		EntityRecord record = new EntityRecord();
		record.entityType = "WanderingCitizen";
		record.name = "Wanderer " + (++uuids);
		record.uuid = String.format("00000000-0000-4000-8000-%012d", uuids);
		record.worldLocation = point(base.getX(), base.getY(), base.getPlane());
		record.wanderBoxBL = point(bl.getX(), bl.getY(), bl.getPlane());
		record.wanderBoxTR = point(tr.getX(), tr.getY(), tr.getPlane());
		record.baseOrientation = baseOrientation;
		record.idleAnimation = "HumanIdle";
		record.moveAnimation = "HumanWalk";
		record.modelIds = new int[]{100};

		EntityDefinition definition = EntityDefinition.fromRecord(record, fileRegionId);
		assertNotNull("the fake built an unusable wanderer", definition);
		assertNotNull("the fake's wanderer lost its box in validation", definition.getWanderBox());
		return definition;
	}

	/**
	 * A row of citizens on consecutive tiles, so a crowd can be described in one
	 * line. Every tile stays inside the region it is filed under.
	 */
	List<EntityDefinition> crowd(int fileRegionId, int x, int y, int count, int... modelIds)
	{
		List<EntityDefinition> out = new ArrayList<>(count);
		for (int i = 0; i < count; i++)
		{
			// Six-wide rows keep the crowd inside one cull radius.
			out.add(citizen(fileRegionId, x + (i % 6), y + (i / 6), 0, modelIds));
		}
		return out;
	}

	List<Integer> loadCalls()
	{
		return loadCalls;
	}

	private static PointRecord point(int x, int y, int plane)
	{
		PointRecord p = new PointRecord();
		p.x = x;
		p.y = y;
		p.plane = plane;
		return p;
	}
}
