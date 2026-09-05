package com.matthewmariner.livelycities;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import net.runelite.api.Constants;
import net.runelite.api.GameState;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.ui.overlay.Overlay;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The plugin class itself: the two clocks, the six invalidating game states, and
 * the rule that a settings change is not a game tick.
 *
 * <p><b>This file exists because of a mutation test.</b> Deleting the
 * {@code scene.onFrame(..)} call from {@code onBeforeRender} <i>and</i> every one
 * of the six {@code case} labels in {@code onGameStateChanged} left the whole
 * suite green: 116 of 116 passing with the plugin's per-frame work and its entire
 * teardown-on-scene-change contract removed. Nothing had ever constructed a
 * {@link LivelyCitiesPlugin}. {@code LivelyCitiesPluginTest} is the RuneLite dev
 * launcher — {@code build.gradle} names it as {@code pluginMainClass} — and has no
 * assertions in it by design, which is how the gap went unnoticed.
 *
 * <p><b>How it is wired without a mocking framework.</b> There is none on the
 * classpath and reflection is out, so:
 * <ul>
 *   <li>{@link RecordingScene} subclasses the real {@link EntityScene} and counts
 *       calls. Every entry point the plugin uses is package-private and
 *       non-final, so this needs nothing but an override.</li>
 *   <li>{@link InlineClientThread} subclasses {@link ClientThread} and runs the
 *       runnable inline — which is what the real one does when it is already on
 *       the client thread, i.e. what happens in every case the plugin cares
 *       about.</li>
 *   <li>{@link FakeClient} supplies the game cycle, the game state, the local
 *       player and the top-level world view; the plugin's three injected fields
 *       are package-private so they can be set from here.</li>
 * </ul>
 *
 * <p>A handful of tests use the real {@link EntityScene} instead, because
 * "a config change must not move anybody" is a claim about a citizen's tile, and
 * a recording double cannot have a tile.
 */
public class LivelyCitiesPluginLifecycleTest
{
	private static final int REGION = 12852;
	private static final WorldPoint PLAYER = new WorldPoint(3225, 3360, 0);

	/** 600 / 20. Written out so a change to the plugin's divisor is visible here. */
	private static final int CLIENT_TICKS_PER_GAME_TICK =
		Constants.GAME_TICK_LENGTH / Constants.CLIENT_TICK_LENGTH;

	/**
	 * The states that mean "every LocalPoint we hold is about to mean something
	 * else". Written out rather than derived from the switch, which is the thing
	 * under test.
	 */
	private static final Set<GameState> MUST_INVALIDATE = EnumSet.of(
		GameState.LOADING,
		GameState.HOPPING,
		GameState.LOGGING_IN,
		GameState.LOGIN_SCREEN,
		GameState.LOGIN_SCREEN_AUTHENTICATOR,
		GameState.CONNECTION_LOST);

	private FakeClient client;
	private FakeWorldView view;
	private FakePlayer player;
	private InlineClientThread clientThread;
	private RecordingOverlays overlays;
	private RecordingSidePanel sidebar;

	@Before
	public void setUp()
	{
		client = new FakeClient();
		view = FakeWorldView.around(PLAYER, REGION);
		player = new FakePlayer(PLAYER);
		clientThread = new InlineClientThread();
		overlays = new RecordingOverlays();
		sidebar = new RecordingSidePanel();

		client.setLocalPlayer(player);
		client.setTopLevelWorldView(view);
		client.setGameState(GameState.LOGGED_IN);
	}

	// --- the interpolation clock ---------------------------------------------

	/**
	 * The fraction the frame handler hands the scene, across one whole game tick.
	 *
	 * <p>It is read out of {@code getGameCycle()} — the client's own 20ms counter
	 * — rather than the wall clock, so a paused client or a sleeping machine
	 * cannot make citizens slide. Thirty client ticks is one game tick, and the
	 * fraction has to reach 1 exactly at the thirtieth and stay there, because a
	 * dropped or delayed game tick would otherwise send a citizen sliding past the
	 * tile it was walking to.
	 */
	@Test
	public void theInterpolationFractionRunsFromZeroToOneAcrossAGameTick()
	{
		RecordingScene scene = new RecordingScene();
		LivelyCitiesPlugin plugin = plugin(scene);

		client.setGameCycle(1000);
		plugin.onGameTick(new GameTick());

		assertEquals("the frame the tick lands on is the start of the step",
			0f, fractionAt(plugin, scene, 1000), 0f);
		assertEquals(1 / 30f, fractionAt(plugin, scene, 1001), 1e-6f);
		assertEquals("halfway", 0.5f, fractionAt(plugin, scene, 1015), 1e-6f);
		assertEquals(29 / 30f, fractionAt(plugin, scene, 1029), 1e-6f);
		assertEquals("the last client tick of the game tick is the end of the step",
			1f, fractionAt(plugin, scene, 1000 + CLIENT_TICKS_PER_GAME_TICK), 0f);
		assertEquals("a late game tick clamps rather than overshooting",
			1f, fractionAt(plugin, scene, 1000 + CLIENT_TICKS_PER_GAME_TICK * 4), 0f);
	}

	/**
	 * A game cycle that has gone backwards is a frame drawn before the tick that
	 * would explain it. Zero — the start of the step — is the only answer that
	 * cannot move a citizen somewhere it has not been told to go; a negative
	 * fraction would drag it back past the tile it came from.
	 *
	 * <p>"Backwards" means backwards by a plausible amount. Past about 2^31 the
	 * subtraction wraps and a huge step back is arithmetically identical to a huge
	 * step forward — see
	 * {@link #theInterpolationFractionSurvivesTheGameCycleWrapping}, which is the
	 * whole reason the wrapping is wanted. The clamp is what makes that harmless
	 * either way: the worst either end can produce is a citizen standing on one of
	 * the two tiles its step runs between.
	 */
	@Test
	public void theInterpolationFractionIsZeroWhenTheClockGoesBackwards()
	{
		RecordingScene scene = new RecordingScene();
		LivelyCitiesPlugin plugin = plugin(scene);

		client.setGameCycle(1000);
		plugin.onGameTick(new GameTick());

		assertEquals("one client tick early", 0f, fractionAt(plugin, scene, 999), 0f);
		assertEquals("a whole game tick early", 0f, fractionAt(plugin, scene, 970), 0f);
		assertEquals("twenty seconds early", 0f, fractionAt(plugin, scene, 0), 0f);
		assertEquals("and as far back as the counter can go without wrapping",
			0f, fractionAt(plugin, scene, 1000 - Integer.MAX_VALUE), 0f);

		// Past the wrap the two directions are the same number, and the clamp
		// keeps the citizen on a tile either way.
		assertEquals(1f, fractionAt(plugin, scene, Integer.MIN_VALUE + 1), 0f);
	}

