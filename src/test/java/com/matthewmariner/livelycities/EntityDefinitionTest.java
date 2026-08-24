package com.matthewmariner.livelycities;

import com.matthewmariner.livelycities.data.EntityRecord;
import com.matthewmariner.livelycities.data.MergedObjectRecord;
import com.matthewmariner.livelycities.data.PointRecord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Validation is exercised directly here, rather than only through JSON, so each
 * rule has its own named case and a failure points at one rule.
 */
public class EntityDefinitionTest
{
	private static final int REGION = 12853;

	/**
	 * Shared with {@link RenderPolicyTest}: builds a minimal valid definition.
	 */
	static EntityDefinition definition(String type, int x, int y, int plane, int[] modelIds)
	{
		EntityRecord record = record(type);
		record.worldLocation = point(x, y, plane);
		record.modelIds = modelIds;
		return EntityDefinition.fromRecord(record, REGION);
	}

	private static EntityRecord record(String type)
	{
		EntityRecord record = new EntityRecord();
		record.entityType = type;
		record.uuid = "44444444-4444-4444-8444-444444444444";
		record.name = "Test subject";
		record.worldLocation = point(3238, 3425, 0);
		record.modelIds = new int[]{235, 248};
		return record;
	}

	private static PointRecord point(Integer x, Integer y, Integer plane)
	{
		PointRecord p = new PointRecord();
		p.x = x;
		p.y = y;
		p.plane = plane;
		return p;
	}

	@Test
	public void nullRecordIsSkipped()
	{
		assertNull(EntityDefinition.fromRecord(null, REGION));
	}

	@Test
	public void unknownOrMissingEntityTypeIsSkipped()
	{
		assertNull(EntityDefinition.fromRecord(record("MaintenanceRobot"), REGION));
		assertNull(EntityDefinition.fromRecord(record(null), REGION));
		assertNull(EntityDefinition.fromRecord(record(""), REGION));
		// The dataset's four types all survive.
		assertNotNull(EntityDefinition.fromRecord(record("StationaryCitizen"), REGION));
		assertNotNull(EntityDefinition.fromRecord(record("WanderingCitizen"), REGION));
		assertNotNull(EntityDefinition.fromRecord(record("ScriptedCitizen"), REGION));
		assertNotNull(EntityDefinition.fromRecord(record("Scenery"), REGION));
	}

	@Test
	public void scriptedCitizensAreParsedAndKeepTheirType()
	{
		// L1 spawns them stationary, but the type must survive for L3.
		EntityDefinition scripted = EntityDefinition.fromRecord(record("ScriptedCitizen"), REGION);
		assertNotNull(scripted);
		assertEquals(EntityType.ScriptedCitizen, scripted.getType());
		assertTrue(scripted.getType().isCitizen());
	}

	@Test
	public void locationsThatCannotBePlacedAreSkipped()
	{
		EntityRecord noLocation = record("StationaryCitizen");
		noLocation.worldLocation = null;
		assertNull(EntityDefinition.fromRecord(noLocation, REGION));

		EntityRecord noPlane = record("StationaryCitizen");
		noPlane.worldLocation = point(3238, 3425, null);
		assertNull(EntityDefinition.fromRecord(noPlane, REGION));

		EntityRecord noX = record("StationaryCitizen");
		noX.worldLocation = point(null, 3425, 0);
		assertNull(EntityDefinition.fromRecord(noX, REGION));

		// A zero coordinate is the world origin, i.e. certainly not authored.
		EntityRecord originX = record("StationaryCitizen");
		originX.worldLocation = point(0, 3425, 0);
		assertNull(EntityDefinition.fromRecord(originX, REGION));

		EntityRecord highPlane = record("StationaryCitizen");
		highPlane.worldLocation = point(3238, 3425, 7);
		assertNull(EntityDefinition.fromRecord(highPlane, REGION));

		EntityRecord negativePlane = record("StationaryCitizen");
		negativePlane.worldLocation = point(3238, 3425, -1);
		assertNull(EntityDefinition.fromRecord(negativePlane, REGION));

		// Plane 3 is the top legal storey and must survive.
		EntityRecord topPlane = record("StationaryCitizen");
		topPlane.worldLocation = point(3238, 3425, 3);
		assertNotNull(EntityDefinition.fromRecord(topPlane, REGION));
	}

