package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;

/**
 * Owns every {@link LivelyEntity} the plugin has created, and decides which of
 * them are active.
 *
 * <p><b>Client thread only.</b> Callers (the plugin's event handlers) are
 * responsible for getting here on the client thread.
 *
 * <p>Two collections matter, and the distinction is the leak guard:
 * <ul>
 *   <li>{@code built} — every wrapper currently cached, keyed by the region
 *       <i>file</i> it came from. Teardown walks <i>this</i>, so an entity whose
 *       region left the scene can never be orphaned in the active state.</li>
 *   <li>{@code inScope} — the wrappers whose own tile sits in a region the
 *       loaded scene covers. Only these are considered for spawning.</li>
 * </ul>
 *
 * <p>There is a third, {@link #retained}, and it is empty on every run that goes to
 * plan. It exists because {@code built} is the <i>only</i> holder of these wrappers,
 * so a teardown that clears it while the client still has an object registered
 * strands that object with no reference anywhere. See that field.
 *
 * <p><b>Scope is keyed on the entity's tile, not on its file name.</b> The two
 * differ: "Dark wizard" is authored in {@code 12853.json} but stands at
 * (3261, 3386), which is region 12852. Deciding visibility by file name would
 * mean asking the client to place an entity in a region it has not loaded, and
 * relying on {@code LocalPoint.fromWorld} returning null to cover for it.
 *
 * <p>Discovery still keys on the file name — it has to, the file name is the
 * lookup — so a misfiled entity is only found while the region it was
 * <i>filed</i> under is also in the scene. The dataset's worst misfiling is 6
 * tiles ({@code RenderPolicyTest} recomputes it), which is well inside the
 * region-sized granularity the scene works at, so in practice the file's region
 * is loaded whenever the entity is close enough to matter. That is a statement
 * about this dataset and this scene size, not an invariant — see
 * {@link RenderPolicy#SUSTAINED_SCENE_RADIUS} for why no invariant is on offer.
 *
 * <p>Wrappers are kept across scene loads on purpose: crossing a region border
 * and coming back then costs an activate rather than a model rebuild. They are
 * not kept forever — see {@link #EVICTION_GRACE_SCOPE_CHANGES}.
 *
 * <p><b>Echoes live in {@link #built} beside the citizens they came from.</b>
 * {@link #ensureBuilt} hands a region's whole authored roster to
 * {@link CitizenEcho#echoesOfRegion} and wraps what comes back, whatever the density
 * dial currently says, and {@link #allowedByConfig} is what refuses them anywhere
 * but {@link CrowdDensity#CROWDED}. Building them unconditionally is what makes
 * "{@code CROWDED} inherits every constraint" a structural fact rather than a
 * checklist: an echo enters and leaves scope, is evicted, is deactivated on a
 * scene change and is torn down by exactly the code above, because there is no
 * separate collection for it to be missing from. Three things treat it differently,
 * and each is written down where it happens: its tile has to satisfy
 * {@link StandableGround} before it may spawn, it sorts behind every authored entity
 * so the object cap can never spend an authored citizen's slot on a derived one
 * (both in {@link #updateVisibility}), and its city checkbox is its source's rather
 * than its own tile's ({@link EntityDefinition#getCityRegionId()}).
 *
 * <p><b>Two clocks.</b> {@link #onGameTick} is the per-game-tick pass: sync the
 * scope, decide who is visible, step the wanderers one tile.
 * {@link #onFrame} is the per-frame pass, and it does exactly one thing —
 * interpolate the wanderers' drawn positions. It walks a maintained list of
 * active wanderers rather than the whole cache, because a frame handler that
 * scans every wrapper is a frame handler that shows up in a profile.
 *
 * <p><b>Both clocks are metered</b>, along with model building and with the region
 * loading in {@link #ensureBuilt}, so that claim is a figure rather than an argument —
 * see {@link FrameTimings}. Off for every shipped client, and off is one field read per
 * pass. The region-load meter is the one that closes the gap the 2026-08-29 measurement
 * opened: {@code syncRegions} runs <i>before</i> the visibility pass rather than inside
 * it, so until then a scene load was per-tick work no meter could see.
 *
 * <p><b>Two budgets, and they bound different things.</b>
 * {@link RenderPolicy#MAX_ACTIVE_OBJECTS} caps how many objects may be <i>active</i>
 * at once; {@link RenderPolicy#MAX_MODEL_BUILDS_PER_PASS} caps how many models one
 * pass may <i>build</i>. The first was always here. The second is the answer to a
 * measurement: 371 builds landed across 331 passes, so individual builds were fine
 * (p99 1.50ms) and the worst pass was still 53.73ms, because walking into Varrock
 * built forty models inside one game tick. {@link #runVisibilityPass} spends the
 * build budget in the sorted candidate order, so what waits for the next pass is
 * always the far end of the crowd, and a citizen that waits is one that has not
 * appeared yet rather than one that flickers — it is never activated, never
 * despawned, and does not spend one of its {@link LivelyEntity#MAX_MODEL_ATTEMPTS}.
 * The cap is nine rather than the three it was first set to; the 2026-08-29
 * re-measurement is what moved it, and {@link RenderPolicy#MAX_MODEL_BUILDS_PER_PASS}
 * carries the arithmetic.
 *
 * <p><b>Where a border-crossing wanderer lives.</b> Scope membership is decided
 * once, from the entity's authored tile, and a citizen walking across a region
 * border does not change it. Three of the 63 wanderers have boxes that straddle
 * one. That choice is what makes both failure modes impossible rather than
 * unlikely:
 * <ul>
 *   <li><b>Double-spawn</b> — there is exactly one wrapper per record, filed
 *       under exactly one region, and {@link #inScope} is rebuilt by one
 *       membership test per wrapper. A citizen cannot be in the list twice
 *       however far it has walked.</li>
 *   <li><b>Orphaned-active</b> — teardown and eviction walk {@link #built}, every
 *       cached wrapper, not the in-scope subset. A citizen standing on the far
 *       side of a border when its home region leaves the scene is deactivated by
 *       the same code that deactivates everything else.</li>
 * </ul>
 * Deriving membership from the live position instead would break both: an entity
 * would enter and leave scope mid-walk (refunding its model-retry budget each
 * time), and a wrapper evicted on its <i>file</i> region's schedule could still
 * be in scope through the region it had wandered into — which is the exact leak
 * {@code anEvictedMisfiledEntityLeavesNothingBehindInScope} exists to catch.
 * How far a wanderer can get from the tile its cull check measured is bounded by
 * {@link RenderPolicy#DATASET_OVERHANG_ALLOWANCE}; whether the ground it lands on
 * is loaded is not something this plugin can guarantee, and
 * {@link RenderPolicy#SUSTAINED_SCENE_RADIUS} says why.
 */
@Slf4j
@Singleton
class EntityScene
{
	/**
	 * How many scope changes a region's wrappers survive out of scope before
	 * they are dropped.
	 *
	 * <p>Each wrapper holds a lit {@code Model} for as long as it lives, so
	 * "keep everything" is a session-length memory leak dressed up as a cache —
	 * and the stated growth path for this plugin is more region files. Two scope
	 * changes is enough that stepping over a border and back is still free,
	 * while bounding the cache to the regions seen in the last few scene loads
	 * rather than the regions seen all session.
	 *
	 * <p>It is also the coarse half of the cold-cache retry: a region that comes
	 * back after eviction is rebuilt, and a fresh wrapper starts with a fresh
	 * {@link LivelyEntity#MAX_MODEL_ATTEMPTS} budget.
	 */
	static final int EVICTION_GRACE_SCOPE_CHANGES = 2;

