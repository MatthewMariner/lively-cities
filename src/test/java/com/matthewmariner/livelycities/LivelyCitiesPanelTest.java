package com.matthewmariner.livelycities;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The one part of the panel that is Swing, exercised without a windowing system.
 *
 * <p>Every <em>decision</em> the panel draws is somewhere else and already under test —
 * {@link PanelModel} for the rows and the numbers, {@link CitizenDirectory} for the
 * names, {@code LivelyCitiesPluginLifecycleTest} for what a click writes. What is left
 * is wiring, and wiring is exactly the part that fails invisibly: a mouse listener on
 * the wrong component, a label that is built and never updated, a filter that hides the
 * row it was supposed to keep. None of that turns any other test red.
 *
 * <p>Headless throughout. {@code JPanel} and its children construct fine without a
 * display — it is {@code Frame} and {@code Window} that do not — so this runs on a build
 * machine, which is the only reason it is allowed to exist. Nothing here paints.
 *
 * <p><b>It walks the component tree rather than adding accessors to the panel.</b> A
 * {@code getCityCardFor(City)} written for a test is a seam that makes the test pass by
 * construction; reading the labels that are actually in the tree is asking the same
 * question a user's eyes ask.
 */
public class LivelyCitiesPanelTest
{
	private static final WorldPoint IN_VARROCK = new WorldPoint(3225, 3360, 0);

	private final FakeConfig config = new FakeConfig();
	private final CitizenDirectory directory =
		new CitizenDirectory(new RegionDataLoader(TestGson.injected()));

	// --- what it draws ---------------------------------------------------------

	/**
	 * The nine places are on it, with the dataset's counts, before anything has been
	 * pushed to it at all.
	 *
	 * <p>The cards are built in the constructor and repainted from each model, so the
	 * first draw happens with no model — and a panel that showed nothing until the first
	 * game tick would be blank for up to 600ms every time it is opened.
	 */
	@Test
	public void everyPlaceIsOnThePanel()
	{
		LivelyCitiesPanel panel = panel();
		panel.accept(loggedOut(config));
		drain();

		List<String> text = labels(panel);
		for (City city : City.values())
		{
			assertTrue(city.getLabel() + " has no card: " + text, text.contains(city.getLabel()));
		}

		assertTrue("Varrock's roster has to be on its card: " + text,
			text.contains("71 citizens"));
		assertTrue("and Edgeville's, which is a different number",
			text.contains("22 citizens"));
	}

	/**
	 * The live figures reach the labels, and the card for the city the player is in says
	 * so.
	 *
	 * <p>This is the whole claim of the panel: numbers a settings screen cannot show.
	 * Asserted on the rendered text rather than on the model, because the model already
	 * has its own test and the failure being guarded here is a card that was built and
	 * then never updated.
	 */
	@Test
	public void theLiveNumbersReachTheCards()
	{
		java.util.EnumMap<City, Integer> active = new java.util.EnumMap<>(City.class);
		active.put(City.VARROCK, 34);
		active.put(City.GRAND_EXCHANGE, 7);

		LivelyCitiesPanel panel = panel();
		panel.accept(PanelModel.of(IN_VARROCK, new SceneCensus(41, 190, 12, 2, active),
			config, Collections.emptySet(), Collections.emptySet(), directory));
		drain();

		List<String> text = labels(panel);
		assertTrue("Varrock's card has to carry both numbers and the marker: " + text,
			text.contains("71 citizens · 34 on screen · you are here"));
		assertTrue("and a city a region away still reports what is up there",
			text.contains("24 citizens · 7 on screen"));
		assertTrue("the header names where the player is", text.contains("Varrock"));
		assertTrue("and the total", text.contains("41"));
		assertTrue("with the breakdown under it",
			text.contains("12 walking · 2 talking · 190 loaded"));
	}

	/**
	 * A card follows its city's checkbox, and only its own.
	 *
	 * <p>The panel repaints nine cards from a nine-row list in a loop, which is the shape
	 * that makes an off-by-one plausible. Nine different labels is what would notice.
	 */
	@Test
	public void eachCardShowsItsOwnCitysState()
	{
		for (City target : City.values())
		{
			LivelyCitiesPanel panel = panel();
			panel.accept(PanelModel.of(IN_VARROCK, SceneCensus.EMPTY,
				new FakeConfig().disableOnly(target), Collections.emptySet(),
				Collections.emptySet(), directory));
			drain();

			assertEquals("exactly one card may read 'off' when one city is unticked",
				1, count(labels(panel), "off"));
			assertEquals("and eight read 'on'", City.values().length - 1,
				count(labels(panel), "on"));
		}
	}