	/**
	 * {@code getGameCycle()} is an {@code int} and the client runs at 50 of them a
	 * second, so it wraps after about 497 days of uptime. The subtraction is what
	 * saves it: two's-complement {@code int} arithmetic wraps the same way the
	 * counter does, so {@code MIN_VALUE - MAX_VALUE} is 1, not -4294967295.
	 *
	 * <p>Pinned because the obvious "fix" — promoting the subtraction to
	 * {@code long}, or comparing the two cycles before subtracting — would break
	 * it: at the wrap a {@code long} subtraction gives a huge negative number, the
	 * {@code elapsed <= 0} guard fires, and every citizen in the world freezes
	 * mid-step until the next game tick.
	 */
	@Test
	public void theInterpolationFractionSurvivesTheGameCycleWrapping()
	{
		RecordingScene scene = new RecordingScene();
		LivelyCitiesPlugin plugin = plugin(scene);

		client.setGameCycle(Integer.MAX_VALUE);
		plugin.onGameTick(new GameTick());

		// One client tick after the last cycle an int can hold.
		assertEquals(1 / 30f, fractionAt(plugin, scene, Integer.MIN_VALUE), 1e-6f);
		assertEquals(0.5f, fractionAt(plugin, scene, Integer.MIN_VALUE + 14), 1e-6f);
		assertEquals(1f, fractionAt(plugin, scene, Integer.MIN_VALUE + 29), 0f);
	}

	// --- the frame handler ---------------------------------------------------

	/**
	 * The per-frame pass happens, and it happens with a fraction that moves.
	 *
	 * <p>Deleting the {@code scene.onFrame(..)} call was one half of the mutation
	 * that proved this file was needed: with it gone every wandering citizen
	 * teleports a whole tile every 600ms and the whole interpolation design is
	 * dead code. A count alone is not enough either — a handler that always passed
	 * 0 would keep the count and lose the movement — so the fractions are asserted
	 * to be strictly increasing across a tick.
	 */
	@Test
	public void everyFrameInterpolatesTheWanderersAndTheFractionAdvances()
	{
		RecordingScene scene = new RecordingScene();
		LivelyCitiesPlugin plugin = plugin(scene);

		client.setGameCycle(500);
		plugin.onGameTick(new GameTick());

		for (int clientTick = 0; clientTick < CLIENT_TICKS_PER_GAME_TICK; clientTick++)
		{
			client.setGameCycle(500 + clientTick);
			plugin.onBeforeRender(new BeforeRender());
		}

		assertEquals("one interpolation per frame", CLIENT_TICKS_PER_GAME_TICK, scene.frames);
		assertEquals(CLIENT_TICKS_PER_GAME_TICK, scene.fractions.size());

		for (int i = 1; i < scene.fractions.size(); i++)
		{
			assertTrue("the fraction has to advance frame to frame, got "
					+ scene.fractions.get(i - 1) + " then " + scene.fractions.get(i),
				scene.fractions.get(i) > scene.fractions.get(i - 1));
		}
		assertEquals(0f, scene.fractions.get(0), 0f);
	}

	/**
	 * No world view is a client with no scene — between logins, or mid-load. The
	 * frame handler has to notice, because {@code EntityScene.onFrame} would
	 * dereference it.
	 */
	@Test
	public void aFrameWithNoWorldViewDoesNoWork()
	{
		RecordingScene scene = new RecordingScene();
		LivelyCitiesPlugin plugin = plugin(scene);

		client.setTopLevelWorldView(null);
		plugin.onBeforeRender(new BeforeRender());

		assertEquals(0, scene.frames);
	}

	// --- the game-state contract --------------------------------------------

	/**
	 * The six states after which nothing we hold may stay active, and the three
	 * after which everything may.
	 *
	 * <p>Deleting all six {@code case} labels was the other half of the mutation
	 * that proved this file was needed: without them a world hop leaves every
	 * {@code RuneLiteObject} registered against a scene that no longer exists.
	 *
	 * <p>It walks {@link GameState#values()} rather than the six, so a state added
	 * to the client has to be classified here — deliberately, one way or the other
	 * — instead of silently defaulting to "does nothing".
	 */
	@Test
	public void exactlyTheStatesThatReplaceTheSceneInvalidateIt()
	{
		for (GameState state : GameState.values())
		{
			RecordingScene scene = new RecordingScene();
			LivelyCitiesPlugin plugin = plugin(scene);

			GameStateChanged event = new GameStateChanged();
			event.setGameState(state);
			plugin.onGameStateChanged(event);

			if (MUST_INVALIDATE.contains(state))
			{
				assertEquals(state + " must invalidate the scene",
					1, scene.invalidations.size());
				assertEquals("and name itself in the log line",
					state.name(), scene.invalidations.get(0));
			}
			else
			{
				assertEquals(state + " must not invalidate anything",
					0, scene.invalidations.size());
			}

			assertEquals(state + " must not run a tick", 0, scene.gameTicks);
		}

		assertEquals("six states replace or lose the scene", 6, MUST_INVALIDATE.size());
		assertEquals("and the client has nine altogether, so three do not",
			9, GameState.values().length);
	}

	// --- the rule that a setting is not a tick -------------------------------

	/**
	 * A config change re-decides visibility and steps nobody.
	 *
	 * <p>This is the guard on the defect: {@code onConfigChanged} used to call the
	 * whole {@code tick()}, so every settings touch walked every citizen a full
	 * tile <i>and</i> restarted the interpolation clock. Both halves are asserted,
	 * because either one alone would still be a bug.
	 */
	@Test
	public void aConfigChangeRunsAVisibilityPassAndNotAGameTick()
	{
		RecordingScene scene = new RecordingScene();
		LivelyCitiesPlugin plugin = plugin(scene);

		client.setGameCycle(1000);
		plugin.onGameTick(new GameTick());
		assertEquals(1, scene.gameTicks);

		// Fifteen client ticks into the step, the user clicks a checkbox.
		client.setGameCycle(1015);
		plugin.onConfigChanged(configChanged(LivelyCitiesConfig.GROUP, "cityVarrock"));

		assertEquals("no walker may be stepped by a settings change", 1, scene.gameTicks);
		assertEquals("but visibility has to be re-decided at once", 1, scene.settingsChanges);
		assertEquals("and the scene still has to be re-checked", 2, scene.syncRegions);

		assertEquals("the interpolation clock must not have been restarted",
			0.5f, fractionAt(plugin, scene, 1015), 1e-6f);
	}

