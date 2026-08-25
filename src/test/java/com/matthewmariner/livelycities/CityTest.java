package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The consistency guard on the city mapping.
 *
 * <p>Two tables have to line up and neither one can be derived from the other:
 * the region ids live in {@link City}, the 9 checkboxes live in
 * {@link LivelyCitiesConfig}, and RuneLite's config annotations make generating
 * one from the other impossible. So this file's whole job is to make a mismatch a
 * red build rather than a checkbox that quietly does nothing, or does someone
 * else's job, or a whole city with no checkbox at all.
 */
public class CityTest
{
	/**
	 * The requirement, stated as a test: every region the plugin ships data for
	 * has exactly one home.
	 *
	 * <p>Both directions matter and both have failed in other people's plugins. A
	 * region with no city cannot be switched off. A region in two cities means one
	 * of the two checkboxes does nothing, which nobody notices until someone asks
	 * why unticking Falador left half of it standing there.
	 *
	 * <p>It walks the constants' own arrays rather than {@link City#of(int)}, on
	 * purpose: the lookup map is fail-soft about collisions, so asking it would be
	 * asking the thing under test to grade itself.
	 */
	@Test
	public void everyShippedRegionBelongsToExactlyOneCity()
	{
		Map<Integer, List<City>> claims = new HashMap<>();
		for (City city : City.values())
		{
			for (int regionId : city.getRegionIds())
			{
				claims.computeIfAbsent(regionId, k -> new ArrayList<>()).add(city);
			}
		}

		List<Integer> shipped = ShippedRegions.ids();
		assertFalse("the dataset has to be on the classpath for this to mean anything",
			shipped.isEmpty());

		List<String> homeless = new ArrayList<>();
		List<String> contested = new ArrayList<>();

		for (int regionId : shipped)
		{
			List<City> claimants = claims.get(regionId);
			if (claimants == null)
			{
				homeless.add(String.valueOf(regionId));
				continue;
			}
			if (claimants.size() > 1)
			{
				contested.add(regionId + " claimed by " + claimants);
			}
		}

		assertEquals("region file(s) with no city, so nothing can switch them off: " + homeless,
			0, homeless.size());
		assertEquals("region(s) claimed by more than one city: " + contested,
			0, contested.size());

		// The other direction: a city naming a region that does not ship is a
		// checkbox for nothing, and usually a typo in an id.
		List<String> phantom = new ArrayList<>();
		for (Map.Entry<Integer, List<City>> entry : claims.entrySet())
		{
			if (!shipped.contains(entry.getKey()))
			{
				phantom.add(entry.getKey() + " (" + entry.getValue() + ")");
			}
		}
		assertEquals("city region id(s) with no region file: " + phantom, 0, phantom.size());

		assertEquals("every shipped region is mapped exactly once",
			shipped.size(), claims.size());
	}

	/**
	 * The enum→checkbox half of the mapping, checked one city at a time.
	 *
	 * <p>{@link FakeConfig} wires each of the 9 getters to its own {@link City},
	 * so switching off exactly one city and finding exactly one city switched off
	 * is the composition of the two mappings coming out as the identity. A
	 * copy-paste in either file — {@code VARROCK.enabledIn} calling
	 * {@code cityLumbridge()}, or two constants sharing a getter — shows up here
	 * as two cities off, or the wrong one.
	 */
	@Test
	public void everyCityCheckboxSwitchesOffExactlyItsOwnCity()
	{
		for (City target : City.values())
		{
			FakeConfig config = new FakeConfig().disableOnly(target);

			for (City other : City.values())
			{
				int regionId = other.getRegionIds()[0];
				boolean enabled = City.isEnabled(regionId, config);

				if (other == target)
				{
					assertFalse("unticking " + target + " should switch it off "
						+ "(region " + regionId + ")", enabled);
				}
				else
				{
					assertTrue("unticking " + target + " must not switch off " + other
						+ " (region " + regionId + ")", enabled);
				}
			}
		}
	}