	private final Client client;
	private final RegionDataLoader loader;
	private final LivelyCitiesConfig config;
	private final CitizenOverrides overrides;

	/**
	 * The stopwatch, off unless a developer asked for it — see {@link FrameTimings}.
	 *
	 * <p>Held rather than reached through a static so that "measuring is off" is a
	 * field on this object and not a global, and so a test can hand this scene a live
	 * meter without changing anything a shipped client does.
	 */
	private final FrameTimings timings;

	/**
	 * Who is saying what.
	 *
	 * <p>A field rather than an injected collaborator because it has no dependency
	 * the scene does not already hold, and because it is driven from exactly one
	 * place: {@link #onGameTick}. Its clock is the game tick, so a second caller
	 * would be a second definition of how often citizens talk — the same rule that
	 * keeps {@link #stepWalkers()} private.
	 */
	private final CitizenChatter chatter;

	/** Parsed region files, keyed by region id. */
	private final Map<Integer, RegionDefinition> parsed = new HashMap<>();

	/** Region ids we have looked for and found nothing — do not look again. */
	private final Set<Integer> withoutData = new HashSet<>();

	/** Currently cached wrappers, keyed by the region file they came from. */
	private final Map<Integer, List<LivelyEntity>> built = new LinkedHashMap<>();

	/**
	 * For each cached region, the scope generation it was last in scope for.
	 * Keys are always a subset of {@link #built}'s.
	 */
	private final Map<Integer, Long> lastInScope = new HashMap<>();

	/**
	 * The wrappers teardown could not get the client to let go of. Empty on every
	 * ordinary run.
	 *
	 * <p>{@link LivelyEntity#despawn()} catches its own {@code RuntimeException}, marks the
	 * entity broken and returns {@code false}, so a client that threw out of
	 * {@code removeRuneLiteObject} leaves the object registered. {@link #shutdown()} used
	 * to notice exactly that, log an error about it, and then clear {@link #built} anyway —
	 * which is worse than having no check, because {@link #built} was the only thing holding
	 * those wrappers. What was left was a {@code RuneLiteObject} still in the client's
	 * registered-object list with no Java reference anywhere: it renders forever, re-enabling
	 * the plugin cannot reach it, and only a client restart removes it. Keeping the wrapper
	 * costs a pointer. Dropping it costs a figure standing in Varrock for the rest of the
	 * session.
	 *
	 * <p><b>A list of its own rather than a corner of {@link #built}, and that is a decision
	 * rather than a convenience.</b> {@link #built} is keyed by region file and
	 * {@link #ensureBuilt} returns early for a key it already holds, so leaving one
	 * unreleasable wrapper filed under region 12852 would mean the next walk into Varrock
	 * rebuilds nothing there — one broken citizen where a hundred should be, for the rest of
	 * the session — and the eviction that would eventually clear the key drops the reference,
	 * which is the leak again by a slower route. Lifting them out instead is what lets every
	 * other collection clear exactly as it always did: {@link #lastInScope} keys a region
	 * that is gone, {@link #parsed} and {@link #withoutData} are pure caches of file
	 * contents, and {@link #scopeRegions}, {@link #inScope} and {@link #walkers} describe a
	 * scene that no longer exists. Nothing that survives here points at any of them, so the
	 * next {@link #syncRegions} loads and builds every region from scratch.
	 *
	 * <p>What survives is a wrapper, its lit model, and the object the client will not
	 * release: out of scope, never a spawn candidate, and asked again on the next
	 * {@link #deactivateAll()} in case the client has come to its senses. A RuneLite plugin
	 * instance outlives a disable/enable cycle, so this list does too, and the next teardown
	 * is a real second chance rather than a gesture.
	 */
	private final List<LivelyEntity> retained = new ArrayList<>();

	/** Wrappers whose tile is in a region the currently loaded scene covers. */
	private final List<LivelyEntity> inScope = new ArrayList<>();

	/**
	 * {@link #inScope}, read-only, for the two things that have to walk the live set
	 * from outside: the overhead-text overlay (once a frame) and the right-click hit
	 * test (once a right-click).
	 *
	 * <p>Wrapped once here rather than per call. {@code unmodifiableList} allocates,
	 * and a frame handler that allocates is a frame handler that shows up in a
	 * profile — the same reasoning as the maintained {@link #walkers} list. It is a
	 * <i>view</i>, not a copy, which is the whole point: a caller that walks it
	 * cannot be looking at a stale set, so a despawned entity's text cannot survive
	 * into the next frame.
	 */
	private final List<LivelyEntity> inScopeView = Collections.unmodifiableList(inScope);

	/** The region ids the current scope was built from, in the client's order. */
	private final List<Integer> scopeRegions = new ArrayList<>();

	/**
	 * The active wandering citizens, rebuilt at the end of every visibility pass.
	 *
	 * <p>The per-frame pass walks this and nothing else. It is always a subset of
	 * {@link #inScope}, so it can never keep an evicted wrapper alive, and it is
	 * cleared everywhere the scope is.
	 */
	private final List<LivelyEntity> walkers = new ArrayList<>();

	/** Region ids no {@link City} claims, so the warning is written once each. */
	private final Set<Integer> unmappedReported = new HashSet<>();

	/** Bumped once per scope change; drives eviction. */
	private long scopeGeneration;

	private boolean reportNextPass;
	private boolean instanceReported;
	private boolean firstReportDone;
	private long totalSpawns;
	private long totalDespawns;

	@Inject
	EntityScene(
		Client client,
		RegionDataLoader loader,
		LivelyCitiesConfig config,
		CitizenOverrides overrides,
		FrameTimings timings)
	{
		this.client = client;
		this.loader = loader;
		this.config = config;
		this.overrides = overrides;
		this.timings = timings;
		this.chatter = new CitizenChatter(config, overrides);
	}

	/**
	 * The same scene with no stopwatch attached.
	 *
	 * <p>For the tests, and it is the default for all but the handful that are about
	 * the measurement itself: a meter that is off records nothing and costs one field
	 * read, so wiring {@link FrameTimings#off()} into every other test keeps those
	 * tests measuring what they are about.
	 */
	EntityScene(
		Client client,
		RegionDataLoader loader,
		LivelyCitiesConfig config,
		CitizenOverrides overrides)
	{
		this(client, loader, config, overrides, FrameTimings.off());
	}

