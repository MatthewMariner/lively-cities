package com.matthewmariner.livelycities;

import java.awt.Rectangle;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The right-click menu: never competing for a click, unmistakably ours, and
 * sending nothing.
 *
 * <p>These three are, collectively, the plugin's licence to exist. The hub
 * disabled the predecessor after a Reddit thread in which a player mistook a
 * broken-looking fake NPC for a real one, and upstream issue #14 — 49 upvotes —
 * is "can't right-click through them" plus "misclicks when using an item". Both
 * are answered here or not at all.
 *
 * <p><b>How "sends nothing to the server" is asserted rather than asserted-to.</b>
 * {@link FakeClient} extends {@link StubClient}, in which every method this plugin
 * has never needed throws — including {@code Client.menuAction(..)}, the one call
 * on this surface that performs a real game action. {@link FakeMenuEntry} extends
 * {@link StubMenuEntry} on the same terms, so {@code onClick}, {@code setItemId}
 * and {@code setForceLeftClick} throw too. A handler that reached for any of them
 * would fail here, loudly, rather than ship.
 *
 * <p><b>The clickbox is a seam, and it has to be.</b>
 * {@code Perspective.getClickbox} projects a model through the live camera, so
 * {@link TestMenu} overrides it with a rectangle and, more importantly, counts how
 * many times it was asked. "No clickbox at all while an item is on the cursor" is a
 * claim about that count, not about the resulting menu.
 */
public class CitizenMenuTest
{
	private static final int VARROCK_NORTH = 12853;
	private static final WorldPoint PLAYER = new WorldPoint(3220, 3420, 0);

	/** Where the mouse is in every test that wants a hit. */
	private static final Point MOUSE = new Point(400, 300);

	private FakeClient client;
	private FakeRegions regions;
	private FakeConfig config;
	private EntityScene scene;
	private FakeWorldView view;
	private TestMenu menu;

	@Before
	public void setUp()
	{
		client = new FakeClient();
		regions = new FakeRegions();
		config = new FakeConfig();
		scene = new EntityScene(client, regions, config, config.overrides());
		view = FakeWorldView.around(PLAYER, VARROCK_NORTH);
		menu = new TestMenu();

		client.setLocalPlayer(new FakePlayer(PLAYER));
		client.setTopLevelWorldView(view);
		client.setMouseCanvasPosition(MOUSE);

		// The menu the client itself would have built for a right-click on the world:
		// array order is bottom row first, so this renders as Talk-to / Walk here /
		// Cancel from the top.
		client.menu().seedWorldClick();
	}

	// --- never compete for a click (issue #14) --------------------------------

	@Test
	public void everyEntryIsRuneLiteTypedAndDeprioritised()
	{
		EntityDefinition citizen = regions.talker(VARROCK_NORTH, 3220, 3421, "Busy today.");
		spawn(citizen);

		menu.onMenuOpened(client.menuOpened());

		List<MenuEntry> created = client.menu().created();
		assertEquals("Examine, Hide and Mute", 3, created.size());
		for (MenuEntry entry : created)
		{
			assertEquals(entry + " must be a RUNELITE action — anything else can reach the server",
				MenuAction.RUNELITE, entry.getType());
			assertTrue(entry + " must be deprioritised, so it sorts below every real option",
				entry.isDeprioritized());
		}
	}

	/**
	 * The entries go in at index 0, which is the bottom row of the rendered menu.
	 *
	 * <p>The array is rendered last-first — {@code MenuOpened.getFirstEntry()}
	 * returns {@code menuEntries[length - 1]}, verified in the 1.12.36 bytecode — so
	 * the last array element is the top row <i>and</i> the left-click action. Our
	 * entries must be at the other end.
	 */
	@Test
	public void entriesAreInsertedBelowEveryRealOption()
	{
		EntityDefinition citizen = regions.talker(VARROCK_NORTH, 3220, 3421, "Busy today.");
		spawn(citizen);

		menu.onMenuOpened(client.menuOpened());

		List<MenuEntry> entries = client.menu().entries();
		assertEquals(6, entries.size());

		// The three ours, at the bottom of the menu, in the order they read on
		// screen: Examine highest of the three, Mute at the very bottom.
		assertEquals(CitizenMenu.OPTION_MUTE, entries.get(0).getOption());
		assertEquals(CitizenMenu.OPTION_HIDE, entries.get(1).getOption());
		assertEquals(CitizenMenu.OPTION_EXAMINE, entries.get(2).getOption());

		// And the real ones untouched above them, with the left-click action — the
		// last element — still the game's.
		assertEquals("Cancel", entries.get(3).getOption());
		assertEquals("Talk-to", entries.get(entries.size() - 1).getOption());
	}

