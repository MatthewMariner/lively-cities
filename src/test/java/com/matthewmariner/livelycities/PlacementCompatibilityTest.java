package com.matthewmariner.livelycities;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The compatibility rule in isolation, against a fixture with more than one
 * theme in it on purpose — a fixture with only ever {@code GENERIC} vs
 * {@code GENERIC} could not tell a correct rule from one that always returns
 * {@code true}.
 */
public class PlacementCompatibilityTest
{
	@Test
	public void aGenericEntityFitsAnyRegion()
	{
		// Every theme there is, so a new constant that this rule happens to reject
		// cannot slip in unnoticed.
		for (Theme regionTheme : Theme.values())
		{
			assertTrue("a generic citizen must fit a " + regionTheme + " region",
				PlacementCompatibility.isCompatible(Theme.GENERIC, regionTheme));
		}
	}

	@Test
	public void aThemedEntityFitsItsOwnRegionTheme()
	{
		assertTrue(PlacementCompatibility.isCompatible(Theme.DESERT, Theme.DESERT));
		assertTrue(PlacementCompatibility.isCompatible(Theme.CAMEO, Theme.CAMEO));
	}

	/**
	 * The fixture deliberately uses <b>two different</b> non-generic themes on the
	 * region side. One would not be enough: a rule that returned {@code true}
	 * whenever the region theme was anything other than the one theme tested would
	 * pass, and the mismatch case is the whole point of the class.
	 */
	@Test
	public void aThemedEntityDoesNotFitAMismatchedRegionTheme()
	{
		assertFalse(PlacementCompatibility.isCompatible(Theme.DESERT, Theme.GENERIC));
		assertFalse(PlacementCompatibility.isCompatible(Theme.DESERT, Theme.CAMEO));
		assertFalse(PlacementCompatibility.isCompatible(Theme.CAMEO, Theme.DESERT));
		assertFalse(PlacementCompatibility.isCompatible(Theme.CAMEO, Theme.GENERIC));
	}

	/**
	 * {@link Theme#UNIQUE_BOSS} is never a region's theme (see {@link CityTheme}
	 * — no city maps to it), so this can never fire in practice. The rule
	 * itself, checked in isolation, is a plain equality: it is
	 * {@code CityTheme}'s job to keep the region side of that equality from
	 * ever being {@code UNIQUE_BOSS}, which {@code PlacementLintTest} guards
	 * separately, and both have to hold for a boss citizen to always be flagged.
	 */
	@Test
	public void aUniqueBossOnlyMatchesAUniqueBossRegionTheme()
	{
		assertTrue(PlacementCompatibility.isCompatible(Theme.UNIQUE_BOSS, Theme.UNIQUE_BOSS));
		assertFalse(PlacementCompatibility.isCompatible(Theme.UNIQUE_BOSS, Theme.GENERIC));
		assertFalse(PlacementCompatibility.isCompatible(Theme.UNIQUE_BOSS, Theme.DESERT));
		assertFalse(PlacementCompatibility.isCompatible(Theme.UNIQUE_BOSS, Theme.CAMEO));
	}
}
