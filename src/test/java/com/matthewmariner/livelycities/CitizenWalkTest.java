package com.matthewmariner.livelycities;

import java.util.HashSet;
import java.util.Set;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The walk itself, driven without a client.
 *
 * <p>{@link EntitySceneTest} exercises the same code through the scene; this file
 * asserts on one citizen so a failure names the rule that broke.
 *
 * <p>The fixture's {@code baseOrientation} is deliberately 500 — a real value
 * from region 12338 and not a multiple of 256, so it can never be confused with
 * any of the eight travel orientations. A fixture using 0 could not tell "went
 * back to the orientation it was authored with" from "never set one".
 */
public class CitizenWalkTest
{
	private static final int REGION = 12852;
	private static final int BASE_ORIENTATION = 500;

	/** Roughly a minute and a half of game ticks. */
	private static final int MANY_TICKS = 2500;

	private FakeRegions regions;

	@Before
	public void setUp()
	{
		regions = new FakeRegions();
	}

	private CitizenWalk walk(int baseX, int baseY, int blX, int blY, int trX, int trY)
	{
		EntityDefinition definition = regions.wanderer(
			REGION,
			new WorldPoint(baseX, baseY, 0),
			new WorldPoint(blX, blY, 0),
			new WorldPoint(trX, trY, 0),
			BASE_ORIENTATION);
		CitizenWalk walk = CitizenWalk.forDefinition(definition);
		assertNotNull(walk);
		return walk;
	}

	@Test
	public void aWandererNeverLeavesItsBoxHoweverLongItWalks()
	{
		CitizenWalk walk = walk(3225, 3355, 3220, 3350, 3230, 3360);
		EntityDefinition.WanderBox box = walk.getBox();

		Set<WorldPoint> visited = new HashSet<>();
		for (int tick = 0; tick < MANY_TICKS; tick++)
		{
			walk.tick();
			WorldPoint tile = walk.currentTile();
			assertTrue("stepped outside " + box + " onto " + tile,
				box.contains(tile.getX(), tile.getY()));
			visited.add(tile);
		}

		// Otherwise a tick() that did nothing at all would pass the assertion
		// above with room to spare.
		assertTrue("the citizen has to actually walk: only visited " + visited.size() + " tile(s)",
			visited.size() > 10);
		assertTrue("and it should get most of the way round an 11x11 box",
			visited.size() >= 40);
	}

	@Test
	public void aBoxOnTheEdgeOfItsRegionIsStillNeverLeft()
	{
		// The shipped shape: a box straddling the 12852/12853 border, with the
		// citizen starting on the northern side of it.
		CitizenWalk walk = walk(3261, 3392, 3258, 3382, 3261, 3395);
		EntityDefinition.WanderBox box = walk.getBox();

		Set<Integer> regionsVisited = new HashSet<>();
		for (int tick = 0; tick < MANY_TICKS; tick++)
		{
			walk.tick();
			WorldPoint tile = walk.currentTile();
			assertTrue("stepped outside " + box + " onto " + tile,
				box.contains(tile.getX(), tile.getY()));
			regionsVisited.add(RenderPolicy.regionIdOf(tile.getX(), tile.getY()));
		}

		assertEquals("the fixture has to straddle the border to prove anything",
			2, regionsVisited.size());
		assertTrue(regionsVisited.contains(12852));
		assertTrue(regionsVisited.contains(12853));
	}

	/**
	 * A tile per game tick, which is the speed the game walks at. Faster and the
	 * citizens skate; slower and they never get anywhere within an idle interval.
	 */
	@Test
	public void aStepIsNeverMoreThanOneTile()
	{
		CitizenWalk walk = walk(3225, 3355, 3218, 3348, 3232, 3362);

		int steps = 0;
		for (int tick = 0; tick < MANY_TICKS; tick++)
		{
			WorldPoint before = walk.currentTile();
			walk.tick();
			WorldPoint after = walk.currentTile();

			int moved = RenderPolicy.tileDistance(before, after);
			assertTrue("moved " + moved + " tiles in one game tick", moved <= 1);
			assertEquals("the step the frame pass interpolates must start where the citizen was",
				before, walk.stepStartTile());

			if (moved == 1)
			{
				steps++;
				assertTrue("a tick that moved must report itself as moving", walk.isMoving());
			}
			else
			{
				assertFalse("a tick that did not move must not report itself as moving",
					walk.isMoving());
			}
		}

		assertTrue("the citizen has to take some steps", steps > 100);
		assertTrue("but it must also stand still sometimes", steps < MANY_TICKS);
	}

