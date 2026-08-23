package com.matthewmariner.livelycities;

import java.util.HashMap;
import java.util.Map;

/**
 * Deliberate oddities the placement lint would otherwise flag, each carrying
 * the one-line reason it is fine as-is.
 *
 * <p><b>What does not belong here.</b> This list is for placements the author
 * judged fine on inspection — not a place to quietly launder a confirmed bug
 * back to green. The six Barrows Brothers ({@code EntityTheme.Theme.UNIQUE_BOSS}
 * in region 14131) are <b>not</b> on this list on purpose: they are the
 * confirmed offender the lint exists to catch (see the placement lint report),
 * and {@code PlacementLintTest} is currently red on them until a fix is chosen
 * and applied to the data. Excepting them here would make the build green
 * without making the game any less wrong.
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
