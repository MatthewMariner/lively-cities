package com.matthewmariner.livelycities;

import net.runelite.api.Constants;
import net.runelite.api.coords.WorldPoint;

/**
 * The safety caps, and the arithmetic behind them.
 *
 * <p>Everything here is static and client-free so it can be tested directly. The
 * object cap is a constant on purpose (see {@link #MAX_ACTIVE_OBJECTS}); the cull
 * radius is a config dial, and this class owns the bounds it is clamped to.
 */
public final class RenderPolicy
{
	/**
	 * Hard ceiling on simultaneously active {@code RuneLiteObject}s.
	 *
	 * <p>The densest neighbourhood in the shipped dataset is in Varrock (regions
	 * 12852/12853, which hold 81 of the 184 entities between them): 59 entities
	 * inside a 25-tile square, 76 inside a {@link #MAX_CULL_RADIUS}-tile one. So
	 * this is headroom rather than a routine constraint — it exists so a future
	 * region file cannot make the client build hundreds of models in one tick.
	 * {@code RenderPolicyTest} recomputes that density from the shipped files
	 * rather than trusting the numbers in this sentence.
	 *
	 * <p><b>Both figures are measured from an arbitrary tile</b>, because that is
	 * what the cull check measures from: {@link #isCandidate} runs from wherever the
	 * player happens to be standing, not from a citizen's own tile. The densest
	 * window <i>centred on an entity</i> is a smaller number and it bounds nothing
	 * — writing it here is the drift {@code CrowdedSceneTest} warns about and
	 * {@code RenderPolicyTest} now fails on.
	 *
	 * <p>Not a config dial: its job is to stop a future region file asking the
	 * client to build hundreds of models in one tick, which is a guard rather than
	 * a preference. Do not mistake it for slack — the densest neighbourhood in the
	 * shipped data holds 76 entities at {@link #MAX_CULL_RADIUS}, four short of
	 * this. An earlier version of this comment justified the constant by claiming
	 * field runs "peaked at 16 active"; that figure was never recorded and is not
	 * evidence of anything.
	 */
	public static final int MAX_ACTIVE_OBJECTS = 80;

	/**
	 * What one region-crossing tick is allowed to cost, in microseconds: 16667, one
	 * frame at 60fps.
	 *
	 * <p><b>This replaces a threshold rather than inventing one, and it is stricter
	 * than what it replaces.</b> {@link FrameTimings} registered "no more than 100ms for
	 * the burst that lands on entering a dense region" before anything was measured.
	 * 100ms is six frames, and it was written when there was no per-pass cap at all — so
	 * it was a statement about what the plugin would tolerate rather than about what it
	 * would do. Now that the burst is something this class bounds, the acceptable figure
	 * is the one the cap can actually hold: a crossing costs the player at most one
	 * dropped frame, once, at a border where the client is already rebuilding a 104x104
	 * scene.
	 *
	 * <p><b>Why not the visibility pass's own 2ms line, which the cap used to be
	 * derived from.</b> That line was registered for the per-tick decision work — a cost
	 * paid every 600ms for as long as the plugin is on — and "half a frame, on a
	 * schedule, is a rhythmic stutter" is the sentence that justifies its 8ms sibling. A
	 * model build is not that: it is paid once per entity, at a crossing, and never
	 * again for that entity. Charging a once-per-crossing burst against a rhythmic-cost
	 * threshold is what made the old derivation say three, and re-running it against the
	 * honest 2026-08-29 figures would have said <i>two</i> — a tighter cap and a crowd
	 * that took twenty-one seconds to arrive, paying for a spike the measurement has
	 * since attributed elsewhere. The visibility meter is now exclusive of building (see
	 * {@link FrameTimings}), so the 2ms line still applies, to a figure it describes.
	 */
	static final int CROSSING_TICK_BUDGET_MICROS = 16_667;

	/**
	 * What a visibility pass costs when it builds nothing: 151µs.
	 *
	 * <p>The measured median pass, 300 game ticks in Varrock on 2026-08-29 (317 samples,
	 * 132 model builds). The median is the right term for "the pass minus its building":
	 * most passes build nothing, and a single median model build is 600µs — four times
	 * this — so a pass at the median plainly contains no build. (124µs in the previous
	 * run, against a smaller dataset; the pass walks everything in scope, and the scene
	 * around Varrock square now holds 164 definitions rather than 151.)
	 */
	static final int MEASURED_PASS_OVERHEAD_MICROS = 151;

