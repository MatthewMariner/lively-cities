package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import javax.annotation.Nullable;
import net.runelite.api.coords.WorldPoint;

/**
 * Everything {@link LivelyCitiesPanel} draws, worked out where the client can be read
 * and handed over finished.
 *
 * <p><b>Why the answer is pushed rather than pulled.</b> Swing's event dispatch thread
 * is not the client thread, and the two numbers this panel exists to show —
 * how many figures are on screen, and which of them belong to which city — come from
 * asking the client whether each {@code RuneLiteObject} is registered. That is a client
 * read, and a client read off the client thread throws {@code IllegalStateException} in
 * a shipped client. So the panel gets a value object and does layout, and the deciding
 * happens in {@link #of}, which is static, client-free and under test.
 *
 * <p><b>Immutable, and every list already sorted.</b> A model is composed once a game
 * tick while the panel is open; if the row order came out of a {@code HashSet} the
 * panel would reshuffle under the cursor every 600ms and be unusable for the one thing
 * it is for, which is clicking a specific row. {@link #getOverrides()} is therefore
 * ordered by city, then name, then uuid — three keys, the last of which is unique, so
 * the order is total and cannot depend on iteration order.
 *
 * <p><b>What it deliberately does not hold.</b> No {@link LivelyEntity}, no
 * {@link EntityDefinition}, no live view of anything. A wrapper reaching Swing would
 * be a lit model kept alive by a panel — the same class of leak the teardown contract
 * spends its time on, arriving through the one door that is not on the client thread.
 * Rows carry a uuid, which is what {@link CitizenOverrides} keys on anyway.
 */
final class PanelModel
{
	/**
	 * What the panel shows before the first game tick, and after a logout.
	 *
	 * <p>Not "no cities": the nine cards are a fact about the plugin rather than about
	 * the session, and a panel that emptied itself at the login screen would look
	 * broken at the exact moment somebody opens it to find out what the plugin does.
	 * The live numbers are the part that goes to zero.
	 */
	static PanelModel loggedOut(LivelyCitiesConfig config, CitizenDirectory directory)
	{
		return of(null, SceneCensus.EMPTY, config,
			Collections.emptySet(), Collections.emptySet(), directory);
	}

	private final boolean inWorld;
	private final int regionId;

	@Nullable
	private final City here;

	private final SceneCensus census;
	private final CrowdDensity density;
	private final List<CityRow> cities;
	private final List<OverrideRow> overrides;

	private PanelModel(
		boolean inWorld,
		int regionId,
		@Nullable City here,
		SceneCensus census,
		CrowdDensity density,
		List<CityRow> cities,
		List<OverrideRow> overrides)
	{
		this.inWorld = inWorld;
		this.regionId = regionId;
		this.here = here;
		this.census = census;
		this.density = density;
		this.cities = Collections.unmodifiableList(cities);
		this.overrides = Collections.unmodifiableList(overrides);
	}

	/**
	 * Composes one reading.
	 *
	 * @param playerLocation where the player is standing, or {@code null} when there is
	 *                       no world — which is a state the panel has to be able to
	 *                       draw, because a plugin panel can be opened at the login
	 *                       screen
	 * @param census         {@link EntityScene#census()}, already taken
	 * @param config         the dials, read once here rather than once per row
	 * @param hidden         {@link CitizenOverrides#hiddenUuids()}
	 * @param muted          {@link CitizenOverrides#mutedUuids()}
	 * @param directory      what puts a name to a uuid — see {@link CitizenDirectory}
	 */
	static PanelModel of(
		@Nullable WorldPoint playerLocation,
		SceneCensus census,
		LivelyCitiesConfig config,
		Set<UUID> hidden,
		Set<UUID> muted,
		CitizenDirectory directory)
	{
		final int regionId = playerLocation == null
			? 0
			: RenderPolicy.regionIdOf(playerLocation.getX(), playerLocation.getY());
		final City here = playerLocation == null ? null : City.of(regionId);

		// A profile carrying a value this build does not know about deserialises to
		// null rather than throwing — the same case EntityScene.runVisibilityPass
		// handles, and the same answer, because a panel that showed no density selected
		// would be a panel with no way to select one.
		CrowdDensity density = config.crowdDensity();
		if (density == null)
		{
			density = CrowdDensity.FULL;
		}

		List<CityRow> cities = new ArrayList<>(City.values().length);
		for (City city : City.values())
		{
			cities.add(new CityRow(
				city,
				City.isEnabled(city.getRegionIds()[0], config),
				directory.citizenCount(city),
				census.activeIn(city),
				city == here));
		}

		return new PanelModel(playerLocation != null, regionId, here, census, density,
			cities, overrideRows(hidden, muted, directory));
	}

