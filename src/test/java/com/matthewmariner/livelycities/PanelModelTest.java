package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Everything the side panel draws, decided here where there is no Swing and no client.
 *
 * <p>{@link LivelyCitiesPanel} is layout and mouse listeners; this is the part with
 * answers in it, and separating them is what makes the answers assertable on a build
 * machine with no display. So the rules that matter are all here: which checkbox a card
 * reads, where the live figures come from, what happens to a uuid nobody can place, and
 * the ordering the whole thing is unusable without.
 */
public class PanelModelTest
{
	/** Varrock square. Region 12852, which {@link City#VARROCK} claims. */
	private static final WorldPoint IN_VARROCK = new WorldPoint(3225, 3360, 0);

	/**
	 * A tile in region 11422 — Keldagrim, which no city claims and which ships no region
	 * file. The same region {@code CityTest.aRegionNoCityClaimsIsStillShown} uses, so the
	 * two tests are talking about the same "nowhere".
	 */
	private static final WorldPoint NOWHERE = new WorldPoint(2845, 10120, 0);

	private final FakeConfig config = new FakeConfig();

	// --- the city cards -------------------------------------------------------

	/**
	 * Each card reads its own city's checkbox, and no other's.
	 *
	 * <p>The same composition {@code CityTest} asserts for the render path, asserted
	 * again for the panel path, and it is not redundant: the panel walks
	 * {@code City.values()} building rows in a loop, which is exactly the shape that
	 * makes an off-by-one — every card showing the state of the city after it — a
	 * plausible defect. {@link FakeConfig} wires all nine getters to their own
	 * {@link City}, so switching off exactly one city and finding exactly one card off
	 * is the two mappings composing to the identity.
	 */
	@Test
	public void everyCardReadsItsOwnCityCheckbox()
	{
		for (City target : City.values())
		{
			PanelModel model = model(new FakeConfig().disableOnly(target), IN_VARROCK,
				SceneCensus.EMPTY, directory());

			assertEquals("one card per city", City.values().length, model.getCities().size());

			for (PanelModel.CityRow row : model.getCities())
			{
				if (row.getCity() == target)
				{
					assertFalse("unticking " + target + " has to switch its own card off",
						row.isEnabled());
				}
				else
				{
					assertTrue("unticking " + target + " must not switch off " + row.getCity(),
						row.isEnabled());
				}
			}
		}
	}

	/** The cards come out in {@link City} order, which is the order the checkboxes are in. */
	@Test
	public void theCardsAreInTheSameOrderAsTheCheckboxes()
	{
		PanelModel model = model(config, IN_VARROCK, SceneCensus.EMPTY, directory());

		City[] cities = City.values();
		for (int i = 0; i < cities.length; i++)
		{
			assertEquals("card " + i, cities[i], model.getCities().get(i).getCity());
		}
	}

	/**
	 * A card's citizen count is the dataset's, and its live count is the census's.
	 *
	 * <p>Two different numbers about the same city, and the reason to check them
	 * together is that they are trivially confusable: a card that showed the live count
	 * where the roster count belongs would read "Varrock, 0 citizens" from the login
	 * screen, which is the panel telling a first-time user the plugin has no data.
	 */
	@Test
	public void aCardShowsTheDatasetsCountAndTheScenesCountSeparately()
	{
		EnumMap<City, Integer> active = new EnumMap<>(City.class);
		active.put(City.VARROCK, 34);
		active.put(City.GRAND_EXCHANGE, 7);

		PanelModel model = model(config, IN_VARROCK,
			new SceneCensus(41, 190, 12, 2, active), directory());

		assertEquals("Varrock's roster", 71, row(model, City.VARROCK).getCitizens());
		assertEquals("and what is on screen of it", 34, row(model, City.VARROCK).getActive());

		// The Grand Exchange is a region away, so it is not where the player is and its
		// figures are still up. A card that only reported the live count for the city
		// the player stands in would lose this, and it is exactly the thing no config
		// screen can show.
		assertEquals(24, row(model, City.GRAND_EXCHANGE).getCitizens());
		assertEquals(7, row(model, City.GRAND_EXCHANGE).getActive());
		assertFalse(row(model, City.GRAND_EXCHANGE).isHere());

		assertEquals("a city with nothing up says so", 0, row(model, City.CATHERBY).getActive());
		assertEquals("but still knows its roster", 24, row(model, City.CATHERBY).getCitizens());
	}