	/**
	 * <b>Opening the panel at the login screen shows the panel, not nine empty outlines.</b>
	 *
	 * <p>Readings arrive through {@code accept}, {@code accept} is driven by
	 * {@code refreshPanel}, and {@code refreshPanel} is driven by {@code GameTick} — which
	 * the client does not post while there is no world. So the whole of this test is the
	 * thing that used to be missing: {@code onActivate} with nothing ever pushed. The panel
	 * shipped with {@code model} null in that case and {@link LivelyCitiesPanel}'s null
	 * branch drew nine bordered cards with no name, no on/off and no counts, no density
	 * chip selected, and an overrides section with no children in it at all — not even its
	 * empty-state sentence — until the player logged in.
	 *
	 * <p>Asserted through the labels rather than through the model, because the model was
	 * never the part that was missing: {@code PanelModel.loggedOut} was complete, correct
	 * and covered by six tests, with no caller anywhere in {@code src/main}.
	 */
	@Test
	public void thePanelHasContentBeforeTheFirstGameTick()
	{
		LivelyCitiesPanel panel = panel();

		// No accept(). This is the login screen: nothing has been pushed and nothing will
		// be until there is a world.
		panel.onActivate();

		List<String> text = labels(panel);
		for (City city : City.values())
		{
			assertTrue(city.getLabel() + " has no card: " + text, text.contains(city.getLabel()));
		}

		assertEquals("every card has to say whether its city is on",
			City.values().length, count(text, "on"));
		assertTrue("with the dataset's own count on it: " + text, text.contains("71 citizens"));
		assertTrue("and Edgeville's, which is a different number", text.contains("22 citizens"));
		assertTrue("the overrides section says something rather than nothing: " + text,
			text.contains("Nobody is hidden or muted."));
		assertTrue("and its header counts them",
			text.stream().anyMatch(line -> line.endsWith("Hidden and muted (0)")));
	}

	/**
	 * And the hidden list is the profile's, not an empty one.
	 *
	 * <p>Hiding and muting are stored in the config, so they are as true at the login
	 * screen as anywhere. A pre-tick reading that emptied them would put "Hidden and muted
	 * (0)" over "Nobody is hidden or muted." in front of somebody who had hidden three
	 * citizens — which is worse than the blank it replaces, because it is a confident
	 * answer rather than an absent one, and it would sit there until they logged in.
	 */
	@Test
	public void theLoginScreenPanelStillKnowsWhoIsHidden()
	{
		FakeRegions regions = new FakeRegions();
		FakeConfig config = new FakeConfig();
		config.overrides().hide(regions.citizen(12852, 3225, 3360, 0));
		config.overrides().mute(regions.talker(12852, 3226, 3360, "Busy today."));

		LivelyCitiesPanel panel = new LivelyCitiesPanel(plugin(config));
		panel.onActivate();

		List<String> text = labels(panel);
		assertTrue("the header has to carry the real count: " + text,
			text.stream().anyMatch(line -> line.endsWith("Hidden and muted (2)")));
		assertEquals("with the way back for the hidden one", 1, actionsLabelled(panel, "show").size());
		assertEquals("and for the muted one", 1, actionsLabelled(panel, "unmute").size());
	}

	// --- the search box --------------------------------------------------------

	/**
	 * Typing filters the cards, and clearing brings them all back.
	 *
	 * <p>Driven through the document, which is what a keystroke reaches — the panel
	 * listens on it rather than on key events, so this is the same path a typed
	 * character takes.
	 */
	@Test
	public void theSearchBoxFiltersTheCards() throws Exception
	{
		LivelyCitiesPanel panel = panel();
		panel.accept(loggedOut(config));
		drain();

		assertEquals("every card starts visible",
			City.values().length, visibleCards(panel));

		type(panel, "var");
		assertEquals("only Varrock matches 'var'", 1, visibleCards(panel));

		type(panel, "");
		assertEquals("and clearing brings them back",
			City.values().length, visibleCards(panel));

		type(panel, "zzzz");
		assertEquals("a query nothing matches hides them all", 0, visibleCards(panel));
	}