	/**
	 * Brings {@link #inScope} in line with the world view's loaded map regions,
	 * loading and parsing any region file not seen before.
	 *
	 * <p>Cheap to call every game tick: it compares the region id array and
	 * returns immediately when nothing moved.
	 *
	 * @return true if the scope changed
	 */
	boolean syncRegions(WorldView worldView)
	{
		int[] regions = worldView.getMapRegions();
		if (regions == null)
		{
			return false;
		}

		if (sameAsScope(regions))
		{
			return false;
		}

		// Anything leaving scope must go inactive before we forget about it.
		int stranded = 0;
		for (LivelyEntity entity : inScope)
		{
			if (despawnQuietly(entity))
			{
				stranded++;
			}
		}
		totalDespawns += stranded;

		scopeGeneration++;
		scopeRegions.clear();
		inScope.clear();
		walkers.clear();

		Set<Integer> scope = new HashSet<>();
		int withData = 0;
		for (int regionId : regions)
		{
			scopeRegions.add(regionId);
			if (!scope.add(regionId))
			{
				continue;
			}

			ensureBuilt(regionId);
			if (built.containsKey(regionId))
			{
				// Only regions we are actually holding wrappers for: this map
				// exists to decide when to drop them, and keying it on every
				// region the session ever walked through would make it the
				// unbounded thing it is here to prevent.
				lastInScope.put(regionId, scopeGeneration);
			}
		}

		int skipped = 0;
		for (int regionId : scope)
		{
			RegionDefinition region = parsed.get(regionId);
			if (region != null)
			{
				withData++;
				skipped += region.getSkippedRecords();
			}
		}

		// Evict before building the new scope, not after: an entity in inScope
		// but no longer in built would be spawnable by the visibility pass and
		// invisible to teardown, which walks built.
		int evicted = evictStaleRegions();

		// Membership by tile, not by file: see the class javadoc.
		for (List<LivelyEntity> entities : built.values())
		{
			for (LivelyEntity entity : entities)
			{
				if (scope.contains(entity.getDefinition().getTileRegionId()))
				{
					entity.onScopeEntered();
					inScope.add(entity);
				}
			}
		}

		// Scene loads happen on every region border crossing, so this is debug:
		// the once-per-region-file line in load() carries the same counts at info.
		log.debug("scene now covers regions {} — {} of {} have data, "
				+ "{} entity definitions in scope, {} record(s) skipped at load, "
				+ "{} entity(ies) deactivated on the way out, "
				+ "{} region(s) evicted, {} still cached",
			scopeRegions, withData, scope.size(), inScope.size(), skipped, stranded,
			evicted, built.size());

		reportNextPass = true;
		return true;
	}

	/**
	 * The whole per-game-tick pass: decide who is visible, then walk the
	 * wanderers one tile.
	 *
	 * <p>Visibility first. A citizen that has just been deactivated should not
	 * spend the tick walking, and one that has just spawned is placed on the tile
	 * its walk left off on, so stepping it afterwards is one clean step from a
	 * position the player can already see.
	 *
	 * <p><b>The only caller of {@link #stepWalkers()}.</b> Movement is the game's
	 * clock and nothing else may advance it — see {@link #onSettingsChanged}.
	 *
	 * @param playerLocation the local player's world location, never null
	 */
	void onGameTick(WorldPoint playerLocation, WorldView worldView)
	{
		updateVisibility(playerLocation, worldView);
		stepWalkers();

		// Last, and after the walk: the visibility pass has decided who is on screen,
		// so a citizen deactivated this tick cannot start talking on it, and one that
		// has just spawned is asked from the position the player can already see.
		chatter.onGameTick(inScope, playerLocation);
	}

	/**
	 * Re-evaluates who should be visible, and moves nobody.
	 *
	 * <p>This exists because a settings change has to take effect at once — the
	 * whole reason unticking a city <i>deactivates</i> its citizens rather than
	 * merely stopping new ones spawning is that the visibility pass has one rule,
	 * "what is not wanted is despawned", so re-running it with the new settings is
	 * the entire implementation.
	 *
	 * <p><b>Why it is not {@link #onGameTick}.</b> It used to be. A config change
	 * ran the full tick, which stepped every walker a whole tile; RuneLite fires a
	 * {@code ConfigChanged} per key, so switching profiles fired about two dozen of
	 * them and teleported the crowd two dozen tiles. Movement belongs to the game
	 * tick, and a setting is not a game tick — a citizen must be able to be
	 * despawned, respawned, or thinned out mid-stride without gaining a step.
	 *
	 * @param playerLocation the local player's world location, never null
	 */
	void onSettingsChanged(WorldPoint playerLocation, WorldView worldView)
	{
		updateVisibility(playerLocation, worldView);

		// The hard off switch, re-applied without advancing the chatter clock. The
		// same distinction as the walkers: unticking "Overhead chatter" has to empty
		// the screen on the click, but a settings change is not a game tick and must
		// never be able to start a remark — RuneLite posts one ConfigChanged per key,
		// so switching profiles would otherwise run the cadence two dozen ticks
		// forward for a change the user made to a checkbox.
		chatter.onSettingsChanged(inScope);
	}

	/**
	 * One frame of visual interpolation for the active wanderers, and nothing
	 * else.
	 *
	 * @param fraction how far through the current game tick this frame is, 0..1
	 */
	void onFrame(WorldView worldView, float fraction)
	{
		final long startedAt = timings.start();

		for (int i = 0; i < walkers.size(); i++)
		{
			frameQuietly(walkers.get(i), worldView, fraction);
		}

		// Every frame, including the ones where walkers is empty — "it does nothing
		// sixty times a second" is the claim, and a meter that only sampled the busy
		// frames could not make it.
		timings.recordFrame(startedAt, walkers.size());
	}

	/**
	 * Activates the entities that should be visible and deactivates the rest.
	 *
	 * @param playerLocation the local player's world location, never null
	 */
	void updateVisibility(WorldPoint playerLocation, WorldView worldView)
	{
		final long startedAt = timings.start();
		final int active = runVisibilityPass(playerLocation, worldView);
		timings.recordVisibility(startedAt, active);
	}