	@Test
	public void recordsWithNothingToRenderAreSkipped()
	{
		EntityRecord noModels = record("Scenery");
		noModels.modelIds = null;
		assertNull(EntityDefinition.fromRecord(noModels, REGION));

		EntityRecord emptyModels = record("Scenery");
		emptyModels.modelIds = new int[0];
		assertNull(EntityDefinition.fromRecord(emptyModels, REGION));

		EntityRecord unusableModels = record("Scenery");
		unusableModels.modelIds = new int[]{0, -12};
		assertNull(EntityDefinition.fromRecord(unusableModels, REGION));
	}

	@Test
	public void unusableModelIdsAreDroppedIndividually()
	{
		EntityRecord mixed = record("Scenery");
		mixed.modelIds = new int[]{0, 235, -1, 248};

		EntityDefinition definition = EntityDefinition.fromRecord(mixed, REGION);
		assertNotNull(definition);
		assertEquals(2, definition.getModelIds().length);
		assertEquals(235, definition.getModelIds()[0]);
		assertEquals(248, definition.getModelIds()[1]);
	}

	@Test
	public void recolourArraysAreTruncatedToMatchedPairs()
	{
		EntityRecord lopsided = record("StationaryCitizen");
		lopsided.modelRecolorFind = new int[]{11, 22, 33};
		lopsided.modelRecolorReplace = new int[]{99, 88};

		EntityDefinition definition = EntityDefinition.fromRecord(lopsided, REGION);
		assertNotNull(definition);
		assertEquals(2, definition.getRecolorFind().length);
		assertEquals(2, definition.getRecolorReplace().length);
		assertEquals((short) 11, definition.getRecolorFind()[0]);
		assertEquals((short) 22, definition.getRecolorFind()[1]);
		assertEquals((short) 99, definition.getRecolorReplace()[0]);
		assertEquals((short) 88, definition.getRecolorReplace()[1]);
	}

	@Test
	public void oneSidedRecolourArraysYieldNoPairs()
	{
		EntityRecord findOnly = record("StationaryCitizen");
		findOnly.modelRecolorFind = new int[]{11, 22};
		findOnly.modelRecolorReplace = null;

		EntityDefinition definition = EntityDefinition.fromRecord(findOnly, REGION);
		assertNotNull(definition);
		assertEquals(0, definition.getRecolorFind().length);
		assertEquals(0, definition.getRecolorReplace().length);
	}

	@Test
	public void colourValuesWrapIntoSignedShorts()
	{
		EntityRecord record = record("StationaryCitizen");
		// Both are real values from the shipped dataset, whose recolour range
		// runs 0..65313 — i.e. past what a signed short holds.
		record.modelRecolorFind = new int[]{43030, 25238};
		record.modelRecolorReplace = new int[]{59437, 10520};

		EntityDefinition definition = EntityDefinition.fromRecord(record, REGION);
		assertNotNull(definition);
		// 43030 overflows a signed short; the client's recolor() takes exactly
		// the wrapped 16-bit value, so the negative must be preserved, not
		// clamped and not rejected.
		assertEquals((short) 43030, definition.getRecolorFind()[0]);
		assertEquals(-22506, definition.getRecolorFind()[0]);
		assertTrue(definition.getRecolorFind()[0] < 0);
		assertEquals((short) 59437, definition.getRecolorReplace()[0]);
		assertTrue(definition.getRecolorReplace()[0] < 0);
		// And a value that does fit is left alone.
		assertEquals(25238, definition.getRecolorFind()[1]);
		assertEquals(10520, definition.getRecolorReplace()[1]);
	}

	@Test
	public void orientationIsWrappedIntoOneRotation()
	{
		assertEquals(0, orientationOf(null));
		assertEquals(0, orientationOf(0));
		assertEquals(512, orientationOf(512));
		assertEquals(2047, orientationOf(2047));
		assertEquals(0, orientationOf(2048));
		assertEquals(512, orientationOf(2560));
		assertEquals(1792, orientationOf(-256));
		// A non-cardinal value is legal and must pass through untouched:
		// region 12338 ships a baseOrientation of 500.
		assertEquals(500, orientationOf(500));
	}

	@Test
	public void unknownAnimationNamesDegradeInsteadOfSkipping()
	{
		EntityRecord record = record("StationaryCitizen");
		record.idleAnimation = "PolishingTheBrasswork";
		record.moveAnimation = "HumanWalk";

		EntityDefinition definition = EntityDefinition.fromRecord(record, REGION);
		assertNotNull("an unknown animation must not cost the entity", definition);
		assertNull(definition.getIdleAnimation());
		assertEquals(LivelyAnimation.HumanWalk, definition.getMoveAnimation());
	}