	/**
	 * A filtered-out card takes its spacer with it.
	 *
	 * <p>The cards are laid out as card, strut, card, strut. The filter hid the card and
	 * left the strut, and a strut's height is a fixed few pixels rather than something its
	 * neighbours decide — so filtering to one place left the matching card sitting on top
	 * of eight 4px voids. Cosmetic, and invisible to every other assertion in this file,
	 * because the panel really was showing exactly one city.
	 *
	 * <p>Counted out of the container the cards are in rather than by measuring pixels:
	 * nothing here is laid out, so a height would be zero whatever the truth was.
	 */
	@Test
	public void aFilteredOutCardTakesItsSpacerWithIt() throws Exception
	{
		LivelyCitiesPanel panel = panel();
		panel.accept(loggedOut(config));
		drain();

		Container cards = labelReading(panel, City.VARROCK.getLabel()).getParent().getParent();
		assertEquals("nine cards and nine struts to start with",
			City.values().length * 2, showingChildren(cards));

		type(panel, "var");
		assertEquals("one card and one strut, not one card and nine struts",
			2, showingChildren(cards));

		type(panel, "zzzz");
		assertEquals("and a query nothing matches leaves nothing at all",
			0, showingChildren(cards));

		type(panel, "");
		assertEquals("clearing brings the struts back with the cards",
			City.values().length * 2, showingChildren(cards));
	}

	// --- the hidden-and-muted list --------------------------------------------

	/**
	 * A restore lands on the right citizen.
	 *
	 * <p><b>The reason this file exists.</b> The rows are built in a loop and each one
	 * closes over its own uuid; a listener that closed over the loop variable, or over
	 * the last row's uuid, would restore the wrong person — and every other test in the
	 * suite would stay green, because the model, the overrides and the writer are all
	 * behaving correctly. Three rows rather than one, and the middle one clicked, so a
	 * listener that always fired for the first or the last is caught.
	 */
	@Test
	public void clickingRestoreOnARowRestoresThatRow()
	{
		FakeRegions regions = new FakeRegions();
		EntityDefinition first = regions.citizen(12852, 3225, 3360, 0);
		EntityDefinition second = regions.citizen(12852, 3226, 3360, 0);
		EntityDefinition third = regions.citizen(12852, 3227, 3360, 0);

		FakeConfig config = new FakeConfig();
		LivelyCitiesPlugin plugin = plugin(config);
		config.overrides().hide(first);
		config.overrides().hide(second);
		config.overrides().hide(third);

		LivelyCitiesPanel panel = new LivelyCitiesPanel(plugin);
		panel.accept(model(config, uuids(first, second, third), Collections.emptySet()));
		drain();

		List<JLabel> actions = actionsLabelled(panel, "show");
		assertEquals("one restore per hidden citizen", 3, actions.size());

		// The rows are sorted, so which one is in the middle is a fact rather than a
		// guess — the three fixture uuids differ only in their last digit and all three
		// citizens are unplaceable by the real directory, so they sort by uuid.
		UUID middle = sortedUuids(first, second, third).get(1);
		click(actions.get(1));

		assertEquals("two still hidden", 2, config.overrides().hiddenUuids().size());
		assertFalse("and the one clicked is the one restored",
			config.overrides().hiddenUuids().contains(middle));
	}

	/**
	 * A citizen who is both hidden and muted gets one row with both restores on it.
	 *
	 * <p>Two rows would be two entries with the same name and no sign they are one
	 * person; one row with one restore would leave the other override with no way back
	 * at all, which is the defect this whole list exists to fix.
	 */
	@Test
	public void aCitizenWhoIsBothHiddenAndMutedGetsBothRestores()
	{
		FakeRegions regions = new FakeRegions();
		EntityDefinition someone = regions.talker(12852, 3225, 3360, "Busy today.");

		FakeConfig config = new FakeConfig();
		LivelyCitiesPlugin plugin = plugin(config);
		config.overrides().hide(someone);
		config.overrides().mute(someone);

		LivelyCitiesPanel panel = new LivelyCitiesPanel(plugin);
		panel.accept(model(config, uuids(someone), uuids(someone)));
		drain();

		assertEquals("one row", 1, actionsLabelled(panel, "show").size());
		assertEquals("with both ways back on it", 1, actionsLabelled(panel, "unmute").size());
		assertTrue("and a subtitle that says so: " + labels(panel),
			labels(panel).stream().anyMatch(text -> text.endsWith("hidden and muted")));

		click(actionsLabelled(panel, "unmute").get(0));
		assertTrue(config.overrides().mutedUuids().isEmpty());
		assertEquals("unmuting must not unhide", 1, config.overrides().hiddenUuids().size());
	}

