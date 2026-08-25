package com.matthewmariner.livelycities;

/**
 * The closed set of place-flavours the placement lint knows how to reason
 * about ({@code PlacementLintTest}).
 *
 * <p><b>Why these four and not the fuller list a plan might imagine</b>
 * (Fremennik/snow, elven, dwarven were all considered and dropped, and
 * Karamja/Morytania were shipped and then lost their anchors):
 *
 * <ul>
 *   <li>{@code FREMENNIK_SNOW} and an elven theme have no anchor — none of the
 *       27 shipped regions is a snow or elven place, so a theme with no
 *       region to ever be compatible with would either sit unused or, worse,
 *       get reached for out of enthusiasm and misclassify something.</li>
 *   <li>{@code KARAMJA_JUNGLE} and {@code MORYTANIA_UNDEAD} were both real
 *       entries here until the nine-city cut (2026-08-24). Musa Point was the
 *       dataset's only tropical place and Canifis and the Barrows its only
 *       Morytanian ones; all three were removed, taking Harry the monkey, Steven
 *       the werewolf and the six Barrow wights with them. That leaves both themes
 *       in exactly the position the first bullet rules out — no region can ever be
 *       compatible with them and no entity carries them — so they were removed
 *       rather than left as two constants a future author could reach for. The
 *       rule is not "delete an unused theme"; it is "a theme must have a region
 *       that anchors it", and it is applied the same way whether the theme never
 *       had one or stopped having one.</li>
 *   <li>A dwarven theme was tried and rejected. "A dwarf" appears on nine
 *       citizens spread across five unrelated regions (the Ranging Guild, two
 *       different Varrock files, Lumbridge, and the actually-dwarven
 *       Motherlode Mine) with near-identical flavour text ("A dwarf.") — the
 *       pattern of a limited stocky-humanoid model reused as ordinary crowd
 *       filler, not a claim that Varrock has a resident dwarf quarter.
 *       Restricting "dwarf" to Motherlode Mine would flag eight fine
 *       placements to catch nothing sharper than the pattern already
 *       flags elsewhere; see {@code EntityTheme} for where that line is
 *       drawn instead. (The cut has since removed the Ranging Guild and the
 *       Motherlode Mine, which settles the question by removing its subject.)</li>
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

	/**
	 * A <b>cameo</b>: a named, player-shaped likeness of one of the plugin
	 * author's friends.
	 *
	 * <p>Mapped to exactly one city in {@code CityTheme} — the Grand Exchange —
	 * and that is the point of giving it a theme at all rather than leaving the
	 * six of them {@link #GENERIC}. {@code GENERIC} is compatible with
	 * everywhere, so a generic cameo could be copied into Varrock square, into
	 * Lumbridge, into Falador, and the lint would have nothing to say. This
	 * theme is compatible with the Grand Exchange and nowhere else, so the
	 * question "may a named human likeness stand here?" has exactly one answer
	 * and the lint is the thing that gives it.
	 *
	 * <p>It is the inverse of {@link #UNIQUE_BOSS} rather than a copy of it.
	 * {@code UNIQUE_BOSS} is mapped to no region because a boss impersonator has
	 * no correct home; a cameo has exactly one, chosen deliberately and behind an
	 * opt-in checkbox, and spreading them would turn six in-joke figures into the
	 * "fake players" the project's content rules forbid.
	 * {@code PlacementLintTest.theCameoThemeMapsToExactlyOneCity} is the poison
	 * property that keeps that true.
	 */
	CAMEO,

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
