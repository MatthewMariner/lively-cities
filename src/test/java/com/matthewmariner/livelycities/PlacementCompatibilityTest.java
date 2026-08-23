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
		assertTrue(PlacementCompatibility.isCompatible(Theme.GENERIC, Theme.GENERIC));
		assertTrue(PlacementCompatibility.isCompatible(Theme.GENERIC, Theme.DESERT));
		assertTrue(PlacementCompatibility.isCompatible(Theme.GENERIC, Theme.KARAMJA_JUNGLE));
		assertTrue(PlacementCompatibility.isCompatible(Theme.GENERIC, Theme.MORYTANIA_UNDEAD));
		assertTrue(PlacementCompatibility.isCompatible(Theme.GENERIC, Theme.UNIQUE_BOSS));
	}

	@Test
	public void aThemedEntityFitsItsOwnRegionTheme()
	{
		assertTrue(PlacementCompatibility.isCompatible(Theme.DESERT, Theme.DESERT));
		assertTrue(PlacementCompatibility.isCompatible(Theme.KARAMJA_JUNGLE, Theme.KARAMJA_JUNGLE));
		assertTrue(PlacementCompatibility.isCompatible(Theme.MORYTANIA_UNDEAD, Theme.MORYTANIA_UNDEAD));
	}

	@Test
	public void aThemedEntityDoesNotFitAMismatchedRegionTheme()
	{
		assertFalse(PlacementCompatibility.isCompatible(Theme.DESERT, Theme.GENERIC));
		assertFalse(PlacementCompatibility.isCompatible(Theme.DESERT, Theme.KARAMJA_JUNGLE));
		assertFalse(PlacementCompatibility.isCompatible(Theme.KARAMJA_JUNGLE, Theme.MORYTANIA_UNDEAD));
		assertFalse(PlacementCompatibility.isCompatible(Theme.MORYTANIA_UNDEAD, Theme.DESERT));
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
		assertFalse(PlacementCompatibility.isCompatible(Theme.UNIQUE_BOSS, Theme.MORYTANIA_UNDEAD));
	}
}