	/**
	 * A row that gains a second override grows the restore for it.
	 *
	 * <p><b>This test exists because of a mutation.</b> Dropping the two flags from
	 * {@link LivelyCitiesPanel}'s rebuild signature — leaving it a list of uuids — left
	 * all 566 tests green. Every other test in this file builds a fresh panel and pushes
	 * one model, so the first draw always rebuilds and the signature is never consulted;
	 * the flags only matter across a <i>transition</i>, and nothing exercised one.
	 *
	 * <p>The transition is the ordinary one. You hide somebody from the right-click menu
	 * and the panel shows "show". You then mute them, from the same menu, and the model
	 * pushed a moment later carries the same uuid — so under the mutation the section is
	 * left exactly as it was and the mute has no way back from the panel at all, which is
	 * the one thing this list exists to provide.
	 */
	@Test
	public void aRowThatGainsASecondOverrideGrowsTheRestoreForIt()
	{
		FakeRegions regions = new FakeRegions();
		EntityDefinition someone = regions.talker(12852, 3225, 3360, "Busy today.");

		FakeConfig config = new FakeConfig();
		LivelyCitiesPlugin plugin = plugin(config);
		config.overrides().hide(someone);

		LivelyCitiesPanel panel = new LivelyCitiesPanel(plugin);
		panel.accept(model(config, uuids(someone), Collections.emptySet()));
		drain();

		assertEquals("hidden, so one way back", 1, actionsLabelled(panel, "show").size());
		assertEquals("and not the other", 0, actionsLabelled(panel, "unmute").size());

		// The same citizen, now muted as well — same uuid, same row, second override.
		config.overrides().mute(someone);
		panel.accept(model(config, uuids(someone), uuids(someone)));
		drain();

		assertEquals("still one row", 1, actionsLabelled(panel, "show").size());
		assertEquals("with the second way back on it now",
			1, actionsLabelled(panel, "unmute").size());
		assertTrue("and a subtitle that says both: " + labels(panel),
			labels(panel).stream().anyMatch(text -> text.endsWith("hidden and muted")));

		// And back down again, because a stale signature is just as wrong in the
		// direction that leaves an action pointing at an override that is gone.
		config.overrides().unmute(someone.getUuid());
		panel.accept(model(config, uuids(someone), Collections.emptySet()));
		drain();

		assertEquals(1, actionsLabelled(panel, "show").size());
		assertEquals("the restore for an override that no longer applies has to go",
			0, actionsLabelled(panel, "unmute").size());
	}

	/**
	 * A different citizen with the same flags is a different row.
	 *
	 * <p><b>This test exists because of a mutation.</b> Dropping the uuid from the rebuild
	 * signature — {@code out.append(row.getUuid())} to {@code out.append("")} — left all 574
	 * tests green. The one test that transitions the section goes from {@code h-;} to
	 * {@code hm;}, which differs whether or not the uuid is in it, so it pins the flags and
	 * says nothing about identity.
	 *
	 * <p>The transition that tells them apart is the ordinary repair: you hid the wrong
	 * person, so you bring them back and hide the right one. Same count, same flags, and
	 * under the mutation the section is left exactly as it was — the only "show" on screen
	 * points at somebody who is already unhidden, so clicking it does nothing, and the
	 * citizen who <i>is</i> hidden has no way back from the panel at all.
	 */
	@Test
	public void swappingWhoIsHiddenRebuildsTheRow()
	{
		FakeRegions regions = new FakeRegions();
		EntityDefinition first = regions.citizen(12852, 3225, 3360, 0);
		EntityDefinition second = regions.citizen(12852, 3226, 3360, 0);

		FakeConfig config = new FakeConfig();
		LivelyCitiesPlugin plugin = plugin(config);
		config.overrides().hide(first);

		LivelyCitiesPanel panel = new LivelyCitiesPanel(plugin);
		panel.accept(model(config, uuids(first), Collections.emptySet()));
		drain();
		assertEquals("one hidden citizen, one way back", 1, actionsLabelled(panel, "show").size());

		// One row before and one row after, hidden both times and muted neither time. The
		// only thing that moved is who it is about.
		config.overrides().unhide(first.getUuid());
		config.overrides().hide(second);
		panel.accept(model(config, uuids(second), Collections.emptySet()));
		drain();

		assertEquals("still one row", 1, actionsLabelled(panel, "show").size());
		assertTrue("and it has to name the citizen who is hidden now: " + labels(panel),
			labels(panel).contains(displayName(second)));

		click(actionsLabelled(panel, "show").get(0));
		assertTrue("clicking the only restore on screen has to restore the only citizen who "
			+ "is hidden", config.overrides().hiddenUuids().isEmpty());
	}

	/** Nobody overridden is a sentence, not an empty gap. */
	@Test
	public void anEmptyListSaysSoRatherThanShowingNothing()
	{
		LivelyCitiesPanel panel = panel();
		panel.accept(loggedOut(config));
		drain();

		assertTrue("the empty state has to say something: " + labels(panel),
			labels(panel).contains("Nobody is hidden or muted."));
		assertTrue("and the header counts zero",
			labels(panel).stream().anyMatch(text -> text.endsWith("Hidden and muted (0)")));
	}

