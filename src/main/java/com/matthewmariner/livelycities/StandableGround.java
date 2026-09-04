package com.matthewmariner.livelycities;

import javax.annotation.Nullable;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;

/**
 * "Could an ordinary person be standing on this tile?", answered from the
 * client's own collision map.
 *
 * <p>This exists for {@link CitizenEcho}. An authored citizen's tile was placed
 * by a human who could see the ground; a procedurally-derived one's was placed by
 * arithmetic, and arithmetic cannot see that the tile two east is the inside of a
 * bank counter. So every echo tile is checked here before it is allowed to spawn,
 * and an echo that fails is <b>skipped, never nudged</b> — a citizen standing in
 * the River Lum is worse than no citizen, and a "find somewhere nearby that works"
 * fallback is how a deterministic placement stops being deterministic.
 *
 * <p><b>The accessor, read out of the 1.12.36 jars rather than guessed.</b>
 * {@code javap net.runelite.api.WorldView} declares
 * {@code public abstract net.runelite.api.CollisionData[] getCollisionMaps();}
 * and {@code javap net.runelite.api.CollisionData} declares exactly one method,
 * {@code public abstract int[][] getFlags();}. In the injected client
 * ({@code injected-client-1.12.36.jar}) the world view is class {@code dz} and the
 * collision map is class {@code gc}:
 * <ul>
 *   <li>{@code dz.getCollisionMaps()} returns the field {@code ab:[Lgc;}, which
 *       the constructor allocates as {@code anewarray gc} of length
 *       {@code iconst_4} — <b>one map per plane</b>, indexed by plane — and then
 *       fills all four slots in a loop <i>before it returns</i>. The array is
 *       assigned once, in that constructor, and so is every element of it. An
 *       earlier version of this javadoc said the elements "are filled in as the
 *       scene loads, so an element can be {@code null}"; two independent
 *       disassemblies, of 1.12.36 and of 1.12.38, say the opposite. See
 *       <b>What the null checks are for</b> below.</li>
 *   <li>{@code gc.getFlags()} returns the field {@code bb:[[I}, allocated in
 *       {@code gc.<init>(int,int,boolean)} as
 *       {@code multianewarray "[[I"} over the two size fields — first dimension
 *       from the constructor's first {@code int}, second from its second. The
 *       world view passes {@code getSizeX()} and {@code getSizeY()} in that
 *       order (both are stored from the same two constructor arguments:
 *       {@code getSizeX()} returns field {@code as}, {@code getSizeY()} returns
 *       field {@code ax}). So it is <b>{@code getFlags()[sceneX][sceneY]}</b>.
 *       Every writer in {@code gc} agrees — e.g. {@code gc.cv(int,int)} is
 *       {@code bb[x - originX][y - originY] |= 2097152} — and so does RuneLite's
 *       own {@code DevToolsOverlay}, which indexes
 *       {@code getFlags()[tile.getSceneLocation().getX()][..getY()]}.</li>
 * </ul>
 *
 * <p><b>Why {@link WorldView#isTopLevel()} is a precondition and not a
 * formality.</b> {@code gc}'s constructor has two shapes, chosen by its
 * {@code boolean}: origin {@code (0,0)} and size {@code (sizeX, sizeY)}, or
 * origin {@code (-1,-1)} and size {@code (sizeX + 6, sizeY + 6)}. The world view
 * passes {@code (getId() != 0)} for that boolean, and
 * {@code WorldView.TOPLEVEL == 0} with {@code dz.isTopLevel()} compiled to
 * {@code getId() == 0}. So on a <i>non</i>-top-level view (a
 * {@code WorldEntity}'s) the flags array is origin-shifted by one tile and scene
 * coordinates would read the wrong tile's flags — silently, and off by one, which
 * is exactly the kind of wrong that puts a citizen half inside a wall. This plugin
 * only ever passes {@code client.getTopLevelWorldView()}, so the check costs
 * nothing; it is here so that a future caller cannot make the mistake.
 *
 * <p><b>What the flags mean.</b> {@code CollisionDataFlag} splits into three
 * kinds, and only one of them answers this question:
 * <ul>
 *   <li>{@code BLOCK_MOVEMENT_OBJECT} (256), {@code BLOCK_MOVEMENT_FLOOR_DECORATION}
 *       (262144) and {@code BLOCK_MOVEMENT_FLOOR} (2097152) say the tile itself
 *       cannot be occupied — a wall or counter fills it, a floor decoration fills
 *       it, or there is no walkable floor there at all (water, void, the inside of
 *       a building's footprint). {@code BLOCK_MOVEMENT_FULL} is exactly those
 *       three OR'd together: {@code 2359552 == 256 | 262144 | 2097152}, which
 *       {@code StandableGroundTest} pins.</li>
 *   <li>The eight directional bits ({@code BLOCK_MOVEMENT_NORTH} and friends) say
 *       a <i>step across one edge</i> of the tile is blocked. A tile with a wall
 *       along its north side is still a tile a person stands on — most of the
 *       pavement in Varrock carries one of these — so they are deliberately
 *       <b>not</b> consulted.</li>
 *   <li>The line-of-sight bits are about projectiles and sight, not standing, and
 *       are not consulted either.</li>
 * </ul>
 *
 * <p><b>What the null checks are for.</b> Not for a client state anybody has
 * observed. On the injected client {@code getCollisionMaps()} never returns
 * {@code null} and no element of what it returns is ever {@code null}, because both
 * the array and its four entries are assigned in the world view's own constructor
 * before it returns. The checks guard the {@link WorldView} <i>interface</i>, which
 * promises none of that: this plugin does not construct the implementation and does
 * not get to make claims about one it was handed. A branch that costs a comparison
 * and turns an unowned {@code NullPointerException} into {@link Verdict#UNKNOWN} is
 * worth keeping even against a client that cannot take it — this method runs from a
 * per-game-tick visibility pass, where a throw abandons every entity after the
 * offending one, including the ones that were about to be deactivated. What is not
 * worth keeping is the claim that it happens, which is what this paragraph replaced.
 *
 * <p><b>{@link Verdict#UNKNOWN} is not "probably fine".</b> Every way of failing
 * to get an answer — no maps at all, no map for that plane, the tile outside the
 * loaded scene, a view whose flags array is origin-shifted — comes back as
 * {@code UNKNOWN}, and the caller decides. {@link CitizenEcho} treats
 * {@code UNKNOWN} as "only if a human already vouched for this ground", i.e. only
 * inside an authored wander box. Nothing here ever guesses.
 *
 * <p><b>Client-thread only</b>, like everything that reads live scene state.
 * Nothing here allocates.
 */
