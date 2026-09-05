package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Who each shipped uuid <i>is</i>, and how many citizens each place holds — the two
 * questions the live scene cannot answer.
 *
 * <p><b>Why it exists.</b> Hiding and muting are stored as bare uuids
 * ({@link CitizenOverrides}), and {@link EntityScene} can only put a name to one while
 * its region is in the loaded scene. So the moment you walk away from somebody you
 * hid, the plugin knows only that <i>a</i> uuid is hidden. The undo that ships in the
 * config screen is "unhide all", which needs no names; a list you can restore one row
 * from does, and the row that reads "Bob the Jagex Mod — Varrock" is the difference
 * between a repair and a wall of hexadecimal.
 *
 * <p><b>Client-free, and that is deliberate rather than incidental.</b> Everything
 * here comes out of the classpath through {@link RegionDataLoader} and
 * {@link CitizenEcho}, neither of which touches the client, so the whole index can be
 * built and asserted without a game running — and so the panel's rows are a fact about
 * the jar rather than about what happens to be on screen.
 *
 * <p><b>Two tiers, and the second is usually never built.</b>
 * <ul>
 *   <li><b>Authored.</b> Parse the 27 shipped region files: 269 citizens, their names,
 *       and the {@link City} each belongs to. This is also where the per-city counts
 *       come from, so a card that says "24 citizens" is counting the dataset rather
 *       than quoting a number somebody typed.</li>
 *   <li><b>Derived.</b> Re-run {@link CitizenEcho#echoesOfRegion} over each region's
 *       roster to learn the echoes' uuids. Echoes exist only at
 *       {@link CrowdDensity#CROWDED} but they are hideable like anybody else, so a
 *       hidden uuid really can be one — and the derivation costs more than the parse
 *       it follows. It is therefore built on the first uuid the authored tier cannot
 *       place and not before, which for the overwhelming majority of profiles is
 *       never.</li>
 * </ul>
 *
 * <p><b>Muted uuids never reach the second tier</b>, and that is structural rather
 * than lucky: {@code CitizenMenu} only offers "Mute" to an entity with remarks, and
 * {@code EntityDefinition.echoOf} gives every echo the empty remarks array. An echo
 * cannot be muted because it has nothing to say.
 *
 * <p><b>Nothing here is cleared on teardown, unlike every cache in
 * {@link EntityScene}.</b> Those hold wrappers, lit models and objects the client has
 * registered — things a teardown exists to let go of. This holds strings and ints
 * decoded from resources inside the running jar, which cannot change while the client
 * is up and which no scene points at. Dropping it would only mean paying the parse
 * again the next time the plugin is enabled.
 */
@Slf4j
@Singleton
class CitizenDirectory
{
	private final RegionDataLoader loader;

	/** Authored citizens by uuid. Null until the first question is asked. */
	@Nullable
	private Map<UUID, Entry> authored;

	/** Citizens per city, from the same pass that fills {@link #authored}. */
	@Nullable
	private Map<City, Integer> counts;

	/**
	 * Echo uuids, built only when {@link #authored} cannot place one — see the class
	 * javadoc. Null means "not built"; an empty map after a build is a legitimate
	 * answer and is why this is not tracked by emptiness.
	 */
	@Nullable
	private Map<UUID, Entry> derived;

	@Inject
	CitizenDirectory(RegionDataLoader loader)
	{
		this.loader = loader;
	}

	/**
	 * @return how many citizens the shipped dataset places in this city — scenery
	 * excluded, echoes excluded. The number on a city card.
	 */
	int citizenCount(City city)
	{
		Integer n = index().get(city);
		return n == null ? 0 : n;
	}

	/**
	 * @return who this uuid is, or {@code null} if no shipped record and no derivable
	 * echo carries it. Null is reachable in practice: a profile can hold a uuid from a
	 * place the dataset used to cover — fifteen were dropped on 2026-08-24 — and the
	 * panel says so rather than inventing a name.
	 */
	@Nullable
	Entry find(UUID uuid)
	{
		Entry entry = authoredIndex().get(uuid);
		if (entry != null)
		{
			// The common case, and the one that must not pay for the tier below: an
			// ordinary profile's overrides are all authored citizens.
			return entry;
		}

		return echoIndex().get(uuid);
	}

	/** @return how many authored citizens are indexed. For the tests. */
	int size()
	{
		return authoredIndex().size();
	}

	/**
	 * @return whether the echo tier has been built. Nothing in the plugin reads this;
	 * it is how {@code CitizenDirectoryTest} asserts that an ordinary profile never
	 * pays for it, which is a claim about cost that no count of results could make.
	 */
	boolean hasBuiltEchoIndex()
	{
		return derived != null;
	}

	private Map<City, Integer> index()
	{
		buildAuthored();
		return counts;
	}

	private Map<UUID, Entry> authoredIndex()
	{
		buildAuthored();
		return authored;
	}

	private void buildAuthored()
	{
		if (authored != null)
		{
			return;
		}

		Map<UUID, Entry> byUuid = new HashMap<>();
		Map<City, Integer> perCity = new EnumMap<>(City.class);

		for (City city : City.values())
		{
			int citizens = 0;
			for (int regionId : city.getRegionIds())
			{
				RegionDefinition region = loader.loadRegion(regionId);
				if (region == null)
				{
					// A city naming a region that ships no file is a red CityTest, not a
					// reason for the panel to fail to open.
					continue;
				}

				for (EntityDefinition definition : region.getEntities())
				{
					if (!definition.getType().isCitizen())
					{
						// Scenery carries no name and cannot be hidden or muted from a
						// menu that is never offered on it.
						continue;
					}

					citizens++;
					byUuid.put(definition.getUuid(), new Entry(nameOf(definition), city));
				}
			}
			perCity.put(city, citizens);
		}

		authored = Collections.unmodifiableMap(byUuid);
		counts = Collections.unmodifiableMap(perCity);
		log.debug("citizen directory: {} authored citizen(s) across {} cities",
			authored.size(), counts.size());
	}

	/**
	 * The echo tier, derived the same way {@link EntityScene#ensureBuilt} derives it —
	 * from the region's <i>whole</i> authored roster, because separation is judged
	 * against everything in the region and deriving one citizen at a time gives
	 * different uuids.
	 */
	private Map<UUID, Entry> echoIndex()
	{
		if (derived != null)
		{
			return derived;
		}

		Map<UUID, Entry> byUuid = new HashMap<>();
		for (City city : City.values())
		{
			for (int regionId : city.getRegionIds())
			{
				RegionDefinition region = loader.loadRegion(regionId);
				if (region == null)
				{
					continue;
				}

				for (EntityDefinition echo : CitizenEcho.echoesOfRegion(region.getEntities()))
				{
					// Its source's city, which is the checkbox that governs it — the same
					// rule EntityScene.allowedByConfig applies, and not the city its own
					// tile happens to fall in.
					City home = City.of(echo.getCityRegionId());
					byUuid.put(echo.getUuid(), new Entry(nameOf(echo), home == null ? city : home));
				}
			}
		}

		derived = Collections.unmodifiableMap(byUuid);
		log.debug("citizen directory: {} derived citizen(s) indexed", derived.size());
		return derived;
	}

	/**
	 * @return every uuid the directory knows, authored tier only. For the tests, which
	 * use it to prove the index really covers the shipped dataset rather than a subset
	 * of it that happened to parse.
	 */
	List<UUID> authoredUuids()
	{
		return new ArrayList<>(authoredIndex().keySet());
	}

	private static String nameOf(EntityDefinition definition)
	{
		String name = definition.getName();
		return name == null || name.trim().isEmpty() ? "Unnamed citizen" : name.trim();
	}

	/** A citizen the directory can place: what it is called, and whose checkbox owns it. */
	static final class Entry
	{
		private final String name;
		private final City city;

		Entry(String name, City city)
		{
			this.name = name;
			this.city = city;
		}

		String getName()
		{
			return name;
		}

		City getCity()
		{
			return city;
		}

		@Override
		public String toString()
		{
			return name + " (" + city.getLabel() + ")";
		}
	}
}
