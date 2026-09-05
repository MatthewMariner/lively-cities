package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The index that puts a name to a stored uuid, and a citizen count on a city card.
 *
 * <p>Both are things the live scene cannot answer. {@link EntityScene} only knows about
 * regions the loaded scene covers, and the whole point of the panel's hidden-and-muted
 * list is that you are usually a city away from the person you changed your mind about.
 */
public class CitizenDirectoryTest
{
	/**
	 * The card counts are the dataset's, city by city.
	 *
	 * <p>The same nine numbers {@code RegionDataLoaderTest.everyCityHoldsTheNumberOf
	 * CitizensItIsSupposedTo} pins, reached by a different path, and written out rather
	 * than recomputed here on purpose: computing the expectation the same way the code
	 * under test computes it is how you get a test that cannot fail. These are the
	 * numbers a user reads off a card, so they are worth stating twice.
	 *
	 * <p>All nine rather than a sample, because a card that quietly showed the wrong
	 * city's roster is exactly the copy-paste this shape of loop invites — and only a
	 * table where the numbers differ can see it. They do differ: Varrock is 71 and
	 * Edgeville is 22.
	 */
	@Test
	public void everyCardsCitizenCountIsWhatTheDatasetHolds()
	{
		Map<City, Integer> expected = new EnumMap<>(City.class);
		expected.put(City.AL_KHARID, 24);
		expected.put(City.ARDOUGNE, 24);
		expected.put(City.CATHERBY, 24);
		expected.put(City.DRAYNOR, 24);
		expected.put(City.EDGEVILLE, 22);
		expected.put(City.FALADOR, 26);
		expected.put(City.GRAND_EXCHANGE, 24);
		expected.put(City.LUMBRIDGE, 30);
		expected.put(City.VARROCK, 71);
		assertEquals("every city has to be listed, or one could empty out unnoticed",
			City.values().length, expected.size());

		CitizenDirectory directory = shipped();

		int total = 0;
		for (City city : City.values())
		{
			assertEquals("the card for " + city.getLabel(),
				(int) expected.get(city), directory.citizenCount(city));
			total += directory.citizenCount(city);
		}

		assertEquals("and the nine cards between them account for every shipped citizen",
			269, total);
		assertEquals("which is also how many the index holds", 269, directory.size());
	}

	/**
	 * Every shipped citizen can be named, and every one is filed under the city whose
	 * checkbox governs it.
	 *
	 * <p>Walks the dataset rather than sampling it: an index built from a loop over
	 * {@code City.values()} can miss a whole region — a city naming a file that does not
	 * parse is skipped rather than fatal, deliberately — and 268 of 269 is a defect no
	 * total would show, because the total is what the same loop produced.
	 */
	@Test
	public void everyShippedCitizenIsInTheIndexUnderItsOwnCity()
	{
		RegionDataLoader loader = new RegionDataLoader(TestGson.injected());
		CitizenDirectory directory = new CitizenDirectory(loader);

		int checked = 0;
		for (City city : City.values())
		{
			for (int regionId : city.getRegionIds())
			{
				RegionDefinition region = loader.loadRegion(regionId);
				assertNotNull("region " + regionId + " failed to load", region);

				for (EntityDefinition definition : region.getEntities())
				{
					if (!definition.getType().isCitizen())
					{
						continue;
					}

					CitizenDirectory.Entry entry = directory.find(definition.getUuid());
					assertNotNull(definition.label() + " is in " + regionId
						+ ".json and the directory cannot place it", entry);
					assertEquals(definition.label() + " belongs to the city whose file ships it",
						city, entry.getCity());
					assertEquals(definition.getName(), entry.getName());
					checked++;
				}
			}
		}

		assertEquals("a walk that checked nothing would pass", 269, checked);
	}

