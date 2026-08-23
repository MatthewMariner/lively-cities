package com.matthewmariner.livelycities;

/**
 * The closed set of place-flavours the placement lint knows how to reason
 * about ({@code PlacementLintTest}).
 *
 * <p><b>Why these five and not the fuller list a plan might imagine</b>
 * (Fremennik/snow, elven, dwarven were all considered and dropped):
 *
 * <ul>
 *   <li>{@code FREMENNIK_SNOW} and an elven theme have no anchor — none of the
 *       45 shipped regions is a snow or elven place, so a theme with no
 *       region to ever be compatible with would either sit unused or, worse,
 *       get reached for out of enthusiasm and misclassify something.</li>
 *   <li>A dwarven theme was tried and rejected. "A dwarf" appears on nine
 *       citizens spread across five unrelated regions (the Ranging Guild, two
 *       different Varrock files, Lumbridge, and the actually-dwarven
 *       Motherlode Mine) with near-identical flavour text ("A dwarf.") — the
 *       pattern of a limited stocky-humanoid model reused as ordinary crowd
 *       filler, not a claim that Varrock has a resident dwarf quarter.
 *       Restricting "dwarf" to Motherlode Mine would flag eight fine
 *       placements to catch nothing sharper than the pattern already
 *       flags elsewhere; see {@code EntityTheme} for where that line is
 *       drawn instead.</li>
 * </ul>
 *
 * <p>{@link #GENERIC} is the default and is compatible with every region —
 * ordinary Gielinor townsfolk need no justification for where they stand. A
 * non-generic theme is compatible only with a region {@code CityTheme} maps to
 * that exact theme; see {@code PlacementCompatibility}.
 */
enum Theme
{
	/** Ordinary townsfolk, animals, and generic monsters. Fits anywhere. */
	GENERIC,

	/** Al Kharid's desert-market identity — turbaned traders, camels, sand. */
	DESERT,

	/** Karamja's tropical identity — jungle wildlife, the volcano coastline. */
	KARAMJA_JUNGLE,

	/** Morytania's undead/werewolf identity — Canifis, the Barrows mounds. */
	MORYTANIA_UNDEAD,

	/**
	 * A specific, named, canonically-unique character — a boss or
	 * boss-adjacent figure with a real in-game identity of their own.
	 *
	 * <p>Deliberately mapped to <b>no</b> region in {@code CityTheme}: a
	 * citizen carrying this theme is flagged wherever it stands, on purpose.
	 * The predecessor plugin's own README design guideline was "no zalcano
	 * standing in Varrock square" — the point generalises past geography.
	 * Cosplaying a unique boss as ambient wandering flavour undersells the
	 * real encounter regardless of which region it happens to stand in, so
	 * this theme has no "correct" region to retag into. The fix for an entity
	 * carrying it is to remove it, or to reauthor it as a generic citizen —
	 * not to find it a home.
	 */
	UNIQUE_BOSS
}