	@Test
	public void malformedScaleAndTranslateAreIgnoredWholesale()
	{
		EntityRecord shortVector = record("Scenery");
		shortVector.scale = new float[]{0.5f, 0.5f};
		shortVector.translate = new float[]{0.1f, 0.2f, 0.3f, 0.4f};

		EntityDefinition definition = EntityDefinition.fromRecord(shortVector, REGION);
		assertNotNull(definition);
		assertNull(definition.getScale());
		assertNull(definition.getTranslate());

		EntityRecord good = record("Scenery");
		good.scale = new float[]{-0.8f, -0.7f, -0.6f};
		EntityDefinition scaled = EntityDefinition.fromRecord(good, REGION);
		assertNotNull(scaled);
		assertNotNull(scaled.getScale());
		assertEquals(-0.7f, scaled.getScale()[1], 0.0001f);
	}

	@Test
	public void mergedObjectsAreFilteredAndRotationsNormalised()
	{
		EntityRecord record = record("StationaryCitizen");
		List<MergedObjectRecord> merged = new ArrayList<>();
		merged.add(mergedObject(7719, 2));
		merged.add(mergedObject(null, 1));      // no id -> dropped
		merged.add(mergedObject(0, 1));         // unusable id -> dropped
		merged.add(mergedObject(1234, null));   // missing rotation -> 0
		merged.add(mergedObject(5678, 6));      // 6 quarter-turns -> 2
		merged.add(mergedObject(9012, -3));     // negative -> 0
		merged.add(null);                       // null entry -> dropped
		record.mergedObjects = merged;

		EntityDefinition definition = EntityDefinition.fromRecord(record, REGION);
		assertNotNull(definition);
		assertEquals(4, definition.getMergedObjects().size());
		assertEquals(7719, definition.getMergedObjects().get(0).getObjectId());
		assertEquals(2, definition.getMergedObjects().get(0).getRotations());
		assertEquals(1234, definition.getMergedObjects().get(1).getObjectId());
		assertEquals(0, definition.getMergedObjects().get(1).getRotations());
		assertEquals(5678, definition.getMergedObjects().get(2).getObjectId());
		assertEquals(2, definition.getMergedObjects().get(2).getRotations());
		assertEquals(9012, definition.getMergedObjects().get(3).getObjectId());
		assertEquals(0, definition.getMergedObjects().get(3).getRotations());
	}

	@Test
	public void missingMergedObjectsListIsEmptyNotNull()
	{
		EntityDefinition definition = EntityDefinition.fromRecord(record("Scenery"), REGION);
		assertNotNull(definition);
		assertEquals(Collections.emptyList(), definition.getMergedObjects());
	}

	@Test
	public void fileRegionIdWinsOverTheRecordsClaim()
	{
		EntityRecord record = record("StationaryCitizen");
		record.regionId = 99999;

		EntityDefinition definition = EntityDefinition.fromRecord(record, REGION);
		assertNotNull(definition);
		assertEquals(REGION, definition.getRegionId());
	}

	/**
	 * The file name says how the entity was found; the tile says where it is.
	 * Those are different questions and the shipped data answers them
	 * differently, so both have to survive validation.
	 */
	@Test
	public void theTileDecidesWhichRegionTheEntityStandsIn()
	{
		// The shipped case: "Dark wizard" is authored in 12853.json and stands
		// at (3261, 3386), six tiles inside region 12852.
		EntityRecord misfiled = record("WanderingCitizen");
		misfiled.worldLocation = point(3261, 3386, 0);

		EntityDefinition definition = EntityDefinition.fromRecord(misfiled, REGION);
		assertNotNull("a misfiled entity is still an entity", definition);
		assertEquals(REGION, definition.getRegionId());
		assertEquals(12852, definition.getTileRegionId());

		// And they agree for a record filed where it stands.
		EntityDefinition home = EntityDefinition.fromRecord(record("StationaryCitizen"), REGION);
		assertNotNull(home);
		assertEquals(REGION, home.getRegionId());
		assertEquals(REGION, home.getTileRegionId());
	}

