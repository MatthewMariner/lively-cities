package com.matthewmariner.livelycities;

import java.awt.Color;

/**
 * Every string and the one colour that say "this thing is ours, not the game's".
 *
 * <p><b>This is the plugin's licence to exist, not decoration.</b> The hub
 * disabled the predecessor after a Reddit thread — r/2007scape {@code 1f3yy3k},
 * "Who is this man? Why does he not have any legs?" — in which a player mistook a
 * broken-looking fake NPC for a real one and went to the maintainers about it.
 * Fake being mistakable for real is the failure mode that gets this category of
 * plugin pulled, so the menu row, the examine line and the overhead text all
 * carry the same colour and the plugin's name.
 *
 * <p><b>Why {@link #FAKE_RGB} is a constant and not a config item.</b> It would
 * be cheap to expose — RuneLite supports {@code Color} config items — and it is
 * deliberately not exposed. The colour's whole job is to be a colour the game
 * never uses for a real target, and a user-settable colour can be set to
 * {@code ffff00}, which is exactly what a real NPC's menu target is. Making the
 * one legibility guarantee user-defeatable to save a constant is a bad trade. The
 * client's own target colours, for the record:
 *
 * <ul>
 *   <li>{@code <col=ffff00>} yellow — NPCs</li>
 *   <li>{@code <col=ffffff>} white — players</li>
 *   <li>{@code <col=00ffff>} cyan — scene objects</li>
 *   <li>{@code <col=ff9040>} orange — items, on the ground and in the inventory</li>
 * </ul>
 *
 * <p>{@code ff66ff} is none of those and is not close to any of them at any
 * brightness, which is the property being bought.
 *
 * <p><b>The tag string is written out rather than formatted from the int.</b>
 * {@code String.format("<col=%06x>", FAKE_RGB)} would be the same six characters
 * and one more thing that can go wrong at runtime, in a string the client parses
 * and silently drops on a mismatch. {@code CitizenLabelTest} asserts the two
 * halves agree, so they cannot drift.
 */
final class CitizenLabel
{
	/**
	 * The one colour. Magenta, chosen for not being any colour the client uses
	 * for a real menu target — see the class javadoc.
	 */
	static final int FAKE_RGB = 0xFF66FF;

	/** {@link #FAKE_RGB} as the client's colour tag, for menu targets and chat. */
	static final String COLOUR_TAG = "<col=ff66ff>";

	/** {@link #FAKE_RGB} for the overhead-text overlay, which draws in AWT. */
	static final Color FAKE_COLOUR = new Color(FAKE_RGB);

	/**
	 * The plugin's name, as it appears to a player who is wondering what they are
	 * looking at. This is the string that has to make the answer findable.
	 */
	static final String PLUGIN_NAME = "Lively Cities";

	private CitizenLabel()
	{
	}

	/**
	 * The menu target for one of our entries: the citizen's name and the plugin's,
	 * in the plugin's colour.
	 *
	 * <p>The name of the plugin is inside the coloured span on purpose — a
	 * half-coloured row reads as a real target with a suffix, which is the
	 * impression this is trying to avoid.
	 *
	 * <p>Scenery has no {@code name} in the dataset, so it falls back to the entity
	 * type. Scenery does not currently get menu entries, but the fallback is here
	 * rather than at the call site because "the label is never empty" is a property
	 * of the label, and a row reading {@code Examine  (Lively Cities)} would be the
	 * confusing outcome this class exists to prevent.
	 */
	static String menuTarget(EntityDefinition definition)
	{
		return COLOUR_TAG + who(definition) + " (" + PLUGIN_NAME + ")</col>";
	}

	/**
	 * The Examine line, printed into the local chat buffer and nowhere else.
	 *
	 * <p>It uses the dataset's own {@code examineText} — all 135 shipped citizens
	 * have one — and then says, in as many words, that the thing is this plugin's
	 * and is not a real NPC. That sentence is the direct answer to the thread that
	 * got the predecessor disabled, and it is why this is not simply the authored
	 * examine text on its own.
	 */
	static String examineMessage(EntityDefinition definition)
	{
		String examine = definition.getExamineText();
		StringBuilder out = new StringBuilder();
		out.append(COLOUR_TAG).append(who(definition)).append("</col>: ");

		if (examine != null && !examine.trim().isEmpty())
		{
			out.append(examine.trim());
			if (!endsSentence(examine.trim()))
			{
				out.append('.');
			}
			out.append(' ');
		}

		out.append('(').append(PLUGIN_NAME)
			.append(" — a cosmetic citizen added by the plugin, not a real NPC.)");
		return out.toString();
	}

	/**
	 * Confirmation for "Hide", naming the way back. A right-click action whose undo
	 * lives in a settings panel the user has not opened is an action they will
	 * report as a bug.
	 */
	static String hiddenMessage(EntityDefinition definition, int totalHidden)
	{
		return COLOUR_TAG + who(definition) + "</col> is hidden. Tick \"Unhide all citizens\" in the "
			+ PLUGIN_NAME + " settings to bring "
			+ (totalHidden == 1 ? "them" : "all " + totalHidden + " of them") + " back.";
	}

	/** Confirmation for "Mute", on the same terms as {@link #hiddenMessage}. */
	static String mutedMessage(EntityDefinition definition, int totalMuted)
	{
		return COLOUR_TAG + who(definition) + "</col> will stop talking. Tick \"Unmute all citizens\" in the "
			+ PLUGIN_NAME + " settings to undo "
			+ (totalMuted == 1 ? "it" : "all " + totalMuted + " of them") + ".";
	}

	private static String who(EntityDefinition definition)
	{
		String name = definition.getName();
		return name != null && !name.trim().isEmpty() ? name.trim() : definition.getType().name();
	}

	private static boolean endsSentence(String text)
	{
		char last = text.charAt(text.length() - 1);
		return last == '.' || last == '!' || last == '?';
	}
}