	@Test
	public void aConfigChangeInAnotherPluginsGroupIsIgnored()
	{
		RecordingScene scene = new RecordingScene();
		LivelyCitiesPlugin plugin = plugin(scene);

		plugin.onConfigChanged(configChanged("gpu", "drawDistance"));
		plugin.onConfigChanged(configChanged("groundmarkers", "showImportedMarkers"));

		assertEquals(0, scene.settingsChanges);
		assertEquals(0, scene.gameTicks);
		assertEquals(0, scene.syncRegions);
	}

	/**
	 * The symptom, against a real citizen: switching RuneLite profiles must not
	 * teleport the crowd.
	 *
	 * <p>RuneLite posts one {@code ConfigChanged} per key when a profile is
	 * applied, so this plugin gets about two dozen in a row — one per city
	 * checkbox plus the two dials. Under the old code each one ran a full game
	 * tick, so every wandering citizen took two dozen steps between two frames.
	 *
	 * <p>Uses the real {@link EntityScene}, because the claim is about a tile.
	 */
	@Test
	public void switchingProfilesDoesNotTeleportTheCrowd()
	{
		FakeRegions regions = new FakeRegions();
		EntityDefinition walker = regions.wanderer(
			REGION,
			new WorldPoint(3225, 3358, 0),
			new WorldPoint(3220, 3353, 0),
			new WorldPoint(3230, 3363, 0),
			500);
		regions.file(REGION, walker);

		FakeConfig defaults = new FakeConfig();
		EntityScene scene = new EntityScene(client, regions, defaults, defaults.overrides());
		LivelyCitiesPlugin plugin = plugin(scene);

		// Get the citizen spawned and actually walking.
		CitizenWalk walk = null;
		for (int tick = 0; tick < 200; tick++)
		{
			client.advanceGameCycle(CLIENT_TICKS_PER_GAME_TICK);
			plugin.onGameTick(new GameTick());

			if (walk == null)
			{
				LivelyEntity wrapper = scene.wrapperFor(walker);
				assertNotNull("the fixture should have built a wrapper", wrapper);
				walk = wrapper.getWalk();
				assertNotNull("and that wrapper should have a walk", walk);
			}

			if (walk.isMoving())
			{
				break;
			}
		}

		assertNotNull(walk);
		assertTrue("the fixture must get the citizen walking", walk.isMoving());

		WorldPoint before = walk.currentTile();
		WorldPoint stepStart = walk.stepStartTile();

		// One profile switch: every key this plugin owns, back to back, all within
		// the same game tick.
		String[] keys = {
			"cullRadius", "crowdDensity",
			"cityAlKharid", "cityArdougne", "cityCatherby", "cityDraynor", "cityEdgeville",
			"cityFalador", "cityGrandExchange", "cityLumbridge", "cityVarrock",
		};
		assertEquals("one key per city plus the two dials", City.values().length + 2, keys.length);

		for (String key : keys)
		{
			plugin.onConfigChanged(configChanged(LivelyCitiesConfig.GROUP, key));
		}

		assertEquals("a profile switch must not move a citizen one tile, let alone "
				+ keys.length, before, walk.currentTile());
		assertEquals("nor end the step it was part-way through", stepStart, walk.stepStartTile());
		assertTrue("nor stop it walking", walk.isMoving());

		// And a real game tick still moves it, so the guard is not just "nothing
		// ever happens".
		client.advanceGameCycle(CLIENT_TICKS_PER_GAME_TICK);
		plugin.onGameTick(new GameTick());
		assertNotEquals("a game tick still steps the walk", before, walk.currentTile());
	}

	/**
	 * The other half of what {@code onConfigChanged} is for: unticking a city has
	 * to deactivate what is already spawned, not merely stop new spawns. Against
	 * the real scene, because the claim is about the client's registered-object
	 * list.
	 */
	@Test
	public void aConfigChangeStillDeactivatesTheCityItSwitchedOff()
	{
		FakeRegions regions = new FakeRegions();
		regions.file(REGION, regions.crowd(REGION, 3220, 3355, 4));

		FakeConfig config = new FakeConfig();
		EntityScene scene = new EntityScene(client, regions, config, config.overrides());
		LivelyCitiesPlugin plugin = plugin(scene);

		// Ticks, plural: a crowd of four arrives over two of them, because
		// RenderPolicy.MAX_MODEL_BUILDS_PER_PASS builds three models a pass. Driven
		// through the plugin's own handler rather than the scene's, since what this
		// test is about is the plugin's config path reaching a fully spawned crowd.
		VisibilityPasses.settle(() -> plugin.onGameTick(new GameTick()));
		assertEquals("region 12852 is Varrock, so the fixture should spawn",
			4, client.registeredCount());

		config.disableOnly(City.VARROCK);
		plugin.onConfigChanged(configChanged(LivelyCitiesConfig.GROUP, "cityVarrock"));

		assertEquals("unticking a city has to deactivate it now, not next tick",
			0, client.registeredCount());
	}

	// --- startup and teardown -----------------------------------------------

	/**
	 * Enabling the plugin mid-session is the common case in dev, and there is no
	 * state change coming to trigger the first pass. Enabling it at the login
	 * screen must not run one, because there is nothing to run it against.
	 */
	@Test
	public void startUpRunsAPassOnlyWhenAlreadyLoggedIn()
	{
		RecordingScene loggedIn = new RecordingScene();
		LivelyCitiesPlugin plugin = plugin(loggedIn);
		client.setGameState(GameState.LOGGED_IN);
		plugin.startUp();
		assertEquals(1, loggedIn.gameTicks);

		RecordingScene atLogin = new RecordingScene();
		LivelyCitiesPlugin second = plugin(atLogin);
		client.setGameState(GameState.LOGIN_SCREEN);
		second.startUp();
		assertEquals(0, atLogin.gameTicks);
	}

	@Test
	public void shutDownTearsTheSceneDown()
	{
		RecordingScene scene = new RecordingScene();
		LivelyCitiesPlugin plugin = plugin(scene);

		plugin.shutDown();

		assertEquals(1, scene.shutdowns);
	}