	/**
	 * The nine cards survive being logged out. Only the live numbers go.
	 *
	 * <p>A panel that emptied itself at the login screen would be blank at the one
	 * moment somebody opens it to find out what the plugin does.
	 */
	@Test
	public void theCardsAreThereWithNoWorldAtAll()
	{
		PanelModel model = PanelModel.loggedOut(config, directory());

		assertFalse(model.isInWorld());
		assertNull("nowhere to be", model.getHere());
		assertEquals(0, model.getRegionId());
		assertEquals(City.values().length, model.getCities().size());
		assertEquals("the roster is a fact about the jar, not the session",
			71, row(model, City.VARROCK).getCitizens());
		assertEquals("what is on screen is a fact about the session",
			0, row(model, City.VARROCK).getActive());
		assertTrue("and nobody is overridden by default", model.getOverrides().isEmpty());
	}

	// --- where the player is --------------------------------------------------

	@Test
	public void standingInACityNamesIt()
	{
		PanelModel model = model(config, IN_VARROCK, SceneCensus.EMPTY, directory());

		assertTrue(model.isInWorld());
		assertEquals(City.VARROCK, model.getHere());
		assertEquals(12852, model.getRegionId());

		PanelModel.CityRow varrock = row(model, City.VARROCK);
		assertTrue("and the card says so", varrock.isHere());
		for (PanelModel.CityRow other : model.getCities())
		{
			if (other.getCity() != City.VARROCK)
			{
				assertFalse(other.getCity() + " is not where the player is", other.isHere());
			}
		}
	}

	/**
	 * Standing somewhere the dataset does not cover is the ordinary case, not an error.
	 *
	 * <p>The plugin ships 27 of the game's regions. Almost everywhere a player stands is
	 * one of the others, so the panel has to have something true to say there — the
	 * region id — rather than a blank or a city it guessed.
	 */
	@Test
	public void standingOutsideEveryCityStillReportsARegion()
	{
		PanelModel model = model(config, NOWHERE, SceneCensus.EMPTY, directory());

		assertTrue(model.isInWorld());
		assertNull("no city claims Keldagrim", model.getHere());
		assertEquals(RenderPolicy.regionIdOf(NOWHERE.getX(), NOWHERE.getY()), model.getRegionId());
		for (PanelModel.CityRow row : model.getCities())
		{
			assertFalse(row.getCity() + " must not claim the player", row.isHere());
		}
	}

	// --- the density dial -----------------------------------------------------

	@Test
	public void theDensityShownIsTheOneConfigured()
	{
		for (CrowdDensity density : CrowdDensity.values())
		{
			PanelModel model = model(new FakeConfig().setCrowdDensity(density),
				IN_VARROCK, SceneCensus.EMPTY, directory());
			assertEquals(density, model.getDensity());
		}
	}

	/**
	 * A density this build does not know about deserialises to {@code null}, and the
	 * panel shows {@code FULL} rather than nothing selected.
	 *
	 * <p>The same case {@code EntityScene.runVisibilityPass} already handles, and the
	 * same answer. It matters more here than there: a panel with no chip lit has no
	 * chip to click back to, so a profile written by a future version would leave a user
	 * with a dial they cannot operate.
	 */
	@Test
	public void anUnknownDensityFallsBackToFullRatherThanToNothing()
	{
		// Implemented rather than subclassed: FakeConfig is final, and every other getter
		// here wants its interface default anyway — this fixture is about exactly one
		// method returning null.
		LivelyCitiesConfig fromTheFuture = new LivelyCitiesConfig()
		{
			@Override
			public CrowdDensity crowdDensity()
			{
				return null;
			}
		};

		assertNull("the fixture has to actually hand back null", fromTheFuture.crowdDensity());
		assertEquals(CrowdDensity.FULL,
			model(fromTheFuture, IN_VARROCK, SceneCensus.EMPTY, directory()).getDensity());
	}

	// --- the hidden-and-muted list -------------------------------------------