	/**
	 * What the region files one scene load brings in cost to parse, derive echoes for
	 * and wrap: 3000µs.
	 *
	 * <p>Charged here because it lands on the <i>same game tick</i> as the burst below —
	 * {@code LivelyCitiesPlugin.tick()} calls {@code syncRegions} and then the visibility
	 * pass — so a budget that ignored it would be a budget for a tick that does not
	 * happen. It is the one term here measured off the client rather than in it:
	 * {@code EntityScene.ensureBuilt}'s whole path is a pure function of the shipped
	 * JSON, so it was timed directly against {@code src/main/resources/RegionData} over
	 * every 3x3 block of regions a 104x104 scene can bring in at once. The worst block
	 * came to 2997µs, of which region 12853 alone — 57 authored entities seeding 53
	 * echoes, the densest file that ships — is 1913µs.
	 *
	 * <p>{@link FrameTimings} now meters this in the field too, under {@code region
	 * load}, so the next report can replace this figure with one measured in the client
	 * instead of beside it.
	 */
	static final int MEASURED_REGION_LOAD_MICROS = 3_000;

	/**
	 * What one model build costs <b>inside a burst</b>: 1400µs, the measured p95 over
	 * 132 builds.
	 *
	 * <p>The <b>p95</b> rather than the mean (712µs), and that change is what this
	 * re-derivation turns on. The old constant argued that "the expected value of a sum
	 * is the sum of the means whatever the shape of the distribution", which is true of
	 * independent draws and false of a burst: the builds in one are consecutive, contend
	 * for the same model cache and the same young generation, and are exactly the samples
	 * in the tail. The measurement says so directly — the 53.73ms pass that produced the
	 * original cap was roughly forty builds, i.e. <b>1.34ms a build</b>, against a mean
	 * of 570µs. Budgeting a burst on the mean understates it by a factor of two; 1.34ms
	 * is what the only burst ever measured actually cost per build, and the p95 is the
	 * published statistic nearest to it.
	 *
	 * <p>Not the p99 (1.70ms) and not the maximum (8.61ms): assuming every build in a
	 * burst lands in the worst one percent yields a cap small enough that the crowd never
	 * arrives, and no per-pass cap can bound the maximum anyway — a pass containing an
	 * 8.61ms build costs at least 8.61ms however low the cap goes.
	 */
	static final int MEASURED_BURST_MODEL_BUILD_MICROS = 1_400;