	/**
	 * The overhead-text overlay is registered on startup and gone after shutdown.
	 *
	 * <p>Same class of leak as a {@code RuneLiteObject} left active: an overlay left
	 * in the {@code OverlayManager} keeps drawing, and it would be drawing from a
	 * scene that has just been emptied underneath it. The whole reason
	 * {@link OverlayRegistry} is an interface is so this is an assertion rather than
	 * a reading of the source.
	 */
	@Test
	public void theOverlayIsRegisteredOnStartUpAndGoneAfterShutDown()
	{
		RecordingScene scene = new RecordingScene();
		client.setGameState(GameState.LOGIN_SCREEN);
		LivelyCitiesPlugin plugin = plugin(scene);

		plugin.startUp();
		assertEquals("exactly one overlay, and it is the chatter one",
			1, overlays.live().size());
		assertTrue(overlays.live().contains(plugin.chatterOverlay));

		plugin.shutDown();
		assertTrue("shutdown must leave nothing registered", overlays.live().isEmpty());
	}

	/**
	 * The sidebar button goes in on startup and is gone after shutdown.
	 *
	 * <p><b>The same promise as the overlay above, and it is the one this plugin has
	 * already had to fix once.</b> A {@code NavigationButton} left in the toolbar is a
	 * button that opens a panel whose plugin is gone — and {@code ClientToolbar} keys its
	 * navigation off the button <i>instance</i>, so a second one built on the way back in
	 * would leave the first sitting there for the rest of the session, unreachable by
	 * anything short of a client restart. That is the leaked-active-object bug wearing a
	 * different coat, which is why {@link SidePanel} is an interface: so this is an
	 * assertion rather than a reading of the source.
	 *
	 * <p>Removed <b>unconditionally</b>, not under whatever condition put it there. There
	 * is no setting behind the button today; the counts are asserted so that adding one
	 * cannot quietly make the teardown conditional too.
	 */
	@Test
	public void theSidebarButtonIsAddedOnStartUpAndGoneAfterShutDown()
	{
		RecordingScene scene = new RecordingScene();
		client.setGameState(GameState.LOGIN_SCREEN);
		LivelyCitiesPlugin plugin = plugin(scene);

		assertFalse("nothing is in the sidebar before startUp", sidebar.inSidebar());

		plugin.startUp();
		assertTrue("startUp has to put the button there", sidebar.inSidebar());
		assertEquals("exactly once", 1, sidebar.getShown());

		plugin.shutDown();
		assertFalse("shutdown must leave nothing in the sidebar", sidebar.inSidebar());
		assertEquals("exactly once", 1, sidebar.getHidden());
	}

	/**
	 * A disable/enable cycle leaves one button, not two.
	 *
	 * <p>A RuneLite plugin instance outlives a disable/enable, so this really is the same
	 * object doing it again — and the failure being guarded is the one where the teardown
	 * half is skipped or the button is rebuilt each time. Counting both calls is what
	 * separates "added, removed, added" from "added, added".
	 */
	@Test
	public void enablingTwiceLeavesOneButtonInTheSidebar()
	{
		RecordingScene scene = new RecordingScene();
		client.setGameState(GameState.LOGIN_SCREEN);
		LivelyCitiesPlugin plugin = plugin(scene);

		plugin.startUp();
		plugin.shutDown();
		plugin.startUp();

		assertTrue(sidebar.inSidebar());
		assertEquals(2, sidebar.getShown());
		assertEquals(1, sidebar.getHidden());

		plugin.shutDown();
		assertFalse("and the second teardown clears the second button",
			sidebar.inSidebar());
		assertEquals(2, sidebar.getHidden());
	}

	/**
	 * <b>And a second injection point cannot produce a second button.</b>
	 *
	 * <p>The two tests above are about the button this plugin was handed. The leak they
	 * cannot see is a <i>different</i> button: {@code provideSidePanel} builds one and
	 * comments that it is "built once here", but an unscoped {@code @Provides} method runs
	 * once per injection point, not once. Ask for a {@link SidePanel} anywhere else — a
	 * second subscriber, a status overlay, anything — and Guice builds a second button
	 * around a second panel. {@code startUp} adds whichever one it was given and the other
	 * sits in the toolbar for the rest of the session, while both halves of the teardown
	 * contract above go on passing, because each is telling the truth about its own.
	 *
	 * <p><b>Checked by reading the source, because there is nowhere else the fact lives.</b>
	 * Proving it by injection would need a {@code ClientToolbar} — private constructor, no
	 * mocking framework here, which is the whole reason {@link SidePanel} is an interface —
	 * and reading the annotation off the class would be reflection, which this project does
	 * not use. The scope is one word in one place; a scan for that word in that place is
	 * the honest amount of machinery. Order-independent, so putting {@code @Singleton}
	 * above {@code @Provides} is not a failure.
	 */
	@Test
	public void theSidePanelProviderIsScopedSoThereCanOnlyEverBeOneButton() throws IOException
	{
		String source = new String(Files.readAllBytes(new File(
			"src/main/java/com/matthewmariner/livelycities/LivelyCitiesPlugin.java").toPath()),
			StandardCharsets.UTF_8);
		assertTrue("the scan has to have found the file",
			source.contains("class LivelyCitiesPlugin"));

		int at = source.indexOf("SidePanel provideSidePanel(");
		assertTrue("no SidePanel provider in the plugin at all", at > 0);

		assertTrue("the SidePanel provider has to carry a scope, or \"the button is built "
				+ "once here\" is a property of there happening to be one injection point "
				+ "today rather than a fact about the code",
			source.substring(Math.max(0, at - 40), at).contains("@Singleton"));
	}

	// --- the panel is fed from the game tick, and only when open --------------

	/**
	 * <b>A closed panel costs one boolean read per tick and nothing else.</b>
	 *
	 * <p>Composing a model walks every cached wrapper and asks the client about each one.
	 * That is real per-tick work in a plugin whose entire performance argument is that it
	 * does almost none, and nobody is looking at a closed panel — which is the whole
	 * reason {@link SidePanel#isOpen()} is in the seam rather than being something the
	 * panel decides for itself after the work is done.
	 *
	 * <p>A hundred ticks rather than one, so "it skipped the first" cannot pass.
	 */
	@Test
	public void aClosedPanelIsNeverHandedAModel()
	{
		RecordingScene scene = new RecordingScene();
		LivelyCitiesPlugin plugin = plugin(scene);
		plugin.startUp();

		for (int tick = 0; tick < 100; tick++)
		{
			client.advanceGameCycle(CLIENT_TICKS_PER_GAME_TICK);
			plugin.onGameTick(new GameTick());
		}
		plugin.onConfigChanged(configChanged(LivelyCitiesConfig.GROUP, "cityVarrock"));

		assertEquals("the ticks still had to happen", 101, scene.gameTicks);
		assertTrue("but nothing may be composed for a panel nobody has open",
			sidebar.models().isEmpty());
	}