	@Test
	public void aWanderingCitizenKeepsItsBoxNormalisedAndInclusive()
	{
		EntityRecord record = record("WanderingCitizen");
		record.worldLocation = point(3238, 3425, 0);
		// Corners the wrong way round: the field names say bottom-left and
		// top-right, but nothing in the schema enforces it.
		record.wanderBoxBL = point(3242, 3429, 0);
		record.wanderBoxTR = point(3234, 3421, 0);

		EntityDefinition definition = EntityDefinition.fromRecord(record, REGION);
		assertNotNull(definition);
		EntityDefinition.WanderBox box = definition.getWanderBox();
		assertNotNull(box);
		assertEquals(3234, box.getMinX());
		assertEquals(3242, box.getMaxX());
		assertEquals(3421, box.getMinY());
		assertEquals(3429, box.getMaxY());
		assertEquals("inclusive, so a 3234..3242 span is nine tiles", 9, box.getWidth());
		assertEquals(9, box.getHeight());
		assertEquals(0, box.getPlane());
		assertTrue(box.contains(3238, 3425));
		assertTrue("the corners are inside it", box.contains(3234, 3421) && box.contains(3242, 3429));
		assertFalse(box.contains(3233, 3425));
		assertFalse(box.contains(3238, 3430));
	}

	/**
	 * The one piece of geometry this class invents, and the reason it does.
	 *
	 * <p>The cull check measures from the citizen's authored tile, so a box that
	 * reaches further than {@link RenderPolicy#DATASET_OVERHANG_ALLOWANCE} would
	 * let a citizen walk past the edge of the loaded scene, where
	 * {@code LocalPoint.fromWorld} returns null and it simply stops being drawn.
	 * Clamping here makes that impossible for any dataset rather than merely
	 * untrue of this one — no shipped box needs it, which is what
	 * {@code RenderPolicyTest} asserts.
	 */
	@Test
	public void aWanderBoxReachingOutOfTheLoadedSceneIsClampedNotHonoured()
	{
		int allowance = RenderPolicy.DATASET_OVERHANG_ALLOWANCE;

		EntityRecord record = record("WanderingCitizen");
		record.worldLocation = point(3238, 3425, 0);
		record.wanderBoxBL = point(3238 - 100, 3425 - 100, 0);
		record.wanderBoxTR = point(3238 + 100, 3425 + 100, 0);

		EntityDefinition definition = EntityDefinition.fromRecord(record, REGION);
		assertNotNull(definition);
		EntityDefinition.WanderBox box = definition.getWanderBox();
		assertNotNull("clamped, not dropped", box);

		assertEquals(3238 - allowance, box.getMinX());
		assertEquals(3238 + allowance, box.getMaxX());
		assertEquals(3425 - allowance, box.getMinY());
		assertEquals(3425 + allowance, box.getMaxY());
		assertFalse("the far corner must be out of reach",
			box.contains(3238 - allowance - 1, 3425));

		// And a box already inside the allowance is left exactly alone.
		EntityRecord modest = record("WanderingCitizen");
		modest.worldLocation = point(3238, 3425, 0);
		modest.wanderBoxBL = point(3236, 3423, 0);
		modest.wanderBoxTR = point(3240, 3427, 0);
		EntityDefinition untouched = EntityDefinition.fromRecord(modest, REGION);
		assertNotNull(untouched);
		assertNotNull(untouched.getWanderBox());
		assertEquals(3236, untouched.getWanderBox().getMinX());
		assertEquals(3240, untouched.getWanderBox().getMaxX());
	}

	/**
	 * Every way a box can be unusable degrades to a citizen standing still —
	 * never to a skipped record, and never to invented geometry.
	 */
	@Test
	public void anUnusableWanderBoxLeavesTheCitizenStationary()
	{
		// No box at all.
		EntityRecord noBox = record("WanderingCitizen");
		noBox.worldLocation = point(3238, 3425, 0);
		assertStationary(noBox);

		// Only one corner.
		EntityRecord halfBox = record("WanderingCitizen");
		halfBox.worldLocation = point(3238, 3425, 0);
		halfBox.wanderBoxBL = point(3236, 3423, 0);
		assertStationary(halfBox);

		// A corner with no coordinates.
		EntityRecord emptyCorner = record("WanderingCitizen");
		emptyCorner.worldLocation = point(3238, 3425, 0);
		emptyCorner.wanderBoxBL = point(null, 3423, 0);
		emptyCorner.wanderBoxTR = point(3240, 3427, 0);
		assertStationary(emptyCorner);

		// A box on another storey: that is not a walk, it is a fall.
		EntityRecord wrongPlane = record("WanderingCitizen");
		wrongPlane.worldLocation = point(3238, 3425, 0);
		wrongPlane.wanderBoxBL = point(3236, 3423, 1);
		wrongPlane.wanderBoxTR = point(3240, 3427, 1);
		assertStationary(wrongPlane);

		// The citizen standing outside its own box.
		EntityRecord outside = record("WanderingCitizen");
		outside.worldLocation = point(3238, 3425, 0);
		outside.wanderBoxBL = point(3200, 3400, 0);
		outside.wanderBoxTR = point(3210, 3410, 0);
		assertStationary(outside);

		// A single-tile box, which is a walk with nowhere to go.
		EntityRecord oneTile = record("WanderingCitizen");
		oneTile.worldLocation = point(3238, 3425, 0);
		oneTile.wanderBoxBL = point(3238, 3425, 0);
		oneTile.wanderBoxTR = point(3238, 3425, 0);
		assertStationary(oneTile);
	}

