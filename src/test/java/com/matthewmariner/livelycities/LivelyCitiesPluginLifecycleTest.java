package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.runelite.api.Constants;
import net.runelite.api.GameState;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.ConfigChanged;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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

	@Before
	public void setUp()
	{
		client = new FakeClient();
		view = FakeWorldView.around(PLAYER, REGION);
		player = new FakePlayer(PLAYER);
		clientThread = new InlineClientThread();

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

		EntityScene scene = new EntityScene(client, regions, new FakeConfig());
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
			"cityAlKharid", "cityArdougne", "cityBarrows", "cityCanifis", "cityCastleWars",
			"cityCatherby", "cityDraynor", "cityEdgeville", "cityFalador", "cityFarmingGuild",
			"cityGrandExchange", "cityLumberYard", "cityLumbridge", "cityMotherlodeMine",
			"cityMusaPoint", "cityOttosGrotto", "cityPaterdomus", "cityPiscatoris",
			"cityRangingGuild", "cityRimmington", "cityCamelot", "cityTaverley",
			"cityTrollheim", "cityVarrock",
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
		EntityScene scene = new EntityScene(client, regions, config);
		LivelyCitiesPlugin plugin = plugin(scene);

		plugin.onGameTick(new GameTick());
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
		LivelyCitiesPlugin plugin = new LivelyCitiesPlugin();
		plugin.client = client;
		plugin.clientThread = clientThread;
		plugin.scene = scene;
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

	/**
	 * The real {@link ClientThread}, minus the thread. {@code invoke(Runnable)}
	 * runs inline on the real one whenever the caller is already on the client
	 * thread, which is every path the plugin uses it for.
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
			super(null, null, null);
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