	/**
	 * No clickbox at all while an item or a spell is on the cursor.
	 *
	 * <p>The specific reported complaint: a misclick while using an item on
	 * something. The assertion is on the projection <i>count</i> rather than on the
	 * menu, because "we computed a clickbox and then decided not to use it" would
	 * pass a menu-shaped test while still paying the cost and still being one
	 * refactor away from the bug.
	 */
	@Test
	public void noClickboxIsComputedWhileAnItemOrSpellIsOnTheCursor()
	{
		EntityDefinition citizen = regions.talker(VARROCK_NORTH, 3220, 3421, "Busy today.");
		spawn(citizen);

		client.setWidgetSelected(true);
		menu.onMenuOpened(client.menuOpened());

		assertEquals("not one clickbox may be computed in target mode", 0, menu.clickboxCalls);
		assertTrue("and not one entry may be added", client.menu().created().isEmpty());
		assertNull("nor may a target be remembered for a later click", menu.getTarget());

		// And it is only the target mode that stopped it.
		client.setWidgetSelected(false);
		menu.onMenuOpened(client.menuOpened());
		assertTrue(menu.clickboxCalls > 0);
		assertEquals(3, client.menu().created().size());
	}

	/**
	 * The clickbox is only computed for citizens close enough to be clickable.
	 *
	 * <p>The plan names the per-object convex hull as the known frame-time cost
	 * centre and the performance budget names 15 tiles. Nine citizens are spawned,
	 * three of them outside the radius; the count is what proves the far ones were
	 * never projected.
	 */
	@Test
	public void clickboxesAreOnlyComputedInsideTheClickboxRadius()
	{
		List<EntityDefinition> near = new ArrayList<>();
		for (int i = 0; i < 6; i++)
		{
			near.add(regions.talker(VARROCK_NORTH, 3221 + i, 3420, "Close by."));
		}
		// 16, 18 and 20 tiles east — inside the default 25-tile cull radius, so they
		// really are spawned, and outside the 15-tile clickbox radius.
		List<EntityDefinition> far = new ArrayList<>();
		for (int distance : new int[]{16, 18, 20})
		{
			far.add(regions.talker(VARROCK_NORTH, 3220 + distance, 3420, "Miles away."));
		}

		List<EntityDefinition> all = new ArrayList<>(near);
		all.addAll(far);
		spawn(all.toArray(new EntityDefinition[0]));
		assertEquals("all nine have to be on screen, or the radius is not what excluded them",
			9, scene.countActive());

		// Nothing is under the mouse, so every candidate gets projected.
		menu.hit = false;
		menu.onMenuOpened(client.menuOpened());

		assertEquals("only the six inside 15 tiles may be projected", 6, menu.clickboxCalls);
	}