	/**
	 * {@link #updateVisibility} with the stopwatch peeled off.
	 *
	 * @return how many objects the client is left holding — {@code planned} minus the
	 * ones that could not be built or placed, minus the ones whose model build
	 * {@link RenderPolicy#MAX_MODEL_BUILDS_PER_PASS} held over to a later pass.
	 * Computed rather than counted: {@link #countActive()} walks every cached wrapper,
	 * so calling it here would make an ordinary user pay for a figure only the meter
	 * wants.
	 */
	private int runVisibilityPass(WorldPoint playerLocation, WorldView worldView)
	{
		if (worldView.isInstance())
		{
			// Instanced scenes remap chunks, so a stored world location does not
			// mean what the file says it means. Better nothing than entities
			// scattered through a raid.
			int cleared = deactivateAll();
			walkers.clear();
			if (!instanceReported)
			{
				log.info("Lively Cities: instanced region — holding off ({} entity(ies) deactivated)", cleared);
				instanceReported = true;
			}
			return 0;
		}
		instanceReported = false;

		int viewPlane = worldView.getPlane();

		// Read the dials once per pass, not once per entity: a config read goes
		// through ConfigManager, and this loop runs over every definition in
		// scope every game tick.
		int cullRadius = RenderPolicy.clampCullRadius(config.cullRadius());
		CrowdDensity density = config.crowdDensity();
		if (density == null)
		{
			// A profile carrying a value this build does not know about
			// deserialises to null rather than throwing. Showing everyone is the
			// right way to be wrong.
			density = CrowdDensity.FULL;
		}

		// Same rule, and it is the reason UuidSetting caches its parse: this is one
		// config read and at most one parse per pass, not one per entity.
		Set<UUID> hiddenUuids = overrides.hiddenUuids();

		// Read once per pass like the other dials. Default false — see the config item.
		boolean cameosAllowed = config.cameos();

		List<LivelyEntity> candidates = new ArrayList<>();
		int offByConfig = 0;
		int onUnusableGround = 0;
		for (LivelyEntity entity : inScope)
		{
			entity.setWanted(false);
			if (entity.isBroken())
			{
				continue;
			}
			if (!allowedByConfig(entity.getDefinition(), density, hiddenUuids, cameosAllowed))
			{
				// Not a candidate, so the deactivate pass below despawns it. That
				// is the whole mechanism behind "unticking a city takes effect
				// now" — nothing separate has to hunt down what is already
				// spawned.
				offByConfig++;
				continue;
			}
			if (RenderPolicy.isCandidate(playerLocation, viewPlane, entity.getDefinition(), cullRadius))
			{
				// The placement gate, and it is deliberately here rather than in
				// allowedByConfig: it reads the live collision map, so it is the one
				// check that costs a scene lookup, and this is the first point at
				// which we know the entity is close enough for the answer to matter.
				// It is a no-op for the 305 authored non-cameo entities. For the 145
				// vendored ones that is because a human stood on those tiles in game;
				// for the 33 added on 2026-08-29 and the 127 added on 2026-09-01 it is
				// because each stands beside a
				// tile that was, or inside a wander box a human drew — which is a
				// weaker claim, and docs/CITY-TOP-UP-CHECK.md is the walk that settles
				// it.
				if (!groundIsUsable(worldView, entity.getDefinition()))
				{
					onUnusableGround++;
					continue;
				}
				candidates.add(entity);
			}
		}

		// Authored citizens first, then nearest first inside each group.
		//
		// The distance half is the original rule: the cap sheds the far edge of the
		// crowd rather than whatever the region files happened to list last. The
		// authored/echo half is what makes CROWDED safe to hit the cap with — and it
		// will hit it, because the densest neighbourhood in the shipped data already
		// holds 76 authored entities at the widest render distance against a cap of
		// 80. Sorting every authored entity ahead of every echo means the cap is
		// spent on echoes and only then on authored citizens, so turning the dial up
		// can never take away somebody a human placed. A single distance comparator
		// would let an echo two tiles away displace an authored citizen ten tiles
		// away, which is exactly the trade nobody asked for.
		candidates.sort(Comparator
			.comparingInt((LivelyEntity e) -> e.getDefinition().isEcho() ? 1 : 0)
			.thenComparingInt(
				e -> RenderPolicy.tileDistance(playerLocation, e.getDefinition().getWorldLocation())));

		int deferred = 0;
		int planned = 0;
		for (LivelyEntity entity : candidates)
		{
			if (!RenderPolicy.hasCapacity(planned))
			{
				deferred++;
				continue;
			}
			entity.setWanted(true);
			planned++;
		}

		// Deactivate first, activate second — deliberately two passes over
		// inScope rather than one fused loop. The cap counts objects the client
		// has registered, and in a fused loop an entity still awaiting its
		// deactivation is registered while the next one activates, so the client
		// could transiently hold up to twice the cap. Ordering the passes makes
		// that impossible instead of harmless-in-practice: after this loop
		// everything registered is something this pass planned for, so the peak
		// equals the final count.
		int despawned = 0;
		for (LivelyEntity entity : inScope)
		{
			if (!entity.isWanted() && despawnQuietly(entity))
			{
				despawned++;
			}
		}

		// Over `candidates` rather than over `inScope`, and that is the ordering half of
		// the build budget below. The two loops cover the same set — `wanted` is cleared
		// for every in-scope entity above and set only inside the candidate loop, so
		// every wanted entity is a candidate — but only this list is sorted, authored
		// first and nearest first inside that. Spawning in scope order would have made
		// "which three get built this pass?" a question about the order region files
		// happen to list their records in, and a citizen the player is standing next to
		// could wait behind one twenty tiles away.
		int spawned = 0;
		int failed = 0;
		int built = 0;
		int buildsDeferred = 0;
		for (LivelyEntity entity : candidates)
		{
			if (!entity.isWanted())
			{
				continue;
			}

			if (entity.isActive())
			{
				// Already on screen. The one thing still worth asking is whether
				// an animation that missed a cold cache has turned up since — a
				// citizen that stands still has no other pass that would ever ask
				// again. Cheap: it returns on a field compare once the animation
				// is installed, which is every working entity.
				retryAnimationQuietly(entity);
				continue;
			}

			// "Is this the spawn that builds the model?" — two field reads either side
			// of the call, and the only way to tell a first spawn (which merges,
			// recolours and lights a model) from a reactivation (which does not) from
			// out here. A deferred build, i.e. a cold cache, leaves the model still null
			// and is deliberately not counted: it timed nothing.
			final boolean building = entity.getRenderedModel() == null;

			if (building && !RenderPolicy.hasBuildBudget(built))
			{
				// The burst brake. This pass has already built its
				// MAX_MODEL_BUILDS_PER_PASS models, so this one waits for the next —
				// 600ms later, which nobody can see, against the 53.73ms stutter that
				// forty builds in one tick produced. The wait is bounded on the other
				// side too: at a budget of nine, the densest shipped neighbourhood
				// finishes arriving in about five seconds rather than the fourteen the
				// 2026-08-29 run measured at three.
				//
				// Nothing else happens to it: spawn() is never called, so the entity
				// does not spend one of its MAX_MODEL_ATTEMPTS and does not touch its
				// retry backoff, and it cannot flicker because it was never active.
				// It stays wanted, which costs nothing — the deactivate loop is behind
				// us and the flag is cleared at the top of every pass — and the next
				// pass re-sorts it into the same place it is in now, at the front,
				// because it is nearer than everything still queued behind it.
				buildsDeferred++;
				continue;
			}

			final long buildStartedAt = building ? timings.start() : 0L;

			if (spawnQuietly(entity, worldView))
			{
				spawned++;
			}
			else
			{
				failed++;
			}

			if (building && entity.getRenderedModel() != null)
			{
				// Charged here, on completion, and not at the check above. The two come
				// apart on a cold model cache: loadParts() returns null, the model is
				// still null, and nothing was built — so charging on intent would let
				// three entities that did no work at all hold the budget shut for the
				// twenty-five passes LivelyEntity.RETRY_BACKOFF_PASSES makes them wait,
				// starving every entity behind them. A miss costs what it always cost.
				built++;

				// `planned` rather than a live count: it is what this pass is about to
				// have on screen, it is already in a local, and asking the client would
				// mean walking every wrapper once per model built.
				timings.recordModelBuild(buildStartedAt, planned);
			}
		}

		// Zero on the overwhelming majority of passes, and the meter ignores those: what
		// the report has to be able to say is how much building the budget moved, and
		// over how many passes it moved it.
		timings.recordBuildsDeferred(buildsDeferred);

		totalSpawns += spawned;
		totalDespawns += despawned;

		rebuildWalkers();

		// countActive() asks the client about every cached wrapper, so it only
		// ever runs when a line is actually going to be written. Passing it as a
		// log.debug argument evaluates it at every log level.
		if ((spawned > 0 || despawned > 0) && log.isDebugEnabled())
		{
			log.debug("visibility pass: +{} -{} (failed {}, {} build(s) held over), {} active of {} in scope",
				spawned, despawned, failed, buildsDeferred, countActive(), inScope.size());
		}

		if (reportNextPass)
		{
			reportNextPass = false;

			// The first pass of a session goes to info so "did it actually
			// spawn anything?" is answerable from a default-level log. Every
			// later scene load repeats it at debug — a border crossing is far
			// too frequent for info.
			boolean first = !firstReportDone;
			if (first || log.isDebugEnabled())
			{
				// The player's tile and region are in here because "I see
				// nothing" is almost always "you are not standing in a region
				// the dataset covers", and that is otherwise unanswerable from
				// a log.
				String summary = "Lively Cities: player at {} (region {}) — regions {}, "
					+ "{} definitions in scope ({} of them echoes), {} active ({} echoes), {} walking, "
					+ "{} switched off by the city/density/cameo settings, "
					+ "{} entity(ies) skipped because the collision map would not vouch for their tile, "
					+ "{} beyond the {}-tile cull or off-plane, {} deferred by the {}-object cap, "
					+ "{} model build(s) held over by the {}-per-pass build budget, {} unbuildable";
				Object[] args = {
					playerLocation,
					RenderPolicy.regionIdOf(playerLocation.getX(), playerLocation.getY()),
					scopeRegions,
					inScope.size(),
					getEchoInScopeCount(),
					countActive(),
					countActiveEchoes(),
					walkers.size(),
					offByConfig,
					onUnusableGround,
					inScope.size() - candidates.size() - offByConfig - onUnusableGround,
					cullRadius,
					deferred,
					RenderPolicy.MAX_ACTIVE_OBJECTS,
					buildsDeferred,
					RenderPolicy.MAX_MODEL_BUILDS_PER_PASS,
					countBroken(),
				};

				if (first)
				{
					firstReportDone = true;
					log.info(summary, args);
				}
				else
				{
					log.debug(summary, args);
				}
			}
		}
		else if (deferred > 0 || buildsDeferred > 0)
		{
			log.debug("{} entity(ies) deferred by the {}-object cap, "
					+ "{} model build(s) held over by the {}-per-pass build budget",
				deferred, RenderPolicy.MAX_ACTIVE_OBJECTS,
				buildsDeferred, RenderPolicy.MAX_MODEL_BUILDS_PER_PASS);
		}

		// What the client is left holding: everything this pass planned for, minus the
		// ones that could not be built or placed, minus the ones whose build the budget
		// held over. Those last two are counted apart because they are different things
		// — one is an entity the client would not give us a model for, the other is one
		// we chose not to ask about yet — and the meter has to be able to tell them
		// apart or the report starts describing a burst it did not measure. Arithmetic
		// on locals rather than a walk of every cached wrapper — see runVisibilityPass's
		// contract.
		return planned - failed - buildsDeferred;
	}

