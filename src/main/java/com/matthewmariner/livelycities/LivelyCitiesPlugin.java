package com.matthewmariner.livelycities;

import com.google.inject.Provides;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
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
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;

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
 *   <li><b>The two interaction handlers are not deferred.</b>
 *       {@code MenuOpened} and {@code MenuOptionClicked} are posted from the
 *       client's own menu code and have to be answered inside it — see
 *       {@link #onMenuOpened} and {@link CitizenMenu}.</li>
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

	/**
	 * Set on {@code ./gradlew auditCacheIds} (never on {@code ./gradlew run} or
	 * {@code ./gradlew test}) to ask {@link #runDeveloperCacheAudit()} to run
	 * once at startup. Read with {@link Boolean#getBoolean}, a plain JVM system
	 * property — no new argument parsing, no reflection.
	 *
	 * <p>Checked in addition to {@link #developerMode}, not instead of it: this
	 * is what stops the one-off validation pass from running on every ordinary
	 * {@code ./gradlew run} session — it is a real client-cache walk over every
	 * id the dataset references, which is exactly the kind of work a developer
	 * wants on demand, not on every restart.
	 */
	static final String CACHE_AUDIT_SYSTEM_PROPERTY = "livelycities.validateCacheIds";

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

	@Inject
	RegionDataLoader regionDataLoader;

	@Inject
	OverlayRegistry overlayRegistry;

	@Inject
	ChatterOverlay chatterOverlay;

	@Inject
	CitizenMenu citizenMenu;

	@Inject
	CitizenOverrides overrides;

	@Inject
	ConfigWriter configWriter;

	/**
	 * The stopwatch, off for every ordinary launch — see {@link FrameTimings}.
	 *
	 * <p>Injected here as well as into {@link EntityScene} because the two halves live
	 * in different places: the scene owns the measuring, and this class owns the
	 * cadence and the file, which is where {@code RuneLite.RUNELITE_DIR} and the
	 * background thread are. Guice hands both the same {@code @Singleton}.
	 */
	@Inject
	FrameTimings frameTimings;

	/**
	 * True when the client was launched with {@code --developer-mode} — the same
	 * {@code RuneLiteProperties} launcher flag {@code ./gradlew run} already
	 * passes for this project (see {@code build.gradle}). Bound by
	 * {@code RuneLiteModule} for every launch, developer or not, so this is
	 * always resolvable and defaults to {@code false} when a test constructs the
	 * plugin directly instead of through Guice.
	 *
	 * <p>Gates {@link #runDeveloperCacheAudit()} the same way the plan's L4.3
	 * authoring mode is gated: a dev-only capability compiled into the shipped
	 * jar is fine, a dev-only capability that a normal user can trigger is not.
	 */
	@Inject
	@Named("developerMode")
	boolean developerMode;

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

		overlayRegistry.add(chatterOverlay);

		// Enabling the plugin mid-session is the common case in dev. Nothing to
		// do here beyond letting the next game tick find the scene — but if we
		// are already logged in, do not make the user wait for a state change
		// that will never come.
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invoke(this::tick);
		}

		// Both flags, deliberately: developerMode alone is true for every
		// ordinary `./gradlew run`, and the cache id audit is a deliberate,
		// on-demand action, not something a normal dev session should pay for.
		if (developerMode && Boolean.getBoolean(CACHE_AUDIT_SYSTEM_PROPERTY))
		{
			clientThread.invoke(this::runDeveloperCacheAudit);
		}

		if (frameTimings.isEnabled())
		{
			// Said out loud, because a measurement running silently is a measurement
			// somebody forgets is running — and because "is it actually on?" is the
			// first question when the report file turns out to be empty.
			log.info("Lively Cities: frame timings are on ({} is set) — a summary every {} game "
					+ "tick(s), and a report in ~/.runelite/lively-cities/{}",
				FrameTimings.SYSTEM_PROPERTY, FrameTimings.REPORT_INTERVAL_TICKS,
				FrameTimings.REPORT_FILE_NAME);
		}
	}

	/**
	 * The cache-backed half of the durability tooling: walks every distinct
	 * model id, merged-object id and animation id
	 * {@code RegionData/*.json} references and asks the live client whether each
	 * one still resolves — see {@code CacheIdAudit}'s javadoc for why this
	 * cannot run in the normal test suite.
	 *
	 * <p>Runs once, from the client thread (required — every model/animation
	 * load in this plugin goes through the client), and only ever on demand: see
	 * {@link #CACHE_AUDIT_SYSTEM_PROPERTY}.
	 *
	 * <p>Package-private and non-final so a test can override it the same way
	 * {@code LivelyCitiesPluginLifecycleTest} overrides {@code EntityScene}'s
	 * entry points — the developer-mode gate above is what is under test there,
	 * not the audit's own logic, which {@code CacheIdAuditTest} covers directly
	 * against {@code FakeClient}.
	 */
	void runDeveloperCacheAudit()
	{
		log.info("Lively Cities: {} is set, running the cache id audit", CACHE_AUDIT_SYSTEM_PROPERTY);

		CacheIdAudit.DatasetIds dataset = CacheIdAudit.collect(regionDataLoader);
		CacheIdAudit.Report report = CacheIdAudit.run(client, dataset);

		log.info("Lively Cities cache id audit: {} model id(s) checked ({} failing), "
				+ "{} merged-object id(s) checked ({} failing), {} animation id(s) checked "
				+ "({} failing, {} known permanently null)",
			dataset.modelIds.size(), report.failingModelIds.size(),
			dataset.mergedObjectIds.size(), report.failingMergedObjectIds.size(),
			dataset.animationIdsByName.size(), report.failingAnimations.size(),
			report.knownPermanentNullAnimations.size());

		// Disk I/O never happens on the client thread. The text is built here, where
		// the report was, so what crosses the thread boundary is a finished snapshot
		// rather than an object still holding collections.
		writeReportAsync(reportDir(), CacheIdAudit.REPORT_FILE_NAME, report.toReportText(),
			"cache id audit");
	}

	/**
	 * Hands one finished report to a background thread.
	 *
	 * <p>Package-private and non-final so a test can run it inline and see what it was
	 * handed — the same seam, for the same reason, as {@link #reportFrameTimings()} and
	 * {@link #runDeveloperCacheAudit()}, one level further down. It exists because the
	 * <b>file name</b> is the one argument at these call sites that nothing else could
	 * check. {@link ReportWriter} has been parameterised by file name since it grew a
	 * second caller, so each caller now names its own constant — and passing
	 * {@code CacheIdAudit.REPORT_FILE_NAME} from the frame-timing path compiles,
	 * type-checks, and silently makes the frame report overwrite the model id audit.
	 * Two callers, two constants, and until this seam existed neither constant appeared
	 * in a single assertion.
	 *
	 * @param what what to call it in the log line, so a failure names the report that
	 *             failed rather than "a report"
	 */
	CompletableFuture<Void> writeReportAsync(File outputDir, String fileName, String text, String what)
	{
		return CompletableFuture.runAsync(() -> writeReport(outputDir, fileName, text, what));
	}

	/**
	 * Writes one finished report. Background thread only — see {@link ReportWriter}.
	 */
	private static void writeReport(File outputDir, String fileName, String text, String what)
	{
		try
		{
			File file = ReportWriter.write(outputDir, fileName, text);
			log.info("Lively Cities {} report written to {}", what, file);
		}
		catch (IOException e)
		{
			log.warn("Lively Cities {}: could not write the report under {}", what, outputDir, e);
		}
	}

	/**
	 * The plugin's own subdirectory of {@code ~/.runelite}.
	 *
	 * <p>Resolved per call rather than held in a static, because
	 * {@code RuneLite.RUNELITE_DIR} is a static that a test never wants written to and
	 * that neither report is on a hot path for.
	 */
	private static File reportDir()
	{
		return new File(RuneLite.RUNELITE_DIR, "lively-cities");
	}

	/**
	 * The frame-timing cadence: one info line and one file, every
	 * {@link FrameTimings#REPORT_INTERVAL_TICKS} game ticks.
	 *
	 * <p>The text is built here, on the client thread, and written on a background one
	 * — the same split {@link #runDeveloperCacheAudit()} uses, and for the same two
	 * reasons: disk I/O never happens on the client thread, and a report handed over as
	 * finished text is a snapshot rather than a view of counters that are still moving.
	 *
	 * <p>Unreachable for an ordinary user: {@link FrameTimings#onGameTick()} returns
	 * false forever unless both halves of its gate are set.
	 *
	 * <p><b>Nothing is written when there is nothing to say.</b> {@link #shutDown()}
	 * calls this unconditionally, so without the {@code hasSamples()} guard a client
	 * that was started and closed without the stopwatch ever taking a sample would
	 * replace a real report with a file that says "no samples" three times over.
	 *
	 * <p>Package-private and non-final so a test can override it and count the calls —
	 * the same seam, for the same reason, as {@link #runDeveloperCacheAudit()}. What
	 * needs asserting is <i>when</i> this runs, and under which file name
	 * ({@link #writeReportAsync}); that it can write a file is {@code ReportWriterTest}'s,
	 * against a temp directory rather than the real {@code ~/.runelite}.
	 */
	void reportFrameTimings()
	{
		if (!frameTimings.hasSamples())
		{
			return;
		}

		log.info(frameTimings.summaryLine());

		writeReportAsync(reportDir(), FrameTimings.REPORT_FILE_NAME, frameTimings.toReportText(),
			"frame timings");
	}

	@Override
	protected void shutDown()
	{
		// Removed before the scene is torn down, and synchronously: an overlay left
		// in the OverlayManager keeps drawing, and it would be drawing from a scene
		// that is being emptied underneath it.
		overlayRegistry.remove(chatterOverlay);

		// The menu holds a reference to whichever citizen the last right-click was
		// on. Left set, it would keep that wrapper — and its lit model — alive past
		// the teardown whose whole job is to leave nothing behind.
		citizenMenu.forget();

		// The tail of the session, before the scene is torn down. A no-op unless the
		// stopwatch is on, and non-blocking either way — the periodic report is the one
		// to rely on if the client is killed rather than closed.
		reportFrameTimings();

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
				// The remembered right-click target is about to be a wrapper the
				// scene has forgotten.
				citizenMenu.forget();
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

		if (handleResetButton(event))
		{
			// The clear wrote another setting, which posts another ConfigChanged, and
			// that one runs the visibility pass. Running it here as well would run it
			// twice for one click.
			return;
		}

		log.debug("config changed ({}), re-running the visibility pass", event.getKey());
		clientThread.invoke(this::refreshVisibility);
	}

	/**
	 * The two "clear the list" checkboxes, which untick themselves.
	 *
	 * <p>1.12.36 has no {@code Button} config type — there is no
	 * {@code net.runelite.client.config.Button} in the client jar — so a control
	 * whose meaning is "do this now" has to be a boolean that is turned back off
	 * once it has been acted on. Unset rather than written false: a key left in the
	 * profile reads as a user override forever, and "the user has no setting here"
	 * is the honest end state for a button.
	 *
	 * <p><b>It terminates.</b> Unsetting posts one more {@code ConfigChanged} for the
	 * same key with a new value of {@code null}, which fails the {@code "true"} test
	 * below and falls through to an ordinary visibility pass. There is no second
	 * write and so no loop.
	 *
	 * @return true if this event was a button press that has now been handled
	 */
	private boolean handleResetButton(ConfigChanged event)
	{
		final String key = event.getKey();
		if (!CitizenOverrides.UNHIDE_ALL_KEY.equals(key) && !CitizenOverrides.UNMUTE_ALL_KEY.equals(key))
		{
			return false;
		}

		if (!"true".equals(event.getNewValue()))
		{
			// The self-unset echo, or somebody unticking a box that was somehow left
			// ticked. Not a press.
			return false;
		}

		int cleared = CitizenOverrides.UNHIDE_ALL_KEY.equals(key)
			? overrides.unhideAll()
			: overrides.unmuteAll();

		log.debug("'{}' pressed, {} citizen(s) restored", key, cleared);

		// Whether or not anything was cleared: the box has to come back up either
		// way, or it stays ticked and can never be pressed again.
		configWriter.write(key, null);
		return true;
	}

	/**
	 * Adds this plugin's right-click entries.
	 *
	 * <p><b>Not wrapped in {@link ClientThread#invoke}, and that is not an
	 * oversight.</b> {@code MenuOpened} is posted from the client's own menu code,
	 * so this is already on the client thread — and {@code Menu.createMenuEntry}
	 * asserts as much. Deferring would be actively wrong rather than merely
	 * unnecessary: the entries would be added after the menu had been built and
	 * drawn, i.e. into the next menu or into none.
	 */
	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		citizenMenu.onMenuOpened(event);
	}

	/**
	 * Handles a click on one of them, locally.
	 *
	 * <p>Same threading as {@link #onMenuOpened}: posted from the client's menu
	 * dispatch, and it has to consume the event before that dispatch continues, so
	 * it cannot be deferred.
	 */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		citizenMenu.onMenuOptionClicked(event);
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

		// After the pass it is timing, so the tick that triggers a report is a tick the
		// report already includes. Returns false forever unless the stopwatch is on.
		if (frameTimings.onGameTick())
		{
			reportFrameTimings();
		}
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

	/**
	 * The plugin's only write path into its own settings.
	 *
	 * <p>This method is the whole reason {@link ConfigWriter} exists — see its
	 * javadoc. {@code ConfigManager}'s constructor is private, so anything that took
	 * one directly could not be constructed by a test; behind this one lambda,
	 * everything that decides <i>what</i> to write is testable against a map.
	 */
	/**
	 * The plugin's only overlay registration path — see {@link OverlayRegistry}.
	 */
	@Provides
	OverlayRegistry provideOverlayRegistry(OverlayManager overlayManager)
	{
		return new OverlayRegistry()
		{
			@Override
			public void add(Overlay overlay)
			{
				overlayManager.add(overlay);
			}

			@Override
			public void remove(Overlay overlay)
			{
				overlayManager.remove(overlay);
			}
		};
	}

	@Provides
	ConfigWriter provideConfigWriter(ConfigManager configManager)
	{
		return (key, value) ->
		{
			if (value == null)
			{
				configManager.unsetConfiguration(LivelyCitiesConfig.GROUP, key);
			}
			else
			{
				configManager.setConfiguration(LivelyCitiesConfig.GROUP, key, value);
			}
		};
	}
}