	/**
	 * Hard ceiling on the number of models one visibility pass may <b>build</b>: nine.
	 *
	 * <p><b>Not the same thing as {@link #MAX_ACTIVE_OBJECTS}, and conflating them is
	 * the mistake this constant exists to avoid.</b> That one is a ceiling on how many
	 * objects the client may have registered at once — a memory and draw-call bound,
	 * spent down and refunded as the player walks. This one is a ceiling on how much
	 * <i>work</i> a single pass may do, spent and refunded every 600ms. A pass may
	 * activate eighty objects and build nine of them; the other seventy-one are models
	 * it already holds, and reactivating one is free.
	 *
	 * <p><b>Where the nine comes from.</b> A crossing tick pays for the region files it
	 * brought in, then for the pass that decides who is visible, then for whatever that
	 * pass built:
	 *
	 * <pre>
	 *   tick = MEASURED_REGION_LOAD_MICROS + MEASURED_PASS_OVERHEAD_MICROS
	 *          + builds x MEASURED_BURST_MODEL_BUILD_MICROS
	 *    9 builds: 3000 + 151 +  9 x 1400 = 15751us  <= 16667us  (inside a frame)
	 *   10 builds: 3000 + 151 + 10 x 1400 = 17151us  >  16667us  (over)
	 * </pre>
	 *
	 * So nine is the largest cap whose full-budget crossing tick still fits in one frame
	 * <i>with every build in it costing what the worst 5% of builds cost</i> — and the
	 * expression below is that sentence rather than the number 9 typed in.
	 *
	 * <p><b>What it does not promise.</b> A pass that spends its whole budget at the
	 * measured <i>mean</i> costs {@code 151 + 9 x 712 = 6.56ms}, so the ordinary case is
	 * well inside and the arithmetic above is deliberately the bad case. And no per-pass
	 * cap can help the other tail at all — the worst single build measured was 8.61ms,
	 * so a pass containing it costs at least that however low the cap goes. What the cap
	 * removes is the unbounded burst: before it existed the worst pass measured was
	 * 53.73ms, which was roughly forty builds landing in one tick on the way into
	 * Varrock.
	 *
	 * <p><b>What it costs, and why three was too tight.</b> A cold walk into the densest
	 * shipped neighbourhood — 76 entities inside {@link #MAX_CULL_RADIUS} — fills over
	 * {@code ceil(76 / 9)} = 9 passes, about five seconds. At three it was 26 passes,
	 * and the field measurement bears that out exactly: walking into Varrock square on
	 * 2026-08-29 held 72 builds over on the first pass and drained them three at a time
	 * over 24 further passes, so the crowd took <b>fourteen seconds</b> to finish
	 * arriving. A citizen that takes fourteen seconds to appear is its own defect, and it
	 * was being paid for a spike the measurement has since attributed elsewhere. Nearest
	 * first either way, so the ones that arrive last are the ones furthest away.
	 *
	 * <p>Not a config dial, for {@link #MAX_ACTIVE_OBJECTS}'s reason: a guard rather
	 * than a preference. {@code RenderPolicyTest} recomputes the arithmetic above, so
	 * a re-measurement moves the cap by editing the measured figures rather than by
	 * picking a new number.
	 */
	public static final int MAX_MODEL_BUILDS_PER_PASS = Math.max(
		// A floor of one, because a budget of zero is not a slower plugin — it is a
		// plugin that never spawns anything, and integer division makes that one bad
		// re-measurement away.
		1,
		(CROSSING_TICK_BUDGET_MICROS - MEASURED_REGION_LOAD_MICROS - MEASURED_PASS_OVERHEAD_MICROS)
			/ MEASURED_BURST_MODEL_BUILD_MICROS);

	/**
	 * The cull radius a fresh install gets: a Chebyshev tile radius around the
	 * player, outside which entities are deactivated rather than merely hidden.
	 *
	 * <p>Also the value the render core shipped as a constant, kept as the default
	 * so turning the dial is opt-in. It is <b>wider than
	 * {@link #SUSTAINED_SCENE_RADIUS}</b>, and that is a deliberate trade rather
	 * than an oversight — see that constant, and the description on
	 * {@code LivelyCitiesConfig.cullRadius()}, which tells the user the same
	 * thing.
	 */
	public static final int DEFAULT_CULL_RADIUS = 25;

	/**
	 * The smallest cull radius worth offering. Below this the dial stops thinning
	 * the crowd and starts deactivating citizens the player is standing next to.
	 */
	public static final int MIN_CULL_RADIUS = 5;

	/**
	 * Tiles from the player to the nearest edge of the scene, <b>at the instant
	 * the scene is built</b> — and only then.
	 *
	 * <p>The arithmetic is real: the client loads a
	 * {@link Constants#SCENE_SIZE}-tile scene (104, i.e. 13 chunks) on a
	 * {@link Constants#CHUNK_SIZE}-aligned base with the player's own chunk
	 * seventh of the thirteen, so at that moment the player is between
	 * {@code SCENE_SIZE/2 - CHUNK_SIZE/2} = 48 and
	 * {@code SCENE_SIZE/2 + CHUNK_SIZE/2 - 1} = 55 tiles from every edge.
	 *
	 * <p><b>What it is not is a guarantee.</b> {@code WorldView.getBaseX()} does
	 * not move while the player walks — the scene is a fixed 104x104 square until
	 * the game sends a new one — so this figure decays tile for tile with every
	 * step the player takes away from where it was measured. An earlier revision
	 * of this class called 48 the "guaranteed scene radius" and derived the cull
	 * ceiling from it; that was wrong, and it is why citizens pop in.
	 * {@link #SUSTAINED_SCENE_RADIUS} is the figure that actually holds.
	 */
	public static final int SCENE_RADIUS_AT_LOAD = Constants.SCENE_SIZE / 2 - Constants.CHUNK_SIZE / 2;