	/**
	 * A citizen who is both hidden and muted is one row carrying both flags, not two
	 * rows.
	 *
	 * <p>Two rows would be two entries with the same name and no indication that they
	 * are one person, and restoring one of them would leave the other looking unchanged.
	 */
	@Test
	public void oneCitizenIsOneRowHoweverManyWaysTheyAreOverridden()
	{
		FakeRegions regions = new FakeRegions();
		CitizenDirectory directory = new CitizenDirectory(regions);

		UUID both = UUID.fromString("00000000-0000-4000-8000-000000000001");
		UUID justHidden = UUID.fromString("00000000-0000-4000-8000-000000000002");
		UUID justMuted = UUID.fromString("00000000-0000-4000-8000-000000000003");

		PanelModel model = model(config, IN_VARROCK, SceneCensus.EMPTY, directory,
			uuids(both, justHidden), uuids(both, justMuted));

		assertEquals("three citizens, three rows", 3, model.getOverrides().size());

		assertTrue(find(model, both).isHidden());
		assertTrue(find(model, both).isMuted());
		assertTrue(find(model, justHidden).isHidden());
		assertFalse(find(model, justHidden).isMuted());
		assertFalse(find(model, justMuted).isHidden());
		assertTrue(find(model, justMuted).isMuted());
	}

	/**
	 * The rows carry the name and the city out of the directory.
	 *
	 * <p>This is the whole point of the feature. "Unhide all" needs no names; a list you
	 * restore one row from is only a repair if the row says who it is, and the citizen
	 * you hid is usually a city away by the time you regret it.
	 */
	@Test
	public void aRowNamesTheCitizenAndThePlaceTheyBelongTo()
	{
		CitizenDirectory directory = new CitizenDirectory(new RegionDataLoader(TestGson.injected()));

		// A real shipped uuid, taken from the dataset rather than typed, so this cannot
		// pass by naming a citizen the fixture invented.
		UUID someone = directory.authoredUuids().get(0);
		CitizenDirectory.Entry expected = directory.find(someone);
		assertNotNull("the directory has to be able to place its own uuid", expected);

		PanelModel model = model(config, IN_VARROCK, SceneCensus.EMPTY, directory,
			Collections.emptySet(), uuids(someone));

		PanelModel.OverrideRow row = find(model, someone);
		assertEquals(expected.getName(), row.getName());
		assertEquals(expected.getName(), row.getDisplayName());
		assertEquals(expected.getCity(), row.getCity());
	}

	/**
	 * A uuid nobody can place still gets a row, and the row does not invent a name.
	 *
	 * <p>Reachable in practice rather than hypothetically: fifteen places were dropped
	 * from the dataset on 2026-08-24, and a profile that hid somebody in Canifis before
	 * then still holds their uuid. The honest outcome is a row you can clear, labelled
	 * with something that is true.
	 */
	@Test
	public void aUuidTheDirectoryCannotPlaceIsStillARowYouCanClear()
	{
		UUID stranger = UUID.fromString("deadbeef-0000-4000-8000-000000000001");

		PanelModel model = model(config, IN_VARROCK, SceneCensus.EMPTY,
			new CitizenDirectory(new FakeRegions()), uuids(stranger), Collections.emptySet());

		PanelModel.OverrideRow row = find(model, stranger);
		assertNull("no name was invented", row.getName());
		assertNull("and no city", row.getCity());
		assertEquals("but the row still says something", "Citizen deadbeef", row.getDisplayName());
		assertTrue(row.isHidden());
	}