	/**
	 * Deactivates everything and forgets the current scope, so the next
	 * {@link #syncRegions} rebuilds it. Used on LOADING / HOPPING / logout.
	 *
	 * @return the number of objects deactivated
	 */
	int invalidate(String reason)
	{
		int cleared = deactivateAll();
		scopeRegions.clear();
		inScope.clear();
		walkers.clear();
		instanceReported = false;

		// A fresh scene gets a fresh cadence phase rather than inheriting one from
		// the world the player just left. deactivateAll() has already silenced
		// everybody; this is the clock.
		chatter.reset();

		if (cleared > 0)
		{
			log.debug("invalidated on {}: deactivated {} entity(ies)", reason, cleared);
		}

		return cleared;
	}

	/**
	 * Full teardown: deactivate everything, then drop every wrapper the client has
	 * actually let go of — and only those.
	 *
	 * <p><b>The one line here that is not bookkeeping is {@link #retainStillRegistered()}.</b>
	 * This method used to count what was still active, log an error about it, and clear
	 * {@link #built} anyway, which detected the leak and then made it permanent. Anything the
	 * client still has an object for now moves to {@link #retained} instead; see that field
	 * for why it does not simply stay where it is, and for what the other collections do
	 * about it (nothing — they clear, and that is the point).
	 *
	 * <p>The count comes from the sweep rather than from {@link #countActive()}, and that is
	 * the second defect in the same eleven lines. {@code countActive()} calls
	 * {@code isActive()} straight, so a client that throws when asked whether it still holds
	 * an object threw that exception out of this method — abandoning the teardown partway,
	 * with the overlay already deregistered, every cache still populated and a stack trace
	 * in the user's log. {@link #stillRegistered} contains it and answers "yes", the
	 * conservative direction: a wrapper still held can be despawned again, one already
	 * dropped cannot.
	 *
	 * @return the number of objects deactivated by this call
	 */
	int shutdown()
	{
		int cleared = deactivateAll();
		int held = retainStillRegistered();

		log.info("Lively Cities teardown: deactivated {} object(s); {} still held; "
				+ "session totals {} spawn(s) / {} despawn(s)",
			cleared, held, totalSpawns, totalDespawns);

		if (held != 0)
		{
			log.warn("Lively Cities could not deactivate {} object(s) at teardown — keeping the "
				+ "reference(s) so a later teardown can try again, rather than leaking the object(s)", held);
		}

		built.clear();
		lastInScope.clear();
		parsed.clear();
		withoutData.clear();
		scopeRegions.clear();
		inScope.clear();
		walkers.clear();
		unmappedReported.clear();
		chatter.reset();
		scopeGeneration = 0;
		reportNextPass = false;
		instanceReported = false;
		firstReportDone = false;
		return cleared;
	}

	/**
	 * The in-scope wrappers, live and read-only.
	 *
	 * <p>Handed out for the two readers that have to see the set as it is
	 * <i>right now</i>: {@code ChatterOverlay}, once a frame, and
	 * {@link CitizenMenu}, once a right-click. It is the live list rather than a
	 * snapshot on purpose — see {@link #inScopeView}.
	 */
	List<LivelyEntity> inScopeEntities()
	{
		return inScopeView;
	}

	/**
	 * Everything the side panel wants to know, read in one pass — see
	 * {@link SceneCensus}.
	 *
	 * <p><b>One walk of {@link #built}, not one per figure asked for.</b> The
	 * alternative is {@link #countActive()} plus a per-city variant plus
	 * {@link #getWalkerCount()}, which is two walks of every cached wrapper and two
	 * rounds of {@code isActive()} calls into the client for numbers that then have to
	 * agree with each other — and would not, because they were taken at different
	 * moments.
	 *
	 * <p><b>Walks {@link #built} rather than {@link #inScope}, for the same reason
	 * {@link #countActive()} does</b>: an object still registered but out of scope is
	 * the leak every count in this class exists to be able to see, and a census that
	 * could not see it would flatter the scene rather than describe it.
	 *
	 * <p><b>The per-city key is {@link EntityDefinition#getCityRegionId()}</b>, which
	 * for an echo is its source's region and not its own tile's — so a derived figure
	 * is counted against the city whose checkbox governs it, and the breakdown adds up
	 * to the same total the cards' checkboxes control. A region no {@link City} claims
	 * contributes to {@link SceneCensus#getActive()} and to no card, which is the
	 * honest outcome: it is exactly the entity no checkbox can switch off.
	 *
	 * <p>Client thread, like everything else here — it asks the client whether each
	 * object is registered, straight, the way {@link #countActive()} does.
	 */
	SceneCensus census()
	{
		EnumMap<City, Integer> activeByCity = new EnumMap<>(City.class);
		int active = 0;

		for (List<LivelyEntity> entities : built.values())
		{
			for (LivelyEntity entity : entities)
			{
				if (!entity.isActive())
				{
					continue;
				}

				active++;
				City city = City.of(entity.getDefinition().getCityRegionId());
				if (city != null)
				{
					activeByCity.merge(city, 1, Integer::sum);
				}
			}
		}

		return new SceneCensus(active, inScope.size(), walkers.size(), countTalking(), activeByCity);
	}