	/**
	 * Nothing is added to an interface's right-click menu.
	 *
	 * <p>The clickbox is in canvas space and the inventory is drawn over the
	 * viewport, so a citizen standing behind it really does project underneath it —
	 * and without the guard, right-clicking a rune would offer to examine him. The
	 * two seeds differ only in whether the client put a "Walk here" in the menu,
	 * which is exactly the signal the guard reads.
	 */
	@Test
	public void nothingIsAddedToAnInterfacesRightClickMenu()
	{
		FakeClient interfaceClient = new FakeClient();
		interfaceClient.setLocalPlayer(new FakePlayer(PLAYER));
		interfaceClient.setTopLevelWorldView(view);
		interfaceClient.setMouseCanvasPosition(MOUSE);
		interfaceClient.menu().seedInterfaceClick();

		EntityDefinition citizen = regions.talker(VARROCK_NORTH, 3220, 3421, "Busy today.");
		regions.file(VARROCK_NORTH, citizen);
		EntityScene interfaceScene = new EntityScene(
			interfaceClient, regions, config, config.overrides());
		interfaceScene.syncRegions(view);
		interfaceScene.updateVisibility(PLAYER, view);
		assertTrue("the citizen has to be on screen, or the guard is not what stopped this",
			interfaceScene.countActive() > 0);

		TestMenu interfaceMenu = new TestMenu(interfaceClient, interfaceScene);
		interfaceMenu.onMenuOpened(interfaceClient.menuOpened());

		assertEquals("an interface menu has no tile under it, so no clickbox may be computed",
			0, interfaceMenu.clickboxCalls);
		assertTrue("and nothing of ours may be in it",
			interfaceClient.menu().created().isEmpty());
	}

	@Test
	public void nothingIsAddedWhenTheMouseIsNotOverACitizen()
	{
		spawn(regions.talker(VARROCK_NORTH, 3220, 3421, "Busy today."));

		menu.hit = false;
		menu.onMenuOpened(client.menuOpened());

		assertTrue("a clickbox that does not contain the mouse is not a hit",
			client.menu().created().isEmpty());
		assertNull(menu.getTarget());
	}

	/**
	 * A citizen with nothing to say gets no Mute entry. An entry that cannot do
	 * anything is worse than a missing one: the user who clicks it learns that the
	 * plugin's menu lies.
	 */
	@Test
	public void aCitizenWithNothingToSayIsNotOfferedMute()
	{
		spawn(regions.citizen(VARROCK_NORTH, 3220, 3421, 0));

		menu.onMenuOpened(client.menuOpened());

		List<MenuEntry> created = client.menu().created();
		assertEquals("Examine and Hide, but not Mute", 2, created.size());
		for (MenuEntry entry : created)
		{
			assertFalse(CitizenMenu.OPTION_MUTE.equals(entry.getOption()));
		}
	}

	// --- fake is unmistakable from real ---------------------------------------

	@Test
	public void theMenuTargetCarriesTheColourAndThePluginsName()
	{
		EntityDefinition citizen = regions.talker(VARROCK_NORTH, 3220, 3421, "Busy today.");
		spawn(citizen);

		menu.onMenuOpened(client.menuOpened());

		for (MenuEntry entry : client.menu().created())
		{
			assertTrue(entry.getTarget() + " must open with the plugin's colour tag",
				entry.getTarget().startsWith(CitizenLabel.COLOUR_TAG));
			assertTrue(entry.getTarget() + " must name the plugin",
				entry.getTarget().contains(CitizenLabel.PLUGIN_NAME));
			assertTrue(entry.getTarget() + " must name the citizen",
				entry.getTarget().contains(citizen.getName()));
		}
	}

	/**
	 * Examine prints the dataset's own text plus the sentence that says whose
	 * citizen it is, and it does so into the local chat buffer — which is the whole
	 * of what it does.
	 */
	@Test
	public void examinePrintsLocallyAndSaysWhoseCitizenItIs()
	{
		EntityDefinition citizen = regions.talker(VARROCK_NORTH, 3220, 3421, "Busy today.");
		spawn(citizen);
		menu.onMenuOpened(client.menuOpened());

		MenuOptionClicked event = click(CitizenMenu.OPTION_EXAMINE, citizen);
		menu.onMenuOptionClicked(event);

		assertTrue("the click has to be consumed", event.isConsumed());
		assertEquals(1, client.chatMessages().size());
		String message = client.chatMessages().get(0);
		assertTrue(message, message.contains(citizen.getExamineText()));
		assertTrue(message, message.contains(CitizenLabel.PLUGIN_NAME));
		assertTrue(message, message.contains("not a real NPC"));
	}

	// --- sends nothing to the server -----------------------------------------