	/**
	 * <b>The empty-state sentence has to agree with the header above it.</b>
	 *
	 * <p>The header counts the whole override list; the sentence says which kind of empty
	 * the section is. The sentence used to be chosen inside the rebuild, and the rebuild is
	 * skipped when the signature has not moved — and the signature only covers the rows
	 * that survive the filter, so every draw that filters down to nothing shares the same
	 * signature. Type a query nothing matches, then hide a citizen, and the panel read
	 * <b>"Hidden and muted (1)"</b> directly above <b>"Nobody is hidden or muted."</b>
	 */
	@Test
	public void theEmptySectionSaysWhichKindOfEmptyItIs() throws Exception
	{
		FakeRegions regions = new FakeRegions();
		EntityDefinition someone = regions.citizen(12852, 3225, 3360, 0);

		FakeConfig config = new FakeConfig();
		LivelyCitiesPanel panel = new LivelyCitiesPanel(plugin(config));
		panel.accept(model(config, Collections.emptySet(), Collections.emptySet()));
		drain();

		type(panel, "zzzz");
		assertTrue("nobody is overridden, so that is what it says: " + labels(panel),
			labels(panel).contains("Nobody is hidden or muted."));

		// Somebody is hidden now, and the query still matches none of them — so the list
		// filters to nothing for the second draw running, and the signature does not move.
		config.overrides().hide(someone);
		panel.accept(model(config, uuids(someone), Collections.emptySet()));
		drain();

		List<String> text = labels(panel);
		assertTrue("the header counts the unfiltered list: " + text,
			text.stream().anyMatch(line -> line.endsWith("Hidden and muted (1)")));
		assertFalse("so the sentence under it cannot claim nobody is hidden: " + text,
			text.contains("Nobody is hidden or muted."));
		assertTrue("it is the filter that emptied this, and it has to say so: " + text,
			text.contains("Nobody here matches that."));
	}

	/**
	 * And back the other way, which is the same bug with the operands swapped.
	 *
	 * <p>Unhide the last citizen while a non-matching query is typed and the section was
	 * left saying "Nobody here matches that." — over a list with nothing left to match, and
	 * under a header reading (0).
	 */
	@Test
	public void theSentenceComesBackWhenTheLastOverrideGoes() throws Exception
	{
		FakeRegions regions = new FakeRegions();
		EntityDefinition someone = regions.citizen(12852, 3225, 3360, 0);

		FakeConfig config = new FakeConfig();
		config.overrides().hide(someone);

		LivelyCitiesPanel panel = new LivelyCitiesPanel(plugin(config));
		panel.accept(model(config, uuids(someone), Collections.emptySet()));
		drain();
		assertEquals("one row to start with", 1, actionsLabelled(panel, "show").size());

		type(panel, "zzzz");
		assertTrue("filtered out, so the other sentence: " + labels(panel),
			labels(panel).contains("Nobody here matches that."));

		config.overrides().unhide(someone.getUuid());
		panel.accept(model(config, Collections.emptySet(), Collections.emptySet()));
		drain();

		List<String> text = labels(panel);
		assertTrue("the header is back to nobody: " + text,
			text.stream().anyMatch(line -> line.endsWith("Hidden and muted (0)")));
		assertTrue("and so is the sentence: " + text,
			text.contains("Nobody is hidden or muted."));
		assertFalse("there is nothing left for a query to fail to match: " + text,
			text.contains("Nobody here matches that."));
	}

	// --- the two clickable dials ----------------------------------------------

	/**
	 * Clicking a density chip asks the plugin for that density, and not for whichever
	 * one the loop finished on.
	 *
	 * <p>The four chips are built in a loop over {@code CrowdDensity.values()} and each
	 * closes over its own value. A listener closing over the loop variable would send
	 * every click to the last one, which no test outside this file could see.
	 */
	@Test
	public void eachDensityChipAsksForItsOwnDensity()
	{
		for (CrowdDensity density : CrowdDensity.values())
		{
			FakeConfig config = new FakeConfig().setCrowdDensity(CrowdDensity.SPARSE);
			LivelyCitiesPanel panel = new LivelyCitiesPanel(plugin(config));
			panel.accept(loggedOut(config));
			drain();

			JLabel chip = labelReading(panel, density.toString());
			assertNotNull("no chip reading '" + density + "'", chip);
			click(chip);

			if (density == CrowdDensity.SPARSE)
			{
				assertTrue("the chip already selected writes nothing",
					config.writes().isEmpty());
				continue;
			}

			assertEquals(1, config.writes().size());
			assertEquals(LivelyCitiesConfig.KEY_CROWD_DENSITY + "=" + density.name(),
				config.writes().get(0));
		}
	}