	/**
	 * Scenery is not in the index, and that is not an omission.
	 *
	 * <p>A {@code sceneryRoster} record carries no name — {@code EntityRecord} says so —
	 * and {@code CitizenMenu} offers neither Hide nor Mute on one, so a crate can never
	 * turn up in the panel's list. Indexing 42 nameless props would only mean 42 rows
	 * reading "Unnamed citizen" if it ever did.
	 */
	@Test
	public void sceneryIsNotIndexed()
	{
		RegionDataLoader loader = new RegionDataLoader(TestGson.injected());
		CitizenDirectory directory = new CitizenDirectory(loader);

		int scenery = 0;
		for (int regionId : ShippedRegions.ids())
		{
			RegionDefinition region = loader.loadRegion(regionId);
			assertNotNull(region);
			for (EntityDefinition definition : region.getEntities())
			{
				if (definition.getType().isCitizen())
				{
					continue;
				}

				assertNull("scenery has no name to show and no menu to be hidden from: "
					+ definition.label(), directory.find(definition.getUuid()));
				scenery++;
			}
		}

		assertEquals("the dataset's 42 props, or this checked nothing", 42, scenery);
	}

	// --- the second tier -------------------------------------------------------

	/**
	 * <b>An ordinary profile never pays for the echo derivation.</b>
	 *
	 * <p>The second tier re-runs {@link CitizenEcho#echoesOfRegion} over all 27 region
	 * files, which is materially more work than the parse it follows — and it is only
	 * ever needed for a uuid the authored tier cannot place. So the claim is about cost,
	 * and no count of results could make it: the index would answer exactly the same
	 * questions with the tier built eagerly. {@link CitizenDirectory#hasBuiltEchoIndex()}
	 * exists so this can be asserted rather than reasoned about.
	 */
	@Test
	public void namingAnAuthoredCitizenDoesNotBuildTheEchoIndex()
	{
		CitizenDirectory directory = shipped();

		for (UUID uuid : directory.authoredUuids())
		{
			assertNotNull(directory.find(uuid));
		}
		assertEquals("the fixture has to have asked about something", 269,
			directory.authoredUuids().size());

		assertFalse("269 authored lookups must not have derived a single echo",
			directory.hasBuiltEchoIndex());
	}

	/**
	 * A hidden echo is named, through the tier that exists for it.
	 *
	 * <p>Echoes are hideable like anybody else — the config item for the density dial
	 * says as much, "hiding one does not hide the citizen it came from" — so a stored
	 * uuid really can be one, and a panel that could not place it would show a row of
	 * hexadecimal for the one figure whose whole identity is that it is anonymous.
	 *
	 * <p>The uuid is derived here the same way {@link EntityScene#ensureBuilt} derives
	 * it: from the region's <b>whole</b> roster. Deriving one citizen at a time produces
	 * different tiles and different uuids, so a fixture that took a shortcut would be
	 * asking about an echo the plugin never builds.
	 */
	@Test
	public void aHiddenEchoIsNamedThroughTheSecondTier()
	{
		RegionDataLoader loader = new RegionDataLoader(TestGson.injected());
		CitizenDirectory directory = new CitizenDirectory(loader);

		RegionDefinition varrock = loader.loadRegion(12852);
		assertNotNull(varrock);
		List<EntityDefinition> echoes = CitizenEcho.echoesOfRegion(varrock.getEntities());
		assertFalse("region 12852 has to seed at least one echo for this to mean anything",
			echoes.isEmpty());

		EntityDefinition echo = echoes.get(0);
		assertTrue(echo.isEcho());

		CitizenDirectory.Entry entry = directory.find(echo.getUuid());
		assertNotNull("a derived citizen has to be nameable too", entry);
		assertEquals(CitizenEcho.ECHO_NAME, entry.getName());
		assertEquals("and filed under the city whose checkbox governs it, which is its "
				+ "source's rather than its own tile's",
			City.of(echo.getCityRegionId()), entry.getCity());

		assertTrue("asking about an echo is what builds the tier", directory.hasBuiltEchoIndex());
	}

