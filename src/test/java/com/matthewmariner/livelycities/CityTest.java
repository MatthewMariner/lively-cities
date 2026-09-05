package com.matthewmariner.livelycities;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
	/**
	 * <b>Each city's {@code configKey} is the key on the checkbox its own
	 * {@code enabledIn} reads.</b>
	 *
	 * <p>This is the guard the side panel needs and nothing else does. Rendering only
	 * ever asks "is this city on?", which {@link City#enabledIn} answers; the panel has
	 * to <i>write</i> the checkbox, which needs the key, and a key paired with the wrong
	 * getter is a card that reads "Varrock: on" and unticks Lumbridge. On a live plugin
	 * that is a setting silently changed under a user who did not ask.
	 *
	 * <p><b>Composed rather than compared, because comparing is what a copy-paste
	 * survives.</b> The chain checked here is: the enum's key → the {@code @ConfigItem}
	 * in the source that declares that key → the getter that annotation sits on → the
	 * getter {@code enabledIn} actually calls. {@link KeyedConfig} closes the last link
	 * by answering {@code true} from exactly one getter, named by its own method name, so
	 * a constant pointing at another city's key produces a city that thinks it is off.
	 *
	 * <p>The source is read rather than reflected on, for the reason
	 * {@code RegionDataLoaderTest.visibleConfigKeys} gives: reflection is banned in this
	 * project, shipped or not, and the annotation's argument is a constant whose name is
	 * only visible in the text.
	 */
	@Test
	public void everyCityCheckboxKeySitsOnTheGetterThatCityReads() throws IOException
	{
		Map<String, String> gettersByKey = cityGettersByKey();
		assertEquals("one city @ConfigItem per city", City.values().length, gettersByKey.size());

		Set<String> keys = new TreeSet<>();
		for (City city : City.values())
		{
			String key = city.getConfigKey();
			assertTrue(city + " reuses the key '" + key + "'", keys.add(key));

			String getter = gettersByKey.get(key);
			assertNotNull(city + " names key '" + key + "', which no @ConfigItem in "
				+ "LivelyCitiesConfig declares", getter);

			// The getter that annotation sits on is the one this city reads — and every
			// other city reads a different one.
			assertTrue(city + "'s key is declared on " + getter + "(), which is not the "
					+ "getter " + city + ".enabledIn calls",
				city.enabledIn(new KeyedConfig(getter)));

			for (City other : City.values())
			{
				if (other != city)
				{
					assertFalse(other + " must not read " + city + "'s checkbox",
						other.enabledIn(new KeyedConfig(getter)));
				}
			}
		}

		assertEquals("nine distinct keys", City.values().length, keys.size());
	}

	/**
	 * The nine {@code city*} {@code @ConfigItem}s in the config source, as
	 * key → the getter the annotation is attached to.
	 *
	 * <p>The scan walks parentheses rather than matching a regex across the file, for the
	 * reason {@code RegionDataLoaderTest} gives: an annotation argument list contains
	 * commas, quotes and nested calls, and a lazy {@code .*?} stopping at the first
	 * {@code )} reads half of one.
	 */
	private static Map<String, String> cityGettersByKey() throws IOException
	{
		String source = new String(Files.readAllBytes(
			new File("src/main/java/com/matthewmariner/livelycities/LivelyCitiesConfig.java")
				.toPath()), StandardCharsets.UTF_8);

		// The interface's own key constants, read out of the source rather than off the
		// interface, so a constant whose declared value stopped matching City's would be
		// caught here rather than agreeing with itself.
		Map<String, String> constants = new HashMap<>();
		Matcher declaration = Pattern
			.compile("String\\s+(KEY_\\w+)\\s*=\\s*\"([^\"]+)\"\\s*;")
			.matcher(source);
		while (declaration.find())
		{
			constants.put(declaration.group(1), declaration.group(2));
		}
		assertTrue("no KEY_ constants found, so this scan is checking nothing",
			constants.size() >= City.values().length);

		Map<String, String> out = new TreeMap<>();
		Pattern keyName = Pattern.compile("keyName\\s*=\\s*(?:\"([^\"]+)\"|([\\w.]+))");
		Pattern getter = Pattern.compile("\\A\\s*default\\s+boolean\\s+(\\w+)\\s*\\(");
		final String marker = "@ConfigItem(";

		for (int at = source.indexOf(marker); at >= 0; at = source.indexOf(marker, at + 1))
		{
			int open = at + marker.length();
			int depth = 1;
			int end = open;
			while (depth > 0)
			{
				assertTrue("unbalanced @ConfigItem at offset " + at, end < source.length());
				char c = source.charAt(end++);
				if (c == '(')
				{
					depth++;
				}
				else if (c == ')')
				{
					depth--;
				}
			}

			Matcher match = keyName.matcher(source.substring(open, end - 1));
			assertTrue("every @ConfigItem must name a key", match.find());

			String key = match.group(1) != null ? match.group(1) : constants.get(match.group(2));
			if (key == null || !key.startsWith("city"))
			{
				continue;
			}

			Matcher declared = getter.matcher(source.substring(end));
			assertTrue("the @ConfigItem for '" + key + "' has to be attached to a boolean "
				+ "getter", declared.find());
			assertNull("two @ConfigItems declare '" + key + "'", out.put(key, declared.group(1)));
		}

		return out;
	}

	/**
	 * A config where exactly one city getter answers {@code true}, chosen by its own
	 * method name.
	 *
	 * <p>{@link FakeConfig} cannot do this job: it wires each getter to a {@link City},
	 * which is the mapping under test, so asking it would be asking the thing under test
	 * to grade itself. Here the only thing a getter knows is what it is called.
	 */
	private static final class KeyedConfig implements LivelyCitiesConfig
	{
		private final String getter;

		private KeyedConfig(String getter)
		{
			this.getter = getter;
		}

		private boolean is(String name)
		{
			return getter.equals(name);
		}

		@Override
		public boolean cityAlKharid()
		{
			return is("cityAlKharid");
		}

		@Override
		public boolean cityArdougne()
		{
			return is("cityArdougne");
		}

		@Override
		public boolean cityCatherby()
		{
			return is("cityCatherby");
		}

		@Override
		public boolean cityDraynor()
		{
			return is("cityDraynor");
		}

		@Override
		public boolean cityEdgeville()
		{
			return is("cityEdgeville");
		}

		@Override
		public boolean cityFalador()
		{
			return is("cityFalador");
		}

		@Override
		public boolean cityGrandExchange()
		{
			return is("cityGrandExchange");
		}

		@Override
		public boolean cityLumbridge()
		{
			return is("cityLumbridge");
		}

		@Override
		public boolean cityVarrock()
		{
			return is("cityVarrock");
		}
	}

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
