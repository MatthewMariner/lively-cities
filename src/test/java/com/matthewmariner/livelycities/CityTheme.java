package com.matthewmariner.livelycities;

import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Which {@link Theme} each {@link City} is expected to hold.
 *
 * <p>Built directly on {@link City} rather than a second region list — the
 * class javadoc on {@code City} is explicit that it is the single source of
 * truth for region grouping, and {@code CityTest} is what keeps every shipped
 * region mapped to exactly one city. Duplicating region ids here would give
 * this lint its own, second chance to disagree with that mapping. This class
 * only ever asks "which theme does this already-resolved city expect", so a
 * region can only be misfiled once, in {@link City}.
 *
 * <p>Only the cities with a real, defensible identity are listed; every other
 * city defaults to {@link Theme#GENERIC} — see {@link Theme} for why the set
 * stops here rather than reaching for Fremennik/snow, elven, or dwarven.
 */
final class CityTheme
{
	private static final Map<City, Theme> BY_CITY = build();

	private CityTheme()
	{
	}

	/**
	 * @param city a city, or {@code null} for a region no city claims
	 * @return the theme entities in this city are expected to match; a city
	 * with no explicit entry (and a {@code null} city — see {@code City}'s own
	 * fail-open rule for an unmapped region) both answer {@link Theme#GENERIC}
	 */
	static Theme of(@Nullable City city)
	{
		if (city == null)
		{
			return Theme.GENERIC;
		}

		return BY_CITY.getOrDefault(city, Theme.GENERIC);
	}

	private static Map<City, Theme> build()
	{
		Map<City, Theme> byCity = new EnumMap<>(City.class);

		// Al Kharid: the shipped dataset's one desert city.
		byCity.put(City.AL_KHARID, Theme.DESERT);

		// Musa Point (Karamja) and Canifis/the Barrows (Morytania) used to be
		// here, carrying Theme.KARAMJA_JUNGLE and Theme.MORYTANIA_UNDEAD. All
		// three places left the dataset in the nine-city cut, so both themes lost
		// their only anchor and were removed from Theme along with these rows —
		// see Theme's javadoc for the rule, which is the same one that keeps
		// FREMENNIK_SNOW and an elven theme out.

		// The Grand Exchange, and the only city that may hold a cameo. Note what
		// this does *not* do: Theme.GENERIC is compatible with every region, so
		// tagging the GE this way does not stop an ordinary townsperson standing
		// there — Richard the cook and the squirrel in 12598.json are both GENERIC
		// and both still pass. What it does is make Theme.CAMEO compatible with
		// here and nowhere else. See Theme.CAMEO.
		byCity.put(City.GRAND_EXCHANGE, Theme.CAMEO);

		return byCity;
	}
}
