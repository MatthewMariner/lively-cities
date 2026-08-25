package com.matthewmariner.livelycities;

import java.util.HashMap;
import java.util.Map;

/**
 * Deliberate oddities the placement lint would otherwise flag, each carrying
 * the one-line reason it is fine as-is.
 *
 * <p><b>What does not belong here.</b> This list is for placements judged fine
 * on inspection — not a place to quietly launder a confirmed bug back to green.
 *
 * <p>The worked example is historical now, and is kept because the distinction it
 * draws is the reason this class is so short. Region 14131 (the Barrows) shipped
 * six citizens named after the Barrows Brothers, carrying "The ghost of
 * &lt;Brother&gt;." examine text, and tagged {@link Theme#UNIQUE_BOSS} — the
 * confirmed offender this lint exists to catch. They were deliberately
 * <b>never</b> added here. They were fixed in the data on 2026-08-23 instead:
 * renamed to generic "Barrow wight"s and retagged to the Morytania theme, which
 * the Barrows region did claim. The lint went green because the content changed,
 * not because the check was told to look away. (Region 14131 was then removed
 * outright in the nine-city cut on 2026-08-24, so neither the citizens nor that
 * theme ship any more.)
 *
 * <p>That is the distinction worth preserving. An exception here would have made
 * the build green while leaving the game exactly as wrong as it was.
 */
final class PlacementExceptions
{
	private static final Map<String, String> BY_UUID = build();

	private PlacementExceptions()
	{
	}

	static boolean isExcepted(String uuid)
	{
		return BY_UUID.containsKey(uuid);
	}

	static String reasonFor(String uuid)
	{
		return BY_UUID.get(uuid);
	}

	static Map<String, String> all()
	{
		return BY_UUID;
	}

	private static Map<String, String> build()
	{
		Map<String, String> byUuid = new HashMap<>();

		byUuid.put("ee3c3e90-7fe5-4387-a976-74463163dab7", // "Ali", Varrock (12853)
			"examine text ('He looks like he's from Al-Kharid.') frames him as a living "
				+ "transplant working in Varrock, not a copy of the Al Kharid citizen standing "
				+ "somewhere it doesn't belong — a capital city plausibly has a few immigrants");

		byUuid.put("fc28fec2-c105-49ab-8040-e36dda874646", // "Afrah", Varrock (12853)
			"same reasoning as 'Ali' above: her own examine text ('She looks like she's from "
				+ "Al-Kharid.') is the in-fiction explanation for why a desert-coded citizen is "
				+ "standing in Varrock");

		return byUuid;
	}
}