	/**
	 * @return how many citizens currently have a remark on screen. For the tests and
	 * for the log line; the overlay counts nothing and asks nobody.
	 */
	int countTalking()
	{
		int n = 0;
		for (LivelyEntity entity : inScope)
		{
			CitizenRemarks remarks = entity.getRemarks();
			if (remarks != null && remarks.isTalking())
			{
				n++;
			}
		}
		return n;
	}

	/** @return the chatter clock, so a test can assert what does and does not advance it */
	int getChatterTick()
	{
		return chatter.getTick();
	}

	/**
	 * @return how many objects the client currently reports as registered, out of the
	 * wrappers this scene is still driving. Deliberately not counting {@link #retained}:
	 * every caller is describing the live scene, and a held wrapper is by definition no
	 * longer part of one. {@link #getRetainedCount()} is the other half. Asks the client
	 * straight, so it throws if the client does — {@link #shutdown()} uses
	 * {@link #stillRegistered} instead for exactly that reason.
	 */
	int countActive()
	{
		int n = 0;
		for (List<LivelyEntity> entities : built.values())
		{
			for (LivelyEntity entity : entities)
			{
				if (entity.isActive())
				{
					n++;
				}
			}
		}
		return n;
	}

	/**
	 * {@link #countActive()}, restricted to one side of the authored/derived line.
	 *
	 * <p>Walks {@link #built} rather than {@link #inScope} for the same reason
	 * {@link #countActive()} does: an entity active but out of scope is the leak
	 * these counts exist to catch, and a counter that could not see it would be
	 * green for the wrong reason.
	 */
	private int countActive(boolean echoes)
	{
		int n = 0;
		for (List<LivelyEntity> entities : built.values())
		{
			for (LivelyEntity entity : entities)
			{
				if (entity.isActive() && entity.getDefinition().isEcho() == echoes)
				{
					n++;
				}
			}
		}
		return n;
	}

	int countBroken()
	{
		int n = 0;
		for (LivelyEntity entity : inScope)
		{
			if (entity.isBroken())
			{
				n++;
			}
		}
		return n;
	}

	int getInScopeCount()
	{
		return inScope.size();
	}

	/**
	 * @return how many of the wrappers in scope are {@link CitizenEcho}-derived.
	 * Echoes are built alongside their sources whatever the density dial says (see
	 * {@link #ensureBuilt}), so this is non-zero at every level — what changes with
	 * the dial is {@link #countActiveEchoes()}.
	 */
	int getEchoInScopeCount()
	{
		int n = 0;
		for (LivelyEntity entity : inScope)
		{
			if (entity.getDefinition().isEcho())
			{
				n++;
			}
		}
		return n;
	}

	/**
	 * @return how many <i>echoes</i> the client currently has registered. Asks the
	 * client, like {@link #countActive()}, so "FULL shows no echoes" is evidence
	 * rather than bookkeeping.
	 */
	int countActiveEchoes()
	{
		return countActive(true);
	}

	/** @return how many <i>authored</i> entities the client currently has registered. */
	int countActiveAuthored()
	{
		return countActive(false);
	}

	/**
	 * @return how many wandering citizens the per-frame pass is currently
	 * interpolating
	 */
	int getWalkerCount()
	{
		return walkers.size();
	}

	/**
	 * The wrapper this scene is driving for a definition, or {@code null} if it
	 * holds none.
	 *
	 * <p>Package-visible for the movement tests, and needed rather than
	 * convenient: the walk state lives on the wrapper, so a test that built its
	 * own {@link CitizenWalk} from the same definition would be asserting about a
	 * copy the scene has never touched.
	 */
	@Nullable
	LivelyEntity wrapperFor(EntityDefinition definition)
	{
		for (List<LivelyEntity> entities : built.values())
		{
			for (LivelyEntity entity : entities)
			{
				if (entity.getDefinition() == definition)
				{
					return entity;
				}
			}
		}
		return null;
	}

	/**
	 * The wrapper for a uuid, or {@code null}.
	 *
	 * <p>{@link #wrapperFor(EntityDefinition)} compares by identity, which is right
	 * for an authored definition — the loader hands out the same object every time —
	 * and useless for an echo, which {@link #ensureBuilt} rederives on every region
	 * build. The uuid is the thing that survives that, which is exactly why
	 * {@link CitizenEcho} derives one.
	 */
	@Nullable
	LivelyEntity wrapperForUuid(UUID uuid)
	{
		for (List<LivelyEntity> entities : built.values())
		{
			for (LivelyEntity entity : entities)
			{
				if (uuid.equals(entity.getDefinition().getUuid()))
				{
					return entity;
				}
			}
		}
		return null;
	}

	/**
	 * Whether the config lets this entity be shown at all.
	 *
	 * <p>Neither question is asked of the file the entity was loaded from. The hide
	 * is asked of its own uuid; the city is asked of
	 * {@link EntityDefinition#getCityRegionId()}, which for an authored entity is the
	 * region of the tile it stands on — the same reason scope membership is keyed on
	 * the tile — and for an echo is the region governing the citizen it was derived
	 * from. Those differ for four shipped echoes, and asking the echo's own tile is
	 * how they used to escape every checkbox: see {@code getCityRegionId()}.
	 */
	private boolean allowedByConfig(
		EntityDefinition definition,
		CrowdDensity density,
		Set<UUID> hiddenUuids,
		boolean cameosAllowed)
	{
		// Hidden first, and cheapest: it is a hash lookup, and it is the only one of
		// the three the user chose for this specific citizen. Upstream issue #40.
		//
		// Asked of the entity's OWN uuid, which for an echo is not its source's — so
		// hiding a citizen hides that citizen and not the stranger derived from it,
		// and each is independently hideable. That is the whole reason CitizenEcho
		// derives a uuid instead of borrowing one.
		if (hiddenUuids.contains(definition.getUuid()))
		{
			return false;
		}

		// The opt-in. An echo is only ever wanted at CROWDED, so FULL yields exactly
		// the authored set — and turning the dial back down despawns the echoes on
		// the click, through the same "what is not wanted is despawned" rule the city
		// checkboxes use.
		if (definition.isEcho() && !density.includesEchoes())
		{
			return false;
		}

		// The other opt-in, and the stricter one: a cameo needs its own checkbox
		// ticked AND its city's, so this is a second gate rather than a replacement
		// for the City.isEnabled call below. Deliberately not folded into that call —
		// City fails open for a region no constant claims (see
		// EntityDefinition.getCityRegionId), and player-shaped content must never
		// inherit a fail-open.
		if (definition.isCameo() && !cameosAllowed)
		{
			return false;
		}

		int regionId = definition.getCityRegionId();
		if (City.of(regionId) == null && unmappedReported.add(regionId))
		{
			// Only ever an authored entity now: an echo is judged by its source's
			// region, and a source is authored. So this line still means what it says —
			// "a region file has landed without a checkbox" — rather than also firing
			// for a derived citizen that stepped over a border.
			log.warn("Lively Cities: region {} has no city in the City enum, so it cannot be switched off — "
				+ "showing it anyway", regionId);
		}

		return City.isEnabled(regionId, config) && density.keeps(definition.stableHash());
	}