	/**
	 * The row order is the same every time, whatever order the two sets arrive in.
	 *
	 * <p><b>This is the guard that makes the list clickable at all.</b> A model is
	 * composed once a game tick while the panel is open, so an order that depended on
	 * set iteration would reshuffle the rows about a hundred times a minute — and the
	 * one thing anybody does with this list is click a specific row.
	 *
	 * <p>The two runs below are given the same uuids in opposite insertion orders, which
	 * is exactly what a user produces by hiding people in a different sequence. A
	 * {@code LinkedHashSet} — which is what {@link UuidSetting} hands out — preserves
	 * that sequence, so this is the real input rather than a contrived one.
	 */
	@Test
	public void theRowsComeOutInTheSameOrderHoweverTheyWentIn()
	{
		CitizenDirectory directory = new CitizenDirectory(new RegionDataLoader(TestGson.injected()));
		List<UUID> shipped = directory.authoredUuids();
		assertTrue("the dataset has to be on the classpath", shipped.size() > 20);

		List<UUID> forwards = shipped.subList(0, 12);
		List<UUID> backwards = new ArrayList<>(forwards);
		Collections.reverse(backwards);

		List<PanelModel.OverrideRow> first = model(config, IN_VARROCK, SceneCensus.EMPTY,
			directory, new LinkedHashSet<>(forwards), Collections.emptySet()).getOverrides();
		List<PanelModel.OverrideRow> second = model(config, IN_VARROCK, SceneCensus.EMPTY,
			directory, new LinkedHashSet<>(backwards), Collections.emptySet()).getOverrides();

		assertEquals(12, first.size());
		assertEquals(first.size(), second.size());
		for (int i = 0; i < first.size(); i++)
		{
			assertEquals("row " + i + " has to be the same citizen both times",
				first.get(i).getUuid(), second.get(i).getUuid());
		}

		// And the order is the one claimed: by city, then by name. Asserted rather than
		// assumed, because "stable" and "sorted the way the panel says" are two claims
		// and only the first survives a comparator that sorted on the uuid alone.
		for (int i = 1; i < first.size(); i++)
		{
			String previous = key(first.get(i - 1));
			String current = key(first.get(i));
			assertTrue("'" + previous + "' must not sort after '" + current + "'",
				previous.compareTo(current) <= 0);
		}
	}

	/** Nothing overridden is an empty list rather than a null one. */
	@Test
	public void aFreshProfileHasNoRows()
	{
		assertTrue(model(config, IN_VARROCK, SceneCensus.EMPTY, directory())
			.getOverrides().isEmpty());
	}

	// --- the census passes through untouched ---------------------------------

	@Test
	public void theLiveTotalsAreTheCensussOwn()
	{
		EnumMap<City, Integer> byCity = new EnumMap<>(City.class);
		byCity.put(City.LUMBRIDGE, 5);
		SceneCensus census = new SceneCensus(5, 61, 3, 1, byCity);

		PanelModel model = model(config, IN_VARROCK, census, directory());

		assertEquals(5, model.getCensus().getActive());
		assertEquals(61, model.getCensus().getInScope());
		assertEquals(3, model.getCensus().getWalking());
		assertEquals(1, model.getCensus().getTalking());
	}

	// --- helpers ---------------------------------------------------------------

	/** The real shipped dataset, which is what the card counts have to come from. */
	private static CitizenDirectory directory()
	{
		return new CitizenDirectory(new RegionDataLoader(TestGson.injected()));
	}

	private static PanelModel model(
		LivelyCitiesConfig config, WorldPoint at, SceneCensus census, CitizenDirectory directory)
	{
		return model(config, at, census, directory, Collections.emptySet(), Collections.emptySet());
	}

	private static PanelModel model(
		LivelyCitiesConfig config,
		WorldPoint at,
		SceneCensus census,
		CitizenDirectory directory,
		Set<UUID> hidden,
		Set<UUID> muted)
	{
		return PanelModel.of(at, census, config, hidden, muted, directory);
	}

	private static Set<UUID> uuids(UUID... uuids)
	{
		return new LinkedHashSet<>(Arrays.asList(uuids));
	}

	private static PanelModel.CityRow row(PanelModel model, City city)
	{
		for (PanelModel.CityRow row : model.getCities())
		{
			if (row.getCity() == city)
			{
				return row;
			}
		}
		throw new AssertionError("no card for " + city);
	}

	private static PanelModel.OverrideRow find(PanelModel model, UUID uuid)
	{
		for (PanelModel.OverrideRow row : model.getOverrides())
		{
			if (row.getUuid().equals(uuid))
			{
				return row;
			}
		}
		throw new AssertionError("no row for " + uuid);
	}

	private static String key(PanelModel.OverrideRow row)
	{
		return (row.getCity() == null ? "￿" : row.getCity().getLabel()) + ' '
			+ row.getDisplayName();
	}
}