	@Test
	public void everyRegionOfACityFollowsTheSameCheckbox()
	{
		for (City city : City.values())
		{
			FakeConfig config = new FakeConfig().disableOnly(city);
			for (int regionId : city.getRegionIds())
			{
				assertEquals("region " + regionId + " should resolve to " + city,
					city, City.of(regionId));
				assertFalse("region " + regionId + " should follow " + city + "'s checkbox",
					City.isEnabled(regionId, config));
			}
		}
	}

	/**
	 * A region no city claims is shown, not hidden. That is what lets a region
	 * file land in one commit and its checkbox in the next; the test above is what
	 * stops the grace period becoming permanent.
	 */
	@Test
	public void aRegionNoCityClaimsIsStillShown()
	{
		// 11422 is Keldagrim. There is no region file for it and no city constant.
		int unmapped = 11422;
		assertNull("the fixture has to be genuinely unmapped", City.of(unmapped));

		FakeConfig everythingOff = new FakeConfig().disable(City.values());
		assertTrue("an unmapped region must fail open",
			City.isEnabled(unmapped, everythingOff));
	}

	@Test
	public void labelsAreUniqueAndNoCityIsEmpty()
	{
		TreeSet<String> labels = new TreeSet<>();
		for (City city : City.values())
		{
			assertNotNull(city.getLabel());
			assertFalse(city + " has a blank label", city.getLabel().trim().isEmpty());
			assertTrue("two cities share the label '" + city.getLabel() + "'",
				labels.add(city.getLabel()));
			assertTrue(city + " claims no regions", city.getRegionIds().length > 0);
		}

		assertEquals("9 checkboxes, one per city", 9, City.values().length);
	}

	/**
	 * The declaration order is the order the checkboxes are numbered in, so the
	 * comment at the top of {@link City} claims it is alphabetical by label. Cheap
	 * to check, and it is the kind of claim that quietly stops being true the first
	 * time a constant is appended at the bottom instead of slotted in.
	 */
	@Test
	public void theDeclarationOrderIsAlphabeticalByLabel()
	{
		City[] cities = City.values();
		for (int i = 1; i < cities.length; i++)
		{
			String previous = cities[i - 1].getLabel();
			String current = cities[i].getLabel();
			assertTrue("'" + previous + "' is declared before '" + current
					+ "', which puts the checkboxes out of alphabetical order",
				previous.compareTo(current) < 0);
		}
	}