	/**
	 * A uuid nothing in the jar carries is answered with {@code null}, and only after
	 * both tiers have been asked.
	 *
	 * <p>Reachable rather than hypothetical: fifteen places left the dataset on
	 * 2026-08-24, taking their citizens with them, and a profile that hid one of them
	 * still holds the uuid. The panel's job there is to say so and offer to clear it,
	 * which it can only do if this returns null instead of guessing.
	 */
	@Test
	public void aUuidFromAPlaceTheDatasetNoLongerCoversIsSimplyNotFound()
	{
		CitizenDirectory directory = shipped();

		assertNull(directory.find(UUID.fromString("deadbeef-0000-4000-8000-000000000001")));
		assertTrue("and it had to look in both tiers before answering",
			directory.hasBuiltEchoIndex());
	}

	// --- the index is a fact about the jar, not about the loader ---------------

	/**
	 * The index is built once and reused.
	 *
	 * <p>The panel composes a model once a game tick while it is open, and every model
	 * asks the directory nine times for card counts plus once per override row. A
	 * directory that reparsed 376KB of JSON on each of those would be per-tick file
	 * work in a plugin whose performance argument is that it does almost none.
	 *
	 * <p>Counted through {@link FakeRegions#loadCalls()}, which records every
	 * {@code loadRegion} — so this measures the parse actually happening rather than a
	 * flag saying it did not.
	 */
	@Test
	public void theDatasetIsReadOnceHoweverManyTimesTheIndexIsAsked()
	{
		FakeRegions regions = new FakeRegions();
		regions.file(12852, regions.citizen(12852, 3225, 3360, 0));
		CitizenDirectory directory = new CitizenDirectory(regions);

		assertEquals(1, directory.citizenCount(City.VARROCK));
		int afterFirst = regions.loadCalls().size();
		assertEquals("the first question has to read every region a city claims",
			totalRegions(), afterFirst);

		for (int i = 0; i < 50; i++)
		{
			directory.citizenCount(City.VARROCK);
			directory.citizenCount(City.FALADOR);
			directory.find(UUID.randomUUID());
		}

		// The echo tier is built by the first unplaceable uuid above and then cached too,
		// so the total is two passes over the region list and not fifty-one.
		assertEquals("and nothing after that may read them again",
			2 * totalRegions(), regions.loadCalls().size());
	}

	/**
	 * A city naming a region with no file behind it costs that city's count and nothing
	 * else.
	 *
	 * <p>{@code CityTest} already fails the build for a phantom region id, so this is
	 * the behaviour on the way to that red test rather than a supported state — but the
	 * panel is a surface a user opens, and "the sidebar throws" is a worse way to learn
	 * about a bad region id than "Falador says 0".
	 */
	@Test
	public void aCityWithNoFilesCountsZeroRatherThanFailing()
	{
		FakeRegions regions = new FakeRegions();
		regions.file(12852, regions.citizen(12852, 3225, 3360, 0));
		CitizenDirectory directory = new CitizenDirectory(regions);

		assertEquals(1, directory.citizenCount(City.VARROCK));
		for (City city : City.values())
		{
			if (city != City.VARROCK)
			{
				assertEquals(city + " has no file in this fixture", 0, directory.citizenCount(city));
			}
		}
	}

	/** No uuid may be claimed by two cities, or a row could name the wrong place. */
	@Test
	public void noUuidIsIndexedTwice()
	{
		RegionDataLoader loader = new RegionDataLoader(TestGson.injected());
		Set<UUID> seen = new HashSet<>();
		List<String> duplicates = new ArrayList<>();

		for (City city : City.values())
		{
			for (int regionId : city.getRegionIds())
			{
				RegionDefinition region = loader.loadRegion(regionId);
				assertNotNull(region);
				for (EntityDefinition definition : region.getEntities())
				{
					if (definition.getType().isCitizen() && !seen.add(definition.getUuid()))
					{
						duplicates.add(definition.label() + " (" + definition.getUuid() + ")");
					}
				}
			}
		}

		assertEquals("a uuid in two files is a row that names whichever city won: "
			+ duplicates, 0, duplicates.size());
		assertEquals(269, seen.size());
	}

	private static CitizenDirectory shipped()
	{
		return new CitizenDirectory(new RegionDataLoader(TestGson.injected()));
	}

	private static int totalRegions()
	{
		int n = 0;
		for (City city : City.values())
		{
			n += city.getRegionIds().length;
		}
		return n;
	}
}