	/**
	 * A click is consumed and reaches nothing but the chat buffer and this plugin's
	 * own config.
	 *
	 * <p>The negative half is structural rather than counted: every other route into
	 * the client — {@code menuAction} above all — throws on {@link StubClient}, and
	 * every setter on {@link StubMenuEntry} that could change what a click does
	 * throws too. Reaching one is a test error, not a silent pass. This test walks
	 * all three options through so that every branch of the handler is on that
	 * hook.
	 */
	@Test
	public void everyClickIsConsumedAndSendsNothingToTheServer()
	{
		EntityDefinition citizen = regions.talker(VARROCK_NORTH, 3220, 3421, "Busy today.");
		spawn(citizen);

		for (String option : new String[]{
			CitizenMenu.OPTION_EXAMINE, CitizenMenu.OPTION_MUTE, CitizenMenu.OPTION_HIDE})
		{
			menu.onMenuOpened(client.menuOpened());
			MenuOptionClicked event = click(option, citizen);
			menu.onMenuOptionClicked(event);
			assertTrue(option + " must be consumed", event.isConsumed());
		}

		assertEquals("three clicks, three local lines and nothing else",
			3, client.chatMessages().size());
	}

	/**
	 * A RUNELITE entry with the same option text that is not ours must fall through
	 * untouched — not be consumed, and not act.
	 *
	 * <p>"Examine" is a common option, and consuming somebody else's would break
	 * their plugin invisibly.
	 */
	@Test
	public void anotherPluginsRuneLiteEntryIsLeftAlone()
	{
		EntityDefinition citizen = regions.talker(VARROCK_NORTH, 3220, 3421, "Busy today.");
		spawn(citizen);
		menu.onMenuOpened(client.menuOpened());

		// Right type, right text, wrong target and wrong identifier.
		MenuOptionClicked foreign = new MenuOptionClicked(new FakeMenuEntry()
			.setOption(CitizenMenu.OPTION_EXAMINE)
			.setTarget("<col=ffff00>Man")
			.setType(MenuAction.RUNELITE)
			.setIdentifier(9999));

		menu.onMenuOptionClicked(foreign);

		assertFalse("another plugin's entry must not be consumed", foreign.isConsumed());
		assertTrue("and must not have done anything", client.chatMessages().isEmpty());
	}

	/**
	 * The target check, isolated: same type, same option text, and — by coincidence —
	 * the same identifier as ours.
	 *
	 * <p>Identifiers are small integers and every plugin picks its own, so a
	 * collision is a matter of time. This is the case the target comparison exists
	 * for, and without a test where the identifier matches, the comparison is
	 * unreachable and a mutation removing it stays green.
	 */
	@Test
	public void anotherPluginsEntryWithTheSameIdentifierIsStillLeftAlone()
	{
		EntityDefinition citizen = regions.talker(VARROCK_NORTH, 3220, 3421, "Busy today.");
		spawn(citizen);
		menu.onMenuOpened(client.menuOpened());

		MenuOptionClicked collision = new MenuOptionClicked(new FakeMenuEntry()
			.setOption(CitizenMenu.OPTION_HIDE)
			.setTarget("<col=ffff00>Man")
			.setType(MenuAction.RUNELITE)
			.setIdentifier(currentToken()));

		menu.onMenuOptionClicked(collision);

		assertFalse("an identifier collision must not be enough to act on", collision.isConsumed());
		assertTrue("and nothing may have been hidden",
			config.overrides().hiddenUuids().isEmpty());
	}

	/**
	 * The identifier check, isolated: our own target, from a menu that has since been
	 * replaced.
	 *
	 * <p>A second right-click bumps the token, so a click event still carrying the
	 * first one is stale — the user right-clicked somebody else in between. Acting on
	 * it would examine or hide the wrong citizen. Without a test where the target
	 * matches, the identifier comparison is unreachable.
	 */
	@Test
	public void aStaleClickFromAnEarlierMenuIsIgnored()
	{
		EntityDefinition first = regions.talker(VARROCK_NORTH, 3220, 3421, "One.");
		EntityDefinition second = regions.talker(VARROCK_NORTH, 3222, 3421, "Two.");
		spawn(first, second);

		menu.onMenuOpened(client.menuOpened());
		MenuOptionClicked stale = click(CitizenMenu.OPTION_HIDE, menu.getTarget().getDefinition());

		// A second right-click, which bumps the token.
		menu.onMenuOpened(client.menuOpened());
		assertNotEquals("the second menu has to have a different token",
			stale.getId(), currentToken());

		menu.onMenuOptionClicked(stale);

		assertFalse("a click from a menu that has been replaced must not act",
			stale.isConsumed());
		assertTrue(config.overrides().hiddenUuids().isEmpty());
	}