	/**
	 * Only wandering citizens get a box, however the record is decorated.
	 * ScriptedCitizens are stationary until a later phase runs their scripts, and
	 * a scripted citizen wandering at random would be worse than one standing
	 * still.
	 */
	@Test
	public void nothingButAWanderingCitizenGetsAWanderBox()
	{
		for (String type : new String[]{"StationaryCitizen", "ScriptedCitizen", "Scenery"})
		{
			EntityRecord record = record(type);
			record.worldLocation = point(3238, 3425, 0);
			record.wanderBoxBL = point(3236, 3423, 0);
			record.wanderBoxTR = point(3240, 3427, 0);

			EntityDefinition definition = EntityDefinition.fromRecord(record, REGION);
			assertNotNull(definition);
			assertNull(type + " must not wander", definition.getWanderBox());
		}
	}

	private static void assertStationary(EntityRecord record)
	{
		EntityDefinition definition = EntityDefinition.fromRecord(record, REGION);
		assertNotNull("an unusable wanderBox must not cost the record", definition);
		assertNull("and must not produce a box", definition.getWanderBox());
	}

	// --- npcAppearanceId and the cameo flag ----------------------------------

	/**
	 * A record with no {@code modelIds} but a plausible {@code npcAppearanceId}
	 * survives the gate, which used to skip anything with no models.
	 */
	@Test
	public void anNpcAppearanceIdIsEnoughOnItsOwnWithNoModelIds()
	{
		EntityRecord record = record("StationaryCitizen");
		record.modelIds = null;
		record.npcAppearanceId = 1798;

		EntityDefinition definition = EntityDefinition.fromRecord(record, REGION);

		assertNotNull("an NPC appearance is a body", definition);
		assertEquals(1798, definition.getNpcAppearanceId());
		assertEquals("and it carries no raw model ids", 0, definition.getModelIds().length);
	}

	/**
	 * Neither is still a skip — and this is the case the old
	 * "no entity has an empty modelIds array" rule could not distinguish from the one
	 * above.
	 */
	@Test
	public void neitherModelIdsNorAnNpcAppearanceIdIsStillASkip()
	{
		EntityRecord empty = record("StationaryCitizen");
		empty.modelIds = new int[0];
		assertNull(EntityDefinition.fromRecord(empty, REGION));

		EntityRecord nulled = record("StationaryCitizen");
		nulled.modelIds = null;
		assertNull(EntityDefinition.fromRecord(nulled, REGION));

		// Non-positive ids are dropped first, so this is "no usable models" too.
		EntityRecord sentinels = record("StationaryCitizen");
		sentinels.modelIds = new int[]{0, -1};
		assertNull(EntityDefinition.fromRecord(sentinels, REGION));
	}

	/**
	 * An implausible {@code npcAppearanceId} is dropped rather than trusted — a
	 * pasted hashcode in that field must not reach {@code client.getNpcDefinition}.
	 *
	 * <p>Dropping it <i>degrades</i> rather than skipping, so a record that also has
	 * models still spawns from them; a record with nothing else left is skipped by the
	 * gate above, which is the same outcome as a record with no models at all.
	 */
	@Test
	public void anImplausibleNpcAppearanceIdIsDroppedAndTheModelIdsSurvive()
	{
		EntityRecord withModels = record("StationaryCitizen");
		withModels.npcAppearanceId = CacheIdPlausibility.MAX_PLAUSIBLE_ID + 1;
		EntityDefinition degraded = EntityDefinition.fromRecord(withModels, REGION);
		assertNotNull("a junk NPC id must not cost the record its models", degraded);
		assertEquals(0, degraded.getNpcAppearanceId());
		assertEquals(2, degraded.getModelIds().length);

		EntityRecord negative = record("StationaryCitizen");
		negative.npcAppearanceId = -1798;
		EntityDefinition alsoDegraded = EntityDefinition.fromRecord(negative, REGION);
		assertNotNull(alsoDegraded);
		assertEquals(0, alsoDegraded.getNpcAppearanceId());

		// Nothing left to build from, so it is skipped.
		EntityRecord only = record("StationaryCitizen");
		only.modelIds = null;
		only.npcAppearanceId = 0;
		assertNull(EntityDefinition.fromRecord(only, REGION));
	}