	/**
	 * The four regions a wrong "Digsite" checkbox used to own, pinned to the place
	 * each of them is actually in.
	 *
	 * <p>Every number here came out of an OSRS Wiki {@code {{Infobox Location}}}
	 * map coordinate put through {@link RenderPolicy#regionIdOf(int, int)}:
	 *
	 * <ul>
	 *   <li>Digsite, map centre (3354, 3420) — region <b>13365</b>, which this
	 *       plugin ships no data for. That is the whole reason the checkbox was
	 *       wrong: it named a place none of its regions contained.</li>
	 *   <li>Lumber Yard, map polygon x 3293..3326 / y 3492..3518 — every corner
	 *       is region <b>13110</b>.</li>
	 *   <li>Paterdomus Temple, map centre (3416, 3487) — region <b>13622</b>.</li>
	 *   <li>Ranging Guild, map polygon x 2651..2686 / y 3411..3446 — every corner
	 *       is region <b>10549</b>. Catherby's own centre (2810, 3440) is region
	 *       11061.</li>
	 * </ul>
	 *
	 * <p>The assertions run the arithmetic rather than quoting the answers, so a
	 * transposed digit in a region id shows up here rather than as a checkbox that
	 * switches off the wrong street.
	 *
	 * <p><b>What this test lost in the nine-city cut, stated plainly.</b> Three of
	 * the four regions — 13110, 13622 and 10549 — no longer ship, so the
	 * "…and it is filed under the checkbox that describes it" half of each pair is
	 * gone with them; {@link City#of} now answers {@code null} for all three, which
	 * is asserted below but is a weaker claim than naming the right city was. The
	 * arithmetic half survives intact and is the half that catches a transposed
	 * digit. The one pairing still checked end to end is 13109 → Varrock, and the
	 * Ranging-Guild-is-not-Catherby point survives as the distance between the two
	 * map points — 159 tiles, which is a claim that can fail, unlike the
	 * {@code 10549 != 11061} it replaced.
	 */
	@Test
	public void theRegionsTheDigsiteCheckboxClaimedBelongToTheirRealPlaces()
	{
		// The Digsite itself. No file ships for it, so no city may claim it, and
		// nothing else may pretend to be it.
		int digsite = RenderPolicy.regionIdOf(3354, 3420);
		assertEquals(13365, digsite);
		assertNull("no city may claim the Digsite while no region file ships for it",
			City.of(digsite));

		// The Lumber Yard's polygon, corner by corner.
		int lumberYard = RenderPolicy.regionIdOf(3293, 3492);
		assertEquals(13110, lumberYard);
		assertEquals(13110, RenderPolicy.regionIdOf(3326, 3518));

		// Paterdomus Temple.
		int paterdomus = RenderPolicy.regionIdOf(3416, 3487);
		assertEquals(13622, paterdomus);

		// The Ranging Guild's polygon, and Catherby, which is not it. What made
		// filing the Guild under Catherby's checkbox a mistake rather than a
		// judgement call is how far apart the two map points are, so the distance is
		// what is asserted. This line used to read `assertFalse(rangingGuild ==
		// 11061)`, which could not fail: `rangingGuild` is asserted to be 10549 two
		// lines above, so the comparison was already decided and the assertion was a
		// sentence rather than a check.
		int rangingGuild = RenderPolicy.regionIdOf(2651, 3411);
		assertEquals(10549, rangingGuild);
		assertEquals(10549, RenderPolicy.regionIdOf(2686, 3446));
		assertEquals(11061, RenderPolicy.regionIdOf(2810, 3440));
		assertEquals(City.CATHERBY, City.of(11061));
		assertEquals("the Ranging Guild's map point is 159 tiles west of Catherby's — "
				+ "a transposed digit in either pair would land them next door to each "
				+ "other and this is what would notice",
			159,
			RenderPolicy.tileDistance(
				new WorldPoint(2651, 3411, 0), new WorldPoint(2810, 3440, 0)));

		// All three are out of the dataset now, and none of them may be claimed by a
		// city while no region file ships for it — the same rule the Digsite is held
		// to above, applied to the three that used to have checkboxes of their own.
		assertNull("13110 ships no file, so no city may claim it", City.of(lumberYard));
		assertNull("13622 ships no file, so no city may claim it", City.of(paterdomus));
		assertNull("10549 ships no file, so no city may claim it", City.of(rangingGuild));

		// The strip outside Varrock's east gate: no landmark of its own, so it is
		// grouped with the city whose wall it starts at. 12853 ends at x 3263. This
		// is the one pairing of the four that still ships, so it is the one that can
		// still be checked all the way through to a checkbox.
		int outsideTheEastGate = RenderPolicy.regionIdOf(3268, 3426);
		assertEquals(13109, outsideTheEastGate);
		assertEquals(City.VARROCK, City.of(outsideTheEastGate));
		assertEquals("Varrock's own centre and its east-gate road share a checkbox",
			City.of(RenderPolicy.regionIdOf(3210, 3448)), City.of(outsideTheEastGate));
	}

	/**
	 * The region id arrays are the enum's state, so handing out the original would
	 * let any caller edit the mapping for the rest of the session.
	 */
	@Test
	public void theRegionIdsCannotBeEditedThroughTheGetter()
	{
		int[] first = City.VARROCK.getRegionIds();
		int original = first[0];
		first[0] = 999999;

		assertEquals("getRegionIds() must hand back a copy",
			original, City.VARROCK.getRegionIds()[0]);
		assertEquals(City.VARROCK, City.of(original));
	}
}
