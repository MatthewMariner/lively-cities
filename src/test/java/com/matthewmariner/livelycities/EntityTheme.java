package com.matthewmariner.livelycities;

import java.util.HashMap;
import java.util.Map;

/**
 * A hand-authored theme for the handful of shipped citizens whose name and
 * examine text carry an unambiguous, non-generic identity. Everything not
 * listed here is {@link Theme#GENERIC}.
 *
 * <p><b>Why a table and not an inference.</b> {@code EntityRecord} has no
 * theme field, and the three signals available — {@code name},
 * {@code examineText}, and {@code modelIds} — do not support a rule that
 * would not also misfire on the dataset's ordinary variety. A model-id-range
 * heuristic was considered and rejected outright: reading a raw OSRS cache id
 * and guessing "this range is desert-y" is exactly the kind of clever-looking,
 * unverifiable heuristic the dataset itself already warns against (see
 * {@code ShippedModelIds}'s javadoc on why the audits read raw ids rather than
 * interpret them). Text is at least human-checkable — every entry below is a
 * direct quote — so this table is keyed on {@code uuid} (stable, unique; see
 * {@code EntityDefinition.stableHash}) with the quote that justified it next
 * to it.
 *
 * <p><b>Confidence, entry by entry.</b> The six Barrows Brothers and Harry the
 * monkey are certain — the examine text names the character outright, or the
 * name is the character. The three Al Kharid "Ali"s are certain the same way.
 * Steven the werewolf is certain. "Ali" and "Afrah" in Varrock are certain
 * <i>as {@link Theme#DESERT}</i> — their own examine text says so — but
 * whether that is a bug is a separate question, answered by
 * {@code PlacementExceptions}, not by this table.
 *
 * <p><b>What was found and deliberately left out</b>, because the signal did
 * not clear the bar above: nine "a dwarf" citizens scattered across five
 * unrelated regions (see {@code Theme}'s javadoc); "Ak-Haranu", a name with a
 * desert-sounding ring but no examine text or shared model id in this dataset
 * to back it up (the three "Ali"s and the two Varrock desert transplants all
 * share exact {@code modelIds} arrays with each other or say "from Al-Kharid"
 * outright — Ak-Haranu does neither); "Ghost" (Draynor) and "Ghost of
 * Lumbridge", both generic haunting flavour rather than a named character, and
 * common enough across Gielinor that tagging bare "ghost" would flag two fine
 * placements to catch nothing sharper than the Barrows table already catches.
 */
final class EntityTheme
{
	private static final Map<String, Theme> BY_UUID = build();

	private EntityTheme()
	{
	}

	static Theme themeOf(String uuid)
	{
		return BY_UUID.getOrDefault(uuid, Theme.GENERIC);
	}

	private static Map<String, Theme> build()
	{
		Map<String, Theme> byUuid = new HashMap<>();

		// --- Desert (Al Kharid) ------------------------------------------------
		// Correctly placed: all three ship inside the Al Kharid region group.
		byUuid.put("bc9a8714-c376-4ea6-b381-fa5c3127968b", Theme.DESERT); // "Ali the goat herder", 13105
		byUuid.put("7de72de4-8585-4756-8c6d-c87847115c65", Theme.DESERT); // "Ali the wanderer", 13106
		byUuid.put("576030cc-da28-4179-ac65-c88826e9fffc", Theme.DESERT); // "Ali the spy", 13361

		// Shipped in Varrock (12853) instead. Same modelIds array as "Ali the
		// wanderer"/"Ali the spy" above, and the examine text says outright
		// "He looks like he's from Al-Kharid." — a desert theme wearing a
		// Varrock address. Whether that address is a deliberate immigrant story
		// or a bug is PlacementExceptions's call, not this table's.
		byUuid.put("ee3c3e90-7fe5-4387-a976-74463163dab7", Theme.DESERT); // "Ali", 12853, "He looks like he's from Al-Kharid."
		byUuid.put("fc28fec2-c105-49ab-8040-e36dda874646", Theme.DESERT); // "Afrah", 12853, "She looks like she's from Al-Kharid."

		// --- Karamja / jungle ----------------------------------------------------
		byUuid.put("8dcbbf3c-f074-4ba7-9ec2-cf0780fb39c3", Theme.KARAMJA_JUNGLE); // "Harry", "A little monkey.", 11569 Musa Point

		// --- Morytania / undead --------------------------------------------------
		byUuid.put("e8b75a86-588c-445a-b118-8414b4dae5ca", Theme.MORYTANIA_UNDEAD); // "Steven", "A freshly-turned werewolf.", 13878 Canifis

		// --- Unique bosses ---------------------------------------------------------
		// The six Barrows Brothers, region 14131. Named outright in their own
		// Region 14131 (the Barrows) held six citizens named after the Barrows
		// Brothers, with "The ghost of <Brother>." examine text — the 303-upvote
		// complaint against the predecessor. Softened rather than deleted
		// (Matthew's call, 2026-08-23): the placements were fine, the
		// impersonation was not. They are now six generic "Barrow wight"s, so
		// MORYTANIA_UNDEAD is the honest tag and it matches the region's own
		// theme. Kept in this table on purpose: tagging them GENERIC would let
		// them pass anywhere, and undead belong in Morytania specifically.
		byUuid.put("cf1b6b28-f503-4901-9077-40aeda080fe5", Theme.MORYTANIA_UNDEAD);
		byUuid.put("d78ce759-d6ca-43ba-ba38-3af7d181c436", Theme.MORYTANIA_UNDEAD);
		byUuid.put("8b8d70ef-f890-4fdd-bc67-23549a7dabe1", Theme.MORYTANIA_UNDEAD);
		byUuid.put("65a7afe5-950c-4031-9453-41d8d5f68a07", Theme.MORYTANIA_UNDEAD);
		byUuid.put("ce292e57-cdc3-4132-9a8f-01c139903a50", Theme.MORYTANIA_UNDEAD);
		byUuid.put("976bfe11-b438-412a-ad26-57615c673017", Theme.MORYTANIA_UNDEAD);

		return byUuid;
	}
}
