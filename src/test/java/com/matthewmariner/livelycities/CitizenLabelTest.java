package com.matthewmariner.livelycities;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The strings and the colour that make a fake citizen legibly fake.
 *
 * <p>This is not string-formatting trivia. The hub disabled the predecessor over
 * a thread in which a player mistook a broken-looking fake NPC for a real one, so
 * "the menu row says whose it is, in a colour the game never uses" is the
 * plugin's licence to exist. Every assertion here is one half of that sentence.
 */
public class CitizenLabelTest
{
	private final FakeRegions regions = new FakeRegions();

	/**
	 * The two spellings of the colour cannot drift.
	 *
	 * <p>{@link CitizenLabel#COLOUR_TAG} is written out as a literal rather than
	 * formatted from {@link CitizenLabel#FAKE_RGB}, so this is the guard that they
	 * are the same colour — and it is the whole reason writing it out is acceptable.
	 */
	@Test
	public void theColourTagAndTheColourAreTheSameColour()
	{
		assertEquals("<col=" + String.format("%06x", CitizenLabel.FAKE_RGB) + ">",
			CitizenLabel.COLOUR_TAG);
		assertEquals(CitizenLabel.FAKE_RGB, CitizenLabel.FAKE_COLOUR.getRGB() & 0xFFFFFF);
	}

	/**
	 * The colour must not be one the client already uses for a real menu target.
	 *
	 * <p>Yellow is an NPC, white is a player, cyan is a scene object, orange is an
	 * item. A colour that collided with any of them would defeat the entire feature
	 * while looking like it worked.
	 */
	@Test
	public void theColourIsNotAColourTheGameUsesForRealTargets()
	{
		int[] realTargetColours = {0xFFFF00, 0xFFFFFF, 0x00FFFF, 0xFF9040};
		for (int colour : realTargetColours)
		{
			assertFalse("the fake colour must not be " + String.format("%06x", colour),
				CitizenLabel.FAKE_RGB == colour);
		}
	}

	@Test
	public void theMenuTargetIsColouredAndNamesThePlugin()
	{
		EntityDefinition citizen = regions.citizen(12853, 3220, 3420, 0);
		String target = CitizenLabel.menuTarget(citizen);

		assertTrue("the target has to open with the colour tag, or the client shows it plain: " + target,
			target.startsWith(CitizenLabel.COLOUR_TAG));
		assertTrue("and close the span, or it bleeds into the rest of the row: " + target,
			target.endsWith("</col>"));
		assertTrue("it has to name the citizen: " + target,
			target.contains(citizen.getName()));
		assertTrue("and it has to name the plugin, inside the coloured span: " + target,
			target.contains(CitizenLabel.PLUGIN_NAME));

		// The plugin's name being inside the span is the point: a half-coloured row
		// reads as a real target with something appended.
		int pluginAt = target.indexOf(CitizenLabel.PLUGIN_NAME);
		int closeAt = target.indexOf("</col>");
		assertTrue("the plugin name must be inside the coloured span: " + target,
			pluginAt < closeAt);
	}

	/**
	 * A record with no {@code name} — every scenery record — still gets a label.
	 * A row reading {@code Examine  (Lively Cities)} would be the confusing outcome
	 * the whole class exists to prevent.
	 */
	@Test
	public void anUnnamedEntityStillGetsALabel()
	{
		EntityDefinition scenery = regions.scenery(12853, 3220, 3420);
		String target = CitizenLabel.menuTarget(scenery);

		assertTrue("an unnamed entity falls back to its type: " + target,
			target.contains(EntityType.Scenery.name()));
		assertFalse("and never produces an empty name: " + target,
			target.contains(CitizenLabel.COLOUR_TAG + " "));
	}

	/**
	 * The Examine line has to carry both halves: the dataset's own authored text,
	 * and the sentence that answers "who is this man?".
	 */
	@Test
	public void theExamineLineUsesTheAuthoredTextAndSaysWhoseCitizenItIs()
	{
		EntityDefinition talker = regions.talker(12853, 3220, 3420, "Busy today.");
		String message = CitizenLabel.examineMessage(talker);

		assertNotNull(talker.getExamineText());
		assertTrue("the authored examine text has to survive: " + message,
			message.contains(talker.getExamineText()));
		assertTrue("and the plugin has to be named: " + message,
			message.contains(CitizenLabel.PLUGIN_NAME));
		assertTrue("in as many words: " + message,
			message.contains("not a real NPC"));
		assertTrue("the name is coloured here too: " + message,
			message.contains(CitizenLabel.COLOUR_TAG + talker.getName() + "</col>"));
	}

	/**
	 * A citizen with no authored examine text still gets the sentence that matters.
	 * The shipped data has one for all 142, but a future record or a hand-written
	 * contribution need not.
	 */
	@Test
	public void anExamineLineWithNoAuthoredTextStillSaysWhoseItIs()
	{
		EntityDefinition plain = regions.citizen(12853, 3220, 3420, 0);
		assertNull("the fixture is only interesting without examine text", plain.getExamineText());

		String message = CitizenLabel.examineMessage(plain);
		assertTrue(message.contains(CitizenLabel.PLUGIN_NAME));
		assertTrue(message.contains("not a real NPC"));
		assertTrue("the citizen is still named: " + message,
			message.contains(plain.getName()));
		assertFalse("and the missing text must not leave a double space: " + message,
			message.contains("  "));
	}

	/**
	 * Authored examine text that is already a sentence must not gain a second full
	 * stop, and text that is not one must gain its first. All 142 shipped records
	 * end in a full stop; a hand-written contribution will not.
	 */
	@Test
	public void theExamineLinePunctuatesTheAuthoredTextExactlyOnce()
	{
		EntityDefinition citizen = regions.talker(12853, 3220, 3420, "Busy today.");
		assertEquals("the fixture's authored text is already a sentence",
			"A talkative citizen.", citizen.getExamineText());
		assertFalse("no doubled full stop: " + CitizenLabel.examineMessage(citizen),
			CitizenLabel.examineMessage(citizen).contains(".."));
	}

	/**
	 * Both confirmations have to name the way back. A right-click action whose undo
	 * lives in a settings panel the user has never opened is an action they report
	 * as a bug.
	 */
	@Test
	public void theHideAndMuteConfirmationsNameTheirUndo()
	{
		EntityDefinition talker = regions.talker(12853, 3220, 3420, "Busy today.");

		String hidden = CitizenLabel.hiddenMessage(talker, 1);
		assertTrue(hidden + " must name the config item that undoes it",
			hidden.contains("Unhide all citizens"));
		assertTrue(hidden.contains(CitizenLabel.PLUGIN_NAME));

		String muted = CitizenLabel.mutedMessage(talker, 1);
		assertTrue(muted + " must name the config item that undoes it",
			muted.contains("Unmute all citizens"));

		// The plural is not cosmetic: "bring them back" after hiding five people is
		// a message that undersells what the button does.
		assertTrue(CitizenLabel.hiddenMessage(talker, 5).contains("all 5 of them"));
	}
}