	/** An open one is fed every tick, because everything on it moves every tick. */
	@Test
	public void anOpenPanelIsHandedAModelEveryGameTick()
	{
		RecordingScene scene = new RecordingScene();
		LivelyCitiesPlugin plugin = plugin(scene);
		plugin.startUp();
		sidebar.opened();

		for (int tick = 0; tick < 5; tick++)
		{
			client.advanceGameCycle(CLIENT_TICKS_PER_GAME_TICK);
			plugin.onGameTick(new GameTick());
		}

		assertEquals("one model per tick", 5, sidebar.models().size());
		assertNotNull(sidebar.last());
		assertEquals("and it describes where the player is standing",
			City.VARROCK, sidebar.last().getHere());
	}

	/**
	 * A click has to be visible on the panel now, not 600ms from now.
	 *
	 * <p>Unticking a city already deactivates its citizens on the click rather than on
	 * the next tick — that is what {@code onConfigChanged} exists for — and a card that
	 * went on saying "on" until the next game tick would look like a click that did not
	 * land. The write and the redraw are the same event.
	 */
	@Test
	public void aSettingsChangeRedrawsTheOpenPanelAtOnce()
	{
		RecordingScene scene = new RecordingScene();
		LivelyCitiesPlugin plugin = plugin(scene);
		plugin.startUp();
		sidebar.opened();

		plugin.onConfigChanged(configChanged(LivelyCitiesConfig.GROUP, "cityVarrock"));

		assertEquals(1, sidebar.models().size());
	}

	/**
	 * Logging out redraws it too, and the redraw says there is no world.
	 *
	 * <p>A panel left showing the last true reading — "Varrock, 34 on screen" — over an
	 * empty world is worse than one showing nothing, because it is indistinguishable from
	 * a current reading.
	 */
	@Test
	public void losingTheSceneRedrawsTheOpenPanel()
	{
		RecordingScene scene = new RecordingScene();
		LivelyCitiesPlugin plugin = plugin(scene);
		plugin.startUp();
		sidebar.opened();

		client.setLocalPlayer(null);
		GameStateChanged event = new GameStateChanged();
		event.setGameState(GameState.LOGIN_SCREEN);
		plugin.onGameStateChanged(event);

		assertEquals(1, sidebar.models().size());
		assertFalse("and it has to say there is nobody anywhere",
			sidebar.last().isInWorld());
	}

	/**
	 * Nothing is composed after teardown, however open the panel was.
	 *
	 * <p>{@code hide()} tells the panel it has left the sidebar, so the flag cannot
	 * outlive the thing it describes — the same shape as {@code processedGameTick} being
	 * cleared for the developer-only reporter, and for the same reason.
	 */
	@Test
	public void nothingIsComposedForThePanelAfterShutDown()
	{
		RecordingScene scene = new RecordingScene();
		LivelyCitiesPlugin plugin = plugin(scene);
		plugin.startUp();
		sidebar.opened();
		plugin.onGameTick(new GameTick());
		assertEquals(1, sidebar.models().size());

		plugin.shutDown();
		plugin.onGameTick(new GameTick());
		plugin.onConfigChanged(configChanged(LivelyCitiesConfig.GROUP, "cityVarrock"));

		assertEquals("a torn-down plugin must not go on composing models",
			1, sidebar.models().size());
	}

	// --- what the panel writes ------------------------------------------------

	/**
	 * A city card writes the very key its own checkbox declares.
	 *
	 * <p>This is what makes the panel and the settings screen two views of one setting
	 * rather than two settings. All nine, because a loop over {@code City.values()} is
	 * exactly the shape that makes "every card writes the key of the city after it" a
	 * plausible defect, and only nine different keys can see it.
	 */
	@Test
	public void everyCityCardWritesTheKeyItsOwnCheckboxDeclares()
	{
		for (City city : City.values())
		{
			FakeConfig config = new FakeConfig();
			LivelyCitiesPlugin plugin = plugin(new RecordingScene(), config);

			plugin.setCityEnabled(city, false);

			assertEquals("one write", 1, config.writes().size());
			assertEquals(city.getConfigKey() + "=false", config.writes().get(0));
			assertEquals("and it is the value the config screen would store",
				"false", config.stored(city.getConfigKey()));
		}
	}

	/**
	 * A click that would change nothing writes nothing.
	 *
	 * <p>{@code ConfigManager} posts a {@code ConfigChanged} per write and this plugin
	 * answers every one with a full visibility pass over every entity in scope, so a
	 * no-op write is a no-op pass over a hundred citizens. The same rule
	 * {@code UuidSetting.add} keeps.
	 */
	@Test
	public void switchingACityToWhatItAlreadyIsWritesNothing()
	{
		FakeConfig config = new FakeConfig();
		LivelyCitiesPlugin plugin = plugin(new RecordingScene(), config);

		plugin.setCityEnabled(City.VARROCK, true);
		assertTrue("Varrock is already on", config.writes().isEmpty());

		config.disableOnly(City.VARROCK);
		plugin.setCityEnabled(City.VARROCK, false);
		assertTrue("and already off", config.writes().isEmpty());
	}

	/**
	 * The density chip writes the enum's own name, which is what {@code ConfigManager}
	 * stores an enum as and reads one back with — so it round-trips through the settings
	 * screen's dropdown rather than sitting beside it.
	 */
	@Test
	public void theDensityChipWritesTheValueTheConfigScreenReads()
	{
		for (CrowdDensity density : CrowdDensity.values())
		{
			FakeConfig config = new FakeConfig().setCrowdDensity(CrowdDensity.FULL);
			LivelyCitiesPlugin plugin = plugin(new RecordingScene(), config);

			plugin.setCrowdDensity(density);

			if (density == CrowdDensity.FULL)
			{
				assertTrue("the one already selected writes nothing",
					config.writes().isEmpty());
				continue;
			}

			assertEquals(1, config.writes().size());
			assertEquals(LivelyCitiesConfig.KEY_CROWD_DENSITY + "=" + density.name(),
				config.writes().get(0));
			assertEquals(density,
				CrowdDensity.valueOf(config.stored(LivelyCitiesConfig.KEY_CROWD_DENSITY)));
		}
	}