	/**
	 * The cadence: a citizen that has arrived waits an interval before looking for
	 * somewhere new. Its first destination is the tile it is already on, so the
	 * wait is observable from the very first tick.
	 */
	@Test
	public void aNewDestinationIsOnlyLookedForOnceAnIntervalHasPassed()
	{
		CitizenWalk walk = walk(3225, 3355, 3220, 3350, 3230, 3360);
		WorldPoint start = walk.currentTile();

		for (int tick = 1; tick < CitizenWalk.IDLE_TICKS_BEFORE_NEW_DESTINATION; tick++)
		{
			walk.tick();
			assertEquals("tick " + tick + " of the idle interval must not move the citizen",
				start, walk.currentTile());
			assertFalse(walk.isMoving());
			assertEquals("nor turn it", BASE_ORIENTATION, walk.getOrientation());
		}

		// From here it is allowed to move. Give it a couple of intervals, because
		// it can legitimately roll the tile it is already standing on.
		boolean moved = false;
		for (int tick = 0; tick < CitizenWalk.IDLE_TICKS_BEFORE_NEW_DESTINATION * 4 && !moved; tick++)
		{
			walk.tick();
			moved = !walk.currentTile().equals(start);
		}
		assertTrue("the citizen must eventually go somewhere", moved);
	}

	/**
	 * Orientation while moving is the direction of travel, and it goes back to the
	 * authored value when the citizen stops. The expected value is recomputed from
	 * the step rather than compared against a table, so this checks the mapping
	 * and not a copy of it.
	 */
	@Test
	public void orientationTracksTheDirectionOfTravelAndResetsWhenIdle()
	{
		CitizenWalk walk = walk(3225, 3355, 3218, 3348, 3232, 3362);

		Set<Integer> facings = new HashSet<>();
		int idleTicks = 0;

		for (int tick = 0; tick < MANY_TICKS; tick++)
		{
			WorldPoint before = walk.currentTile();
			walk.tick();
			WorldPoint after = walk.currentTile();

			if (!walk.isMoving())
			{
				idleTicks++;
				assertEquals("an idle citizen faces the way it was authored facing",
					BASE_ORIENTATION, walk.getOrientation());
				continue;
			}

			int dx = after.getX() - before.getX();
			int dy = after.getY() - before.getY();
			assertEquals("facing for step " + dx + "," + dy,
				expectedOrientation(dx, dy), walk.getOrientation());
			facings.add(walk.getOrientation());
		}

		assertTrue("the citizen has to stand still at some point", idleTicks > 0);
		assertTrue("and it has to walk in more than one direction: saw " + facings,
			facings.size() >= 4);
		assertFalse("the base orientation is not a travel orientation, so it must never "
			+ "show up while moving", facings.contains(BASE_ORIENTATION));
	}

	/**
	 * The client's convention, spelled out independently of the implementation:
	 * 0 is south and the angle rises as the facing turns clockwise, which is what
	 * {@code Angle.getNearestDirection()} decodes as
	 * {@code (angle >> 9) & 3 -> south, west, north, east}.
	 */
	private static int expectedOrientation(int dx, int dy)
	{
		// atan2 measured from south, clockwise, over 2048 units of turn.
		double radians = Math.atan2(-dx, -dy);
		int jau = (int) Math.round(radians * 2048.0 / (2.0 * Math.PI));
		return ((jau % 2048) + 2048) % 2048;
	}

	@Test
	public void theCardinalOrientationsMatchTheClientsOwnBuckets()
	{
		// Cross-check of the helper above against the client's decoder, so the
		// test's own expectation is not the thing being trusted.
		assertEquals(0, expectedOrientation(0, -1));       // south
		assertEquals(512, expectedOrientation(-1, 0));     // west
		assertEquals(1024, expectedOrientation(0, 1));     // north
		assertEquals(1536, expectedOrientation(1, 0));     // east
		assertEquals(256, expectedOrientation(-1, -1));    // south-west
		assertEquals(768, expectedOrientation(-1, 1));     // north-west
		assertEquals(1280, expectedOrientation(1, 1));     // north-east
		assertEquals(1792, expectedOrientation(1, -1));    // south-east
	}

	/**
	 * The same citizen walks the same route every session. That is what makes a
	 * thinned or repopulated street look like the same street, and it is why the
	 * walk is seeded from the entity's identity rather than the clock.
	 */
	@Test
	public void theSameCitizenAlwaysWalksTheSameRoute()
	{
		EntityDefinition definition = regions.wanderer(
			REGION,
			new WorldPoint(3225, 3355, 0),
			new WorldPoint(3220, 3350, 0),
			new WorldPoint(3230, 3360, 0),
			BASE_ORIENTATION);

		CitizenWalk first = CitizenWalk.forDefinition(definition);
		CitizenWalk second = CitizenWalk.forDefinition(definition);
		assertNotNull(first);
		assertNotNull(second);

		StringBuilder firstRoute = new StringBuilder();
		StringBuilder secondRoute = new StringBuilder();
		for (int tick = 0; tick < 500; tick++)
		{
			first.tick();
			second.tick();
			firstRoute.append(first.currentTile()).append(';');
			secondRoute.append(second.currentTile()).append(';');
		}

		assertEquals("two walks of the same citizen must be identical",
			firstRoute.toString(), secondRoute.toString());

		// And a different citizen in the same box must not shadow it, or a whole
		// street would move in lockstep.
		EntityDefinition other = regions.wanderer(
			REGION,
			new WorldPoint(3225, 3355, 0),
			new WorldPoint(3220, 3350, 0),
			new WorldPoint(3230, 3360, 0),
			BASE_ORIENTATION);
		CitizenWalk third = CitizenWalk.forDefinition(other);
		assertNotNull(third);

		StringBuilder thirdRoute = new StringBuilder();
		for (int tick = 0; tick < 500; tick++)
		{
			third.tick();
			thirdRoute.append(third.currentTile()).append(';');
		}
		assertFalse("two citizens in the same box must not walk in lockstep",
			firstRoute.toString().equals(thirdRoute.toString()));
	}