	/**
	 * A non-RUNELITE click is never touched, whatever it says. This is the type
	 * check, and without it the handler would consume real game actions.
	 */
	@Test
	public void aRealGameActionIsNeverTouched()
	{
		EntityDefinition citizen = regions.talker(VARROCK_NORTH, 3220, 3421, "Busy today.");
		spawn(citizen);
		menu.onMenuOpened(client.menuOpened());

		MenuOptionClicked real = new MenuOptionClicked(new FakeMenuEntry()
			.setOption(CitizenMenu.OPTION_EXAMINE)
			.setTarget(CitizenLabel.menuTarget(citizen))
			.setType(MenuAction.EXAMINE_NPC)
			.setIdentifier(1));

		menu.onMenuOptionClicked(real);

		assertFalse("a real EXAMINE_NPC must reach the client untouched", real.isConsumed());
		assertTrue(client.chatMessages().isEmpty());
	}

	/**
	 * A click arriving with no open menu behind it — a stale event, or one for a
	 * menu that was invalidated by a scene change — does nothing rather than acting
	 * on whoever was last right-clicked.
	 */
	@Test
	public void aClickWithNoRememberedTargetDoesNothing()
	{
		EntityDefinition citizen = regions.talker(VARROCK_NORTH, 3220, 3421, "Busy today.");
		spawn(citizen);
		menu.onMenuOpened(client.menuOpened());

		MenuOptionClicked event = click(CitizenMenu.OPTION_EXAMINE, citizen);
		menu.forget();
		menu.onMenuOptionClicked(event);

		assertFalse(event.isConsumed());
		assertTrue(client.chatMessages().isEmpty());
	}

	// --- hide one citizen (issue #40) -----------------------------------------

	/**
	 * Hide writes the uuid, and the visibility pass takes the citizen off the
	 * screen. Nothing in {@link CitizenMenu} despawns anything: the write posts a
	 * {@code ConfigChanged} and the pass has one rule — what is not wanted is
	 * despawned.
	 */
	@Test
	public void hidingACitizenWritesItsUuidAndTakesItOffTheScreen()
	{
		EntityDefinition hide = regions.talker(VARROCK_NORTH, 3220, 3421, "Busy today.");
		EntityDefinition keep = regions.talker(VARROCK_NORTH, 3221, 3421, "Still here.");
		spawn(hide, keep);
		assertEquals(2, scene.countActive());

		menu.onMenuOpened(client.menuOpened());
		menu.onMenuOptionClicked(click(CitizenMenu.OPTION_HIDE, hide));

		assertEquals("the uuid is what is persisted, in canonical form",
			hide.getUuid().toString(), config.stored(CitizenOverrides.HIDDEN_KEY));

		scene.updateVisibility(PLAYER, view);
		assertEquals("only the hidden one goes", 1, scene.countActive());
		assertFalse("and it is the right one", scene.wrapperFor(hide).isActive());
		assertTrue(scene.wrapperFor(keep).isActive());
	}