	/**
	 * Clicking a city card toggles that city, and not the one beside it.
	 *
	 * <p>Same closure hazard as the chips, and worse consequences: this is a live
	 * plugin, and a card that unticked its neighbour would be a setting changed under a
	 * user who did not ask.
	 */
	@Test
	public void eachCityCardTogglesItsOwnCity()
	{
		for (City city : City.values())
		{
			FakeConfig config = new FakeConfig();
			LivelyCitiesPanel panel = new LivelyCitiesPanel(plugin(config));

			// Drawn with no world rather than from Varrock square, so that the header's
			// "you are in Varrock" label cannot be the first thing reading "Varrock" and
			// send this click at the header instead of at the card. The first version of
			// this test did exactly that and failed on one city in nine.
			panel.accept(loggedOut(config));
			drain();

			JLabel name = labelReading(panel, city.getLabel());
			assertNotNull("no card reading '" + city.getLabel() + "'", name);
			click(name.getParent());

			assertEquals("one write", 1, config.writes().size());
			assertEquals(city.getConfigKey() + "=false", config.writes().get(0));
		}
	}

	// --- opening and closing --------------------------------------------------

	/**
	 * The panel only claims to be open between {@code onActivate} and
	 * {@code onDeactivate}, and {@code closed()} is a third way out.
	 *
	 * <p>That flag is what the game tick asks before doing any per-tick work at all, so
	 * a panel that returned true from construction would cost every user a census a
	 * second for a panel they never opened.
	 */
	@Test
	public void thePanelIsOnlyOpenWhileItIsOpen()
	{
		LivelyCitiesPanel panel = panel();
		assertFalse("a freshly built panel is not in the sidebar", panel.isOpen());

		panel.onActivate();
		assertTrue(panel.isOpen());

		panel.onDeactivate();
		assertFalse(panel.isOpen());

		panel.onActivate();
		panel.closed();
		assertFalse("and removing the button is the other way out", panel.isOpen());
	}

	// --- layout ---------------------------------------------------------------

	/**
	 * <b>Nothing on the panel is capped at zero pixels high.</b>
	 *
	 * <p>Every row in a vertical {@code BoxLayout} has to have its maximum height pinned
	 * to its preferred height, or the column stretches one row to fill the slack. The cap
	 * has to be applied <i>after</i> the row's labels are in it: an empty {@code JPanel}
	 * prefers to be nothing tall, so capping on the way out of the factory pins the row
	 * at zero — and a 0px row is laid out, visible, painted, and gone. Every assertion in
	 * this file passed with the title and all three headings capped that way, because
	 * {@code isVisible()} is true of a component with no height.
	 *
	 * <p>Checked over the whole tree rather than the four rows that were wrong, so the
	 * next component built the same way is caught too. The spacers are excluded by name:
	 * they are deliberately a fixed few pixels and are the only things here whose height
	 * is not derived from their contents.
	 */
	@Test
	public void noRowOnThePanelIsPinnedToZeroHeight()
	{
		LivelyCitiesPanel panel = panel();
		panel.accept(loggedOut(config));
		drain();

		List<String> pinned = new ArrayList<>();
		int checked = 0;
		for (Component component : tree(panel))
		{
			if (!(component instanceof javax.swing.JComponent) || !component.isVisible())
			{
				continue;
			}

			java.awt.Dimension max = component.getMaximumSize();
			java.awt.Dimension preferred = component.getPreferredSize();
			if (preferred.height == 0)
			{
				// A spacer, or a container the layout gives no height of its own.
				continue;
			}

			checked++;
			if (max.height < preferred.height)
			{
				pinned.add(component.getClass().getSimpleName() + " prefers "
					+ preferred.height + "px and is capped at " + max.height);
			}
		}

		assertTrue("a scan that measured nothing would pass; " + checked + " components",
			checked > 10);
		assertEquals("a row capped below its own preferred height is a row that is laid "
			+ "out, visible, painted and invisible: " + pinned, 0, pinned.size());
	}

	// --- the palette ----------------------------------------------------------