	/**
	 * The interpolation itself: at the start of a tick the citizen is drawn on the
	 * tile it left, at the end on the tile it walked to, and half way through it is
	 * half way between them.
	 */
	@Test
	public void theDrawnPositionSlidesBetweenTheTwoTilesAcrossOneTick()
	{
		CitizenWalk walk = walk(3225, 3355, 3220, 3350, 3230, 3360);
		FakeWorldView view = FakeWorldView.around(new WorldPoint(3225, 3355, 0), REGION);

		// Walk until a step is actually in progress.
		for (int tick = 0; tick < 200 && !walk.isMoving(); tick++)
		{
			walk.tick();
		}
		assertTrue(walk.isMoving());

		LocalPoint from = LocalPoint.fromWorld(view, walk.stepStartTile());
		LocalPoint to = LocalPoint.fromWorld(view, walk.currentTile());
		assertNotNull(from);
		assertNotNull(to);

		LocalPoint atStart = walk.localPoint(view, 0f);
		LocalPoint atEnd = walk.localPoint(view, 1f);
		LocalPoint halfway = walk.localPoint(view, 0.5f);
		assertNotNull(atStart);
		assertNotNull(atEnd);
		assertNotNull(halfway);

		assertEquals("at fraction 0 it is still on the tile it left", from.getX(), atStart.getX());
		assertEquals(from.getY(), atStart.getY());
		assertEquals("at fraction 1 it has arrived", to.getX(), atEnd.getX());
		assertEquals(to.getY(), atEnd.getY());
		assertEquals("half way through the tick it is half way across the tile",
			(from.getX() + to.getX()) / 2, halfway.getX());
		assertEquals((from.getY() + to.getY()) / 2, halfway.getY());

		// Out-of-range fractions are clamped, not extrapolated: a late game tick
		// must not send the citizen sliding past the tile it was walking to.
		assertEquals(to.getX(), walk.localPoint(view, 4f).getX());
		assertEquals(from.getX(), walk.localPoint(view, -2f).getX());
	}

	@Test
	public void anIdleCitizenIsDrawnOnItsTileWhateverTheFraction()
	{
		CitizenWalk walk = walk(3225, 3355, 3220, 3350, 3230, 3360);
		FakeWorldView view = FakeWorldView.around(new WorldPoint(3225, 3355, 0), REGION);

		assertFalse("it starts idle", walk.isMoving());
		LocalPoint tile = LocalPoint.fromWorld(view, walk.currentTile());
		assertNotNull(tile);

		for (float fraction = 0f; fraction <= 1f; fraction += 0.25f)
		{
			LocalPoint drawn = walk.localPoint(view, fraction);
			assertNotNull(drawn);
			assertEquals(tile.getX(), drawn.getX());
			assertEquals(tile.getY(), drawn.getY());
		}
	}

	@Test
	public void aTileOutsideTheLoadedSceneHasNoDrawnPosition()
	{
		// 200 tiles north of the view's centre: inside the region, outside the
		// 104-tile scene.
		CitizenWalk walk = walk(3225, 3560, 3220, 3555, 3230, 3565);
		FakeWorldView view = FakeWorldView.around(new WorldPoint(3225, 3355, 0), REGION);

		assertEquals("nothing to draw, and nothing to fall over on",
			null, walk.localPoint(view, 0.5f));
	}

	/**
	 * Everything that does not wander gets no walk at all, so the per-frame pass
	 * never sees it. Scripted citizens are in that list on purpose — running their
	 * scripts is a later phase, and a scripted citizen wandering at random would
	 * be worse than one standing still.
	 */
	@Test
	public void onlyWanderingCitizensGetAWalk()
	{
		assertEquals(null, CitizenWalk.forDefinition(
			regions.citizen(REGION, 3225, 3355, 0)));

		EntityDefinition wanderer = regions.wanderer(
			REGION,
			new WorldPoint(3225, 3355, 0),
			new WorldPoint(3220, 3350, 0),
			new WorldPoint(3230, 3360, 0),
			BASE_ORIENTATION);
		assertNotNull(CitizenWalk.forDefinition(wanderer));
	}
}