	/**
	 * A hidden citizen stays hidden across a scene change, and after its wrapper has
	 * been evicted and rebuilt from the region file.
	 *
	 * <p>This is the test that the hide lives in the config rather than on the
	 * wrapper. An implementation that latched a flag on {@link LivelyEntity} would
	 * pass the previous test and fail this one: eviction drops the wrapper, and the
	 * rebuilt one would come back visible.
	 */
	@Test
	public void aHiddenCitizenStaysHiddenAcrossASceneChange()
	{
		EntityDefinition hide = regions.talker(VARROCK_NORTH, 3220, 3421, "Busy today.");
		spawn(hide);

		menu.onMenuOpened(client.menuOpened());
		menu.onMenuOptionClicked(click(CitizenMenu.OPTION_HIDE, hide));
		scene.updateVisibility(PLAYER, view);
		assertEquals(0, scene.countActive());

		// Walk far enough away that the region leaves the scene and its wrappers are
		// evicted, then come back.
		WorldPoint elsewhere = new WorldPoint(3600, 3200, 0);
		FakeWorldView away = FakeWorldView.around(elsewhere, RenderPolicy.regionIdOf(3600, 3200));
		for (int i = 0; i <= EntityScene.EVICTION_GRACE_SCOPE_CHANGES + 1; i++)
		{
			away.setMapRegions(RenderPolicy.regionIdOf(3600, 3200) + i);
			scene.syncRegions(away);
		}
		assertEquals("the region has to have been evicted for this test to mean anything",
			0, scene.getCachedRegionCount());

		scene.syncRegions(view);
		scene.updateVisibility(PLAYER, view);

		assertEquals("a rebuilt wrapper for a hidden uuid must still be hidden",
			0, scene.countActive());
	}

	/**
	 * The hide survives a round trip through the raw config string — which is what
	 * a restart is.
	 */
	@Test
	public void aHiddenCitizenSurvivesAConfigRoundTrip()
	{
		EntityDefinition hide = regions.talker(VARROCK_NORTH, 3220, 3421, "Busy today.");
		spawn(hide);
		menu.onMenuOpened(client.menuOpened());
		menu.onMenuOptionClicked(click(CitizenMenu.OPTION_HIDE, hide));

		String persisted = config.stored(CitizenOverrides.HIDDEN_KEY);
		assertNotNull(persisted);

		// A new session: new config object, new overrides, new scene — everything the
		// second one knows came out of that string.
		FakeConfig reloaded = new FakeConfig();
		reloaded.writer().write(CitizenOverrides.HIDDEN_KEY, persisted);
		FakeClient freshClient = new FakeClient();
		EntityScene freshScene = new EntityScene(
			freshClient, regions, reloaded, reloaded.overrides());

		freshScene.syncRegions(view);
		freshScene.updateVisibility(PLAYER, view);

		assertEquals("the hide has to survive a restart", 0, freshScene.countActive());
	}

	@Test
	public void unhideAllBringsEveryHiddenCitizenBack()
	{
		EntityDefinition first = regions.talker(VARROCK_NORTH, 3220, 3421, "One.");
		EntityDefinition second = regions.talker(VARROCK_NORTH, 3221, 3421, "Two.");
		spawn(first, second);

		// Hidden one at a time, with a visibility pass between, because the menu
		// targets whichever citizen is nearest and still on screen — which is exactly
		// how a user would do it, two right-clicks apart.
		menu.onMenuOpened(client.menuOpened());
		assertEquals(first, menu.getTarget().getDefinition());
		menu.onMenuOptionClicked(click(CitizenMenu.OPTION_HIDE, first));
		scene.updateVisibility(PLAYER, view);

		menu.onMenuOpened(client.menuOpened());
		assertEquals("with the first one gone, the menu targets the second",
			second, menu.getTarget().getDefinition());
		menu.onMenuOptionClicked(click(CitizenMenu.OPTION_HIDE, second));
		scene.updateVisibility(PLAYER, view);

		assertEquals(0, scene.countActive());
		assertEquals(2, config.overrides().hiddenUuids().size());

		assertEquals("both come back", 2, config.overrides().unhideAll());

		scene.updateVisibility(PLAYER, view);
		assertEquals(2, scene.countActive());
		assertNull("and the key is unset rather than left holding an empty string",
			config.stored(CitizenOverrides.HIDDEN_KEY));
	}

	// --- mute one citizen -----------------------------------------------------