	/**
	 * Unhiding one citizen from the panel restores that one and nobody else.
	 *
	 * <p><b>This is the repair the panel exists for.</b> The shipped undo is a checkbox
	 * that unhides everybody, so taking back one decision cost every other one — and the
	 * assertion that matters is not that the citizen came back but that the other two
	 * did not.
	 */
	@Test
	public void unhidingOneCitizenFromThePanelLeavesTheOthersHidden()
	{
		FakeConfig config = new FakeConfig();
		LivelyCitiesPlugin plugin = plugin(new RecordingScene(), config);

		FakeRegions regions = new FakeRegions();
		EntityDefinition first = regions.citizen(REGION, 3225, 3360, 0);
		EntityDefinition second = regions.citizen(REGION, 3226, 3360, 0);
		EntityDefinition third = regions.citizen(REGION, 3227, 3360, 0);
		config.overrides().hide(first);
		config.overrides().hide(second);
		config.overrides().hide(third);
		assertEquals(3, config.overrides().hiddenUuids().size());

		plugin.unhide(second.getUuid());

		assertEquals("one back, two still hidden", 2, config.overrides().hiddenUuids().size());
		assertTrue(config.overrides().hiddenUuids().contains(first.getUuid()));
		assertFalse(config.overrides().hiddenUuids().contains(second.getUuid()));
		assertTrue(config.overrides().hiddenUuids().contains(third.getUuid()));
	}

	/** And unmuting is the mute list's own, not the hide list's. */
	@Test
	public void unmutingOneCitizenFromThePanelLeavesTheHideListAlone()
	{
		FakeConfig config = new FakeConfig();
		LivelyCitiesPlugin plugin = plugin(new RecordingScene(), config);

		FakeRegions regions = new FakeRegions();
		EntityDefinition hidden = regions.citizen(REGION, 3225, 3360, 0);
		EntityDefinition muted = regions.talker(REGION, 3226, 3360, "Busy today.");
		config.overrides().hide(hidden);
		config.overrides().mute(muted);

		plugin.unmute(muted.getUuid());

		assertTrue("the mute goes", config.overrides().mutedUuids().isEmpty());
		assertEquals("the hide does not", 1, config.overrides().hiddenUuids().size());
	}

	/** Restoring somebody who was never overridden writes nothing at all. */
	@Test
	public void restoringSomebodyWhoWasNeverOverriddenWritesNothing()
	{
		FakeConfig config = new FakeConfig();
		LivelyCitiesPlugin plugin = plugin(new RecordingScene(), config);

		plugin.unhide(UUID.randomUUID());
		plugin.unmute(UUID.randomUUID());

		assertTrue(config.writes().isEmpty());
	}

	// --- the interaction handlers -------------------------------------------

	/**
	 * Both menu events reach {@link CitizenMenu}, and neither is deferred.
	 *
	 * <p>Not deferred is the load-bearing half. {@code Menu.createMenuEntry} asserts
	 * {@code isClientThread()} and the menu has already been built by the time
	 * {@code MenuOpened} is posted, so a handler that queued its work through
	 * {@link ClientThread} would add its entries to the next menu or to none.
	 * {@link InlineClientThread} runs inline, so the way this test proves the
	 * handlers are synchronous is by counting: the {@code ClientThread} must not have
	 * been used at all.
	 */
	@Test
	public void bothMenuEventsAreHandledSynchronously()
	{
		FakeConfig config = new FakeConfig();
		RecordingScene scene = new RecordingScene();
		LivelyCitiesPlugin plugin = plugin(scene, config);
		CountingMenu counting = new CountingMenu(client, scene, config.overrides());
		plugin.citizenMenu = counting;

		int invocationsBefore = clientThread.invocations;

		plugin.onMenuOpened(new MenuOpened());
		plugin.onMenuOptionClicked(new MenuOptionClicked(new FakeMenuEntry()));

		assertEquals(1, counting.opened);
		assertEquals(1, counting.clicked);
		assertEquals("neither handler may go through the ClientThread",
			invocationsBefore, clientThread.invocations);
	}

	/**
	 * Every state that invalidates the scene also drops the menu's remembered
	 * target.
	 *
	 * <p>The target is a {@link LivelyEntity}, and every one of those holds a lit
	 * {@code Model}. Left set, it keeps a wrapper the scene has already forgotten
	 * alive through this one field — which is the leak the teardown contract exists
	 * to prevent, arriving by a different door.
	 */
	@Test
	public void everyInvalidatingStateAlsoForgetsTheMenusTarget()
	{
		for (GameState state : MUST_INVALIDATE)
		{
			FakeConfig config = new FakeConfig();
			RecordingScene scene = new RecordingScene();
			LivelyCitiesPlugin plugin = plugin(scene, config);
			CountingMenu counting = new CountingMenu(client, scene, config.overrides());
			plugin.citizenMenu = counting;

			GameStateChanged event = new GameStateChanged();
			event.setGameState(state);
			plugin.onGameStateChanged(event);

			assertEquals(state + " must drop the remembered right-click target",
				1, counting.forgotten);
		}
	}

	/** And shutdown does the same, for the same reason. */
	@Test
	public void shutDownForgetsTheMenusTarget()
	{
		FakeConfig config = new FakeConfig();
		RecordingScene scene = new RecordingScene();
		LivelyCitiesPlugin plugin = plugin(scene, config);
		CountingMenu counting = new CountingMenu(client, scene, config.overrides());
		plugin.citizenMenu = counting;

		plugin.shutDown();

		assertEquals(1, counting.forgotten);
	}

	// --- the two self-unticking reset buttons -------------------------------

	/**
	 * "Unhide all" clears the list, unticks itself, and does not run a second
	 * visibility pass on top of the one its own write will cause.
	 *
	 * <p>1.12.36 has no {@code Button} config type, so a control meaning "do this
	 * now" has to be a boolean that is turned back off once it has been acted on.
	 */
	@Test
	public void unhideAllClearsTheListAndUnticksItself()
	{
		FakeConfig config = new FakeConfig();
		RecordingScene scene = new RecordingScene();
		LivelyCitiesPlugin plugin = plugin(scene, config);

		EntityDefinition someone = new FakeRegions().citizen(REGION, 3225, 3360, 0);
		config.overrides().hide(someone);
		assertEquals(1, config.overrides().hiddenUuids().size());

		int writesBefore = config.writes().size();
		plugin.onConfigChanged(configChanged(
			LivelyCitiesConfig.GROUP, CitizenOverrides.UNHIDE_ALL_KEY, "true"));

		assertTrue("the list has to be empty", config.overrides().hiddenUuids().isEmpty());
		assertEquals("two writes: the cleared list, and the button unticking itself",
			writesBefore + 2, config.writes().size());
		assertEquals(CitizenOverrides.UNHIDE_ALL_KEY + "=null",
			config.writes().get(config.writes().size() - 1));
		assertEquals("the press itself must not also run a visibility pass — its own "
				+ "write posts a ConfigChanged that will",
			0, scene.settingsChanges);
	}

