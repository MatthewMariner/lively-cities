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
 * <p>Only the four cities with a real, defensible identity are listed; every
 * other city defaults to {@link Theme#GENERIC} — see {@link Theme} for why the
 * set stops here rather than reaching for Fremennik/snow, elven, or dwarven.
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

		// Musa Point: Karamja's jungle coastline. The only tropical city shipped.
		byCity.put(City.MUSA_POINT, Theme.KARAMJA_JUNGLE);

		// Canifis: Morytania's werewolf town.
		byCity.put(City.CANIFIS, Theme.MORYTANIA_UNDEAD);

		// The Barrows mounds themselves. Note this does NOT make the six
		// Barrows Brothers compatible — they carry Theme.UNIQUE_BOSS, which is
		// mapped to no city anywhere, deliberately. This entry is here for a
		// generic, unnamed undead citizen (a "barrow wight", say), which the
		// dataset does not currently ship.
		byCity.put(City.BARROWS, Theme.MORYTANIA_UNDEAD);

		return byCity;
	}
}