	/**
	 * Whether the ground under an entity is ground a person could stand on — asked
	 * only of the entities whose tile <b>this plugin</b> chose rather than a human
	 * standing on it in game.
	 *
	 * <p>Two such kinds, and they are disjoint:
	 * <ul>
	 *   <li><b>An echo</b>, whose tile is arithmetic. {@link CitizenEcho#isPlaceable}
	 *       owns that case, including its wander-box fallback for
	 *       {@link StandableGround.Verdict#UNKNOWN}.</li>
	 *   <li><b>A cameo</b>, whose tile was authored off a wiki map rather than by
	 *       walking to it — nobody has stood in the Grand Exchange and confirmed the
	 *       six tiles are open floor. {@code UNKNOWN} is <b>not</b> admitted here:
	 *       there is no wander box to fall back to and nothing has vouched for the
	 *       ground, so a cameo appears once the collision map says yes and not
	 *       before. In practice that is the tick the scene finishes building, because
	 *       an entity outside the loaded scene has already failed
	 *       {@code LocalPoint.fromWorld} in {@code LivelyEntity.spawn}.</li>
	 * </ul>
	 *
	 * <p>The failure mode this buys is the right one: a cameo standing inside a bank
	 * booth is exactly the "broken-looking fake" that got the predecessor plugin
	 * disabled, and a cameo that silently does not appear is a bug report with a tile
	 * in it. The count lands in the visibility-pass log line either way.
	 *
	 * <p>Reads the live collision map, so: client thread, and only for entities
	 * already inside the cull radius.
	 */
	private static boolean groundIsUsable(WorldView worldView, EntityDefinition definition)
	{
		if (definition.isCameo())
		{
			return StandableGround.verdict(worldView, definition.getWorldLocation())
				== StandableGround.Verdict.STANDABLE;
		}

		return CitizenEcho.isPlaceable(worldView, definition);
	}

	/**
	 * Rebuilds the per-frame work list from scratch at the end of every visibility
	 * pass.
	 *
	 * <p>Rebuilt rather than incrementally maintained on purpose: the list has to
	 * be a subset of what is both active and in scope, and both of those change
	 * for half a dozen reasons (cull radius, cap, city toggle, eviction, a
	 * structural failure). One loop over {@link #inScope} once a game tick is
	 * cheaper than being wrong.
	 */
	private void rebuildWalkers()
	{
		walkers.clear();
		for (LivelyEntity entity : inScope)
		{
			if (entity.getWalk() != null && entity.isActive())
			{
				walkers.add(entity);
			}
		}
	}

	/**
	 * Steps every active wanderer one tile. Runs after the visibility pass, so the
	 * list is the one that pass just built.
	 *
	 * <p>Private, and called from {@link #onGameTick} and nowhere else. A second
	 * caller is a second definition of how fast a citizen walks.
	 */
	private void stepWalkers()
	{
		for (int i = 0; i < walkers.size(); i++)
		{
			stepQuietly(walkers.get(i));
		}
	}

	/**
	 * @return how many region files' wrappers are currently cached
	 */
	int getCachedRegionCount()
	{
		return built.size();
	}

	/**
	 * @return how many wrappers this scene is holding on to because the client would not
	 * release their objects. Zero on every ordinary run — see {@link #retained}.
	 */
	int getRetainedCount()
	{
		return retained.size();
	}

	/**
	 * Walks every wrapper currently cached, not just the ones in scope — that is
	 * the whole point.
	 *
	 * <p>{@link #retained} is walked too, and it is the only thing that ever gets those
	 * objects off the screen: a wrapper the client refused to release at an earlier
	 * teardown is precisely the one worth asking about again. It costs one pass over an
	 * empty list on every ordinary run.
	 */
	private int deactivateAll()
	{
		int cleared = 0;
		for (List<LivelyEntity> entities : built.values())
		{
			cleared += deactivateEach(entities);
		}
		cleared += deactivateEach(retained);
		totalDespawns += cleared;
		return cleared;
	}

	private int deactivateEach(List<LivelyEntity> entities)
	{
		int cleared = 0;
		for (LivelyEntity entity : entities)
		{
			entity.setWanted(false);
			if (despawnQuietly(entity))
			{
				cleared++;
			}
		}
		return cleared;
	}

	/**
	 * Moves every wrapper the client still has an object for out of {@link #built} and into
	 * {@link #retained}, and lets go of the ones it does not — {@link #shutdown()} clears
	 * {@link #built} on the next line, so what is not moved here is dropped.
	 *
	 * <p>{@link #retained} is swept first: a wrapper held over from an earlier teardown that
	 * this one managed to deactivate is a reference nothing needs any more, and a list that
	 * only ever grows is its own kind of leak.
	 *
	 * @return how many wrappers are being held when this returns
	 */
	private int retainStillRegistered()
	{
		retained.removeIf(entity -> !stillRegistered(entity));

		for (List<LivelyEntity> entities : built.values())
		{
			for (LivelyEntity entity : entities)
			{
				if (stillRegistered(entity))
				{
					retained.add(entity);
				}
			}
		}

		return retained.size();
	}

	/**
	 * @return whether the client still has this entity's object, treating a throw as
	 * "yes". A client that will not answer is not one to take at its word, and the two
	 * mistakes are not symmetrical: a wrapper still held can be despawned again, one
	 * already dropped cannot.
	 */
	private static boolean stillRegistered(LivelyEntity entity)
	{
		try
		{
			return entity.isActive();
		}
		catch (RuntimeException e)
		{
			log.warn("{}: threw when asked whether it is still registered",
				entity.getDefinition().label(), e);
			return true;
		}
	}

