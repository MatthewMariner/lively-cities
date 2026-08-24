package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
 * <p>Everything here is offline — {@link CitizenEcho#echoesOfRegion} touches nothing
 * but its argument — so these run against the real shipped dataset as well as
 * against hand-built fixtures. The shipped-data tests are the ones that keep the
 * feature's headline claim honest: they recompute the echo count from the 45 vendored
 * region files rather than trusting the number written in a comment, and they are the
 * only tests that can see a citizen colliding with somebody else's echo — a fixture
 * holding one citizen has no other lineage to collide with.
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
			echoesOf(regions.citizen(VARROCK_SOUTH, 3225, 3355, 0)).isEmpty());
	}

	@Test
	public void aCitizenWithASinglePairSeedsNothing()
	{
		FakeRegions regions = new FakeRegions();

		assertTrue("one slot cannot be re-dealt into a different slot",
			echoesOf(regions.recoloured(VARROCK_SOUTH, 3225, 3355, 1)).isEmpty());
	}

	@Test
	public void twoPairsSeedOneEchoAndThreeOrMoreSeedTwo()
	{
		FakeRegions regions = new FakeRegions();

		assertEquals("two pairs admit exactly one distinct re-deal",
			1, echoesOf(regions.recoloured(VARROCK_SOUTH, 3225, 3355, 2)).size());
		assertEquals(CitizenEcho.MAX_ECHOES_PER_CITIZEN,
			echoesOf(regions.recoloured(VARROCK_SOUTH, 3230, 3355, 3)).size());
		assertEquals("and the cap holds however rich the palette gets",
			CitizenEcho.MAX_ECHOES_PER_CITIZEN,
			echoesOf(regions.recoloured(VARROCK_SOUTH, 3235, 3355, 11)).size());
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
			echoesOf(regions.scenery(VARROCK_SOUTH, 3225, 3355)).isEmpty());
	}

	@Test
	public void anEchoNeverSeedsAnEchoOfItsOwn()
	{
		FakeRegions regions = new FakeRegions();
		List<EntityDefinition> echoes =
			echoesOf(regions.recoloured(VARROCK_SOUTH, 3225, 3355, 6));
		assertFalse(echoes.isEmpty());

		for (EntityDefinition echo : echoes)
		{
			assertTrue("a re-deal of a re-deal drifts away from the authored palette",
				echoesOf(echo).isEmpty());
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

		List<EntityDefinition> first = echoesOf(source);
		List<EntityDefinition> second = echoesOf(source);

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
	 * <b>The same roster in a different order derives the same crowd.</b>
	 *
	 * <p>Once separation is judged across a whole region, two citizens can want the
	 * same tile and only the first one asked can have it — so whatever decides who is
	 * asked first decides where somebody stands. If that were the region file's own
	 * listing order, then re-exporting the dataset, or a loader that read the scenery
	 * roster before the citizen roster, would silently move people around. Sources are
	 * walked in uuid order instead, and this is what says so: the same nine citizens,
	 * listed forwards, backwards and rotated, produce the same echoes on the same tiles
	 * in the same order.
	 *
	 * <p>The fixture is the tight three-by-three block again, because in a crowd with
	 * room to spare every order gives the same answer and the test would pass on a
	 * coincidence.
	 */
	@Test
	public void theDerivationDoesNotDependOnTheOrderTheRosterIsListedIn()
	{
		FakeRegions regions = new FakeRegions();
		List<EntityDefinition> roster = new ArrayList<>();
		for (int i = 0; i < 9; i++)
		{
			roster.add(regions.recoloured(VARROCK_SOUTH, 3220 + (i % 3) * 3, 3350 + (i / 3) * 3, 6));
		}

		List<String> asListed = placements(CitizenEcho.echoesOfRegion(roster));

		List<EntityDefinition> backwards = new ArrayList<>(roster);
		Collections.reverse(backwards);

		List<EntityDefinition> rotated = new ArrayList<>(roster);
		Collections.rotate(rotated, 4);

		assertFalse("the fixture has to produce echoes", asListed.isEmpty());
		assertEquals("listing the roster backwards must not move anybody",
			asListed, placements(CitizenEcho.echoesOfRegion(backwards)));
		assertEquals("nor must rotating it",
			asListed, placements(CitizenEcho.echoesOfRegion(rotated)));
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
		List<EntityDefinition> echoes = echoesOf(source);
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
	 * <b>And never within the minimum distance of anybody else's citizen, or anybody
	 * else's echo.</b>
	 *
	 * <p>The test above is the old rule: an echo against its own source and its own
	 * sibling. This is the rule as it is actually written down — an echo against
	 * everything the plugin renders in its region — and the fixture is deliberately
	 * tight enough to tell them apart. A three-by-three block of citizens three tiles
	 * apart leaves no tile in its interior more than one tile from somebody, so every
	 * candidate ring runs straight through a neighbour: a derivation that could only
	 * see one lineage would put echoes on top of strangers here, exactly as it did
	 * across the shipped files, where it produced 41 such pairs.
	 *
	 * <p>The shortfall is asserted too. A crowd this tight cannot house two echoes per
	 * citizen, and if it could, the fixture would not be exercising contention at all
	 * — it would be nine separate one-citizen fixtures standing near each other.
	 */
	@Test
	public void anEchoKeepsItsDistanceFromTheWholeRegionAndNotJustItsOwnLineage()
	{
		FakeRegions regions = new FakeRegions();
		List<EntityDefinition> roster = new ArrayList<>();
		for (int i = 0; i < 9; i++)
		{
			roster.add(regions.recoloured(VARROCK_SOUTH, 3220 + (i % 3) * 3, 3350 + (i / 3) * 3, 6));
		}

		List<EntityDefinition> echoes = CitizenEcho.echoesOfRegion(roster);
		assertFalse("the fixture has to produce echoes", echoes.isEmpty());

		List<EntityDefinition> rendered = new ArrayList<>(roster);
		rendered.addAll(echoes);

		for (int i = 0; i < rendered.size(); i++)
		{
			for (int j = i + 1; j < rendered.size(); j++)
			{
				EntityDefinition a = rendered.get(i);
				EntityDefinition b = rendered.get(j);
				if (!a.isEcho() && !b.isEcho())
				{
					// Authored against authored is the fixture's own business.
					continue;
				}

				assertTrue(describe(a) + " at " + a.getWorldLocation() + " and " + describe(b)
						+ " at " + b.getWorldLocation() + " are too close",
					RenderPolicy.tileDistance(a.getWorldLocation(), b.getWorldLocation())
						>= CitizenEcho.MIN_SEPARATION_TILES);
			}
		}

		assertTrue("a row this tight has to cost somebody an echo, or the fixture is not "
				+ "exercising contention: got " + echoes.size() + " of "
				+ roster.size() * CitizenEcho.MAX_ECHOES_PER_CITIZEN,
			echoes.size() < roster.size() * CitizenEcho.MAX_ECHOES_PER_CITIZEN);
	}

	/**
	 * <b>A floor is not a gap, but a floor between two people is.</b> The claimed-tile
	 * set is keyed on the plane as well as the coordinates, so a crowd upstairs cannot
	 * take the tiles a crowd downstairs needs.
	 *
	 * <p>{@link RenderPolicy#tileDistance} ignores the plane — it is the render
	 * distance metric, where a citizen one storey up is still a citizen a few tiles
	 * away — so this is the one place the plane has to be put back. The fixture stacks
	 * two identical nine-citizen blocks one storey apart, both tight enough to be
	 * fighting over tiles within their own storey; if the storeys were pooled they
	 * would be fighting over the same ones and the total would drop.
	 */
	@Test
	public void aCrowdUpstairsDoesNotTakeTheTilesTheCrowdDownstairsNeeds()
	{
		FakeRegions regions = new FakeRegions();
		List<EntityDefinition> groundFloor = new ArrayList<>();
		List<EntityDefinition> upstairs = new ArrayList<>();
		for (int i = 0; i < 9; i++)
		{
			int x = 3220 + (i % 3) * 3;
			int y = 3350 + (i / 3) * 3;
			groundFloor.add(regions.recoloured(VARROCK_SOUTH, x, y, 0, 6));
			upstairs.add(regions.recoloured(VARROCK_SOUTH, x, y, 1, 6));
		}

		List<EntityDefinition> groundAlone = CitizenEcho.echoesOfRegion(groundFloor);
		List<EntityDefinition> upstairsAlone = CitizenEcho.echoesOfRegion(upstairs);
		assertFalse("the fixture has to produce echoes", groundAlone.isEmpty());
		assertTrue("and each storey has to be crowded enough to be short of tiles on its own, "
				+ "or pooling the two would cost nothing and this would pass either way",
			groundAlone.size() < groundFloor.size() * CitizenEcho.MAX_ECHOES_PER_CITIZEN
				&& upstairsAlone.size() < upstairs.size() * CitizenEcho.MAX_ECHOES_PER_CITIZEN);

		List<EntityDefinition> both = new ArrayList<>(groundFloor);
		both.addAll(upstairs);
		List<EntityDefinition> stacked = CitizenEcho.echoesOfRegion(both);

		assertEquals("the ground floor's echoes must be exactly where they were when it was "
				+ "the only storey in the region",
			placements(groundAlone), placements(onPlane(stacked, 0)));
		assertEquals("and so must the first floor's",
			placements(upstairsAlone), placements(onPlane(stacked, 1)));
		assertEquals("with nobody else anywhere",
			groundAlone.size() + upstairsAlone.size(), stacked.size());
	}

	/**
	 * Each echo as {@code uuid@tile facing n}, in the order it was derived — so a
	 * comparison of two derivations names the field that moved rather than saying
	 * "not equal".
	 */
	private static List<String> placements(List<EntityDefinition> echoes)
	{
		List<String> out = new ArrayList<>(echoes.size());
		for (EntityDefinition echo : echoes)
		{
			out.add(echo.getUuid() + "@" + echo.getWorldLocation() + " facing " + echo.getOrientation());
		}
		return out;
	}

	private static List<EntityDefinition> onPlane(List<EntityDefinition> entities, int plane)
	{
		List<EntityDefinition> out = new ArrayList<>();
		for (EntityDefinition entity : entities)
		{
			if (entity.getPlane() == plane)
			{
				out.add(entity);
			}
		}
		return out;
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
		List<EntityDefinition> echoes = echoesOf(source);
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
				echoesOf(regions.recoloured(VARROCK_SOUTH, 3210 + i * 3, 3340, 6)))
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
		List<EntityDefinition> echoes = echoesOf(source);
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
		EntityDefinition echo = echoesOf(source).get(0);

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

		List<EntityDefinition> echoes = echoesOf(wanderer);
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
	 * palette and a line to speak. 39 of the 135 shipped citizens have both, so the
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

		List<EntityDefinition> echoes = echoesOf(source);
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
		EntityDefinition echo = echoesOf(wanderer).get(0);

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

		for (EntityDefinition echo : echoesOf(wanderer))
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

		for (EntityDefinition echo : echoesOf(source))
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
			echoesOf(regions.recoloured(VARROCK_SOUTH, 3225, 3355, 6)).get(0);
		EntityDefinition boxEcho = echoesOf(regions.recolouredWanderer(
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
	 * <p>135 authored citizens and 143 echoes, i.e. 278 in total. The band is wide
	 * on purpose — what it is guarding is "roughly twice as many", not an exact
	 * figure — but the exact figures are asserted too, so a data change that moved
	 * them says so instead of drifting.
	 *
	 * <p><b>Why 143 and not 144.</b> 76 citizens have a palette rich enough to dress
	 * an echo differently, and between them they ask for 144 echoes; one of those has
	 * nowhere left in its region to stand that is
	 * {@link CitizenEcho#MIN_SEPARATION_TILES} from everything else the plugin renders
	 * there — the "Mysterious Old Man" in Varrock (region 12853) gets one echo instead
	 * of two — so it is never derived at all. An earlier revision derived all 144 by
	 * checking separation against one citizen's own lineage only, and 41 pairs of
	 * rendered entities ended up closer than the minimum, three of them on the same
	 * tile — see {@link #noTwoShippedRenderedEntitiesStandCloserThanTheMinimum}.
	 */
	@Test
	public void theShippedRosterRoughlyDoublesUnderCrowded()
	{
		int citizens = 0;
		for (EntityDefinition entity : shippedEntities())
		{
			if (entity.getType().isCitizen())
			{
				citizens++;
			}
		}

		int echoes = 0;
		int fromBoxes = 0;
		Set<UUID> seeds = new HashSet<>();
		for (EntityDefinition echo : shippedEchoes())
		{
			echoes++;
			if (echo.isEchoOnAuthoredGround())
			{
				fromBoxes++;
			}
			seeds.add(echo.getEchoSourceUuid());
		}

		assertEquals("the authored citizen roster", 135, citizens);
		assertEquals("citizens that seeded at least one echo", 76, seeds.size());
		assertEquals("echoes derived from them", 143, echoes);
		assertEquals("of which this many stand inside an authored wander box", 63, fromBoxes);
		assertEquals("the rest stand on a derived offset the collision map has to vouch for",
			80, echoes - fromBoxes);

		assertTrue("CROWDED must never yield fewer citizens than FULL", echoes >= 0);
		assertTrue("and it must actually roughly double them: " + citizens + " + " + echoes,
			echoes >= citizens);

		double ratio = (citizens + echoes) / (double) citizens;
		assertTrue("total/authored was " + String.format("%.3f", ratio) + ", expected roughly 2",
			ratio >= 1.8 && ratio <= 2.4);
	}

	/**
	 * Every uuid in play — 135 authored citizens, 46 scenery records and 143 echoes —
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
		}

		for (EntityDefinition echo : shippedEchoes())
		{
			if (!seen.add(echo.getUuid()))
			{
				clashes.add("echo of " + echo.getEchoSourceUuid() + " " + echo.getUuid());
			}
		}

		assertTrue("uuid collision(s): " + clashes, clashes.isEmpty());
		assertEquals("181 authored entities plus 143 echoes", 324, seen.size());
	}

	/**
	 * <b>Nobody stands inside anybody.</b> Every pair of entities the plugin would
	 * render out of the shipped dataset, at the same storey, at least
	 * {@link CitizenEcho#MIN_SEPARATION_TILES} apart — authored citizens, scenery and
	 * echoes, all in one pot.
	 *
	 * <p>This is the widened form of a test that used to compare each echo against
	 * its own source and its own sibling and nothing else. That is not what the rule
	 * says, and the dataset proved it: judged one lineage at a time, the shipped files
	 * produced <b>41 pairs</b> closer than the minimum — 57 counted once per echo
	 * rather than once per pair — of which three were exact same-tile collisions and
	 * two of those were echo on echo. Twins standing in each other, produced by the
	 * rule against twins standing in each other. The fixture tests above cannot catch
	 * that class of fault at all, because a fixture with one citizen in it has no
	 * other lineage to collide with; only the real dataset does.
	 *
	 * <p><b>It walks the whole dataset rather than one region at a time</b>, so it is
	 * a wider net than {@link CitizenEcho#echoesOfRegion} casts: that method can only
	 * separate an echo from the region file it was derived with, and two entities
	 * either side of a region border are outside its reach. The shipped files have no
	 * such pair (they had none before this change either), so the net is empty rather
	 * than slack — and if a future region file ever creates one, this goes red and
	 * somebody has to decide what to do about it, which is the right outcome for a
	 * problem the derivation cannot see.
	 *
	 * <p><b>Authored against authored is excluded, and counted instead.</b> 44 pairs
	 * of hand-placed entities are closer than the minimum and eight of those share a
	 * tile exactly — a stall and its owner, two guards on a gate. A human chose those
	 * and this feature has no vote on them; what it must not do is add to them. The
	 * count is pinned so that "excluded" cannot quietly grow to cover a derived
	 * offender.
	 */
	@Test
	public void noTwoShippedRenderedEntitiesStandCloserThanTheMinimum()
	{
		List<EntityDefinition> rendered = new ArrayList<>(shippedEntities());
		int authoredCount = rendered.size();
		rendered.addAll(shippedEchoes());

		List<String> violations = new ArrayList<>();
		int authoredPairs = 0;
		int authoredCollisions = 0;

		for (int i = 0; i < rendered.size(); i++)
		{
			for (int j = i + 1; j < rendered.size(); j++)
			{
				EntityDefinition a = rendered.get(i);
				EntityDefinition b = rendered.get(j);

				if (a.getPlane() != b.getPlane())
				{
					// A different storey is not the same tile.
					continue;
				}

				int distance = RenderPolicy.tileDistance(a.getWorldLocation(), b.getWorldLocation());
				if (distance >= CitizenEcho.MIN_SEPARATION_TILES)
				{
					continue;
				}

				if (!a.isEcho() && !b.isEcho())
				{
					authoredPairs++;
					if (distance == 0)
					{
						authoredCollisions++;
					}
					continue;
				}

				violations.add(describe(a) + " and " + describe(b) + " are " + distance
					+ " tile(s) apart at " + a.getWorldLocation() + " / " + b.getWorldLocation());
			}
		}

		assertEquals("the fixture has to be the whole authored roster", 181, authoredCount);
		assertTrue("separation violation(s) involving a derived citizen: " + violations,
			violations.isEmpty());
		assertEquals("hand-placed entities closer than the minimum to each other, which is "
				+ "authored content and none of this feature's business", 44, authoredPairs);
		assertEquals("of which this many share a tile exactly", 8, authoredCollisions);
	}

	/**
	 * Every shipped echo wears its source's own colours, re-dealt.
	 *
	 * <p>The same three claims as the fixture test, over the 143 real echoes. What
	 * this adds is coverage of the dataset's awkward palettes — repeated colours,
	 * eleven-pair wardrobes — where a rotation can quietly come out as the identity.
	 */
	@Test
	public void everyShippedEchoWearsAReDealOfItsSourcesOwnPalette()
	{
		Map<UUID, EntityDefinition> sources = new HashMap<>();
		for (EntityDefinition entity : shippedEntities())
		{
			sources.put(entity.getUuid(), entity);
		}

		List<String> violations = new ArrayList<>();
		Map<UUID, List<short[]>> palettesBySource = new HashMap<>();

		for (EntityDefinition echo : shippedEchoes())
		{
			EntityDefinition source = sources.get(echo.getEchoSourceUuid());
			assertNotNull("every echo has to name a source in the same dataset", source);

			short[] sourceReplace = source.getRecolorReplace();
			short[] sourceSorted = sourceReplace.clone();
			Arrays.sort(sourceSorted);

			if (!Arrays.equals(source.getRecolorFind(), echo.getRecolorFind()))
			{
				violations.add(describe(echo) + " recolours different slots from " + source.label());
			}

			short[] sorted = echo.getRecolorReplace().clone();
			Arrays.sort(sorted);
			if (!Arrays.equals(sourceSorted, sorted))
			{
				violations.add(describe(echo) + " wears a colour " + source.label() + " never wore");
			}

			if (Arrays.equals(sourceReplace, echo.getRecolorReplace()))
			{
				violations.add(describe(echo) + " is dressed identically to " + source.label());
			}

			List<short[]> siblings =
				palettesBySource.computeIfAbsent(echo.getEchoSourceUuid(), k -> new ArrayList<>());
			for (short[] sibling : siblings)
			{
				if (Arrays.equals(sibling, echo.getRecolorReplace()))
				{
					violations.add(describe(echo) + " is dressed identically to its own sibling");
				}
			}
			siblings.add(echo.getRecolorReplace());
		}

		assertTrue("appearance violation(s): " + violations, violations.isEmpty());
	}

	/**
	 * <b>Which checkbox switches an echo off is a question about its source, not about
	 * the tile it happens to stand on</b> — and the shipped dataset is what makes the
	 * difference visible.
	 *
	 * <p>{@link City#isEnabled} fails open for a region no city claims, on purpose, so
	 * that a region file can ship one commit before its checkbox does
	 * ({@code CityTest.aRegionNoCityClaimsIsStillShown}). Four shipped echoes stand a
	 * few tiles over a border in a region nobody claims — three derived from
	 * Piscatoris citizens, one from a Camelot citizen — so judging an echo by its own
	 * tile sent all four through that door: unticking Piscatoris or Camelot left them
	 * standing in an empty village. Asked of the source's region instead, they answer
	 * to the checkbox that governs the citizen they are copies of.
	 *
	 * <p>Every assertion here is over the real files. The escaping four are named by
	 * count and region so that a data change that moves them says so, and the
	 * fail-open behaviour they used to exploit is asserted to still be there — this
	 * fix narrows who may use that door, not whether it exists.
	 */
	@Test
	public void everyShippedEchoIsGovernedByItsSourcesCityEvenWhereItStandsInNoCity()
	{
		Map<UUID, EntityDefinition> sources = new HashMap<>();
		for (EntityDefinition entity : shippedEntities())
		{
			sources.put(entity.getUuid(), entity);
		}

		List<EntityDefinition> strays = new ArrayList<>();
		Map<String, Integer> strayRegions = new TreeMap<>();

		for (EntityDefinition echo : shippedEchoes())
		{
			EntityDefinition source = sources.get(echo.getEchoSourceUuid());
			assertNotNull(source);

			assertEquals("an echo answers to whatever governs the citizen it came from",
				source.getTileRegionId(), echo.getCityRegionId());

			if (City.of(echo.getTileRegionId()) != City.of(source.getTileRegionId()))
			{
				strays.add(echo);
				strayRegions.merge(
					echo.getTileRegionId() + " from " + City.of(echo.getCityRegionId()), 1, Integer::sum);
			}
		}

		assertEquals("echoes standing in a region their source's city does not claim",
			4, strays.size());
		assertEquals("{10806 from Camelot=1, 9271 from Piscatoris=3}", strayRegions.toString());

		for (EntityDefinition stray : strays)
		{
			City owner = City.of(stray.getCityRegionId());
			assertNotNull("a stray's source still has a city, or this proves nothing", owner);

			assertNull("the tile it stands on is the fail-open case, which is how it escaped",
				City.of(stray.getTileRegionId()));
			assertTrue("and that region does still fail open, for the authored entities the "
					+ "rule exists for",
				City.isEnabled(stray.getTileRegionId(), new FakeConfig().disable(City.values())));

			assertFalse("but unticking " + owner + " has to switch this echo off",
				City.isEnabled(stray.getCityRegionId(), new FakeConfig().disableOnly(owner)));
			assertTrue("and leaving it ticked has to leave the echo alone",
				City.isEnabled(stray.getCityRegionId(), new FakeConfig()));
		}
	}

	/** An echo never changes storey, and never leaves the file its source was filed under. */
	@Test
	public void everyShippedEchoStaysOnItsSourcesStoreyAndInItsSourcesFile()
	{
		Map<UUID, EntityDefinition> sources = new HashMap<>();
		for (EntityDefinition entity : shippedEntities())
		{
			sources.put(entity.getUuid(), entity);
		}

		int checked = 0;
		for (EntityDefinition echo : shippedEchoes())
		{
			EntityDefinition source = sources.get(echo.getEchoSourceUuid());
			assertNotNull(source);
			assertEquals("an echo never changes storey", source.getPlane(), echo.getPlane());
			assertEquals("nor the file it is discovered through",
				source.getRegionId(), echo.getRegionId());
			checked++;
		}

		assertEquals("the shipped echo roster", 143, checked);
	}

	private static String describe(EntityDefinition entity)
	{
		return (entity.isEcho() ? "echo of " + entity.getEchoSourceUuid() + " " : "authored ")
			+ entity.label();
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
	 * One citizen's echoes, derived as if it were the only thing in its region.
	 *
	 * <p>{@link CitizenEcho#echoesOfRegion} takes a whole region file, because
	 * separation is a claim about a tile and everything standing near it. A fixture
	 * with one citizen in it is a region with one citizen in it, and that is exactly
	 * what most of the tests above want: the derivation with nothing else in the way.
	 * The dataset tests below hand it real rosters instead.
	 */
	private static List<EntityDefinition> echoesOf(EntityDefinition source)
	{
		return CitizenEcho.echoesOfRegion(Collections.singletonList(source));
	}

	/**
	 * Every entity in the shipped dataset, through the real
	 * {@link EntityDefinition#fromRecord} — so wander boxes are the validated,
	 * clamped ones the game would use, which is what {@link CitizenEcho} places
	 * echoes inside.
	 */
	private static List<EntityDefinition> shippedEntities()
	{
		List<EntityDefinition> out = new ArrayList<>();
		for (List<EntityDefinition> roster : shippedRosters().values())
		{
			out.addAll(roster);
		}

		assertEquals("the whole shipped roster", 181, out.size());
		return out;
	}

	/**
	 * Every echo the shipped dataset seeds, derived the way the scene derives them:
	 * one whole region file at a time.
	 */
	private static List<EntityDefinition> shippedEchoes()
	{
		List<EntityDefinition> out = new ArrayList<>();
		for (List<EntityDefinition> roster : shippedRosters().values())
		{
			out.addAll(CitizenEcho.echoesOfRegion(roster));
		}
		return out;
	}

	/** The shipped rosters, keyed by region file, in ascending region order. */
	private static Map<Integer, List<EntityDefinition>> shippedRosters()
	{
		RegionDataLoader loader = new RegionDataLoader(TestGson.injected());
		Map<Integer, List<EntityDefinition>> out = new LinkedHashMap<>();

		for (int regionId : ShippedRegions.ids())
		{
			RegionDefinition region = loader.loadRegion(regionId);
			assertNotNull("region " + regionId + " failed to load", region);
			out.put(regionId, region.getEntities());
		}

		return out;
	}
}
