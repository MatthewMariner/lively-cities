package com.matthewmariner.livelycities;

import com.google.inject.Provides;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/**
 * Cosmetic townsfolk and scenery, spawned from the vendored region dataset.
 *
 * <p>Lifecycle, and why it is shaped this way:
 *
 * <ul>
 *   <li><b>Work happens on {@link GameTick}, not on {@code LOGGED_IN}.</b>
 *       {@code LOGGED_IN} fires before the world is usable —
 *       {@code getLocalPlayer()} is still null at that point — so the state
 *       handler only invalidates, and the tick handler does the work behind a
 *       null check.</li>
 *   <li><b>The scene is re-checked every tick</b> by comparing the loaded map
 *       region ids. That is an array compare, not a scene scan, and it means a
 *       region border crossing cannot be missed however the game state
 *       transitions happen to be ordered.</li>
 *   <li><b>{@link BeforeRender} is the second clock.</b> It is posted from the
 *       client's own frame loop, once per rendered frame, and it is where the
 *       wanderers' positions are interpolated between tiles. Nothing else
 *       happens per frame: the animations are advanced by the client, which calls
 *       {@code RuneLiteObject.tick(ticksSinceLastFrame)} on every registered
 *       object as it draws it.</li>
 *   <li><b>Everything reaches {@link EntityScene} on the client thread.</b>
 *       {@code @Subscribe} runs on the posting thread, so the handlers wrap their
 *       calls in {@link ClientThread#invoke} — which runs inline when we are
 *       already on the client thread, and defers when we are not. The frame
 *       handler is the exception: it is already on the client thread by
 *       construction, and queueing a task per frame would be a queue that never
 *       drains.</li>
 * </ul>
 */
@Slf4j
@PluginDescriptor(
	name = "Lively Cities",
	description = "Cosmetic townsfolk and scenery that make cities feel lived-in",
	tags = {"npc", "citizens", "cosmetic", "immersion", "scenery"}
)
public class LivelyCitiesPlugin extends Plugin
{
	/**
	 * Client ticks in one game tick: 600ms / 20ms.
	 *
	 * <p>The denominator for the interpolation fraction. Taken from the client's
	 * own constants rather than written as 30, because both halves are named
	 * there and a divisor that is silently wrong shows up as citizens that finish
	 * their step early and then stand still, which is hard to attribute.
	 */
	private static final int CLIENT_TICKS_PER_GAME_TICK =
		Constants.GAME_TICK_LENGTH / Constants.CLIENT_TICK_LENGTH;

	// Package-private rather than private so the tests in this package can wire
	// their own fakes in. Guice injects a package-private field exactly as it
	// injects a private one, so this costs the runtime nothing — and the
	// alternative is either a mocking framework (none on the classpath) or
	// reflection (banned).
	@Inject
	Client client;

	@Inject
	ClientThread clientThread;

	@Inject
	EntityScene scene;

	/**
	 * {@code getGameCycle()} at the last game tick we processed.
	 *
	 * <p>The frame handler needs to know how far through the current game tick it
	 * is. {@code getGameCycle()} increments every 20ms — the client's own clock,
	 * which stops when the client stops — so the difference divided by
	 * {@link #CLIENT_TICKS_PER_GAME_TICK} is that fraction. Wall-clock time would
	 * also work until the first time the client is paused or the machine sleeps.
	 *
	 * <p><b>Written only by {@link #tick()}.</b> It is the interpolation clock's
	 * origin, so anything else that reset it would restart every citizen's step
	 * from wherever it had got to — which is what {@link #onConfigChanged} used to
	 * do.
	 */
	private int cycleAtLastTick;