final class StandableGround
{
	/**
	 * The mask that decides it: {@code CollisionDataFlag.BLOCK_MOVEMENT_FULL}.
	 *
	 * <p>Named through the API constant rather than written as {@code 0x240100} so
	 * a client-side change to the aggregate travels here, and so the one number
	 * this class turns on is traceable to its declaration.
	 */
	static final int BLOCKS_STANDING = CollisionDataFlag.BLOCK_MOVEMENT_FULL;

	enum Verdict
	{
		/** The collision map says an ordinary person could be standing here. */
		STANDABLE,

		/** The collision map says the tile is filled, decorated, or floorless. */
		BLOCKED,

		/** No answer available — see the class javadoc. Never treated as a yes. */
		UNKNOWN
	}

	private StandableGround()
	{
	}

	/**
	 * @param worldView the view whose scene the tile is being judged in — must be
	 *                  the top-level view, or the answer is {@link Verdict#UNKNOWN}
	 * @param tile      the world tile, whose {@code plane} selects the collision map
	 * @return whether an ordinary person could stand there. Never throws, never
	 * allocates.
	 */
	static Verdict verdict(@Nullable WorldView worldView, @Nullable WorldPoint tile)
	{
		if (worldView == null || tile == null)
		{
			return Verdict.UNKNOWN;
		}

		if (!worldView.isTopLevel())
		{
			// Origin-shifted flags array — see the class javadoc. Reading it with
			// scene coordinates would answer about the tile one to the south-west.
			return Verdict.UNKNOWN;
		}

		CollisionData[] maps = worldView.getCollisionMaps();
		if (maps == null)
		{
			return Verdict.UNKNOWN;
		}

		int plane = tile.getPlane();
		if (plane < 0 || plane >= maps.length)
		{
			return Verdict.UNKNOWN;
		}

		CollisionData map = maps[plane];
		if (map == null)
		{
			// Not a state the injected client produces: the world view's constructor
			// allocates the four-slot array and fills every slot before it returns.
			// This guards the WorldView interface, which promises nothing of the sort
			// — see "What the null checks are for" in the class javadoc.
			return Verdict.UNKNOWN;
		}

		int[][] flags = map.getFlags();
		if (flags == null)
		{
			return Verdict.UNKNOWN;
		}

		// The same subtraction LocalPoint.fromWorld does, without the <<7 / >>7
		// round trip and without LocalPoint's plane coupling: fromWorld returns
		// null whenever the view's current plane differs from the point's, which
		// is a question about what the player is looking at rather than about what
		// the ground is like.
		int sceneX = tile.getX() - worldView.getBaseX();
		int sceneY = tile.getY() - worldView.getBaseY();

		if (sceneX < 0 || sceneX >= flags.length)
		{
			return Verdict.UNKNOWN;
		}

		int[] column = flags[sceneX];
		if (column == null || sceneY < 0 || sceneY >= column.length)
		{
			return Verdict.UNKNOWN;
		}

		return (column[sceneY] & BLOCKS_STANDING) == 0 ? Verdict.STANDABLE : Verdict.BLOCKED;
	}
}