	/**
	 * <b>Every colour on the panel comes from {@link net.runelite.client.ui.ColorScheme}.</b>
	 *
	 * <p>Not a style rule. RuneLite's sidebar is themed, and a panel carrying its own
	 * greys is a panel that looks wrong beside every other one the moment the client's
	 * palette moves — which it has. The failure is also silent: a hardcoded colour is
	 * indistinguishable from the right one on the machine it was picked on.
	 *
	 * <p>Checked by reading the source, because there is nowhere else the fact lives: a
	 * {@code Color} on a component at runtime is just a colour, and asking whether it
	 * equals a {@code ColorScheme} constant would pass for anything that happened to
	 * match today. The eight aliases at the top of the panel are the whole palette, and
	 * every one of them is assigned from that class.
	 *
	 * <p>{@code CitizenLabel} is deliberately not held to this — its "this is fake"
	 * colour has to be one the game never uses for a real menu target, which is the
	 * opposite requirement. That is a colour in the 3D scene, not in the sidebar.
	 */
	@Test
	public void thePanelTakesEveryColourFromTheClientsOwnPalette() throws Exception
	{
		String source = new String(java.nio.file.Files.readAllBytes(new java.io.File(
			"src/main/java/com/matthewmariner/livelycities/LivelyCitiesPanel.java").toPath()),
			java.nio.charset.StandardCharsets.UTF_8);

		assertTrue("the scan has to have found the file", source.contains("class LivelyCitiesPanel"));
		assertFalse("no colour may be constructed in the panel: " + source.length()
			+ " chars scanned", source.contains("new Color("));
		assertFalse("and none may be written as a literal", source.matches("(?s).*0x[0-9A-Fa-f]{6}.*"));

		// The sample guard: a scan that matched nothing at all would pass both of the
		// above while proving nothing about where the colours do come from.
		int fromTheScheme = 0;
		for (int at = source.indexOf("ColorScheme."); at >= 0;
			at = source.indexOf("ColorScheme.", at + 1))
		{
			fromTheScheme++;
		}
		assertTrue("the panel has to actually name ColorScheme, or the two checks above are "
			+ "checking an empty file", fromTheScheme >= 8);
	}

	// --- helpers ---------------------------------------------------------------

	private LivelyCitiesPanel panel()
	{
		return new LivelyCitiesPanel(plugin(config));
	}

	private LivelyCitiesPlugin plugin(FakeConfig config)
	{
		LivelyCitiesPlugin plugin = new LivelyCitiesPlugin();
		plugin.config = config;
		plugin.configWriter = config.writer();
		plugin.overrides = config.overrides();

		// The real 27-file dataset, because the panel asks this plugin for the pre-tick
		// reading and the counts on the cards are the dataset's — see
		// thePanelHasContentBeforeTheFirstGameTick.
		plugin.directory = directory;
		return plugin;
	}

	/**
	 * The pre-tick reading, composed the way {@link LivelyCitiesPlugin#loggedOutModel}
	 * composes it — from the fixture's own override store rather than from two empty
	 * sets, so a test that hides somebody first sees them.
	 */
	private PanelModel loggedOut(FakeConfig config)
	{
		return PanelModel.loggedOut(config, config.overrides().hiddenUuids(),
			config.overrides().mutedUuids(), directory);
	}

	private PanelModel model(FakeConfig config, Set<UUID> hidden, Set<UUID> muted)
	{
		return PanelModel.of(IN_VARROCK, SceneCensus.EMPTY, config, hidden, muted,
			new CitizenDirectory(new FakeRegions()));
	}

	private static Set<UUID> uuids(EntityDefinition... definitions)
	{
		Set<UUID> out = new LinkedHashSet<>();
		for (EntityDefinition definition : definitions)
		{
			out.add(definition.getUuid());
		}
		return out;
	}

	/**
	 * What the panel puts on the row for a citizen the directory cannot place — see
	 * {@code PanelModel.OverrideRow.getDisplayName}. Every fixture uuid here is one,
	 * because {@link FakeRegions} is not the shipped dataset.
	 */
	private static String displayName(EntityDefinition definition)
	{
		String text = definition.getUuid().toString();
		return "Citizen " + text.substring(0, text.indexOf('-'));
	}

	private static List<UUID> sortedUuids(EntityDefinition... definitions)
	{
		List<UUID> out = new ArrayList<>();
		for (EntityDefinition definition : definitions)
		{
			out.add(definition.getUuid());
		}
		out.sort(java.util.Comparator.comparing(UUID::toString));
		return out;
	}