	/**
	 * Tiles from the player to the nearest edge of the scene that hold <b>at all
	 * times</b>, not just after a load. Sixteen.
	 *
	 * <p><b>Where the number comes from, stated plainly:</b> not from
	 * {@link Constants}, which describes the scene's size and says nothing about
	 * when it is replaced. It is the map-rebuild margin in the game's own
	 * protocol. The server sends a fresh scene once the player's position inside
	 * the current one leaves {@code [16, SCENE_SIZE - 16)} — i.e. once they come
	 * within 16 tiles of an edge — so the player can drift up to four chunks (32
	 * tiles) from the chunk the scene was centred on, and 48 - 32 = 16 is what is
	 * left. That margin is a property of the protocol rather than of any constant
	 * in the jar, so it is written here as a number with its source named, and
	 * <b>not</b> presented as a derivation.
	 *
	 * <p><b>The consequence, which the plugin does not try to engineer around:</b>
	 * any cull radius above this can select an entity whose ground is not loaded.
	 * {@code LocalPoint.fromWorld} then returns null, and the entity is not
	 * spawned (or, mid-walk, is left where it was) until the next scene load
	 * brings the ground in. That is the "citizens pop in" symptom, and it is
	 * inherent above 16 tiles — for a <i>wandering</i> citizen it is inherent at
	 * any radius, because the box it paces can reach
	 * {@link #DATASET_OVERHANG_ALLOWANCE} tiles past the tile the cull check
	 * measured. There is no radius that makes it impossible; the honest response
	 * is to say so on the dial rather than to shrink the feature until the symptom
	 * disappears.
	 */
	public static final int SUSTAINED_SCENE_RADIUS = 16;

	/**
	 * How far a rendered entity is allowed to be from the tile its cull check was
	 * measured against.
	 *
	 * <p>The cull check uses {@link EntityDefinition#getWorldLocation()} — the
	 * authored tile, which never moves — because culling on a wandering citizen's
	 * live position would make it pop in and out as it paced across the radius.
	 * Two things then sit outside that anchor tile:
	 *
	 * <ul>
	 *   <li><b>Wandering.</b> A citizen walks anywhere in its authored box. The
	 *       widest in the dataset is Grace in {@code 12850.json}, whose box runs
	 *       19 tiles west of the tile she starts on. That is the binding term, and
	 *       it is <i>enforced</i> rather than merely observed:
	 *       {@code EntityDefinition} clamps any wander box that reaches further
	 *       than this from the anchor.</li>
	 *   <li><b>Misfiling.</b> Entities are discovered by region <i>file</i>, and
	 *       the dataset misfiles one: "Dark wizard" is in {@code 12853.json} and
	 *       stands 6 tiles inside region 12852. For a misfiled entity to be found
	 *       at all, the region it is filed under has to be in the scene too.</li>
	 * </ul>
	 *
	 * <p><b>What the clamp buys, now that it is not buying a scene-fit proof.</b>
	 * It bounds the disagreement between the tile the cull decision was made about
	 * and the tile the citizen is drawn on. Without it a single authored box could
	 * put a citizen an arbitrary distance from the anchor the crowd cap sorted by,
	 * so "nearest first" would stop meaning nearest and the dial would stop
	 * meaning anything measurable. It is a coherence bound, not a placement
	 * guarantee — {@link #SUSTAINED_SCENE_RADIUS} explains why no placement
	 * guarantee is available.
	 *
	 * <p>{@code RenderPolicyTest} recomputes both terms from the shipped files and
	 * fails if either outgrows this allowance, so the number cannot rot as data is
	 * added — it either still covers the dataset or the build goes red.
	 */
	public static final int DATASET_OVERHANG_ALLOWANCE = 18;