	/**
	 * One row per citizen the user has overridden, whether they hid it, muted it or
	 * both.
	 *
	 * <p><b>One row and not two lists</b>, because the two overrides are decisions
	 * about the same person and a citizen who is both hidden and muted would otherwise
	 * appear twice with no indication that the two rows are one figure. The row carries
	 * both flags and the panel offers whichever restores apply.
	 *
	 * <p>The union is walked through a {@link TreeSet} keyed on the uuid string so this
	 * method's own iteration order cannot depend on the two incoming sets' — they are
	 * {@code LinkedHashSet}s in insertion order today, which is an order the user's
	 * click history decides. The final sort is what the panel sees; this one only makes
	 * the intermediate deterministic so a failing test says the same thing twice.
	 */
	private static List<OverrideRow> overrideRows(
		Set<UUID> hidden, Set<UUID> muted, CitizenDirectory directory)
	{
		Set<UUID> all = new TreeSet<>(Comparator.comparing(UUID::toString));
		all.addAll(hidden);
		all.addAll(muted);

		List<OverrideRow> rows = new ArrayList<>(all.size());
		for (UUID uuid : all)
		{
			CitizenDirectory.Entry entry = directory.find(uuid);
			rows.add(new OverrideRow(
				uuid,
				entry == null ? null : entry.getName(),
				entry == null ? null : entry.getCity(),
				hidden.contains(uuid),
				muted.contains(uuid)));
		}

		// City, then name, then uuid. The last key is unique, so the order is total:
		// two citizens sharing a name in the same city — the dataset has several — still
		// come out in the same order on every tick, and a row cannot move under the
		// cursor between the reading and the click.
		rows.sort(Comparator
			.comparing((OverrideRow row) -> row.getCity() == null ? "￿" : row.getCity().getLabel())
			.thenComparing(OverrideRow::getDisplayName)
			.thenComparing(row -> row.getUuid().toString()));
		return rows;
	}

	/** @return whether there is a player in a world to describe at all */
	boolean isInWorld()
	{
		return inWorld;
	}

	/** @return the region the player is standing in, or {@code 0} when out of world */
	int getRegionId()
	{
		return regionId;
	}

	/**
	 * @return the city the player is standing in, or {@code null} — which is the
	 * ordinary case, because the dataset covers 27 of the game's regions and the player
	 * is usually in one of the others
	 */
	@Nullable
	City getHere()
	{
		return here;
	}

	SceneCensus getCensus()
	{
		return census;
	}

	CrowdDensity getDensity()
	{
		return density;
	}

	/** @return one row per city, in {@link City} declaration order, never empty */
	List<CityRow> getCities()
	{
		return cities;
	}

	/** @return one row per overridden citizen, sorted; empty for a fresh profile */
	List<OverrideRow> getOverrides()
	{
		return overrides;
	}

	/** One city card: its checkbox, what the dataset holds, and what is on screen. */
	static final class CityRow
	{
		private final City city;
		private final boolean enabled;
		private final int citizens;
		private final int active;
		private final boolean here;

		CityRow(City city, boolean enabled, int citizens, int active, boolean here)
		{
			this.city = city;
			this.enabled = enabled;
			this.citizens = citizens;
			this.active = active;
			this.here = here;
		}

		City getCity()
		{
			return city;
		}

		boolean isEnabled()
		{
			return enabled;
		}

		/** @return how many citizens the dataset places here, whatever is on screen */
		int getCitizens()
		{
			return citizens;
		}

		/**
		 * @return how many of this city's figures the client currently has registered.
		 * Zero everywhere but the place the player is standing in, and often lower than
		 * {@link #getCitizens()} even there — the render distance, the density dial and
		 * the object cap all cut it down, which is exactly what a player looking at
		 * "24 citizens · 11 here" is entitled to see.
		 */
		int getActive()
		{
			return active;
		}

		/** @return whether the player is standing in this city right now */
		boolean isHere()
		{
			return here;
		}
	}

	/**
	 * One citizen the user hid, muted, or both.
	 *
	 * <p>{@code name} and {@code city} are nullable together: a uuid the directory
	 * cannot place is one from a place the dataset no longer covers — fifteen were
	 * dropped on 2026-08-24 — and the row says so rather than inventing somebody.
	 */
	static final class OverrideRow
	{
		private final UUID uuid;

		@Nullable
		private final String name;

		@Nullable
		private final City city;

		private final boolean hidden;
		private final boolean muted;

		OverrideRow(UUID uuid, @Nullable String name, @Nullable City city, boolean hidden, boolean muted)
		{
			this.uuid = uuid;
			this.name = name;
			this.city = city;
			this.hidden = hidden;
			this.muted = muted;
		}

		UUID getUuid()
		{
			return uuid;
		}

		/** @return the citizen's name, or {@code null} if the directory could not place it */
		@Nullable
		String getName()
		{
			return name;
		}

		/**
		 * @return what to put on the row: the name when there is one, and otherwise the
		 * uuid's first block. Never null, so the panel has nothing to branch on and a
		 * sort key that always exists.
		 */
		String getDisplayName()
		{
			if (name != null)
			{
				return name;
			}

			String text = uuid.toString();
			return "Citizen " + text.substring(0, text.indexOf('-'));
		}

		@Nullable
		City getCity()
		{
			return city;
		}

		boolean isHidden()
		{
			return hidden;
		}

		boolean isMuted()
		{
			return muted;
		}
	}
}
