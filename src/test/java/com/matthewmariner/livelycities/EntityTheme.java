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
 * <p><b>Confidence, entry by entry.</b> The three Al Kharid "Ali"s are certain —
 * the examine text names the character outright, or the name is the character.
 * The six cameos are certain the same way. "Ali" and "Afrah" in Varrock are
 * certain <i>as {@link Theme#DESERT}</i> — their own examine text says so — but
 * whether that is a bug is a separate question, answered by
 * {@code PlacementExceptions}, not by this table.
 *
 * <p>(Harry the monkey, Steven the werewolf and the six Barrow wights were listed
 * here on the same footing until the nine-city cut removed all three places from
 * the dataset. See the comment where their rows used to be.)
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
 * placements and catch nothing. (When this was written the sharper catch was the
 * six Barrow wights, which are no longer in the dataset — see below. That removes
 * the comparison, not the conclusion: a bare "ghost" tag would still be two false
 * positives and no true one.)
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

		// --- Karamja / jungle, and Morytania / undead ------------------------------
		// Eight rows used to live here and no longer do, because the citizens they
		// named no longer ship: "Harry" the monkey (KARAMJA_JUNGLE, 11569 Musa
		// Point), "Steven" the werewolf (MORYTANIA_UNDEAD, 13878 Canifis) and the
		// six Barrow wights (MORYTANIA_UNDEAD, 14131) all went in the nine-city cut
		// on 2026-08-24, and both themes went with them — see Theme.
		//
		// The rows were deleted rather than left in place. A uuid in this table that
		// matches nothing in the dataset protects nothing while reading as though it
		// does, which is the same failure PlacementLintTest.everyExceptionNamesA
		// ShippedCitizen makes a red test for PlacementExceptions; this table has no
		// such guard, so the discipline has to be manual.

		// --- Cameos ---------------------------------------------------------------
		// The six named likenesses of the plugin author's friends, region 12598
		// (the Grand Exchange), behind the `cameos` config item which is off by
		// default. Certain, in the way the Barrows six were certain: the name *is*
		// the character, and there is no reading of these that is a generic
		// townsperson.
		//
		// Theme.CAMEO is compatible with the Grand Exchange and nowhere else, so
		// this table is what stops a future edit copying one into Varrock square —
		// which is exactly the "fake players at a classic bank spot" the project's
		// content rules forbid, and which tagging them GENERIC would allow silently.
		// CameoPlacementTest cross-checks this list against the dataset's own
		// `cameo: true` flag, so a seventh cameo that nobody tagged is a red test
		// rather than a cameo the lint cannot see.
		byUuid.put("0ca20001-9f4e-4b17-8d63-1e5a7c2b40d1", Theme.CAMEO); // "Cazh", 12598
		byUuid.put("0ca20002-3b8d-4f52-9a71-6c0e4d19b8f3", Theme.CAMEO); // "Gunnar", 12598
		byUuid.put("0ca20003-7d16-4a9c-8b45-2f80e6c31a97", Theme.CAMEO); // "Peter", 12598
		byUuid.put("0ca20004-5e2f-4c81-9d38-4a7b0916e5c2", Theme.CAMEO); // "Sludgellama", 12598
		byUuid.put("0ca20005-1a6b-4d73-8f92-b30c58e2417d", Theme.CAMEO); // "MrCream", 12598
		byUuid.put("0ca20006-8c47-4e29-9b05-7d16a4f9302e", Theme.CAMEO); // "Rob", 12598

		return byUuid;
	}
}
