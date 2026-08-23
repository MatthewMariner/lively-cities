package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * <p><b>Two clocks.</b> {@link #onGameTick} is the per-game-tick pass: sync the
 * scope, decide who is visible, step the wanderers one tile.
 * {@link #onFrame} is the per-frame pass, and it does exactly one thing —
 * interpolate the wanderers' drawn positions. It walks a maintained list of
 * active wanderers rather than the whole cache, because a frame handler that
 * scans every wrapper is a frame handler that shows up in a profile.
 *
 * <p><b>Where a border-crossing wanderer lives.</b> Scope membership is decided
 * once, from the entity's authored tile, and a citizen walking across a region
 * border does not change it. Six of the 63 wanderers have boxes that straddle
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

	/** Wrappers whose tile is in a region the currently loaded scene covers. */
	private final List<LivelyEntity> inScope = new ArrayList<>();

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
	EntityScene(Client client, RegionDataLoader loader, LivelyCitiesConfig config)
	{
		this.client = client;
		this.loader = loader;
		this.config = config;
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
	}

	/**
	 * One frame of visual interpolation for the active wanderers, and nothing
	 * else.
	 *
	 * @param fraction how far through the current game tick this frame is, 0..1
	 */
	void onFrame(WorldView worldView, float fraction)
	{
		for (int i = 0; i < walkers.size(); i++)
		{
			frameQuietly(walkers.get(i), worldView, fraction);
		}
	}

	/**
	 * Activates the entities that should be visible and deactivates the rest.
	 *
	 * @param playerLocation the local player's world location, never null
	 */
	void updateVisibility(WorldPoint playerLocation, WorldView worldView)
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
			return;
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

		List<LivelyEntity> candidates = new ArrayList<>();
		int offByConfig = 0;
		for (LivelyEntity entity : inScope)
		{
			entity.setWanted(false);
			if (entity.isBroken())
			{
				continue;
			}
			if (!allowedByConfig(entity.getDefinition(), density))
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
				candidates.add(entity);
			}
		}

		// Nearest first, so the cap sheds the far edge of the crowd rather than
		// whatever the region files happened to list last.
		candidates.sort(Comparator.comparingInt(
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

		int spawned = 0;
		int failed = 0;
		for (LivelyEntity entity : inScope)
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

			if (spawnQuietly(entity, worldView))
			{
				spawned++;
			}
			else
			{
				failed++;
			}
		}

		totalSpawns += spawned;
		totalDespawns += despawned;

		rebuildWalkers();

		// countActive() asks the client about every cached wrapper, so it only
		// ever runs when a line is actually going to be written. Passing it as a
		// log.debug argument evaluates it at every log level.
		if ((spawned > 0 || despawned > 0) && log.isDebugEnabled())
		{
			log.debug("visibility pass: +{} -{} (failed {}), {} active of {} in scope",
				spawned, despawned, failed, countActive(), inScope.size());
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
					+ "{} definitions in scope, {} active, {} walking, "
					+ "{} switched off by the city/density settings, "
					+ "{} beyond the {}-tile cull or off-plane, {} deferred by the {}-object cap, {} unbuildable";
				Object[] args = {
					playerLocation,
					RenderPolicy.regionIdOf(playerLocation.getX(), playerLocation.getY()),
					scopeRegions,
					inScope.size(),
					countActive(),
					walkers.size(),
					offByConfig,
					inScope.size() - candidates.size() - offByConfig,
					cullRadius,
					deferred,
					RenderPolicy.MAX_ACTIVE_OBJECTS,
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
		else if (deferred > 0)
		{
			log.debug("{} entity(ies) deferred by the {}-object cap", deferred, RenderPolicy.MAX_ACTIVE_OBJECTS);
		}
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

		if (cleared > 0)
		{
			log.debug("invalidated on {}: deactivated {} entity(ies)", reason, cleared);
		}

		return cleared;
	}

	/**
	 * Full teardown: deactivate everything, drop every wrapper and cache.
	 *
	 * @return the number of objects deactivated by this call
	 */
	int shutdown()
	{
		int cleared = deactivateAll();
		int remaining = countActive();

		log.info("Lively Cities teardown: deactivated {} object(s); {} still active; "
				+ "session totals {} spawn(s) / {} despawn(s)",
			cleared, remaining, totalSpawns, totalDespawns);

		if (remaining != 0)
		{
			log.error("Lively Cities leaked {} active object(s) on shutdown", remaining);
		}

		built.clear();
		lastInScope.clear();
		parsed.clear();
		withoutData.clear();
		scopeRegions.clear();
		inScope.clear();
		walkers.clear();
		unmappedReported.clear();
		scopeGeneration = 0;
		reportNextPass = false;
		instanceReported = false;
		firstReportDone = false;
		return cleared;
	}

	/**
	 * @return how many objects the client currently reports as registered
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
	 * Whether the config lets this entity be shown at all.
	 *
	 * <p>Both questions are asked of the entity's <b>tile</b> region and its own
	 * uuid, not of the file it was loaded from. Which city a citizen is in is a
	 * question about where it stands — the same reason scope membership is keyed
	 * on the tile.
	 */
	private boolean allowedByConfig(EntityDefinition definition, CrowdDensity density)
	{
		int regionId = definition.getTileRegionId();
		if (City.of(regionId) == null && unmappedReported.add(regionId))
		{
			log.warn("Lively Cities: region {} has no city in the City enum, so it cannot be switched off — "
				+ "showing it anyway", regionId);
		}

		return City.isEnabled(regionId, config) && density.keeps(definition.stableHash());
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
	 * Walks every wrapper currently cached, not just the ones in scope — that is
	 * the whole point.
	 */
	private int deactivateAll()
	{
		int cleared = 0;
		for (List<LivelyEntity> entities : built.values())
		{
			for (LivelyEntity entity : entities)
			{
				entity.setWanted(false);
				if (despawnQuietly(entity))
				{
					cleared++;
				}
			}
		}
		totalDespawns += cleared;
		return cleared;
	}

	private void ensureBuilt(int regionId)
	{
		if (built.containsKey(regionId))
		{
			return;
		}

		RegionDefinition region = load(regionId);
		if (region == null)
		{
			return;
		}

		List<LivelyEntity> entities = new ArrayList<>(region.getEntityCount());
		for (EntityDefinition definition : region.getEntities())
		{
			entities.add(new LivelyEntity(client, definition));
		}
		built.put(regionId, entities);
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