	/**
	 * When a record carries both, the NPC appearance wins — models and palette alike.
	 *
	 * <p>Documented on {@code EntityRecord.npcAppearanceId} and pinned here. The
	 * {@code modelIds} survive on the definition (nothing gains from erasing them)
	 * but {@code LivelyEntity} never builds them; {@code LivelyEntityTest} asserts
	 * that half, because "which field wins" is only observable at render time.
	 */
	@Test
	public void bothPresentMeansTheNpcAppearanceWinsAndTheRecordKeepsItsModelIds()
	{
		EntityRecord record = record("StationaryCitizen");
		record.modelIds = new int[]{235, 248};
		record.npcAppearanceId = 1798;
		record.modelRecolorFind = new int[]{1};
		record.modelRecolorReplace = new int[]{2};

		EntityDefinition definition = EntityDefinition.fromRecord(record, REGION);

		assertNotNull(definition);
		assertEquals("the NPC id is what the renderer will use", 1798, definition.getNpcAppearanceId());
		assertEquals("the ignored modelIds are still readable, for the log line",
			2, definition.getModelIds().length);
	}

	@Test
	public void anAbsentCameoFlagMeansNotACameo()
	{
		EntityRecord absent = record("StationaryCitizen");
		assertFalse(EntityDefinition.fromRecord(absent, REGION).isCameo());

		EntityRecord explicitlyFalse = record("StationaryCitizen");
		explicitlyFalse.cameo = Boolean.FALSE;
		assertFalse(EntityDefinition.fromRecord(explicitlyFalse, REGION).isCameo());

		EntityRecord flagged = record("StationaryCitizen");
		flagged.cameo = Boolean.TRUE;
		assertTrue(EntityDefinition.fromRecord(flagged, REGION).isCameo());
	}

	/**
	 * Scenery cannot be a cameo. The flag's job is to keep player-shaped named
	 * likenesses behind an opt-in, and a crate is neither — a crate carrying it would
	 * be a crate that a checkbox nobody expects switches off. Same rule, and the same
	 * place, as a talking crate being silenced.
	 */
	@Test
	public void sceneryCannotBeACameoHoweverItIsFlagged()
	{
		EntityRecord crate = record("Scenery");
		crate.cameo = Boolean.TRUE;

		EntityDefinition definition = EntityDefinition.fromRecord(crate, REGION);

		assertNotNull("the flag is ignored, not fatal", definition);
		assertFalse(definition.isCameo());

		// All three citizen flavours may be.
		for (String type : new String[]{"StationaryCitizen", "WanderingCitizen", "ScriptedCitizen"})
		{
			EntityRecord citizen = record(type);
			citizen.cameo = Boolean.TRUE;
			assertTrue(type + " may be a cameo", EntityDefinition.fromRecord(citizen, REGION).isCameo());
		}
	}

	@Test
	public void aMissingOrBrokenUuidGetsOneGeneratedRatherThanSkipping()
	{
		EntityRecord noUuid = record("StationaryCitizen");
		noUuid.uuid = null;
		EntityDefinition generated = EntityDefinition.fromRecord(noUuid, REGION);
		assertNotNull(generated);
		assertNotNull(generated.getUuid());

		EntityRecord badUuid = record("StationaryCitizen");
		badUuid.uuid = "not-a-uuid";
		EntityDefinition recovered = EntityDefinition.fromRecord(badUuid, REGION);
		assertNotNull(recovered);
		assertNotNull(recovered.getUuid());
	}

	private static MergedObjectRecord mergedObject(Integer objectId, Integer rotations)
	{
		MergedObjectRecord m = new MergedObjectRecord();
		m.objectId = objectId;
		m.count90CCWRotations = rotations;
		return m;
	}

	private static int orientationOf(Integer raw)
	{
		EntityRecord record = record("StationaryCitizen");
		record.baseOrientation = raw;
		EntityDefinition definition = EntityDefinition.fromRecord(record, REGION);
		assertNotNull(definition);
		return definition.getOrientation();
	}
}