	/**
	 * The self-unset echo is not a second press.
	 *
	 * <p>This is what makes the button terminate: unsetting the key posts one more
	 * {@code ConfigChanged} for the same key, and if that were treated as a press the
	 * plugin would write again forever.
	 */
	@Test
	public void theButtonsOwnEchoIsNotAnotherPress()
	{
		FakeConfig config = new FakeConfig();
		RecordingScene scene = new RecordingScene();
		LivelyCitiesPlugin plugin = plugin(scene, config);

		int writesBefore = config.writes().size();
		plugin.onConfigChanged(configChanged(
			LivelyCitiesConfig.GROUP, CitizenOverrides.UNHIDE_ALL_KEY, null));

		assertEquals("an untick writes nothing", writesBefore, config.writes().size());
		assertEquals("and falls through to an ordinary visibility pass", 1, scene.settingsChanges);
	}

	/** The mute list has its own button, and it is not the hide one. */
	@Test
	public void unmuteAllClearsTheMuteListAndNotTheHideList()
	{
		FakeConfig config = new FakeConfig();
		RecordingScene scene = new RecordingScene();
		LivelyCitiesPlugin plugin = plugin(scene, config);

		FakeRegions regions = new FakeRegions();
		EntityDefinition hidden = regions.citizen(REGION, 3225, 3360, 0);
		EntityDefinition muted = regions.talker(REGION, 3226, 3360, "Busy today.");
		config.overrides().hide(hidden);
		config.overrides().mute(muted);

		plugin.onConfigChanged(configChanged(
			LivelyCitiesConfig.GROUP, CitizenOverrides.UNMUTE_ALL_KEY, "true"));

		assertTrue("the mute list goes", config.overrides().mutedUuids().isEmpty());
		assertEquals("the hide list does not", 1, config.overrides().hiddenUuids().size());
	}

	/** A press on a key in another plugin's group is not ours to act on. */
	@Test
	public void anotherPluginsResetKeyIsIgnored()
	{
		FakeConfig config = new FakeConfig();
		RecordingScene scene = new RecordingScene();
		LivelyCitiesPlugin plugin = plugin(scene, config);

		EntityDefinition someone = new FakeRegions().citizen(REGION, 3225, 3360, 0);
		config.overrides().hide(someone);

		plugin.onConfigChanged(configChanged(
			"someotherplugin", CitizenOverrides.UNHIDE_ALL_KEY, "true"));

		assertEquals("another group's key must not clear our list",
			1, config.overrides().hiddenUuids().size());
		assertEquals("nor run our visibility pass", 0, scene.settingsChanges);
	}

	/**
	 * {@code LOGGED_IN} fires before the world is usable, so both halves of
	 * "where is the player" can be absent. Neither may reach the scene, and
	 * neither may restart the interpolation clock — a tick that did no work must
	 * not claim the step started now.
	 */
	@Test
	public void aTickWithNoPlayerOrNoSceneDoesNothingAndDoesNotTouchTheClock()
	{
		RecordingScene scene = new RecordingScene();
		LivelyCitiesPlugin plugin = plugin(scene);

		client.setGameCycle(1000);
		plugin.onGameTick(new GameTick());
		assertEquals(1, scene.gameTicks);

		// No local player at all.
		client.setLocalPlayer(null);
		client.setGameCycle(1015);
		plugin.onGameTick(new GameTick());

		// A player with no tile yet.
		client.setLocalPlayer(player);
		player.setWorldLocation(null);
		plugin.onGameTick(new GameTick());

		// A player, a tile, but no scene.
		player.setWorldLocation(PLAYER);
		client.setTopLevelWorldView(null);
		plugin.onGameTick(new GameTick());

		assertEquals("none of the three may reach the scene", 1, scene.gameTicks);
		assertEquals(1, scene.syncRegions);

		client.setTopLevelWorldView(view);
		assertEquals("and the clock still belongs to the tick that did the work",
			0.5f, fractionAt(plugin, scene, 1015), 1e-6f);
	}

	// --- helpers -------------------------------------------------------------

	private LivelyCitiesPlugin plugin(EntityScene scene)
	{
		return plugin(scene, new FakeConfig());
	}

	/**
	 * The plugin with every injected collaborator wired to a fake.
	 *
	 * <p>{@code overlayRegistry} and {@code configWriter} exist as interfaces
	 * precisely so this method can exist: {@code OverlayManager} and
	 * {@code ConfigManager} both have private constructors in 1.12.36, so a plugin
	 * holding either directly is a plugin no test can construct — and this file
	 * exists because that is exactly what happened once already.
	 */
	private LivelyCitiesPlugin plugin(EntityScene scene, FakeConfig config)
	{
		LivelyCitiesPlugin plugin = new LivelyCitiesPlugin();
		plugin.client = client;
		plugin.clientThread = clientThread;
		plugin.scene = scene;
		plugin.overlayRegistry = overlays;
		plugin.chatterOverlay = new ChatterOverlay(plugin, client, scene, config);
		plugin.citizenMenu = new CitizenMenu(client, scene, config.overrides());
		plugin.overrides = config.overrides();
		plugin.configWriter = config.writer();
		plugin.config = config;
		plugin.sidePanel = sidebar;

		// A directory over a dataset with no files in it: every city reads zero citizens
		// and no uuid resolves. That is the right default for a lifecycle test — the
		// numbers are CitizenDirectoryTest's subject, and a fixture that loaded the real
		// 27 files would make every test in this class pay for a parse it never looks at.
		plugin.directory = new CitizenDirectory(new FakeRegions());

		// A stopwatch that measures nothing. Off is what a shipped client gets, so it
		// is what every test here should get; the measuring itself is FrameTimingsTest's.
		plugin.frameTimings = FrameTimings.off();
		return plugin;
	}

