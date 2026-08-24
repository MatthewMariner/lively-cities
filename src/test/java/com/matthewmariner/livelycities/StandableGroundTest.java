package com.matthewmariner.livelycities;

import net.runelite.api.CollisionDataFlag;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * The collision-map read, and the meaning of the flags it reads.
 *
 * <p>This is the class the whole {@link CrowdDensity#CROWDED} feature rests on: if
 * it answers "standable" for a tile that is a wall, the feature puts people inside
 * buildings, and no amount of determinism elsewhere helps. So every branch has a
 * test, including the four separate ways of having no answer at all — because
 * {@link StandableGround.Verdict#UNKNOWN} being silently treated as a yes is the
 * failure that would look fine in a unit test and wrong in Varrock.
 *
 * <p>The scene here is the one {@link FakeWorldView#around} builds, which places
 * the 104x104 square the way the client does, so the scene-coordinate arithmetic
 * under test is the arithmetic the game exercises.
 */
public class StandableGroundTest
{
	private static final int VARROCK_SOUTH = 12852;

	/** Inside region 12852, comfortably inside the scene the fake builds. */
	private static final WorldPoint PLAYER = new WorldPoint(3225, 3360, 0);

	@Test
	public void aTileWithNoBlockingFlagsIsStandable()
	{
		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);

		assertSame(StandableGround.Verdict.STANDABLE,
			StandableGround.verdict(view, new WorldPoint(3230, 3362, 0)));
	}

	/**
	 * The three bits that mean "the tile itself cannot be occupied", one at a time.
	 *
	 * <p>Separately rather than only through {@code BLOCK_MOVEMENT_FULL}, because a
	 * mask narrowed to any two of the three would still pass a test that only ever
	 * sets all three at once — and each of the three is a real, common piece of
	 * geometry: a wall or counter fills the tile ({@code OBJECT}), a floor decoration
	 * fills it ({@code FLOOR_DECORATION}), or there is no walkable floor there at all
	 * ({@code FLOOR}, which is what open water and a building's footprint look like).
	 */
	@Test
	public void eachOfTheThreeFullBlockBitsOnItsOwnBlocksStanding()
	{
		int[] bits = {
			CollisionDataFlag.BLOCK_MOVEMENT_OBJECT,
			CollisionDataFlag.BLOCK_MOVEMENT_FLOOR_DECORATION,
			CollisionDataFlag.BLOCK_MOVEMENT_FLOOR,
		};

		for (int bit : bits)
		{
			FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
			WorldPoint tile = new WorldPoint(3230, 3362, 0);
			view.setFlags(tile, bit);

			assertSame("flag " + bit + " must block standing",
				StandableGround.Verdict.BLOCKED, StandableGround.verdict(view, tile));
		}
	}

	/**
	 * A wall along one edge of a tile does not stop a person standing on it.
	 *
	 * <p>This is the guard on the other side of the mask, and it is the one that
	 * matters for the crowd: almost every tile of pavement next to a building in
	 * Varrock carries one or more directional bits, so a check that refused them
	 * would refuse most of the ground a citizen could actually be standing on and
	 * quietly shrink the feature to nothing. Line-of-sight bits are in here too —
	 * they are about projectiles and sight, not about standing.
	 */
	@Test
	public void directionalAndLineOfSightBitsDoNotStopSomebodyStandingOnTheTile()
	{
		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		WorldPoint tile = new WorldPoint(3230, 3362, 0);

		view.setFlags(tile, CollisionDataFlag.BLOCK_MOVEMENT_NORTH
			| CollisionDataFlag.BLOCK_MOVEMENT_NORTH_EAST
			| CollisionDataFlag.BLOCK_MOVEMENT_EAST
			| CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_EAST
			| CollisionDataFlag.BLOCK_MOVEMENT_SOUTH
			| CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_WEST
			| CollisionDataFlag.BLOCK_MOVEMENT_WEST
			| CollisionDataFlag.BLOCK_MOVEMENT_NORTH_WEST
			| CollisionDataFlag.BLOCK_LINE_OF_SIGHT_FULL);

		assertSame("a tile boxed in by walls is still a tile somebody is standing on",
			StandableGround.Verdict.STANDABLE, StandableGround.verdict(view, tile));
	}

	/**
	 * {@code BLOCK_MOVEMENT_FULL} is exactly the three non-directional bits.
	 *
	 * <p>Pinned as arithmetic on the API's own constants, because the entire
	 * justification for using that one aggregate is that it is those three and
	 * nothing else. If a client update folded a directional bit into it, this goes
	 * red and the reasoning in {@link StandableGround}'s javadoc gets revisited
	 * instead of silently becoming untrue.
	 */
	@Test
	public void blockMovementFullIsExactlyTheThreeNonDirectionalBits()
	{
		assertEquals(
			CollisionDataFlag.BLOCK_MOVEMENT_OBJECT
				| CollisionDataFlag.BLOCK_MOVEMENT_FLOOR_DECORATION
				| CollisionDataFlag.BLOCK_MOVEMENT_FLOOR,
			CollisionDataFlag.BLOCK_MOVEMENT_FULL);

		assertEquals("and that aggregate is what this class turns on",
			CollisionDataFlag.BLOCK_MOVEMENT_FULL, StandableGround.BLOCKS_STANDING);
	}

	/**
	 * The flags array is {@code [sceneX][sceneY]}, not the transpose.
	 *
	 * <p>An asymmetric fixture on purpose: blocking (dx=+5, dy=+1) and then asking
	 * about (dx=+1, dy=+5) is a question a transposed reader gets exactly backwards,
	 * while a square offset like (+3,+3) would let both readers pass. This is the
	 * order the injected client writes with — {@code gc.cv(x,y)} does
	 * {@code bb[x - originX][y - originY] |= 2097152} — and the order RuneLite's own
	 * DevTools collision overlay reads with.
	 */
	@Test
	public void theFlagsAreIndexedSceneXThenSceneY()
	{
		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		WorldPoint blocked = new WorldPoint(PLAYER.getX() + 5, PLAYER.getY() + 1, 0);
		WorldPoint transposed = new WorldPoint(PLAYER.getX() + 1, PLAYER.getY() + 5, 0);
		view.block(blocked);

		assertSame(StandableGround.Verdict.BLOCKED, StandableGround.verdict(view, blocked));
		assertSame("the transposed tile must be untouched, or the indices are the wrong way round",
			StandableGround.Verdict.STANDABLE, StandableGround.verdict(view, transposed));
	}

	/** Each plane has its own map, so blocking one storey must not block another. */
	@Test
	public void theMapIsSelectedByTheTilesOwnPlane()
	{
		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		WorldPoint ground = new WorldPoint(3230, 3362, 0);
		WorldPoint upstairs = new WorldPoint(3230, 3362, 1);
		view.block(ground);

		assertSame(StandableGround.Verdict.BLOCKED, StandableGround.verdict(view, ground));
		assertSame("blocking the ground floor must not block the storey above it",
			StandableGround.Verdict.STANDABLE, StandableGround.verdict(view, upstairs));
	}

	// --- The four ways of having no answer ------------------------------------

	@Test
	public void noCollisionDataAtAllIsUnknownRatherThanStandable()
	{
		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH).withoutCollisionData();

		assertSame(StandableGround.Verdict.UNKNOWN,
			StandableGround.verdict(view, new WorldPoint(3230, 3362, 0)));
	}

	@Test
	public void aPlaneWithNoMapYetIsUnknown()
	{
		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH).withoutCollisionMapFor(0);

		assertSame(StandableGround.Verdict.UNKNOWN,
			StandableGround.verdict(view, new WorldPoint(3230, 3362, 0)));
		assertSame("and the planes that do have a map still answer",
			StandableGround.Verdict.STANDABLE,
			StandableGround.verdict(view, new WorldPoint(3230, 3362, 1)));
	}

	/**
	 * A plane outside the four the client allocates.
	 *
	 * <p>{@code EntityDefinition} already refuses a record whose plane is outside
	 * 0..3, so this is unreachable from the dataset — and it is tested anyway,
	 * because the alternative to a bounds check is an
	 * {@code ArrayIndexOutOfBoundsException} thrown once per game tick out of a
	 * visibility pass.
	 */
	@Test
	public void aPlaneOutsideTheFourTheClientAllocatesIsUnknown()
	{
		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);

		assertSame(StandableGround.Verdict.UNKNOWN,
			StandableGround.verdict(view, new WorldPoint(3230, 3362, 9)));
	}

	@Test
	public void aTileOutsideTheLoadedSceneIsUnknown()
	{
		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);

		assertSame("far to the west of the scene",
			StandableGround.Verdict.UNKNOWN,
			StandableGround.verdict(view, new WorldPoint(PLAYER.getX() - 500, PLAYER.getY(), 0)));
		assertSame("far to the north of it",
			StandableGround.Verdict.UNKNOWN,
			StandableGround.verdict(view, new WorldPoint(PLAYER.getX(), PLAYER.getY() + 500, 0)));
	}

	/**
	 * A {@code WorldEntity}'s view answers nothing.
	 *
	 * <p>Not caution: the injected client builds a non-top-level collision map with
	 * its origin one tile to the south-west and its size six tiles larger in each
	 * axis ({@code gc.<init>} takes a boolean for exactly this, and the world view
	 * passes {@code getId() != 0}). Reading it with scene coordinates would answer
	 * about the tile diagonally behind the one asked about — off by one, silently,
	 * which is how a citizen ends up half inside a wall. This plugin only ever hands
	 * over {@code client.getTopLevelWorldView()}; the check is so a later caller
	 * cannot.
	 */
	@Test
	public void aNonTopLevelWorldViewIsUnknownBecauseItsFlagsAreOriginShifted()
	{
		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH).asWorldEntityView();

		assertSame(StandableGround.Verdict.UNKNOWN,
			StandableGround.verdict(view, new WorldPoint(3230, 3362, 0)));
	}

	@Test
	public void nullsAreUnknownRatherThanAThrow()
	{
		assertSame(StandableGround.Verdict.UNKNOWN,
			StandableGround.verdict(null, new WorldPoint(3230, 3362, 0)));
		assertSame(StandableGround.Verdict.UNKNOWN,
			StandableGround.verdict(FakeWorldView.around(PLAYER, VARROCK_SOUTH), null));
	}
}