	private void ensureBuilt(int regionId)
	{
		if (built.containsKey(regionId))
		{
			return;
		}

		// Metered from here rather than from syncRegions, because "what does one region
		// file cost?" is the question with a threshold against it — a crossing brings in
		// between zero and nine files and their sizes differ by a factor of forty. The
		// clock starts before load() so that the JSON parse is inside the figure the
		// first time a file is seen, and a region whose wrappers were evicted and are
		// being rebuilt is still a sample: it really does pay the echo derivation and
		// the wrapper construction again.
		final long startedAt = timings.start();

		RegionDefinition region = load(regionId);
		if (region == null)
		{
			// No file for this region, or it would not parse. Nothing happened, so
			// nothing is recorded — a meter full of zero-cost non-events would drag every
			// percentile of a real load down.
			return;
		}

		List<EntityDefinition> authored = region.getEntities();
		List<LivelyEntity> entities = new ArrayList<>(authored.size());
		for (EntityDefinition definition : authored)
		{
			entities.add(new LivelyEntity(client, definition));
		}

		// Echoes are built whatever the density dial says, and gated in
		// allowedByConfig instead. That is not laziness: it means an echo enters
		// and leaves scope, is evicted, is torn down, and is despawned by exactly
		// the same code as an authored citizen, so "CROWDED inherits every
		// constraint" is structural rather than a list of places to remember. The
		// cost of an unwanted echo is one wrapper with no model — LivelyEntity
		// builds the model on its first spawn — so a FULL user pays a few hundred
		// bytes per region in scope and nothing else.
		//
		// The whole roster goes in at once because separation is a claim about a tile
		// and everything standing near it: derived one citizen at a time, echoes stood
		// inside other citizens and inside each other. This is the only caller, so the
		// region file is always complete here — see CitizenEcho.echoesOfRegion.
		List<EntityDefinition> echoes = CitizenEcho.echoesOfRegion(authored);
		for (EntityDefinition echo : echoes)
		{
			entities.add(new LivelyEntity(client, echo));
		}
		built.put(regionId, entities);

		// Everything above, timed: the parse, every EntityDefinition.fromRecord, the
		// echo derivation and the wrappers. It is the only per-tick work this plugin does
		// that the visibility meter never saw — syncRegions runs before the pass, not
		// inside it — and it is charged to the game-tick total by FrameTimings.
		timings.recordRegionLoad(startedAt, entities.size());

		if (!echoes.isEmpty())
		{
			log.debug("region {}: {} authored entity(ies) seeded {} echo(es) for the Crowded density",
				regionId, region.getEntityCount(), echoes.size());
		}
	}

	/**
	 * Drops the wrappers for regions that have been out of scope for longer than
	 * {@link #EVICTION_GRACE_SCOPE_CHANGES} scope changes, deactivating them on
	 * the way out.
	 *
	 * <p>Eviction is keyed on the region file, while membership is keyed on the
	 * entity's tile, so a misfiled entity is dropped when the file's region
	 * leaves scope even if its own region has not. That costs a rebuild and
	 * nothing else: for the file's region to be out of scene at all the player
	 * has to be further away than the cull radius could ever reach.
	 *
	 * <p>The despawn below is a backstop, not a reachable path — mutation testing
	 * confirms no test can tell it from a no-op. {@link #syncRegions} deactivates
	 * everything in {@link #inScope} before calling this, and anything active is
	 * by definition in scope, so there is never anything left for it to do. It
	 * stays because "we are about to forget about this wrapper" is exactly the
	 * place a future caller would get it wrong.
	 *
	 * @return the number of regions dropped
	 */
	private int evictStaleRegions()
	{
		int dropped = 0;
		Iterator<Map.Entry<Integer, List<LivelyEntity>>> it = built.entrySet().iterator();
		while (it.hasNext())
		{
			Map.Entry<Integer, List<LivelyEntity>> entry = it.next();
			Long last = lastInScope.get(entry.getKey());
			if (last != null && scopeGeneration - last <= EVICTION_GRACE_SCOPE_CHANGES)
			{
				continue;
			}

			for (LivelyEntity entity : entry.getValue())
			{
				entity.setWanted(false);
				if (despawnQuietly(entity))
				{
					totalDespawns++;
				}
			}

			it.remove();
			lastInScope.remove(entry.getKey());
			dropped++;
		}

		if (dropped > 0)
		{
			log.debug("evicted {} region(s) out of scope for more than {} scene load(s), {} still cached",
				dropped, EVICTION_GRACE_SCOPE_CHANGES, built.size());
		}

		return dropped;
	}

	/**
	 * {@link LivelyEntity} contains its own failures; this is the backstop for a
	 * throw from anywhere else in a loop body. The passes run from EventBus
	 * handlers, where an escaping exception abandons every entity after this one
	 * — including the ones that were supposed to deactivate — and then does it
	 * again on the next tick.
	 */
	private boolean spawnQuietly(LivelyEntity entity, WorldView worldView)
	{
		try
		{
			return entity.spawn(worldView);
		}
		catch (RuntimeException e)
		{
			entity.markBroken();
			log.warn("{}: dropped from the scene after an unexpected failure while spawning",
				entity.getDefinition().label(), e);
			return false;
		}
	}

	/**
	 * Same containment rule as {@link #spawnQuietly}: it reaches
	 * {@code client.loadAnimation}, and it runs from a loop that has to reach
	 * every other entity.
	 */
	private void retryAnimationQuietly(LivelyEntity entity)
	{
		try
		{
			entity.retryMissingAnimation();
		}
		catch (RuntimeException e)
		{
			entity.markBroken();
			despawnQuietly(entity);
			log.warn("{}: dropped from the scene after an unexpected failure while re-requesting its animation",
				entity.getDefinition().label(), e);
		}
	}

	private boolean despawnQuietly(LivelyEntity entity)
	{
		try
		{
			return entity.despawn();
		}
		catch (RuntimeException e)
		{
			entity.markBroken();
			log.warn("{}: dropped from the scene after an unexpected failure while despawning",
				entity.getDefinition().label(), e);
			return false;
		}
	}

	/**
	 * Same containment rule as {@link #spawnQuietly}, and it matters more here:
	 * the frame version of this runs sixty times a second, so an escaping
	 * exception would be sixty stack traces a second <i>and</i> would abandon
	 * every wanderer after the offender on every one of them.
	 */
	private void stepQuietly(LivelyEntity entity)
	{
		try
		{
			entity.advanceTick();
		}
		catch (RuntimeException e)
		{
			entity.markBroken();
			despawnQuietly(entity);
			log.warn("{}: dropped from the scene after an unexpected failure while walking",
				entity.getDefinition().label(), e);
		}
	}

	private void frameQuietly(LivelyEntity entity, WorldView worldView, float fraction)
	{
		try
		{
			entity.advanceFrame(worldView, fraction);
		}
		catch (RuntimeException e)
		{
			entity.markBroken();
			despawnQuietly(entity);
			log.warn("{}: dropped from the scene after an unexpected failure while interpolating",
				entity.getDefinition().label(), e);
		}
	}

	private RegionDefinition load(int regionId)
	{
		if (withoutData.contains(regionId))
		{
			return null;
		}

		RegionDefinition region = parsed.get(regionId);
		if (region != null)
		{
			return region;
		}

		region = loader.loadRegion(regionId);
		if (region == null)
		{
			withoutData.add(regionId);
			return null;
		}

		parsed.put(regionId, region);
		log.info("Lively Cities: loaded region {} — {} citizen(s), {} scenery, {} skipped (schema v{})",
			regionId, region.getCitizenCount(), region.getSceneryCount(),
			region.getSkippedRecords(), region.getVersion());
		return region;
	}

	private boolean sameAsScope(int[] regions)
	{
		if (regions.length != scopeRegions.size())
		{
			return false;
		}

		for (int i = 0; i < regions.length; i++)
		{
			if (scopeRegions.get(i) != regions[i])
			{
				return false;
			}
		}

		return true;
	}
}