	/**
	 * The largest cull radius the dial may be set to: 30.
	 *
	 * <p>A choice, bounded by arithmetic — not a derivation, and not a promise
	 * that everything inside it is drawable. What the bound rules out is a setting
	 * that could <i>never</i> be satisfied: an entity selected at radius {@code R}
	 * may be {@code R + DATASET_OVERHANG_ALLOWANCE} tiles away by the time it is
	 * drawn, so past {@code SCENE_RADIUS_AT_LOAD - DATASET_OVERHANG_ALLOWANCE} the
	 * far edge of the dial is outside the scene even in the best case — the
	 * instant after a load — and those entities would simply never appear at any
	 * point in the cycle. Offering a number that can only ever disappoint is worse
	 * than not offering it.
	 *
	 * <p>Inside the bound, coverage is a spectrum rather than a yes/no:
	 * everything within {@link #SUSTAINED_SCENE_RADIUS} is always placeable,
	 * everything out to here is placeable for part of the walk between scene
	 * loads. Turning the dial up trades steadiness for reach, and the config
	 * description says so.
	 */
	public static final int MAX_CULL_RADIUS = SCENE_RADIUS_AT_LOAD - DATASET_OVERHANG_ALLOWANCE;

	private RenderPolicy()
	{
	}

	/**
	 * Brings a configured cull radius inside {@link #MIN_CULL_RADIUS} ..
	 * {@link #MAX_CULL_RADIUS}.
	 *
	 * <p>{@code @Range} on the config item only constrains the slider. The value
	 * also arrives from {@code settings.properties}, from a profile synced off
	 * another install, and from a hand edit — so the clamp lives here, where every
	 * read goes through it, rather than being trusted to the UI.
	 */
	public static int clampCullRadius(int requested)
	{
		return Math.max(MIN_CULL_RADIUS, Math.min(MAX_CULL_RADIUS, requested));
	}

	/**
	 * The region id containing a world coordinate.
	 *
	 * <p>Identical to {@link WorldPoint#getRegionID()}; duplicated here so the
	 * scene code can compute a region id without allocating a WorldPoint, and so
	 * the relationship is pinned by a test rather than by assumption.
	 */
	public static int regionIdOf(int worldX, int worldY)
	{
		return ((worldX >> 6) << 8) | (worldY >> 6);
	}

	/**
	 * Chebyshev ("king move") distance, ignoring plane — the same metric the
	 * client uses for render distance.
	 */
	public static int tileDistance(WorldPoint a, WorldPoint b)
	{
		return Math.max(
			Math.abs(a.getX() - b.getX()),
			Math.abs(a.getY() - b.getY()));
	}

	/**
	 * Whether an entity is a candidate for rendering at all: same plane as the
	 * view, and inside the cull radius.
	 *
	 * <p>Being a candidate is necessary but not sufficient — the caller still has
	 * to resolve a {@code LocalPoint} (the entity may sit outside the loaded
	 * scene) and still has to respect {@link #MAX_ACTIVE_OBJECTS}.
	 *
	 * @param cullRadius the effective radius, already through
	 *                   {@link #clampCullRadius(int)}
	 */
	public static boolean isCandidate(
		WorldPoint playerLocation,
		int viewPlane,
		EntityDefinition definition,
		int cullRadius)
	{
		if (playerLocation == null || definition == null)
		{
			return false;
		}

		if (definition.getPlane() != viewPlane)
		{
			return false;
		}

		return tileDistance(playerLocation, definition.getWorldLocation()) <= cullRadius;
	}

	/**
	 * @param plannedCount how many objects this pass has already committed to
	 *                     activating. The caller counts what it plans, not what
	 *                     is currently registered, and then deactivates before
	 *                     it activates — see the two passes in
	 *                     {@link EntityScene#updateVisibility} — so the number
	 *                     the client ever has registered never exceeds this.
	 * @return true if one more may be planned
	 */
	public static boolean hasCapacity(int plannedCount)
	{
		return plannedCount < MAX_ACTIVE_OBJECTS;
	}

	/**
	 * The other budget, and deliberately a second method rather than a second argument
	 * to {@link #hasCapacity(int)} — see {@link #MAX_MODEL_BUILDS_PER_PASS} for why the
	 * two must not be conflated.
	 *
	 * @param builtThisPass how many models this pass has already <b>finished</b>
	 *                      building. Counted on completion rather than on intent:
	 *                      a spawn the client could not satisfy (a cold model cache)
	 *                      built nothing, so it must not spend a budget that exists to
	 *                      bound the cost of building.
	 * @return true if one more may be built
	 */
	public static boolean hasBuildBudget(int builtThisPass)
	{
		return builtThisPass < MAX_MODEL_BUILDS_PER_PASS;
	}
}
