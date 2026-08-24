package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The derivation: who seeds an echo, how many, where it stands, what it looks
 * like, and what it is called.
 *
 * <p>Everything here is offline — {@link CitizenEcho#echoesOf} touches nothing but
 * its argument — so these run against the real shipped dataset as well as against
 * hand-built fixtures. The shipped-data tests are the ones that keep the feature's
 * headline claim honest: they recompute the echo count from the 45 vendored region
 * files rather than trusting the number written in a comment.
 */
public class CitizenEchoTest
{
	private static final int VARROCK_SOUTH = 12852;

	// --- Who seeds an echo ----------------------------------------------------

	@Test
	public void aCitizenWithNoRecolourPaletteSeedsNothing()
	{
		FakeRegions regions = new FakeRegions();

		assertTrue("nothing to re-deal means no honest way to make it look different",
			CitizenEcho.echoesOf(regions.citizen(VARROCK_SOUTH, 3225, 3355, 0)).isEmpty());
	}

	@Test
	public void aCitizenWithASinglePairSeedsNothing()
	{
		FakeRegions regions = new FakeRegions();

		assertTrue("one slot cannot be re-dealt into a different slot",
			CitizenEcho.echoesOf(regions.recoloured(VARROCK_SOUTH, 3225, 3355, 1)).isEmpty());
	}

	@Test
	public void twoPairsSeedOneEchoAndThreeOrMoreSeedTwo()
	{
		FakeRegions regions = new FakeRegions();

		assertEquals("two pairs admit exactly one distinct re-deal",
			1, CitizenEcho.echoesOf(regions.recoloured(VARROCK_SOUTH, 3225, 3355, 2)).size());
		assertEquals(CitizenEcho.MAX_ECHOES_PER_CITIZEN,
			CitizenEcho.echoesOf(regions.recoloured(VARROCK_SOUTH, 3230, 3355, 3)).size());
		assertEquals("and the cap holds however rich the palette gets",
			CitizenEcho.MAX_ECHOES_PER_CITIZEN,
			CitizenEcho.echoesOf(regions.recoloured(VARROCK_SOUTH, 3235, 3355, 11)).size());
	}

	/**
	 * A palette whose {@code replace} values are all the same colour.
	 *
	 * <p>Four shipped citizens are like this ("Brother Keptic", "Dark wizard",
	 * "Ambatu", "Sister Palus"). Every rotation of {@code [red, red]} is
	 * {@code [red, red]}, so an echo would be a pixel-for-pixel copy of its source
	 * standing two tiles away — which is the twins failure. Counting pairs alone
	 * would have let them through.
	 */
	@Test
	public void aPaletteThatCannotActuallyBeReDealtSeedsNothing()
	{
		assertEquals(0, CitizenEcho.distinctDeals(new short[]{4769, 4769}).length);
		assertEquals(0, CitizenEcho.distinctDeals(new short[]{10508, 10508, 10508}).length);

		// And a palette that repeats in a pattern offers fewer deals than rotations:
		// [a,b,a,b] has three rotations and one distinct result.
		assertEquals(1, CitizenEcho.distinctDeals(new short[]{7, 9, 7, 9}).length);
	}

	@Test
	public void sceneryNeverSeedsAnEcho()
	{
		FakeRegions regions = new FakeRegions();

		assertTrue("a second market stall two tiles from the first is not a livelier city",
			CitizenEcho.echoesOf(regions.scenery(VARROCK_SOUTH, 3225, 3355)).isEmpty());
	}

	@Test
	public void anEchoNeverSeedsAnEchoOfItsOwn()
	{
		FakeRegions regions = new FakeRegions();
		List<EntityDefinition> echoes =
			CitizenEcho.echoesOf(regions.recoloured(VARROCK_SOUTH, 3225, 3355, 6));
		assertFalse(echoes.isEmpty());

		for (EntityDefinition echo : echoes)
		{
			assertTrue("a re-deal of a re-deal drifts away from the authored palette",
				CitizenEcho.echoesOf(echo).isEmpty());
		}
	}

	// --- Determinism ----------------------------------------------------------

	/**
	 * Two derivations from the same source have to agree on every field.
	 *
	 * <p>Field by field rather than "the lists are the same size": the region cache
	 * is rebuilt on every border crossing and every eviction, so this runs many
	 * times per session, and an echo that moved a tile or changed colour when its
	 * region was rebuilt would flicker exactly where a player is looking.
	 */
	@Test
	public void theSameSourceDerivesTheSameEchoesEveryTime()
	{
		FakeRegions regions = new FakeRegions();
		EntityDefinition source = regions.recoloured(VARROCK_SOUTH, 3225, 3355, 5);

		List<EntityDefinition> first = CitizenEcho.echoesOf(source);
		List<EntityDefinition> second = CitizenEcho.echoesOf(source);

		assertEquals(first.size(), second.size());
		assertFalse("the fixture has to actually produce echoes", first.isEmpty());

		for (int i = 0; i < first.size(); i++)
		{
			EntityDefinition a = first.get(i);
			EntityDefinition b = second.get(i);

			assertEquals("uuid", a.getUuid(), b.getUuid());
			assertEquals("tile", a.getWorldLocation(), b.getWorldLocation());
			assertEquals("facing", a.getOrientation(), b.getOrientation());
			assertEquals("name", a.getName(), b.getName());
			assertEquals("examine", a.getExamineText(), b.getExamineText());
			assertEquals("source", a.getEchoSourceUuid(), b.getEchoSourceUuid());
			assertEquals("region", a.getTileRegionId(), b.getTileRegionId());
			assertTrue("recolour find", Arrays.equals(a.getRecolorFind(), b.getRecolorFind()));
			assertTrue("recolour replace", Arrays.equals(a.getRecolorReplace(), b.getRecolorReplace()));
			assertEquals("stable hash", a.stableHash(), b.stableHash());
		}
	}

	/**
	 * An anchor value for the uuid derivation, so it cannot change by accident.
	 *
	 * <p>Same job as {@code CrowdDensityTest.theHashIsPinned..}: the uuid is what
	 * {@link CitizenOverrides} writes into the user's profile when they hide an echo,
	 * so a "harmless" tweak to the derivation silently discards every hide anybody
	 * had set on an echo, and swaps which stranger is hidden. Changing this number
	 * deliberately is fine; changing it without noticing is what this catches.
	 */
	@Test
	public void theEchoUuidDerivationIsPinned()
	{
		UUID source = UUID.fromString("44444444-4444-4444-8444-444444444444");

		assertEquals(UUID.fromString("663c3dc2-2e38-d721-b7b9-44c9f279864d"),
			CitizenEcho.echoUuid(source, 0));
		assertEquals(UUID.fromString("5d01ffe4-1f40-7d93-9091-4d172f2dd35c"),
			CitizenEcho.echoUuid(source, 1));
	}

	@Test
	public void everyEchoIndexGetsItsOwnUuid()
	{
		UUID source = UUID.fromString("44444444-4444-4444-8444-444444444444");

		assertNotEquals(CitizenEcho.echoUuid(source, 0), CitizenEcho.echoUuid(source, 1));
		assertNotEquals("and neither may be the source's own",
			source, CitizenEcho.echoUuid(source, 0));
		assertNotEquals(source, CitizenEcho.echoUuid(source, 1));
	}

	// --- Not twins ------------------------------------------------------------

	@Test
	public void anEchoIsNeverWithinTheMinimumDistanceOfItsSourceOrOfItsSibling()
	{
		FakeRegions regions = new FakeRegions();
		EntityDefinition source = regions.recoloured(VARROCK_SOUTH, 3225, 3355, 6);
		List<EntityDefinition> echoes = CitizenEcho.echoesOf(source);
		assertEquals(2, echoes.size());

		for (EntityDefinition echo : echoes)
		{
			assertTrue("echo at " + echo.getWorldLocation() + " is too close to its source",
				RenderPolicy.tileDistance(source.getWorldLocation(), echo.getWorldLocation())
					>= CitizenEcho.MIN_SEPARATION_TILES);
		}

		assertTrue("two echoes of one citizen must not stand on top of each other",
			RenderPolicy.tileDistance(
				echoes.get(0).getWorldLocation(), echoes.get(1).getWorldLocation())
				>= CitizenEcho.MIN_SEPARATION_TILES);
	}

	/**
	 * The recolour is the source's own palette, re-dealt — not a new palette and not
	 * the same one.
	 *
	 * <p>Three separate claims, and each is a different way of getting it wrong: the
	 * {@code find} slots have to be untouched (a different find array would recolour
	 * parts of the model the author never meant to touch), the {@code replace} values
	 * have to be exactly the source's own multiset (anything else is an invented
	 * colour), and the mapping has to actually differ (or it is a twin).
	 */
	@Test
	public void anEchoWearsItsSourcesOwnColoursInADifferentOrder()
	{
		FakeRegions regions = new FakeRegions();
		EntityDefinition source = regions.recoloured(VARROCK_SOUTH, 3225, 3355, 4);
		List<EntityDefinition> echoes = CitizenEcho.echoesOf(source);
		assertEquals(2, echoes.size());

		short[] sourceFind = source.getRecolorFind();
		short[] sourceReplace = source.getRecolorReplace();

		for (EntityDefinition echo : echoes)
		{
			assertTrue("the slots being recoloured must be the source's own",
				Arrays.equals(sourceFind, echo.getRecolorFind()));

			short[] sorted = echo.getRecolorReplace().clone();
			short[] sourceSorted = sourceReplace.clone();
			Arrays.sort(sorted);
			Arrays.sort(sourceSorted);
			assertTrue("every colour must be one the source already wore",
				Arrays.equals(sourceSorted, sorted));

			assertFalse("but not in the same order, or it is the source standing next to itself",
				Arrays.equals(sourceReplace, echo.getRecolorReplace()));
		}

		assertFalse("and two echoes of one citizen must differ from each other too",
			Arrays.equals(echoes.get(0).getRecolorReplace(), echoes.get(1).getRecolorReplace()));
	}

	@Test
	public void reDealingRotatesThePaletteAndLeavesTheOriginalAlone()
	{
		short[] palette = {10, 20, 30};

		assertArray(new short[]{20, 30, 10}, CitizenEcho.redeal(palette, 1));
		assertArray(new short[]{30, 10, 20}, CitizenEcho.redeal(palette, 2));
		assertArray("the source's own array must not be touched", new short[]{10, 20, 30}, palette);
	}

	/**
	 * Two echoes of one citizen must not stand shoulder to shoulder facing the same
	 * way — the "twins" tell that survives a recolour. The facing comes from each
	 * echo's own uuid rather than from the source's, which is what makes them differ.
	 */
	@Test
	public void anEchoFacesOneOfTheEightCardinalDirections()
	{
		FakeRegions regions = new FakeRegions();
		Set<Integer> facings = new HashSet<>();

		for (int i = 0; i < 30; i++)
		{
			for (EntityDefinition echo :
				CitizenEcho.echoesOf(regions.recoloured(VARROCK_SOUTH, 3210 + i * 3, 3340, 6)))
			{
				int facing = echo.getOrientation();
				assertEquals("a facing off the eight looks like a rendering fault, not a person",
					0, facing % 256);
				assertTrue("orientation must be inside 0..2047", facing >= 0 && facing < 2048);
				facings.add(facing);
			}
		}

		assertTrue("the derivation has to actually spread across the eight, not pick one: " + facings,
			facings.size() >= 6);
	}

	// --- Identity -------------------------------------------------------------

	@Test
	public void anEchoCarriesNeitherItsSourcesNameNorItsExamineText()
	{
		FakeRegions regions = new FakeRegions();
		EntityDefinition source = regions.recoloured(VARROCK_SOUTH, 3225, 3355, 5);
		List<EntityDefinition> echoes = CitizenEcho.echoesOf(source);
		assertFalse(echoes.isEmpty());

		for (EntityDefinition echo : echoes)
		{
			assertEquals(CitizenEcho.ECHO_NAME, echo.getName());
			assertNotEquals(source.getName(), echo.getName());
			assertNotEquals(source.getExamineText(), echo.getExamineText());
			assertEquals(CitizenEcho.ECHO_EXAMINE_TEXT, echo.getExamineText());
			assertNotEquals("and its own identity is its own", source.getUuid(), echo.getUuid());
			assertEquals("while still knowing where it came from",
				source.getUuid(), echo.getEchoSourceUuid());
			assertTrue(echo.isEcho());
		}

		assertFalse("and the source is not an echo", source.isEcho());
		assertNull(source.getEchoSourceUuid());
	}

	/**
	 * Examine on an echo must not claim to be the source, and must say what the thing
	 * actually is.
	 *
	 * <p>Read through {@link CitizenLabel#examineMessage} rather than off the field,
	 * because the message is what a player sees and it is assembled from two halves —
	 * asserting only on {@code examineText} would miss a version that pasted the
	 * source's name into the coloured prefix.
	 */
	@Test
	public void examineOnAnEchoIsTruthfulAboutWhatItIs()
	{
		FakeRegions regions = new FakeRegions();
		EntityDefinition source = regions.recoloured(VARROCK_SOUTH, 3225, 3355, 5);
		EntityDefinition echo = CitizenEcho.echoesOf(source).get(0);

		String message = CitizenLabel.examineMessage(echo);

		assertFalse("it must not name the citizen it was derived from: " + message,
			message.contains(source.getName()));
		assertFalse("nor repeat that citizen's examine text: " + message,
			message.contains(source.getExamineText()));
		assertTrue("it has to name what it is: " + message, message.contains(CitizenEcho.ECHO_NAME));
		assertTrue("and say which setting put it there: " + message, message.contains("Crowded"));
		assertTrue("on top of the plugin's own disclaimer: " + message,
			message.contains(CitizenLabel.PLUGIN_NAME));
		assertTrue(message.contains("not a real NPC"));
	}

	// --- What an echo is not --------------------------------------------------

	@Test
	public void anEchoNeitherWandersNorHasAnythingToSay()
	{
		FakeRegions regions = new FakeRegions();
		EntityDefinition wanderer = regions.recolouredWanderer(
			VARROCK_SOUTH,
			new WorldPoint(3225, 3355, 0),
			new WorldPoint(3218, 3348, 0),
			new WorldPoint(3232, 3362, 0),
			6);
		assertNotNull("the source itself does wander", wanderer.getWanderBox());

		List<EntityDefinition> echoes = CitizenEcho.echoesOf(wanderer);
		assertEquals(2, echoes.size());

		for (EntityDefinition echo : echoes)
		{
			assertNull("an echo's ground is proved at one tile, so it stays on it",
				echo.getWanderBox());
			assertNull("and CitizenWalk therefore builds nothing for it",
				CitizenWalk.forDefinition(echo));
			assertEquals("a WanderingCitizen that stands still would be a lie in every log line",
				EntityType.StationaryCitizen, echo.getType());

			assertEquals("an echo has no authored line, so it has none at all",
				0, echo.getRemarks().length);
			assertNull("and therefore no remark object for the chatter to drive",
				CitizenRemarks.forDefinition(echo));
		}
	}

	/**
	 * An echo of a citizen that <i>does</i> have something to say still says nothing.
	 *
	 * <p>Separate from the test above, and it is the one that earns its place: making
	 * an echo carry its source's {@code remarks} array left the whole suite green
	 * until this fixture existed, because no other fixture had a source with both a
	 * palette and a line to speak. 33 of the 129 shipped citizens have both, so the
	 * gap was in the fixtures rather than in the dataset.
	 */
	@Test
	public void anEchoOfATalkativeCitizenStillHasNothingToSay()
	{
		FakeRegions regions = new FakeRegions();
		EntityDefinition source = regions.recolouredTalker(
			VARROCK_SOUTH, 3225, 3355, 5, "Busy today.", "Lovely weather.");
		assertEquals("the source itself has two lines", 2, source.getRemarks().length);
		assertNotNull(CitizenRemarks.forDefinition(source));

		List<EntityDefinition> echoes = CitizenEcho.echoesOf(source);
		assertEquals(2, echoes.size());

		for (EntityDefinition echo : echoes)
		{
			assertEquals("putting an authored line in a second mouth makes it ambient noise",
				0, echo.getRemarks().length);
			assertNull(CitizenRemarks.forDefinition(echo));
		}
	}

	@Test
	public void anEchoInheritsItsSourcesBodyAndIdleAnimationButNotItsWalkAnimation()
	{
		FakeRegions regions = new FakeRegions();
		EntityDefinition wanderer = regions.recolouredWanderer(
			VARROCK_SOUTH,
			new WorldPoint(3225, 3355, 0),
			new WorldPoint(3218, 3348, 0),
			new WorldPoint(3232, 3362, 0),
			4);
		EntityDefinition echo = CitizenEcho.echoesOf(wanderer).get(0);

		assertTrue("same body", Arrays.equals(wanderer.getModelIds(), echo.getModelIds()));
		assertEquals(wanderer.getIdleAnimation(), echo.getIdleAnimation());
		assertNull("nothing moves, so there is nothing to play a walk cycle for",
			echo.getMoveAnimation());
		assertEquals("and it stays in the file its source was filed under",
			wanderer.getRegionId(), echo.getRegionId());
	}

	// --- Placement ------------------------------------------------------------

	@Test
	public void anEchoOfAWandererStandsOnItsSourcesOwnAuthoredGround()
	{
		FakeRegions regions = new FakeRegions();
		EntityDefinition wanderer = regions.recolouredWanderer(
			VARROCK_SOUTH,
			new WorldPoint(3225, 3355, 0),
			new WorldPoint(3218, 3348, 0),
			new WorldPoint(3232, 3362, 0),
			6);
		EntityDefinition.WanderBox box = wanderer.getWanderBox();
		assertNotNull(box);

		for (EntityDefinition echo : CitizenEcho.echoesOf(wanderer))
		{
			WorldPoint tile = echo.getWorldLocation();
			assertTrue("a box tile is ground a human already decided a citizen could pace: " + tile,
				box.contains(tile.getX(), tile.getY()));
			assertEquals("and on the same storey", wanderer.getPlane(), echo.getPlane());
			assertTrue("so it is allowed through even with no collision map at all",
				echo.isEchoOnAuthoredGround());
		}
	}

	@Test
	public void anEchoOfAStationaryCitizenStandsOnADerivedOffsetAndSaysSo()
	{
		FakeRegions regions = new FakeRegions();
		EntityDefinition source = regions.recoloured(VARROCK_SOUTH, 3225, 3355, 6);

		for (EntityDefinition echo : CitizenEcho.echoesOf(source))
		{
			assertFalse("nobody has vouched for an offset, so the collision map must",
				echo.isEchoOnAuthoredGround());
			assertEquals(CitizenEcho.MIN_SEPARATION_TILES,
				RenderPolicy.tileDistance(source.getWorldLocation(), echo.getWorldLocation()));
			assertEquals(source.getPlane(), echo.getPlane());
		}
	}

	/**
	 * The placement gate: standable passes, blocked is skipped, and unknown falls
	 * back to whether a human vouched for the ground.
	 */
	@Test
	public void placementFollowsTheCollisionMapAndFallsBackToAuthoredGround()
	{
		FakeRegions regions = new FakeRegions();
		WorldPoint player = new WorldPoint(3225, 3360, 0);

		EntityDefinition offsetEcho =
			CitizenEcho.echoesOf(regions.recoloured(VARROCK_SOUTH, 3225, 3355, 6)).get(0);
		EntityDefinition boxEcho = CitizenEcho.echoesOf(regions.recolouredWanderer(
			VARROCK_SOUTH,
			new WorldPoint(3235, 3355, 0),
			new WorldPoint(3228, 3348, 0),
			new WorldPoint(3242, 3362, 0),
			6)).get(0);

		FakeWorldView open = FakeWorldView.around(player, VARROCK_SOUTH);
		assertTrue(CitizenEcho.isPlaceable(open, offsetEcho));
		assertTrue(CitizenEcho.isPlaceable(open, boxEcho));

		FakeWorldView blocked = FakeWorldView.around(player, VARROCK_SOUTH);
		blocked.block(offsetEcho.getWorldLocation());
		blocked.block(boxEcho.getWorldLocation());
		assertFalse("a blocked tile is skipped, whatever it was derived from",
			CitizenEcho.isPlaceable(blocked, offsetEcho));
		assertFalse("authored ground does not override the collision map",
			CitizenEcho.isPlaceable(blocked, boxEcho));

		FakeWorldView unknown = FakeWorldView.around(player, VARROCK_SOUTH).withoutCollisionData();
		assertFalse("with no answer, an offset is not admitted",
			CitizenEcho.isPlaceable(unknown, offsetEcho));
		assertTrue("but a tile inside an authored wander box is",
			CitizenEcho.isPlaceable(unknown, boxEcho));
	}

	@Test
	public void anAuthoredEntityIsAlwaysPlaceableAndNeverConsultsTheCollisionMap()
	{
		FakeRegions regions = new FakeRegions();
		EntityDefinition authored = regions.citizen(VARROCK_SOUTH, 3225, 3355, 0);

		// StubWorldView throws on every method the render core has never needed, so a
		// version that asked about an authored entity's tile would blow up here rather
		// than quietly cost a scene lookup per authored citizen per tick.
		assertTrue(CitizenEcho.isPlaceable(new StubWorldView(), authored));
		assertTrue(CitizenEcho.isPlaceable(null, authored));
	}

	// --- The shipped dataset --------------------------------------------------

	/**
	 * The headline claim, recomputed from the vendored files.
	 *
	 * <p>129 authored citizens and 144 echoes, i.e. 273 in total. The band is wide
	 * on purpose — what it is guarding is "roughly twice as many", not an exact
	 * figure — but the exact figures are asserted too, so a data change that moved
	 * them says so instead of drifting.
	 */
	@Test
	public void theShippedRosterRoughlyDoublesUnderCrowded()
	{
		List<EntityDefinition> authored = shippedEntities();
		int citizens = 0;
		int echoes = 0;
		int fromBoxes = 0;
		int seeds = 0;

		for (EntityDefinition entity : authored)
		{
			if (entity.getType().isCitizen())
			{
				citizens++;
			}

			List<EntityDefinition> derived = CitizenEcho.echoesOf(entity);
			if (!derived.isEmpty())
			{
				seeds++;
			}
			for (EntityDefinition echo : derived)
			{
				echoes++;
				if (echo.isEchoOnAuthoredGround())
				{
					fromBoxes++;
				}
			}
		}

		assertEquals("the authored citizen roster", 129, citizens);
		assertEquals("citizens whose own palette can dress an echo differently", 76, seeds);
		assertEquals("echoes derived from them", 144, echoes);
		assertEquals("of which this many stand inside an authored wander box", 67, fromBoxes);
		assertEquals("the rest stand on a derived offset the collision map has to vouch for",
			77, echoes - fromBoxes);

		assertTrue("CROWDED must never yield fewer citizens than FULL", echoes >= 0);
		assertTrue("and it must actually roughly double them: " + citizens + " + " + echoes,
			echoes >= citizens);

		double ratio = (citizens + echoes) / (double) citizens;
		assertTrue("total/authored was " + String.format("%.3f", ratio) + ", expected roughly 2",
			ratio >= 1.8 && ratio <= 2.4);
	}

	/**
	 * Every uuid in play — 129 authored citizens, 46 scenery records and 144 echoes —
	 * has to be distinct.
	 *
	 * <p>A collision would mean two entities the user cannot tell apart in the
	 * hidden/muted settings: hiding one would hide the other, permanently, and the
	 * derivation would be the reason. Asserted over the whole roster rather than
	 * argued from the mixer's properties.
	 */
	@Test
	public void everyDerivedUuidIsDistinctFromEveryOtherUuidInTheDataset()
	{
		Set<UUID> seen = new HashSet<>();
		List<String> clashes = new ArrayList<>();

		for (EntityDefinition entity : shippedEntities())
		{
			if (!seen.add(entity.getUuid()))
			{
				clashes.add("authored " + entity.label() + " " + entity.getUuid());
			}

			for (EntityDefinition echo : CitizenEcho.echoesOf(entity))
			{
				if (!seen.add(echo.getUuid()))
				{
					clashes.add("echo of " + entity.label() + " " + echo.getUuid());
				}
			}
		}

		assertTrue("uuid collision(s): " + clashes, clashes.isEmpty());
		assertEquals("175 authored entities plus 144 echoes", 319, seen.size());
	}

	/**
	 * Every derived tile obeys the separation rule, across the whole dataset.
	 *
	 * <p>The hand-built fixtures above prove the rule for a citizen with room around
	 * it. This proves it for the real thing, including the two shipped wanderers
	 * whose boxes are too small to hold two well-separated echoes and therefore fall
	 * through to the ring.
	 */
	@Test
	public void noShippedEchoStandsTooCloseToItsSourceOrItsSibling()
	{
		List<String> violations = new ArrayList<>();

		for (EntityDefinition source : shippedEntities())
		{
			List<EntityDefinition> echoes = CitizenEcho.echoesOf(source);
			for (int i = 0; i < echoes.size(); i++)
			{
				EntityDefinition echo = echoes.get(i);

				if (RenderPolicy.tileDistance(source.getWorldLocation(), echo.getWorldLocation())
					< CitizenEcho.MIN_SEPARATION_TILES)
				{
					violations.add(source.label() + " echo " + i + " at " + echo.getWorldLocation()
						+ " is on top of its source");
				}

				assertEquals("an echo never changes storey", source.getPlane(), echo.getPlane());

				for (int j = 0; j < i; j++)
				{
					if (RenderPolicy.tileDistance(
						echoes.get(j).getWorldLocation(), echo.getWorldLocation())
						< CitizenEcho.MIN_SEPARATION_TILES)
					{
						violations.add(source.label() + " echoes " + j + " and " + i
							+ " are on top of each other");
					}
				}
			}
		}

		assertTrue("separation violation(s): " + violations, violations.isEmpty());
	}

	/**
	 * Every shipped echo wears its source's own colours, re-dealt.
	 *
	 * <p>The same three claims as the fixture test, over the 144 real echoes. What
	 * this adds is coverage of the dataset's awkward palettes — repeated colours,
	 * eleven-pair wardrobes — where a rotation can quietly come out as the identity.
	 */
	@Test
	public void everyShippedEchoWearsAReDealOfItsSourcesOwnPalette()
	{
		List<String> violations = new ArrayList<>();

		for (EntityDefinition source : shippedEntities())
		{
			short[] sourceReplace = source.getRecolorReplace();
			short[] sourceSorted = sourceReplace.clone();
			Arrays.sort(sourceSorted);

			List<EntityDefinition> echoes = CitizenEcho.echoesOf(source);
			for (int i = 0; i < echoes.size(); i++)
			{
				EntityDefinition echo = echoes.get(i);

				if (!Arrays.equals(source.getRecolorFind(), echo.getRecolorFind()))
				{
					violations.add(source.label() + " echo " + i + " recolours different slots");
				}

				short[] sorted = echo.getRecolorReplace().clone();
				Arrays.sort(sorted);
				if (!Arrays.equals(sourceSorted, sorted))
				{
					violations.add(source.label() + " echo " + i + " wears an invented colour");
				}

				if (Arrays.equals(sourceReplace, echo.getRecolorReplace()))
				{
					violations.add(source.label() + " echo " + i + " is dressed identically to it");
				}

				for (int j = 0; j < i; j++)
				{
					if (Arrays.equals(
						echoes.get(j).getRecolorReplace(), echo.getRecolorReplace()))
					{
						violations.add(source.label() + " echoes " + j + " and " + i
							+ " are dressed identically");
					}
				}
			}
		}

		assertTrue("appearance violation(s): " + violations, violations.isEmpty());
	}

	private static void assertArray(short[] expected, short[] actual)
	{
		assertArray("", expected, actual);
	}

	private static void assertArray(String message, short[] expected, short[] actual)
	{
		assertTrue(message + " expected " + Arrays.toString(expected)
			+ " but was " + Arrays.toString(actual), Arrays.equals(expected, actual));
	}

	/**
	 * Every entity in the shipped dataset, through the real
	 * {@link EntityDefinition#fromRecord} — so wander boxes are the validated,
	 * clamped ones the game would use, which is what {@link CitizenEcho} places
	 * echoes inside.
	 */
	private static List<EntityDefinition> shippedEntities()
	{
		RegionDataLoader loader = new RegionDataLoader(TestGson.injected());
		List<EntityDefinition> out = new ArrayList<>();

		for (int regionId : ShippedRegions.ids())
		{
			RegionDefinition region = loader.loadRegion(regionId);
			assertNotNull("region " + regionId + " failed to load", region);
			out.addAll(region.getEntities());
		}

		assertEquals("the whole shipped roster", 175, out.size());
		return out;
	}
}