	/**
	 * {@link LivelyCitiesPanel#accept} publishes and then hops to Swing, so a test that
	 * asserted straight afterwards would be racing the redraw. This waits for the event
	 * queue to reach the task {@code accept} put on it.
	 */
	private static void drain()
	{
		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
			});
		}
		catch (Exception e)
		{
			throw new AssertionError("the Swing queue never drained", e);
		}
	}

	private static void type(LivelyCitiesPanel panel, String query) throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
			net.runelite.client.ui.components.IconTextField.class.cast(
				find(panel, net.runelite.client.ui.components.IconTextField.class))
				.setText(query));
	}

	private static Component find(Container root, Class<?> type)
	{
		for (Component child : root.getComponents())
		{
			if (type.isInstance(child))
			{
				return child;
			}
			if (child instanceof Container)
			{
				Component found = find((Container) child, type);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static void collect(Container root, List<Component> into)
	{
		for (Component child : root.getComponents())
		{
			into.add(child);
			if (child instanceof Container)
			{
				collect((Container) child, into);
			}
		}
	}

	private static List<Component> tree(LivelyCitiesPanel panel)
	{
		List<Component> out = new ArrayList<>();
		collect(panel, out);
		return out;
	}

	/**
	 * Every piece of text a reader could actually see, in tree order.
	 *
	 * <p><b>Showing means the whole chain is showing, not just the label.</b>
	 * {@code isVisible()} is a component's own flag and stays true inside a
	 * {@code setVisible(false)} ancestor — and what the search filter hides is a
	 * <i>card</i>, never the labels on it. Filtered on the label alone, this method
	 * returned the text of every hidden card, and
	 * {@link #everyPlaceIsOnThePanel()}, {@link #theLiveNumbersReachTheCards()} and
	 * {@link #eachCardShowsItsOwnCitysState()} all passed with every card on the panel
	 * hidden. {@link #visibleCards(LivelyCitiesPanel)} was already right, which is why
	 * only the search test noticed.
	 *
	 * <p>Same family as the zero-height bug {@link #noRowOnThePanelIsPinnedToZeroHeight()}
	 * exists for: asserting existence where the property being claimed is visibility.
	 */
	private static List<String> labels(LivelyCitiesPanel panel)
	{
		List<String> out = new ArrayList<>();
		for (Component component : tree(panel))
		{
			if (component instanceof JLabel && showing(panel, component))
			{
				String text = ((JLabel) component).getText();
				if (text != null && !text.isEmpty())
				{
					out.add(text);
				}
			}
		}
		return out;
	}

	/** @return whether this component and every ancestor up to the panel is visible */
	private static boolean showing(LivelyCitiesPanel panel, Component component)
	{
		for (Component at = component; at != null; at = at.getParent())
		{
			if (!at.isVisible())
			{
				return false;
			}
			if (at == panel)
			{
				break;
			}
		}
		return true;
	}

	private static JLabel labelReading(LivelyCitiesPanel panel, String text)
	{
		for (Component component : tree(panel))
		{
			if (component instanceof JLabel && text.equals(((JLabel) component).getText()))
			{
				return (JLabel) component;
			}
		}
		return null;
	}

	/** The per-row action labels, in the order the rows are drawn. */
	private static List<JLabel> actionsLabelled(LivelyCitiesPanel panel, String text)
	{
		List<JLabel> out = new ArrayList<>();
		for (Component component : tree(panel))
		{
			if (component instanceof JLabel && text.equals(((JLabel) component).getText()))
			{
				out.add((JLabel) component);
			}
		}
		return out;
	}

	/**
	 * How many city cards are showing.
	 *
	 * <p>Counted by the city-name labels whose whole card is visible, because that is
	 * what the filter actually hides — a card, not a label.
	 */
	private static int visibleCards(LivelyCitiesPanel panel)
	{
		int visible = 0;
		for (City city : City.values())
		{
			JLabel name = labelReading(panel, city.getLabel());
			if (name != null && name.getParent() != null && name.getParent().isVisible())
			{
				visible++;
			}
		}
		return visible;
	}

	/** @return how many of this container's own children are visible */
	private static int showingChildren(Container container)
	{
		int showing = 0;
		for (Component child : container.getComponents())
		{
			if (child.isVisible())
			{
				showing++;
			}
		}
		return showing;
	}

	private static int count(List<String> texts, String exact)
	{
		int n = 0;
		for (String text : texts)
		{
			if (exact.equals(text))
			{
				n++;
			}
		}
		return n;
	}

	/** Fires the press every listener on this component is waiting for. */
	private static void click(Component component)
	{
		MouseEvent event = new MouseEvent(component, MouseEvent.MOUSE_PRESSED,
			System.currentTimeMillis(), 0, 1, 1, 1, false);
		for (MouseListener listener : component.getMouseListeners())
		{
			listener.mousePressed(event);
		}
	}
}