	/**
	 * Draws one frame at a given game cycle and hands back the fraction the plugin
	 * gave the scene.
	 *
	 * <p>{@code tickFraction()} is private, and deliberately: the fraction is not
	 * a fact about the plugin, it is the argument it passes. Asking for it through
	 * the frame handler is asking the question the citizens ask.
	 */
	private float fractionAt(LivelyCitiesPlugin plugin, RecordingScene scene, int gameCycle)
	{
		client.setGameCycle(gameCycle);
		int before = scene.fractions.size();
		plugin.onBeforeRender(new BeforeRender());
		assertEquals("the frame handler has to have run", before + 1, scene.fractions.size());
		return scene.fractions.get(scene.fractions.size() - 1);
	}

	private static ConfigChanged configChanged(String group, String key)
	{
		ConfigChanged event = new ConfigChanged();
		event.setGroup(group);
		event.setKey(key);
		return event;
	}

	private static ConfigChanged configChanged(String group, String key, @Nullable String newValue)
	{
		ConfigChanged event = configChanged(group, key);
		event.setNewValue(newValue);
		return event;
	}

	/**
	 * {@link CitizenMenu} with its three entry points counted and none of them doing
	 * anything.
	 *
	 * <p>Same shape as {@link RecordingScene}, and for the same reason: the question
	 * here is whether the plugin routes the events, not what the menu does with them
	 * — {@code CitizenMenuTest} answers that against the real one.
	 */
	private static final class CountingMenu extends CitizenMenu
	{
		private int opened;
		private int clicked;
		private int forgotten;

		private CountingMenu(net.runelite.api.Client client, EntityScene scene, CitizenOverrides overrides)
		{
			super(client, scene, overrides);
		}

		@Override
		void onMenuOpened(MenuOpened event)
		{
			assertNotNull(event);
			opened++;
		}

		@Override
		void onMenuOptionClicked(MenuOptionClicked event)
		{
			assertNotNull(event);
			clicked++;
		}

		@Override
		void forget()
		{
			forgotten++;
		}
	}

	/**
	 * The overlay manager, as two lists.
	 *
	 * <p>Registered rather than counted, so "shutDown removes the same overlay
	 * startUp added" is answerable — an overlay left registered draws forever, which
	 * is the overlay-shaped version of the leaked-active-object bug this plugin
	 * already guards against.
	 */
	private static final class RecordingOverlays implements OverlayRegistry
	{
		private final List<Overlay> added = new ArrayList<>();
		private final List<Overlay> removed = new ArrayList<>();

		@Override
		public void add(Overlay overlay)
		{
			assertNotNull("the plugin must never register a null overlay", overlay);
			added.add(overlay);
		}

		@Override
		public void remove(Overlay overlay)
		{
			removed.add(overlay);
		}

		/** @return what is registered now: everything added and not removed */
		List<Overlay> live()
		{
			List<Overlay> out = new ArrayList<>(added);
			out.removeAll(removed);
			return out;
		}
	}

	/**
	 * The real {@link ClientThread}, minus the thread. {@code invoke(Runnable)} runs
	 * inline on the real one whenever the caller is already on the client thread, which
	 * is every path this plugin uses it for.
	 *
	 * <p><b>The {@code BooleanSupplier} override is the guard, and it is here rather than
	 * beside any one call site because it covers all four at once.</b>
	 * {@code ClientThread} overloads {@code invoke} on {@link Runnable} and
	 * {@link BooleanSupplier}, and the two mean very different things: a
	 * {@code BooleanSupplier} is put on a queue and <b>re-run every tick until it returns
	 * true</b>. Which overload a call site binds to is decided by the return type of the
	 * method it wraps, so a signature change somewhere else entirely can move it —
	 * silently, because both overloads compile. JLS 15.12.2.5 makes the value-returning
	 * function type the more specific one, so a lambda that becomes value-compatible does
	 * not become ambiguous; {@code BooleanSupplier} simply wins.
	 *
	 * <p>This plugin has four {@code invoke} call sites — the first pass in
	 * {@code startUp}, the teardown in {@code shutDown}, the invalidate in
	 * {@code onGameStateChanged} and the visibility pass in {@code onConfigChanged} — and
	 * every one of them is driven by a test in this class. None may ever be a repeating
	 * task: a teardown that runs forever, or a scene invalidate that never finishes
	 * invalidating, is not a thing a user could diagnose. So reaching this overload at
	 * all fails the test that reached it.
	 */
	private static final class InlineClientThread extends ClientThread
	{
		private int invocations;

		@Override
		public void invoke(Runnable runnable)
		{
			invocations++;
			runnable.run();
		}

		@Override
		public void invoke(BooleanSupplier supplier)
		{
			fail("this plugin has no repeating client-thread task: invoke(BooleanSupplier) "
				+ "re-queues its argument every tick until it returns true. Reaching it means "
				+ "a call site bound to the wrong overload, which happens on its own when the "
				+ "method behind it grows a boolean return. Use a block lambda whose body is a "
				+ "bare statement — that has no value, so only Runnable fits.");
		}
	}

	/**
	 * The real {@link EntityScene} with every entry point counted and none of them
	 * doing anything. Nothing is stubbed out that the plugin does not call, so a
	 * new call from the plugin lands on the real implementation and fails on the
	 * null collaborators rather than being silently swallowed.
	 */
	private static final class RecordingScene extends EntityScene
	{
		private final List<Float> fractions = new ArrayList<>();
		private final List<String> invalidations = new ArrayList<>();

		private int syncRegions;
		private int gameTicks;
		private int settingsChanges;
		private int frames;
		private int shutdowns;

		RecordingScene()
		{
			super(null, null, null, null);
		}

		@Override
		boolean syncRegions(WorldView worldView)
		{
			assertNotNull("the plugin must never hand the scene a null world view", worldView);
			syncRegions++;
			return false;
		}

		@Override
		void onGameTick(@Nullable WorldPoint playerLocation, WorldView worldView)
		{
			assertNotNull("the plugin must never hand the scene a null player tile", playerLocation);
			gameTicks++;
		}

		@Override
		void onSettingsChanged(@Nullable WorldPoint playerLocation, WorldView worldView)
		{
			assertNotNull("the plugin must never hand the scene a null player tile", playerLocation);
			settingsChanges++;
		}

		@Override
		void onFrame(WorldView worldView, float fraction)
		{
			assertNotNull(worldView);
			frames++;
			fractions.add(fraction);
		}

		@Override
		int invalidate(String reason)
		{
			invalidations.add(reason);
			return 0;
		}

		@Override
		int shutdown()
		{
			shutdowns++;
			return 0;
		}
	}
}
