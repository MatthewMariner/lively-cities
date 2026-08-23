package com.matthewmariner.livelycities;

import java.util.Random;
import javax.annotation.Nullable;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * One wandering citizen's walk: where it is going, where it is, and where it
 * should be drawn part-way between two tiles.
 *
 * <p><b>The per-tick / per-frame split lives here, and it is the whole design.</b>
 *
 * <ul>
 *   <li>{@link #tick()} is <b>game-tick work</b>: pick a destination, take one
 *       tile of a step, work out which way that faces. A tile per game tick is
 *       the speed the game itself walks at, and doing it anywhere else would
 *       either make citizens sprint at the frame rate or make their speed depend
 *       on the machine.</li>
 *   <li>{@link #localPoint(WorldView, float)} is <b>frame work</b>: it slides the
 *       drawn position between the tile the citizen left and the tile it is
 *       walking to. There is no walk API on {@code RuneLiteObject} — no
 *       destination, no speed, no path — so this interpolation is the only thing
 *       standing between a smooth walk and a citizen teleporting one tile every
 *       600ms.</li>
 * </ul>
 *
 * <p><b>The other half of "smooth" is the animation, and this class does not own
 * any of it.</b> The mechanism, read out of the 1.12.36 API jar rather than
 * guessed at:
 *
 * <ul>
 *   <li>{@code RuneLiteObject.tick(ticksSinceLastFrame)} is called by the client,
 *       once per frame, for every registered object — the API javadoc says
 *       "Called every frame the RuneLiteObject is registered and in the scene",
 *       and the injected client calls
 *       {@code RuneLiteObjectController.tick(I)} immediately before drawing each
 *       one. It forwards to the {@code AnimationController}, whose {@code tick}
 *       accumulates client ticks (20ms) against the animation's own
 *       {@code getFrameLengths()}. So the <i>frame index</i> already advances
 *       between game ticks, without this plugin doing anything.</li>
 *   <li>Whether the client also <i>interpolates between</i> two frames is decided
 *       by {@code AnimationController.getPackedFrame()}, which is private and read
 *       at draw time. Disassembled, it is:
 *       {@code filter = client.getAnimationInterpolationFilter(); return (filter
 *       != null && filter.test(animation.getId())) ? (Integer.MIN_VALUE |
 *       (elapsedTicks << 16) | frame) : frame;} — the high bit plus the elapsed
 *       ticks is what tells {@code applyTransformations} to blend; the bare frame
 *       index is what tells it not to.</li>
 *   <li>The only thing in RuneLite that installs that filter is the core
 *       <b>Animation Smoothing</b> plugin, and it is
 *       {@code enabledByDefault = false}. With it off the filter is null and
 *       nothing — player, NPC or {@code RuneLiteObject} — is interpolated.</li>
 * </ul>
 *
 * <p><b>So the honest position on smoothing.</b> It is not ours to switch on, and
 * it is not gated on our animation ids: the filter Animation Smoothing installs
 * is a <i>denylist</i> of 23 ids ({@code isAnimationInterpolatable} returns
 * {@code false} for those and {@code true} for everything else), and none of the
 * ids in {@link LivelyAnimation} is on it —
 * {@code LivelyAnimationTest.noShippedAnimationIsOnTheSmoothingDenylist} pins
 * that. Our citizens are therefore smoothed exactly when the user has Animation
 * Smoothing enabled, and stepped frame-by-frame when they do not, on the same
 * terms as every real NPC. There is nothing to work around and no reason to
 * ask for the frame index ourselves.
 *
 * <p>What {@link LivelyEntity} keeping one controller per animation actually buys
 * is unrelated to any of that: {@code AnimationController.setAnimation} calls
 * {@code reset()}, zeroing both {@code frame} and {@code elapsedTicks}, so
 * building a fresh controller — or re-setting an existing one — restarts the walk
 * cycle from its first frame. Holding both controllers means a citizen resumes
 * mid-stride instead of snapping back to frame zero on every idle↔move switch.
 * (An earlier version of this comment claimed the predecessor plugin's smoothing
 * complaint came from animations being reset every game tick. It did not do that;
 * the sentence was an explanation of a symptom nobody had traced.)
 *
 * <p>This class must never call {@code tick()} itself — the client already does,
 * once per frame, and a second caller runs every animation at double speed.
 *
 * <p><b>Scripted citizens are not wandering citizens.</b> The six
 * {@code ScriptedCitizen} records carry a {@code startScript} name and no wander
 * box, so they get no {@code CitizenWalk} and stand exactly where L1 put them.
 * That is on purpose and not an oversight: running authored scripts is a later
 * phase, and a scripted citizen wandering at random would be worse than one
 * standing still.
 *
 * <p><b>Client-thread-free.</b> Nothing here touches the client;
 * {@link #localPoint} only reads the world view's scene rectangle. That is what
 * lets {@code CitizenWalkTest} drive ten thousand ticks without a game running.
 */
final class CitizenWalk
{
	/**
	 * Game ticks a citizen stands still before looking for somewhere new to go.
	 *
	 * <p>Ten, which is the cadence the predecessor used and about six seconds —
	 * long enough that a street reads as people pausing and moving on rather than
	 * as a crowd of pacing sentries.
	 */
	static final int IDLE_TICKS_BEFORE_NEW_DESTINATION = 10;

	/**
	 * Orientation, in RuneLite's 0..2047 turn units, for each of the eight steps a
	 * citizen can take. Indexed {@code [dx + 1][dy + 1]}.
	 *
	 * <p>The convention is the client's own, taken from
	 * {@code Angle.getNearestDirection()}, which buckets {@code (angle >> 9) & 3}
	 * as 0 = south, 1 = west, 2 = north, 3 = east — i.e. the angle rises as the
	 * facing turns clockwise from south. A table rather than
	 * {@code atan2(-dx, -dy)} because there are only eight answers, all exact, and
	 * a lookup cannot be a rounding bug.
	 */
	private static final int[][] STEP_ORIENTATION = {
		//        dy = -1 (south)  dy = 0        dy = +1 (north)
		/* dx=-1 */ {256, 512, 768},      // south-west, west, north-west
		/* dx= 0 */ {0, -1, 1024},        // south, (not moving), north
		/* dx=+1 */ {1792, 1536, 1280},   // south-east, east, north-east
	};

	private final EntityDefinition.WanderBox box;
	private final int plane;
	private final int baseOrientation;
	private final Random random;

	/** The tile the citizen is walking to over the current game tick. */
	private int x;
	private int y;

	/** The tile it left at the start of that step; equal to x,y while idle. */
	private int fromX;
	private int fromY;

	/** Where it is heading over the next several ticks. */
	private int destinationX;
	private int destinationY;

	private int idleTicks;
	private boolean moving;
	private int orientation;

	/**
	 * @param definition a definition whose {@link EntityDefinition#getWanderBox()}
	 *                   is not null
	 */
	private CitizenWalk(EntityDefinition definition, EntityDefinition.WanderBox box)
	{
		this.box = box;
		this.plane = box.getPlane();
		this.baseOrientation = definition.getOrientation();
		this.orientation = baseOrientation;

		// Seeded from the entity's identity, not from the clock. A citizen then
		// paces the same route every session, which is what makes
		// CitizenWalkTest able to assert anything at all about ten thousand
		// ticks — and which stops a city's traffic pattern being different every
		// login for no reason the player could ever see as intentional. The
		// mixing is already done by stableHash(), so nearby uuids do not produce
		// neighbouring seeds.
		this.random = new Random(definition.stableHash());

		this.x = definition.getWorldLocation().getX();
		this.y = definition.getWorldLocation().getY();
		this.fromX = x;
		this.fromY = y;
		this.destinationX = x;
		this.destinationY = y;
	}

	/**
	 * @return a walk for this entity, or {@code null} if it does not wander
	 */
	@Nullable
	static CitizenWalk forDefinition(EntityDefinition definition)
	{
		EntityDefinition.WanderBox box = definition.getWanderBox();
		return box == null ? null : new CitizenWalk(definition, box);
	}

	/**
	 * One game tick of walking: either take a step towards the destination, or
	 * count down to choosing a new one.
	 */
	void tick()
	{
		// Whatever happens, the step that was in flight is over: the drawn
		// position has caught up with the tile, and the next interpolation starts
		// from here.
		fromX = x;
		fromY = y;

		if (x == destinationX && y == destinationY)
		{
			moving = false;
			orientation = baseOrientation;

			if (++idleTicks < IDLE_TICKS_BEFORE_NEW_DESTINATION)
			{
				return;
			}

			idleTicks = 0;
			chooseDestination();

			if (x == destinationX && y == destinationY)
			{
				// It rolled the tile it is already standing on. Standing still for
				// another interval is the honest outcome; forcing a re-roll would
				// bias the destination away from the citizen's own tile.
				return;
			}
		}

		int dx = Integer.signum(destinationX - x);
		int dy = Integer.signum(destinationY - y);
		x += dx;
		y += dy;
		moving = true;
		orientation = STEP_ORIENTATION[dx + 1][dy + 1];
	}

	/**
	 * Where to draw the citizen right now.
	 *
	 * @param worldView the view the entity is being placed in
	 * @param fraction  how far through the current game tick this frame is, 0..1
	 * @return the interpolated local position, or {@code null} if the tile it is
	 * walking to is outside the loaded scene — the caller then leaves the object
	 * where it was rather than moving it somewhere meaningless
	 */
	@Nullable
	LocalPoint localPoint(WorldView worldView, float fraction)
	{
		LocalPoint to = LocalPoint.fromWorld(worldView, new WorldPoint(x, y, plane));
		if (to == null)
		{
			return null;
		}

		if (!moving || (fromX == x && fromY == y))
		{
			return to;
		}

		LocalPoint from = LocalPoint.fromWorld(worldView, new WorldPoint(fromX, fromY, plane));
		if (from == null)
		{
			// It stepped in from outside the scene. Snapping to the tile it is
			// walking to is one tile of pop, at the far edge of the render
			// distance, instead of a frame drawn at a stale position.
			return to;
		}

		float clamped = fraction < 0f ? 0f : (fraction > 1f ? 1f : fraction);
		int lx = from.getX() + Math.round((to.getX() - from.getX()) * clamped);
		int ly = from.getY() + Math.round((to.getY() - from.getY()) * clamped);

		// The (int, int, WorldView) constructor rather than passing an id: it is
		// the same one LocalPoint.fromScene uses, so the world view on the point
		// this returns matches the one on the points either side of it. That is
		// load-bearing — RuneLiteObject.setLocation deactivates and reactivates
		// the object whenever the point's world view differs from the object's,
		// so a mismatch here would churn the client's registered-object list
		// once per frame per citizen.
		return new LocalPoint(lx, ly, worldView);
	}

	/** @return the tile the citizen is on, or walking onto */
	WorldPoint currentTile()
	{
		return new WorldPoint(x, y, plane);
	}

	/** @return the tile the current step started from */
	WorldPoint stepStartTile()
	{
		return new WorldPoint(fromX, fromY, plane);
	}

	/** @return true while a step is in progress, i.e. this tick moved the citizen */
	boolean isMoving()
	{
		return moving;
	}

	/**
	 * @return the direction of travel while moving, and the authored
	 * {@code baseOrientation} while idle, both in 0..2047
	 */
	int getOrientation()
	{
		return orientation;
	}

	EntityDefinition.WanderBox getBox()
	{
		return box;
	}

	private void chooseDestination()
	{
		destinationX = box.getMinX() + random.nextInt(box.getWidth());
		destinationY = box.getMinY() + random.nextInt(box.getHeight());
	}
}
