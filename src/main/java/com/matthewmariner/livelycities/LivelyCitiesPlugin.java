package com.matthewmariner.livelycities;

import com.google.inject.Provides;
import java.util.UUID;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
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
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

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
	 * The dials, held here for one reason: the side panel draws them.
	 *
	 * <p>Everything else in this class reaches settings through the collaborator that
	 * owns them — {@link EntityScene} for the render dials, {@link CitizenOverrides} for
	 * the two uuid lists — and that is still the arrangement. What has no owner is "what
	 * do the dials currently say?", which is exactly the question a panel showing them
	 * has to ask before it can offer to change one.
	 */
	@Inject
	LivelyCitiesConfig config;

	@Inject
	OverlayRegistry overlayRegistry;

	/**
	 * The sidebar — see {@link SidePanel}, and note that this is the interface rather
	 * than the panel. Nothing here may touch Swing.
	 */
	@Inject
	SidePanel sidePanel;

	/**
	 * Who each uuid is. Only the panel asks, and it asks lazily — see
	 * {@link CitizenDirectory}.
	 */
	@Inject
	CitizenDirectory directory;

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
	 * <p><b>Nothing in this class reads it, and that is deliberate.</b> The measuring
	 * happens in {@link EntityScene}, which Guice hands the same {@code @Singleton};
	 * the <i>reporting</i> — the cadence, {@code RuneLite.RUNELITE_DIR} and the
	 * background thread that writes a file — moved to the test source set, because
	 * filesystem I/O in a shipped jar costs the Plugin Hub submission its automated
	 * review. See {@code LivelyCitiesDevReportsPlugin}, which is loaded only by
	 * {@code ./gradlew runWithTimings} and {@code ./gradlew auditCacheIds}.
	 *
	 * <p>This field is how that plugin reaches <i>this</i> stopwatch rather than a
	 * second one. It declares {@code @PluginDependency(LivelyCitiesPlugin.class)}, so
	 * RuneLite builds its injector as a child of this plugin's and binds this instance
	 * into it; it then reads this field. Resolving {@code FrameTimings} from its own
	 * injector instead would work only as long as Guice keeps hoisting the just-in-time
	 * binding to the root injector, and the failure mode if it ever did not is a report
	 * full of zeros rather than an error.
	 */
	@Inject
	FrameTimings frameTimings;

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

	/**
	 * Whether the most recent {@link GameTick} was one {@link #tick()} ran a full pass
	 * on, rather than one it returned early from.
	 *
	 * <p><b>Read by the developer-only reporter, and by nothing here.</b>
	 * {@code frameTimings.onGameTick()} used to be the last statement of
	 * {@link #tick()}, after its early exit — so the population the frame report
	 * described was "ticks this plugin processed", which is the population it claims to
	 * describe. Moving the cadence to {@code LivelyCitiesDevReportsPlugin} put it on its
	 * own {@code GameTick} subscriber, where "did the pass happen?" is no longer implied
	 * by "am I running", so the reporter has to ask.
	 *
	 * <p>Cleared in {@link #shutDown()}, which is the case that makes this a flag rather
	 * than an assumption: the reporter is a separate plugin and stays on when this one
	 * is toggled off, and a flag left true would let the 300-tick cadence keep firing —
	 * rewriting the report from samples nothing is adding to any more.
	 *
	 * <p>The reporter can only ever see the current tick's value because it subscribes
	 * at {@code priority = -1f} and therefore runs after this class. At any other
	 * priority it reads the previous tick's, which is what
	 * {@code FrameTimingsTest.theReportIncludesTheTickThatTriggeredIt} exists to catch.
	 */
	private boolean processedGameTick;

	@Override
	protected void startUp()
	{
		log.info("Lively Cities starting (cull {} tiles, max {}, cap {} objects)",
			RenderPolicy.DEFAULT_CULL_RADIUS, RenderPolicy.MAX_CULL_RADIUS, RenderPolicy.MAX_ACTIVE_OBJECTS);

		overlayRegistry.add(chatterOverlay);

		// The button, unconditionally. It is the only affordance this plugin has that
		// announces itself — everything else is a right-click nobody has been told about
		// or a settings screen you have to already be looking for — so there is no
		// setting behind it to disagree with.
		sidePanel.show();

		// Enabling the plugin mid-session is the common case in dev. Nothing to
		// do here beyond letting the next game tick find the scene — but if we
		// are already logged in, do not make the user wait for a state change
		// that will never come.
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			// The braces are load-bearing, and this is not a style choice.
			// ClientThread overloads invoke() on Runnable and BooleanSupplier, and the
			// two mean different things: a BooleanSupplier is re-queued every tick
			// until it returns true. When tick() was void, `this::tick` bound to the
			// Runnable overload. Giving it a boolean return — for the reporting
			// cadence, nothing to do with this line — silently rebound it to
			// BooleanSupplier and turned one first pass into a retry loop, with no
			// warning and no character of this call site changed. A block lambda whose
			// body is a bare statement has no value, so only Runnable fits.
			clientThread.invoke(() ->
			{
				tick();
			});
		}
	}

	@Override
	protected void shutDown()
	{
		// Removed before the scene is torn down, and synchronously: an overlay left
		// in the OverlayManager keeps drawing, and it would be drawing from a scene
		// that is being emptied underneath it.
		overlayRegistry.remove(chatterOverlay);

		// And the sidebar. Same class of leak as an overlay left in the manager: a
		// NavigationButton left in the toolbar is a button that opens a panel whose
		// plugin is gone, and ClientToolbar keys its navigation off the button instance,
		// so a second one built on the way back in would leave the first sitting there
		// for the rest of the session. The panel's "somebody is looking" flag is cleared
		// with it, or the next enable would compose a model for a panel that is not in
		// the sidebar.
		sidePanel.hide();

		// The menu holds a reference to whichever citizen the last right-click was
		// on. Left set, it would keep that wrapper — and its lit model — alive past
		// the teardown whose whole job is to leave nothing behind.
		citizenMenu.forget();

		// The developer-only reporter is a separate plugin and does not stop when this
		// one does. Left true, its 300-tick cadence would go on firing over a scene
		// nobody is measuring any more.
		processedGameTick = false;

		// Not blocking: invoke() runs inline on the client thread and defers
		// otherwise. The count lands in the log either way.
		//
		// A block lambda rather than `scene::shutdown`, for the same reason the braces
		// in startUp() are load-bearing. That method reference binds to the Runnable
		// overload today only because shutdown() returns int, which cannot be a
		// BooleanSupplier's result. Give it a boolean return one day and it silently
		// rebinds — JLS 15.12.2.5 makes the value-returning function type the more
		// specific of the two, so BooleanSupplier wins outright rather than the call
		// becoming ambiguous and failing to compile — and ClientThread re-queues a
		// BooleanSupplier every tick until it returns true. That is a teardown that
		// runs forever, with no warning and no character of this call site changed. A
		// block lambda whose body is a bare statement has no value, so only Runnable
		// ever fits. LivelyCitiesPluginLifecycleTest's InlineClientThread fails the
		// test outright if the BooleanSupplier overload is ever reached from anywhere
		// in this class, so the binding is pinned rather than merely intended.
		clientThread.invoke(() ->
		{
			scene.shutdown();
		});
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

				// Braces again, and not for symmetry. `() -> scene.invalidate(..)` is
				// an expression lambda, and invalidate() returns int — so it is
				// void-compatible only, and only Runnable fits. The day that return
				// type becomes boolean, the same lambda becomes value-compatible too
				// and BooleanSupplier wins the overload, which would re-queue this
				// every tick until it returned true: a scene load that never finishes
				// invalidating. The block form has no value to offer, so it cannot
				// drift.
				clientThread.invoke(() ->
				{
					scene.invalidate(state.name());

					// Logging out empties the scene, and a panel still showing "Varrock,
					// 34 on screen" over an empty world is worse than one showing
					// nothing: it is the last true reading, indistinguishable from a
					// current one.
					refreshPanel();
				});
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
		processedGameTick = tick();

		// The panel's clock. Everything on it either moves every tick (how many figures
		// are on screen, who is talking) or changes on a click, and a click already runs
		// a pass of its own through onConfigChanged — so the game tick is the only
		// cadence it needs, and it is the cadence the numbers are true at.
		refreshPanel();
	}

	/**
	 * Whether the most recent {@link GameTick} reached the scene — see
	 * {@link #processedGameTick}.
	 *
	 * <p>Package-private, and the developer-only reporter in the test source set is its
	 * only caller. It is a plain getter rather than a listener hook on purpose: nothing
	 * in {@code src/main} may hold a sink, a nullable writer or a "reporting enabled"
	 * branch, which is the arrangement the whole move exists to produce.
	 */
	boolean processedGameTick()
	{
		return processedGameTick;
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
	 *
	 * @return true if a full pass ran; false if there was no player or no world view
	 * yet and this tick did nothing. {@link #processedGameTick} is the only reader,
	 * and it is why this returns anything at all.
	 */
	private boolean tick()
	{
		final WorldPoint playerLocation = playerLocation();
		final WorldView worldView = client.getTopLevelWorldView();
		if (playerLocation == null || worldView == null)
		{
			return false;
		}

		// Restarting the interpolation clock is a game-tick-only act: the frame
		// handler measures from here, so writing it anywhere else would drop every
		// citizen back to the start of its step.
		cycleAtLastTick = client.getGameCycle();

		scene.syncRegions(worldView);
		scene.onGameTick(playerLocation, worldView);
		return true;
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
		refreshPanel();
	}

	/**
	 * Takes a reading and hands it to the sidebar.
	 *
	 * <p><b>Behind {@link SidePanel#isOpen()}, and that is a cost decision rather than a
	 * cosmetic one.</b> Composing a model walks every cached wrapper and asks the client
	 * whether each object is still registered — real per-tick work, in a plugin whose
	 * whole performance argument is that it does almost none per frame and very little
	 * per tick. Nobody is looking at a closed panel, so a closed panel costs one boolean
	 * read every 600ms and nothing else.
	 *
	 * <p>Client thread: every caller is already on it, and {@link EntityScene#census()}
	 * reads the client. The hop to Swing happens on the far side of
	 * {@link SidePanel#refresh}, which is the panel's job rather than this one's.
	 */
	private void refreshPanel()
	{
		if (!sidePanel.isOpen())
		{
			return;
		}

		sidePanel.refresh(PanelModel.of(
			playerLocation(),
			scene.census(),
			config,
			overrides.hiddenUuids(),
			overrides.mutedUuids(),
			directory));
	}

	/**
	 * The reading to draw when the panel is opened before anything has been pushed to
	 * it — see {@link PanelModel#loggedOut}.
	 *
	 * <p><b>Why the panel has to ask for one at all.</b> {@link #refreshPanel} is the only
	 * thing that ever hands a model over, and it is driven by {@link GameTick} — which the
	 * client does not post while there is no world. So enabling the plugin at the login
	 * screen, or opening the panel there, left it with the null model it was constructed
	 * with until the player logged in: nine bordered cards with no name, no on/off and no
	 * counts, no density selected, and an overrides section with nothing in it at all.
	 *
	 * <p><b>Client-free, and that is what makes it callable from Swing.</b> The dials, the
	 * two uuid lists and the citizen counts come out of the profile and the classpath;
	 * nothing here reads the client, so there is no thread to hop to and no reading that
	 * could throw off the client thread. The one number that would need a client —
	 * how many figures are on screen — is zero when there is no world, which is
	 * {@link SceneCensus#EMPTY}.
	 *
	 * <p>Package-private, and {@link LivelyCitiesPanel#onActivate} is its only caller.
	 */
	PanelModel loggedOutModel()
	{
		return PanelModel.loggedOut(config, overrides.hiddenUuids(), overrides.mutedUuids(), directory);
	}

	/**
	 * Turns one city's checkbox on or off, from the panel.
	 *
	 * <p><b>Writes the same key the config screen writes</b>, through the same
	 * {@link ConfigWriter} — see {@link City#getConfigKey()}, which is the very constant
	 * the {@code @ConfigItem} is annotated with. So the panel and the settings screen are
	 * two views of one setting rather than two settings, and unticking a city here shows
	 * up unticked there.
	 *
	 * <p><b>A write that would change nothing does not happen.</b> {@code ConfigManager}
	 * posts a {@code ConfigChanged} per {@code setConfiguration}, and this plugin answers
	 * every one of those with a full visibility pass over every entity in scope — so a
	 * no-op write is a no-op pass over a hundred citizens. The same rule
	 * {@link UuidSetting#add} keeps, for the same reason.
	 *
	 * <p>Called from Swing's event dispatch thread and deliberately not marshalled:
	 * nothing here reads the client. {@code ConfigManager} is thread-safe and is what
	 * RuneLite's own settings panel writes through, from that same thread. The work that
	 * <i>does</i> need the client thread — the visibility pass — is already marshalled by
	 * {@link #onConfigChanged}, which is what the write posts.
	 */
	void setCityEnabled(City city, boolean enabled)
	{
		if (city.enabledIn(config) == enabled)
		{
			return;
		}

		configWriter.write(city.getConfigKey(), Boolean.toString(enabled));
	}

	/**
	 * Moves the density dial, from the panel. Same rules as {@link #setCityEnabled}.
	 *
	 * <p>The value is {@link Enum#name()} because that is what {@code ConfigManager}
	 * stores an enum as and what it reads one back with, so this round-trips through the
	 * settings screen's dropdown rather than sitting beside it.
	 */
	void setCrowdDensity(CrowdDensity density)
	{
		if (config.crowdDensity() == density)
		{
			return;
		}

		configWriter.write(LivelyCitiesConfig.KEY_CROWD_DENSITY, density.name());
	}

	/**
	 * Brings one hidden citizen back, from the panel.
	 *
	 * <p>The panel exists for this. Hiding is per citizen; until now the only undo was
	 * a checkbox that unhid everybody, so taking back one decision cost every other one.
	 * {@link CitizenOverrides} does the work and skips a write that would change nothing;
	 * the respawn comes from the {@code ConfigChanged} that write posts, through the same
	 * visibility pass the checkbox uses.
	 */
	void unhide(UUID uuid)
	{
		overrides.unhide(uuid);
	}

	/** Lets one muted citizen talk again. Same shape as {@link #unhide}. */
	void unmute(UUID uuid)
	{
		overrides.unmute(uuid);
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

	/**
	 * The panel's place in the sidebar — see {@link SidePanel}.
	 *
	 * <p><b>The button is built once here and added and removed</b>, rather than rebuilt
	 * on every toggle. {@code ClientToolbar} keys its navigation off the button instance,
	 * so a second one built on the way back in would leave the first behind — a button
	 * that cannot be removed by anything short of a client restart, which is the
	 * navigation-shaped version of the leaked active object this plugin already guards
	 * against.
	 *
	 * <p><b>{@code @Singleton} is what makes "once" structural rather than a property of
	 * there happening to be one injection point today.</b> A {@code @Provides} method with
	 * no scope on it runs once per injection point, so the day something else asks for a
	 * {@link SidePanel} — a second subscriber, a status overlay, anything — Guice builds a
	 * second button around a second panel, {@code startUp} adds whichever one it was given,
	 * and the other is a button nothing will ever remove. That is the leak the paragraph
	 * above is about, arriving by the one door the teardown test cannot see: it asserts
	 * that {@code shutDown} hides the panel it was handed, and both plugins would still be
	 * telling the truth about their own.
	 */
	@Provides
	@Singleton
	SidePanel provideSidePanel(ClientToolbar clientToolbar, LivelyCitiesPanel panel)
	{
		final NavigationButton button = NavigationButton.builder()
			.tooltip("Lively Cities")
			.icon(ImageUtil.loadImageResource(LivelyCitiesPanel.class, "panel_icon.png"))
			.priority(7)
			.panel(panel)
			.build();

		return new SidePanel()
		{
			@Override
			public void show()
			{
				clientToolbar.addNavigation(button);
			}

			@Override
			public void hide()
			{
				clientToolbar.removeNavigation(button);

				// Removing the navigation does not have to produce an onDeactivate, and
				// a panel that still believes it is open is a panel the game tick keeps
				// composing models for. Told here rather than inferred there.
				panel.closed();
			}

			@Override
			public boolean isOpen()
			{
				return panel.isOpen();
			}

			@Override
			public void refresh(PanelModel model)
			{
				panel.accept(model);
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