	@Override
	protected void startUp()
	{
		log.info("Lively Cities starting (cull {} tiles, max {}, cap {} objects)",
			RenderPolicy.DEFAULT_CULL_RADIUS, RenderPolicy.MAX_CULL_RADIUS, RenderPolicy.MAX_ACTIVE_OBJECTS);

		// Enabling the plugin mid-session is the common case in dev. Nothing to
		// do here beyond letting the next game tick find the scene — but if we
		// are already logged in, do not make the user wait for a state change
		// that will never come.
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invoke(this::tick);
		}
	}

	@Override
	protected void shutDown()
	{
		// Not blocking: invoke() runs inline on the client thread and defers
		// otherwise. The count lands in the log either way.
		clientThread.invoke(scene::shutdown);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		final GameState state = event.getGameState();

		switch (state)
		{
			// The scene is being replaced or is gone. Every LocalPoint we hold
			// is about to mean something else, so nothing may stay active.
			case LOADING:
			case HOPPING:
			case LOGGING_IN:
			case LOGIN_SCREEN:
			case LOGIN_SCREEN_AUTHENTICATOR:
			case CONNECTION_LOST:
				clientThread.invoke(() -> scene.invalidate(state.name()));
				break;

			// Deliberately no work on LOGGED_IN: the local player may still be
			// null here. onGameTick picks it up.
			default:
				break;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		tick();
	}

	/**
	 * Runs a <i>visibility</i> pass as soon as a setting changes, rather than
	 * waiting for the next game tick.
	 *
	 * <p>This is what makes unticking a city <i>deactivate</i> its citizens
	 * instead of merely stopping new ones spawning: the pass has one rule — what
	 * is not wanted is despawned — so re-running it with the new settings is the
	 * whole implementation. A tick is only 600ms away, but a toggle that visibly
	 * lags the click reads as a toggle that did not work.
	 *
	 * <p><b>A visibility pass and not a game tick.</b> This used to call
	 * {@link #tick()}, which also steps every walker one tile and restarts the
	 * interpolation clock. RuneLite posts one {@code ConfigChanged} per key, so
	 * switching profiles posts one per item this plugin owns — about two dozen —
	 * and the crowd teleported two dozen tiles for a change the user made to a
	 * checkbox. Movement comes from the game's clock; a setting is not a tick.
	 */
	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!LivelyCitiesConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		log.debug("config changed ({}), re-running the visibility pass", event.getKey());
		clientThread.invoke(this::refreshVisibility);
	}

	/**
	 * The per-frame hook. Posted by the client from its draw loop, so this is
	 * already on the client thread.
	 */
	@Subscribe
	public void onBeforeRender(BeforeRender event)
	{
		final WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return;
		}

		scene.onFrame(worldView, tickFraction());
	}

	/**
	 * The one place that touches the scene per game tick, and the only writer of
	 * {@link #cycleAtLastTick}. Runs on the client thread — GameTick is posted
	 * from it, and {@link #startUp()} routes through {@link ClientThread}.
	 */
	private void tick()
	{
		final WorldPoint playerLocation = playerLocation();
		final WorldView worldView = client.getTopLevelWorldView();
		if (playerLocation == null || worldView == null)
		{
			return;
		}

		// Restarting the interpolation clock is a game-tick-only act: the frame
		// handler measures from here, so writing it anywhere else would drop every
		// citizen back to the start of its step.
		cycleAtLastTick = client.getGameCycle();

		scene.syncRegions(worldView);
		scene.onGameTick(playerLocation, worldView);
	}

	/**
	 * The settings path: re-decide who is visible, step nobody, and leave the
	 * interpolation clock alone.
	 *
	 * <p>{@code syncRegions} is still called. It is an array compare that returns
	 * immediately when the scene has not moved, and calling it means this entry
	 * point works even when it is the first thing to run after a login — a config
	 * change can arrive before the first {@link GameTick}, and a visibility pass
	 * over an empty scope would silently do nothing.
	 */
	private void refreshVisibility()
	{
		final WorldPoint playerLocation = playerLocation();
		final WorldView worldView = client.getTopLevelWorldView();
		if (playerLocation == null || worldView == null)
		{
			return;
		}

		scene.syncRegions(worldView);
		scene.onSettingsChanged(playerLocation, worldView);
	}

	/**
	 * @return the local player's tile, or {@code null} if there is not one yet.
	 * {@code getLocalPlayer()} is null on {@code LOGGED_IN} and its
	 * {@code getWorldLocation()} can be null for a tick after that, which is why
	 * both halves are checked rather than just the first.
	 */
	@Nullable
	private WorldPoint playerLocation()
	{
		final Player local = client.getLocalPlayer();
		return local == null ? null : local.getWorldLocation();
	}

	/**
	 * @return how far through the current game tick this frame is, clamped to
	 * 0..1. Clamped rather than allowed to run past 1 because a dropped or
	 * delayed game tick would otherwise send citizens sliding past the tile they
	 * were walking to, and clamped at 0 because a frame drawn before the tick that
	 * would explain it must not drag one back past the tile it came from.
	 *
	 * <p><b>The subtraction stays in {@code int} on purpose.</b>
	 * {@code getGameCycle()} is an {@code int} counting 20ms ticks, so it wraps
	 * after about 497 days of client uptime — and two's-complement {@code int}
	 * subtraction wraps with it, which makes the elapsed count come out right
	 * across the seam. Promoting either side to {@code long} would turn that one
	 * frame into a huge negative elapsed, trip the {@code <= 0} guard, and freeze
	 * every citizen mid-step until the next game tick.
	 * {@code LivelyCitiesPluginLifecycleTest} pins both.
	 */
	private float tickFraction()
	{
		int elapsed = client.getGameCycle() - cycleAtLastTick;
		if (elapsed <= 0)
		{
			return 0f;
		}
		if (elapsed >= CLIENT_TICKS_PER_GAME_TICK)
		{
			return 1f;
		}
		return elapsed / (float) CLIENT_TICKS_PER_GAME_TICK;
	}

	@Provides
	LivelyCitiesConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(LivelyCitiesConfig.class);
	}
}