	/**
	 * Mute writes the uuid and stops that citizen's current remark on the click.
	 *
	 * <p>Both halves matter: the write is what makes it last, and clearing the
	 * remark is what makes the click look like it worked — a mute that took up to
	 * the whole dwell to become visible is a mute users report as broken.
	 */
	@Test
	public void mutingACitizenPersistsAndShutsItUpOnTheClick()
	{
		EntityDefinition mute = regions.talker(VARROCK_NORTH, 3220, 3421, "Busy today.");
		EntityDefinition keep = regions.talker(VARROCK_NORTH, 3221, 3421, "Still talking.");
		spawn(mute, keep);

		CitizenRemarks muted = scene.wrapperFor(mute).getRemarks();
		CitizenRemarks other = scene.wrapperFor(keep).getRemarks();
		assertNotNull(muted);
		assertNotNull(other);
		muted.say(0, Integer.MAX_VALUE);
		other.say(0, Integer.MAX_VALUE);

		menu.onMenuOpened(client.menuOpened());
		menu.onMenuOptionClicked(click(CitizenMenu.OPTION_MUTE, mute));

		assertEquals(mute.getUuid().toString(), config.stored(CitizenOverrides.MUTED_KEY));
		assertFalse("the muted citizen stops mid-remark", muted.isTalking());
		assertTrue("and nobody else does", other.isTalking());
		assertTrue("the citizen stays visible — mute is not hide",
			scene.wrapperFor(mute).isActive());
	}

	@Test
	public void unmuteAllLetsEveryMutedCitizenTalkAgain()
	{
		EntityDefinition mute = regions.talker(VARROCK_NORTH, 3220, 3421, "Busy today.");
		spawn(mute);
		menu.onMenuOpened(client.menuOpened());
		menu.onMenuOptionClicked(click(CitizenMenu.OPTION_MUTE, mute));
		assertEquals(1, config.overrides().mutedUuids().size());

		assertEquals(1, config.overrides().unmuteAll());
		assertTrue(config.overrides().mutedUuids().isEmpty());
		assertNull(config.stored(CitizenOverrides.MUTED_KEY));
	}

	// --- helpers ------------------------------------------------------------

	private void spawn(EntityDefinition... entities)
	{
		regions.file(VARROCK_NORTH, entities);
		scene.syncRegions(view);
		scene.updateVisibility(PLAYER, view);
		assertTrue("the fixture has to actually spawn", scene.countActive() > 0);
	}

	/**
	 * The click event the client would post for one of our entries: our type, our
	 * option, our target, and the identifier the open menu stamped.
	 */
	private MenuOptionClicked click(String option, EntityDefinition definition)
	{
		return new MenuOptionClicked(new FakeMenuEntry()
			.setOption(option)
			.setTarget(CitizenLabel.menuTarget(definition))
			.setType(MenuAction.RUNELITE)
			.setIdentifier(currentToken()));
	}

	/**
	 * The identifier the open menu stamped on its entries.
	 *
	 * <p>Read back off the entries the plugin created rather than tracked here: the
	 * token is the plugin's, and a test that kept its own copy would pass even if
	 * the plugin stopped stamping one.
	 */
	private int currentToken()
	{
		List<MenuEntry> created = client.menu().created();
		assertFalse("there has to be an open menu to click on", created.isEmpty());
		return created.get(created.size() - 1).getIdentifier();
	}

	/**
	 * {@link CitizenMenu} with the camera taken out.
	 *
	 * <p>{@link #hit} decides whether the fake clickbox contains the mouse, and
	 * {@link #clickboxCalls} counts how many were computed — which is the assertion
	 * for both halves of issue #14 that are not about the entry's own fields.
	 */
	private final class TestMenu extends CitizenMenu
	{
		private int clickboxCalls;
		private boolean hit = true;

		private TestMenu()
		{
			this(client, scene);
		}

		private TestMenu(FakeClient client, EntityScene scene)
		{
			super(client, scene, config.overrides());
		}

		@Override
		@Nullable
		Shape clickbox(LivelyEntity entity, WorldView worldView)
		{
			clickboxCalls++;
			assertNotNull("a clickbox must never be computed for an unplaced entity",
				entity.getRenderLocation());

			// A box around the mouse, or one nowhere near it — so "the mouse is
			// inside" is a real branch rather than always true.
			return hit
				? new Rectangle(MOUSE.getX() - 10, MOUSE.getY() - 10, 20, 20)
				: new Rectangle(-500, -500, 20, 20);
		}
	}
}
