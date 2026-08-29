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
	 * The visibility pass's own acceptance threshold, in microseconds: 2ms.
	 *
	 * <p>Not invented here — it is the "acceptable" figure {@link FrameTimings}
	 * registered in its javadoc, in the report header and in the README <i>before</i>
	 * anything was measured. Restated as a number so the build budget below can be
	 * arithmetic against it rather than a guess that happens to agree with it.
	 */
	static final int VISIBILITY_BUDGET_MICROS = 2_000;

	/**
	 * What a visibility pass costs when it builds nothing: 124µs.
	 *
	 * <p>The measured median pass, 300 game ticks in Varrock (331 samples, 371 model
	 * builds). The median is the right term for "the pass minus its building": most
	 * passes build nothing, and a single median model build is 478µs — four times this
	 * — so a pass at the median plainly contains no build.
	 */
	static final int MEASURED_PASS_OVERHEAD_MICROS = 124;

	/**
	 * What building one model costs: 570µs, the measured mean over 371 builds.
	 *
	 * <p>The <b>mean</b> rather than the median (478µs) or the p99 (1.50ms), and which
	 * one is used decides the cap, so it is worth saying why. The budget below bounds a
	 * <i>sum</i> of up to {@link #MAX_MODEL_BUILDS_PER_PASS} draws, and the expected
	 * value of a sum is the sum of the means whatever the shape of the distribution.
	 * Budgeting on the median would understate a right-skewed tail; budgeting on the
	 * p99 would assume every build in a burst lands in the worst one percent, which
	 * yields a cap of 1 and a city that takes 48 seconds to populate.
	 */
	static final int MEASURED_MEAN_MODEL_BUILD_MICROS = 570;

	/**
	 * Hard ceiling on the number of models one visibility pass may <b>build</b>: three.
	 *
	 * <p><b>Not the same thing as {@link #MAX_ACTIVE_OBJECTS}, and conflating them is
	 * the mistake this constant exists to avoid.</b> That one is a ceiling on how many
	 * objects the client may have registered at once — a memory and draw-call bound,
	 * spent down and refunded as the player walks. This one is a ceiling on how much
	 * <i>work</i> a single pass may do, spent and refunded every 600ms. A pass may
	 * activate eighty objects and build three of them; the other seventy-seven are
	 * models it already holds, and reactivating one is free.
	 *
	 * <p><b>Where the three comes from.</b> The visibility figure is inclusive of model
	 * building, so a pass costs its own overhead plus whatever it built:
	 *
	 * <pre>
	 *   pass = MEASURED_PASS_OVERHEAD_MICROS + builds x MEASURED_MEAN_MODEL_BUILD_MICROS
	 *   3 builds: 124 + 3 x 570 = 1834us  <= 2000us  (acceptable)
	 *   4 builds: 124 + 4 x 570 = 2404us  >  2000us  (over)
	 * </pre>
	 *
	 * So three is the largest cap whose full-budget pass, at the measured mean cost of a
	 * build, still lands inside the pre-registered acceptable threshold — and the
	 * expression below is that sentence rather than the number 3 typed in.
	 *
	 * <p><b>What it does not promise.</b> A pass that spends its whole budget on three
	 * p99 builds costs {@code 124 + 3 x 1500 = 4.62ms}: over "acceptable", still inside
	 * the 8ms "a problem" line. And no per-pass cap can help the other tail at all — the
	 * worst single build measured was 15.45ms, so a pass containing it costs at least
	 * that however low the cap goes. What the cap removes is the burst: the worst pass
	 * measured was 53.73ms, which was roughly forty builds landing in one tick on the
	 * way into Varrock.
	 *
	 * <p><b>What it costs.</b> A cold walk into the densest shipped neighbourhood — 76
	 * entities inside {@link #MAX_CULL_RADIUS} — now fills over {@code ceil(76 / 3)} =
	 * 26 passes, about 15 seconds, instead of one 53ms stutter. Nearest first, so the
	 * ones that arrive last are the ones furthest away.
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
		(VISIBILITY_BUDGET_MICROS - MEASURED_PASS_OVERHEAD_MICROS)
			/ MEASURED_MEAN_MODEL_BUILD_MICROS);

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
