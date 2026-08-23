package com.matthewmariner.livelycities;

/**
 * The one rule the placement lint is built on, pulled out on its own so it has
 * its own test ({@code PlacementCompatibilityTest}) against a hand-built
 * fixture rather than only ever being exercised indirectly through the real
 * 129-citizen dataset.
 */
final class PlacementCompatibility
{
	private PlacementCompatibility()
	{
	}

	/**
	 * @param entityTheme the theme a citizen carries
	 * @param regionTheme the theme its region expects ({@code CityTheme.of(..)})
	 * @return {@code true} if a citizen with {@code entityTheme} may stand in a
	 * region expecting {@code regionTheme}
	 */
	static boolean isCompatible(Theme entityTheme, Theme regionTheme)
	{
		// A plain Gielinor citizen needs no regional justification.
		if (entityTheme == Theme.GENERIC)
		{
			return true;
		}

		// Otherwise the two have to actually agree. Theme.UNIQUE_BOSS is never
		// the region theme CityTheme hands out for any city (see CityTheme), so
		// this equality can never hold for it in practice — it is compatible
		// with nothing, everywhere, by construction rather than by a special
		// case here.
		return entityTheme == regionTheme;
	}
}
