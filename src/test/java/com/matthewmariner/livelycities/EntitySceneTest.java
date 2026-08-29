package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The teardown contract, asserted against a client that actually keeps its
 * registered-object list.
 *
 * <p>This file exists because of a mutation test: gutting {@code despawn()},
 * {@code deactivateAll()} and {@code shutdown()} to no-ops — a total
 * {@code RuneLiteObject} leak on every despawn, scene change and plugin
 * shutdown — left all 37 of the earlier tests green. Nothing in the suite
 * looked at whether anything was ever deactivated.
 *
 * <p>Region 12852 spans x 3200-3263, y 3328-3391; region 12853 is the square
 * directly above it, y 3392-3455. Those two are Varrock, and they are where the
 * shipped dataset is densest, so the fixtures use them rather than invented ids.
 */
public class EntitySceneTest
{
	private static final int VARROCK_SOUTH = 12852;
	private static final int VARROCK_NORTH = 12853;

	/** In 12852, comfortably inside the cull radius of the crowds below. */
	private static final WorldPoint PLAYER = new WorldPoint(3225, 3360, 0);

	private FakeClient client;
	private FakeRegions regions;
	private FakeConfig config;
	private EntityScene scene;

	@Before
	public void setUp()
	{
		client = new FakeClient();
		regions = new FakeRegions();
		config = new FakeConfig();
		scene = new EntityScene(client, regions, config, config.overrides());
	}

	@Test
	public void changingSceneDeactivatesEverythingItActivated()
	{
		regions.file(VARROCK_SOUTH, regions.crowd(VARROCK_SOUTH, 3220, 3355, 5));

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);
		VisibilityPasses.settle(scene, PLAYER, view);
		assertEquals("the fixture should have spawned", 5, client.registeredCount());

		// Walk far enough that the scene covers a different region entirely.
		WorldPoint elsewhere = new WorldPoint(3600, 3200, 0);
		FakeWorldView moved = FakeWorldView.around(elsewhere, RenderPolicy.regionIdOf(3600, 3200));
		scene.syncRegions(moved);

		assertEquals("a scene change must leave nothing registered", 0, client.registeredCount());
	}

	@Test
	public void shutdownDeactivatesEverythingAndForgetsIt()
	{
		regions.file(VARROCK_SOUTH, regions.crowd(VARROCK_SOUTH, 3220, 3355, 4));

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);
		VisibilityPasses.settle(scene, PLAYER, view);
		assertEquals(4, client.registeredCount());

		assertEquals("shutdown reports what it deactivated", 4, scene.shutdown());
		assertEquals("shutdown must leave nothing registered", 0, client.registeredCount());
		assertEquals("and must drop every wrapper", 0, scene.getCachedRegionCount());
	}

	@Test
	public void invalidateDeactivatesEverythingWithoutDroppingTheWrappers()
	{
		regions.file(VARROCK_SOUTH, regions.crowd(VARROCK_SOUTH, 3220, 3355, 3));

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);
		scene.updateVisibility(PLAYER, view);
		assertEquals(3, client.registeredCount());

		assertEquals(3, scene.invalidate("LOADING"));
		assertEquals("LOADING must leave nothing registered", 0, client.registeredCount());
		assertEquals("but the models stay cached", 1, scene.getCachedRegionCount());
	}

	@Test
	public void aRegionLeavingScopeDeactivatesOnlyItsOwnEntities()
	{
		// One entity in each region, both within the cull radius of a player
		// standing near the border.
		WorldPoint border = new WorldPoint(3225, 3390, 0);
		regions.file(VARROCK_SOUTH, regions.citizen(VARROCK_SOUTH, 3225, 3385, 0));
		regions.file(VARROCK_NORTH, regions.citizen(VARROCK_NORTH, 3225, 3395, 0));

		FakeWorldView view = FakeWorldView.around(border, VARROCK_SOUTH, VARROCK_NORTH);
		scene.syncRegions(view);
		scene.updateVisibility(border, view);
		assertEquals(2, client.registeredCount());

		// The northern region drops out of the scene; the player has not moved
		// far enough for the southern one to.
		view.setMapRegions(VARROCK_SOUTH);
		scene.syncRegions(view);
		scene.updateVisibility(border, view);

		assertEquals("only the region still in scope may stay registered", 1, client.registeredCount());
		assertEquals(1, scene.getInScopeCount());
	}

	@Test
	public void theActiveCapIsNeverExceeded()
	{
		// A crowd half again as big as the cap, in 6-wide rows running north.
		int cap = RenderPolicy.MAX_ACTIVE_OBJECTS;
		List<EntityDefinition> crowd = regions.crowd(VARROCK_SOUTH, 3210, 3350, cap + 40);
		regions.file(VARROCK_SOUTH, crowd);

		// Standing at the north end: the cap keeps the northern rows.
		WorldPoint north = new WorldPoint(3212, 3372, 0);
		FakeWorldView view = FakeWorldView.around(north, VARROCK_SOUTH);
		scene.syncRegions(view);
		VisibilityPasses.settle(scene, north, view);

		assertEquals("the crowd is bigger than the cap", cap + 40, scene.getInScopeCount());
		assertEquals(cap, client.registeredCount());

		// Now walk to the south end. Every entity is still inside the cull
		// radius, so this swaps which of them the cap keeps — and it swaps them
		// against the iteration order, so the newly wanted entities come up
		// before the ones being dropped. That is exactly where a single fused
		// deactivate/activate loop transiently holds far more than the cap.
		//
		// It also takes more than one pass now: the southern rows have no models yet,
		// and RenderPolicy.MAX_MODEL_BUILDS_PER_PASS builds three of them per pass. That
		// does not weaken this test — the peak is sampled on every pass by FakeClient,
		// so a fused loop would be caught on whichever pass it over-registered on, and
		// there are now twenty-eight chances instead of one.
		WorldPoint south = new WorldPoint(3212, 3348, 0);
		VisibilityPasses.settle(scene, south, view);

		assertEquals(cap, client.registeredCount());
		assertTrue("the two positions must actually want different entities",
			client.peakRegistered() >= cap);
		assertEquals("the client must never hold more than the cap, mid-pass included",
			cap, client.peakRegistered());
	}

	// --- The build budget -----------------------------------------------------
	//
	// Measured, not supposed: 300 game ticks in Varrock put the visibility pass's p99
	// in the histogram's overflow bucket at >=11ms and its worst pass at 53.73ms,
	// against a pre-registered "a problem" line of 8ms. Individual builds were fine
	// (p99 1.50ms) — 371 of them simply landed across 331 passes, so walking into
	// Varrock square built dozens inside one game tick. These four tests are the fix:
	// a pass builds at most RenderPolicy.MAX_MODEL_BUILDS_PER_PASS models, nearest
	// first, and the rest arrive on later passes.

	@Test
	public void aPassBuildsNoMoreThanItsBudgetAndTheRestArriveOnLaterPasses()
	{
		int budget = RenderPolicy.MAX_MODEL_BUILDS_PER_PASS;

		// Three full passes and a fourth with a single citizen left in it, so the last
		// pass is one where the budget is deliberately not spent — a version that built
		// exactly `budget` every pass regardless would overshoot the crowd there.
		int crowd = budget * 3 + 1;
		regions.file(VARROCK_SOUTH, regions.crowd(VARROCK_SOUTH, 3220, 3355, crowd));

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);

		for (int pass = 1; pass <= 4; pass++)
		{
			client.resetCounters();
			scene.updateVisibility(PLAYER, view);

			int outstanding = crowd - (pass - 1) * budget;
			int expected = Math.max(0, Math.min(budget, outstanding));

			assertEquals("pass " + pass + " may build " + expected + " model(s)",
				expected, client.mergeCalls());
			assertEquals("and a citizen whose build was held over must not have asked the "
					+ "client for its parts either — the whole point is that the work does "
					+ "not happen on this pass",
				expected, client.loadModelDataCalls());
			assertEquals("pass " + pass + ": everything built so far is on screen",
				Math.min(crowd, pass * budget), client.registeredCount());
		}

		// And once the crowd has arrived the budget costs nothing at all: a settled
		// scene builds nothing, so this is a brake on the burst rather than a
		// permanent tax on every pass.
		client.resetCounters();
		scene.updateVisibility(PLAYER, view);
		assertEquals(0, client.mergeCalls());
		assertEquals(crowd, client.registeredCount());
	}

	/**
	 * What waits is the far end of the crowd, never the citizen the player is standing
	 * next to.
	 *
	 * <p><b>The fixture files them furthest first</b>, so scope order and distance order
	 * disagree. That is the whole test: the budget is spent walking the sorted candidate
	 * list, and a version that spent it in the order the region file happens to list its
	 * records would build the three citizens eight tiles away and leave the one on the
	 * next tile waiting — which is the same "nearest first" property
	 * {@link RenderPolicy#MAX_ACTIVE_OBJECTS} has always had, and it has to survive.
	 */
	@Test
	public void theBudgetHoldsBackTheFarCitizensAndNeverTheNearOnes()
	{
		// Longer than the budget, or the assertion below is satisfied by a version with
		// no budget in it at all — every citizen would be near enough to build and
		// "distance <= budget" would be true for all of them. Sized off the constant so
		// a re-measurement that moves the cap cannot quietly hollow this out.
		List<EntityDefinition> line = new ArrayList<>();
		for (int distance = RenderPolicy.MAX_MODEL_BUILDS_PER_PASS + 3; distance >= 1; distance--)
		{
			line.add(regions.citizen(
				VARROCK_SOUTH, PLAYER.getX(), PLAYER.getY() + distance, 0));
		}
		regions.file(VARROCK_SOUTH, line);

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);
		scene.updateVisibility(PLAYER, view);

		assertEquals(RenderPolicy.MAX_MODEL_BUILDS_PER_PASS, client.registeredCount());

		for (EntityDefinition definition : line)
		{
			LivelyEntity wrapper = scene.wrapperFor(definition);
			assertNotNull(wrapper);

			int distance = RenderPolicy.tileDistance(PLAYER, definition.getWorldLocation());
			assertEquals("the citizen " + distance + " tile(s) away, filed at position "
					+ line.indexOf(definition) + " of " + line.size(),
				distance <= RenderPolicy.MAX_MODEL_BUILDS_PER_PASS, wrapper.isActive());
		}

		// The far ones are not lost, refused or broken — they simply have not arrived.
		VisibilityPasses.settle(scene, PLAYER, view);
		assertEquals("every one of them gets there", line.size(), client.registeredCount());
		assertEquals("and none of them is latched out on the way", 0, scene.countBroken());
	}

	/**
	 * A build held over by the budget is not an attempt, and must not spend one.
	 *
	 * <p>The bug this exists to make impossible: {@link LivelyEntity#MAX_MODEL_ATTEMPTS}
	 * is three per scene load, spaced by {@link LivelyEntity#RETRY_BACKOFF_PASSES}, and
	 * it is there to stop a genuinely missing model producing a per-tick storm of cache
	 * calls. If waiting for the budget counted against it, a crowd four passes deep
	 * would leave its last citizens having "attempted" three times without the client
	 * ever being asked anything — and then unbuildable for the next twenty-five passes,
	 * on a warm cache, for no reason at all.
	 *
	 * <p>The fixture is sized so that only that failure mode can show up here: one more
	 * pass' worth of crowd than the retry budget has attempts in it.
	 */
	@Test
	public void aBuildHeldOverByTheBudgetIsNotOneOfTheEntitysModelAttempts()
	{
		int budget = RenderPolicy.MAX_MODEL_BUILDS_PER_PASS;
		int passes = LivelyEntity.MAX_MODEL_ATTEMPTS + 1;
		int crowd = budget * passes;
		regions.file(VARROCK_SOUTH, regions.crowd(VARROCK_SOUTH, 3218, 3352, crowd));

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);

		for (int pass = 0; pass < passes; pass++)
		{
			scene.updateVisibility(PLAYER, view);
		}

		assertEquals("every citizen arrives inside " + passes + " passes, and none of them "
				+ "burns a retry attempt waiting its turn",
			crowd, client.registeredCount());
		assertEquals("the client is asked for each citizen's parts exactly once", crowd,
			client.loadModelDataCalls());
		assertEquals(0, scene.countBroken());
	}

	/**
	 * A cold model cache does not spend the build budget, because it did not build
	 * anything.
	 *
	 * <p>This is the reason the budget is charged when a build <i>completes</i> rather
	 * than when one is planned, and it is the difference between a fix and a regression.
	 * Charged on intent, the three nearest citizens on a cold login would hold the whole
	 * budget shut while doing no work, and — since a miss puts them into
	 * {@link LivelyEntity#RETRY_BACKOFF_PASSES} — would keep holding it for
	 * twenty-five passes with everybody behind them never asked at all. Charged on
	 * completion, a cold pass costs exactly what it always cost.
	 */
	@Test
	public void aColdModelCacheDoesNotSpendTheBuildBudget()
	{
		int crowd = RenderPolicy.MAX_MODEL_BUILDS_PER_PASS * 3;
		regions.file(VARROCK_SOUTH, regions.crowd(VARROCK_SOUTH, 3220, 3355, crowd));
		client.setCacheCold(true);

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);
		scene.updateVisibility(PLAYER, view);

		assertEquals("a cold cache spawns nobody", 0, client.registeredCount());
		assertEquals("but every one of them is asked for, on the one pass: a build that "
				+ "did not happen must not spend a budget that bounds the cost of building",
			crowd, client.loadModelDataCalls());
		assertEquals("and nothing was built, so nothing was merged", 0, client.mergeCalls());

		// And when the cache warms, they all arrive — through the retry backoff and then
		// through the budget, which is the two mechanisms cooperating rather than one
		// starving the other.
		client.setCacheCold(false);
		for (int pass = 0; pass < LivelyEntity.RETRY_BACKOFF_PASSES; pass++)
		{
			scene.updateVisibility(PLAYER, view);
		}
		VisibilityPasses.settle(scene, PLAYER, view);
		assertEquals("a warm cache must spawn what the cold one could not",
			crowd, client.registeredCount());
	}

	/**
	 * The crowd cap still counts objects and the build budget still counts builds, in
	 * the one fixture where both bind.
	 */
	@Test
	public void theCrowdCapBoundsWhatIsActiveAndTheBudgetOnlyBoundsHowFastItGetsThere()
	{
		int cap = RenderPolicy.MAX_ACTIVE_OBJECTS;
		regions.file(VARROCK_SOUTH, regions.crowd(VARROCK_SOUTH, 3210, 3350, cap + 20));

		WorldPoint north = new WorldPoint(3212, 3372, 0);
		FakeWorldView view = FakeWorldView.around(north, VARROCK_SOUTH);
		scene.syncRegions(view);

		client.resetCounters();
		VisibilityPasses.settle(scene, north, view);

		assertEquals("the crowd cap is what decides how many are on screen", cap,
			client.registeredCount());
		assertEquals("and the budget is not a second, smaller cap — every one of the "
				+ "capped crowd is built, just over more than one pass",
			cap, client.mergeCalls());
		assertEquals("the client must never hold more than the cap, mid-pass included",
			cap, client.peakRegistered());
	}

	@Test
	public void aPartialModelBuildActivatesNothing()
	{
		// Two-part model; the second part never resolves.
		regions.file(VARROCK_SOUTH, regions.citizen(VARROCK_SOUTH, 3225, 3355, 0, 217, 218));
		client.setUnloadable(218);

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);
		scene.updateVisibility(PLAYER, view);

		assertEquals("half a citizen must not be spawned", 0, client.registeredCount());
		assertEquals("and must not be merged into a model either", 0, client.mergeCalls());
		assertEquals("a missing part is not a broken entity", 0, scene.countBroken());
	}

	@Test
	public void aTransientModelMissIsRetriedAndHealsWhenTheCacheWarms()
	{
		regions.file(VARROCK_SOUTH, regions.citizen(VARROCK_SOUTH, 3225, 3355, 0, 217));
		client.setCacheCold(true);

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);
		scene.updateVisibility(PLAYER, view);
		assertEquals(0, client.registeredCount());

		// The cache warms up. No scene change and no new wrapper, so the same
		// entity has to heal itself — which it does on its next spaced-out
		// retry, not on the next tick.
		client.setCacheCold(false);
		scene.updateVisibility(PLAYER, view);
		assertEquals("a retry is spaced out, not fired on the next tick", 0, client.registeredCount());

		for (int pass = 0; pass < LivelyEntity.RETRY_BACKOFF_PASSES; pass++)
		{
			scene.updateVisibility(PLAYER, view);
		}
		assertEquals("a warm cache must spawn what the cold one could not", 1, client.registeredCount());
	}

	@Test
	public void theRetryBudgetIsBoundedAndRefilledByTheNextSceneLoad()
	{
		regions.file(VARROCK_SOUTH, regions.citizen(VARROCK_SOUTH, 3225, 3355, 0, 217));
		client.setCacheCold(true);

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);

		// Twice as many passes as the budget can possibly cover, and the client
		// is still asked exactly the budget: otherwise a permanently missing
		// model is a per-tick storm of calls and warnings.
		int passes = LivelyEntity.MAX_MODEL_ATTEMPTS * LivelyEntity.RETRY_BACKOFF_PASSES * 2;
		for (int i = 0; i < passes; i++)
		{
			scene.updateVisibility(PLAYER, view);
		}
		assertEquals(LivelyEntity.MAX_MODEL_ATTEMPTS, client.loadModelDataCalls());
		assertEquals(0, client.registeredCount());

		// A scene load is the granularity at which it is worth asking again.
		client.resetCounters();
		view.setMapRegions(VARROCK_SOUTH, VARROCK_NORTH);
		scene.syncRegions(view);
		scene.updateVisibility(PLAYER, view);
		assertEquals("a scene load must hand the budget back", 1, client.loadModelDataCalls());
	}

	@Test
	public void aThrowingModelLoadCostsOneEntityNotThePass()
	{
		List<EntityDefinition> entities = new ArrayList<>();
		entities.add(regions.citizen(VARROCK_SOUTH, 3225, 3355, 0, 900));
		entities.addAll(regions.crowd(VARROCK_SOUTH, 3220, 3350, 4));
		regions.file(VARROCK_SOUTH, entities);
		client.setThrowing(900);

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);
		VisibilityPasses.settle(scene, PLAYER, view);

		assertEquals("the four neighbours must still be spawned", 4, client.registeredCount());
		assertEquals("the thrower is latched out", 1, scene.countBroken());

		// And it stays out: no second attempt, no second stack trace per tick.
		client.resetCounters();
		scene.updateVisibility(PLAYER, view);
		assertEquals(0, client.loadModelDataCalls());
		assertEquals(4, client.registeredCount());
	}

	@Test
	public void anInstancedSceneDeactivatesEverything()
	{
		regions.file(VARROCK_SOUTH, regions.crowd(VARROCK_SOUTH, 3220, 3355, 3));

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);
		scene.updateVisibility(PLAYER, view);
		assertEquals(3, client.registeredCount());

		view.setInstance(true);
		scene.updateVisibility(PLAYER, view);
		assertEquals("an instance must leave nothing registered", 0, client.registeredCount());
	}

	@Test
	public void scopeFollowsTheEntitysTileNotTheFileItIsFiledUnder()
	{
		// The shipped case: "Dark wizard" lives in 12853.json and stands at
		// (3261, 3386), which is region 12852.
		EntityDefinition misfiled = regions.citizen(VARROCK_NORTH, 3261, 3386, 0, 217);
		assertEquals(VARROCK_NORTH, misfiled.getRegionId());
		assertEquals(VARROCK_SOUTH, misfiled.getTileRegionId());
		regions.file(VARROCK_NORTH, misfiled);

		WorldPoint nearby = new WorldPoint(3255, 3382, 0);

		// The file's region is in the scene but the tile's region is not: the
		// client has not loaded the ground this entity stands on.
		FakeWorldView northOnly = FakeWorldView.around(nearby, VARROCK_NORTH);
		scene.syncRegions(northOnly);
		assertEquals("an entity whose own region is not loaded is not in scope",
			0, scene.getInScopeCount());

		// Both loaded, which is what actually happens near a border.
		FakeWorldView both = FakeWorldView.around(nearby, VARROCK_NORTH, VARROCK_SOUTH);
		scene.syncRegions(both);
		scene.updateVisibility(nearby, both);
		assertEquals(1, scene.getInScopeCount());
		assertEquals(1, client.registeredCount());
	}

	@Test
	public void wrappersAreEvictedAfterTheirRegionHasBeenOutOfScopeForTheGracePeriod()
	{
		// Both sides of the border, all four inside the cull radius of a player
		// standing on it.
		regions.file(VARROCK_SOUTH, regions.crowd(VARROCK_SOUTH, 3220, 3380, 3));
		regions.file(VARROCK_NORTH, regions.citizen(VARROCK_NORTH, 3225, 3395, 0));

		WorldPoint border = new WorldPoint(3225, 3390, 0);
		FakeWorldView view = FakeWorldView.around(border, VARROCK_SOUTH, VARROCK_NORTH);
		scene.syncRegions(view);
		VisibilityPasses.settle(scene, border, view);
		assertEquals(2, scene.getCachedRegionCount());
		assertEquals(4, client.registeredCount());

		// Inside the grace period the wrappers survive, so stepping over a
		// border and back costs an activate rather than a rebuild.
		for (int i = 0; i < EntityScene.EVICTION_GRACE_SCOPE_CHANGES; i++)
		{
			view.setMapRegions(VARROCK_SOUTH, 10000 + i);
			scene.syncRegions(view);
			scene.updateVisibility(border, view);
		}
		assertEquals("still within the grace period", 2, scene.getCachedRegionCount());

		// One scope change past it, and the northern region is dropped —
		// deactivated on the way out, not orphaned in the active state.
		view.setMapRegions(VARROCK_SOUTH, 10099);
		scene.syncRegions(view);
		scene.updateVisibility(border, view);

		assertEquals("the stale region's wrappers are gone", 1, scene.getCachedRegionCount());
		assertEquals("and nothing it held is still registered", 3, client.registeredCount());
	}

	@Test
	public void anEvictedRegionIsRebuiltWhenItComesBack()
	{
		regions.file(VARROCK_NORTH, regions.citizen(VARROCK_NORTH, 3225, 3395, 0, 217));

		WorldPoint border = new WorldPoint(3225, 3390, 0);
		FakeWorldView view = FakeWorldView.around(border, VARROCK_NORTH);
		scene.syncRegions(view);
		scene.updateVisibility(border, view);
		assertEquals(1, client.registeredCount());

		for (int i = 0; i <= EntityScene.EVICTION_GRACE_SCOPE_CHANGES; i++)
		{
			view.setMapRegions(10000 + i);
			scene.syncRegions(view);
		}
		assertEquals(0, scene.getCachedRegionCount());
		assertEquals(0, client.registeredCount());

		view.setMapRegions(VARROCK_NORTH);
		scene.syncRegions(view);
		scene.updateVisibility(border, view);
		assertEquals("coming back rebuilds and reactivates", 1, client.registeredCount());
		assertEquals(1, scene.getCachedRegionCount());
	}

	/**
	 * A misfiled entity is the one case where the region that keeps its wrapper
	 * alive (the file's) and the region that makes it visible (its tile's) can
	 * come and go independently. Nothing may end up in scope pointing at a
	 * wrapper that has been evicted: teardown walks the cache, so an entity that
	 * is spawnable but no longer cached is a leak by construction.
	 */
	@Test
	public void anEvictedMisfiledEntityLeavesNothingBehindInScope()
	{
		regions.file(VARROCK_NORTH, regions.citizen(VARROCK_NORTH, 3261, 3386, 0, 217));

		WorldPoint nearby = new WorldPoint(3255, 3382, 0);
		FakeWorldView view = FakeWorldView.around(nearby, VARROCK_NORTH, VARROCK_SOUTH);
		scene.syncRegions(view);
		scene.updateVisibility(nearby, view);
		assertEquals(1, client.registeredCount());

		// The file's region leaves the scene for longer than the grace period
		// while the tile's region stays in it.
		for (int i = 0; i <= EntityScene.EVICTION_GRACE_SCOPE_CHANGES; i++)
		{
			view.setMapRegions(VARROCK_SOUTH, 10000 + i);
			scene.syncRegions(view);
			scene.updateVisibility(nearby, view);
		}

		assertEquals(0, scene.getCachedRegionCount());
		assertEquals("an evicted wrapper must not still be in scope", 0, scene.getInScopeCount());
		assertEquals(0, client.registeredCount());
	}

	/**
	 * The explicit user requirement: a city's checkbox has to <i>deactivate</i>
	 * what is already spawned, not merely stop new spawns. Both regions here are
	 * Varrock, so one checkbox governs both.
	 */
	@Test
	public void untickingACityDeactivatesItsCitizensAndTickingItBackBringsThemBack()
	{
		regions.file(VARROCK_SOUTH, regions.crowd(VARROCK_SOUTH, 3220, 3355, 5));

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);
		VisibilityPasses.settle(scene, PLAYER, view);
		scene.onGameTick(PLAYER, view);
		assertEquals(5, client.registeredCount());

		// Untick Varrock. No scene change, no reload — just the next pass. One pass is
		// still enough: every model is built by now, and the build budget bounds
		// building rather than activating, so bringing them back below is instant.
		config.disableOnly(City.VARROCK);
		scene.onGameTick(PLAYER, view);
		assertEquals("unticking a city must deactivate what it already spawned",
			0, client.registeredCount());
		assertEquals("the wrappers stay cached, ready to come back", 1, scene.getCachedRegionCount());
		assertEquals("and they are still in scope — they are switched off, not gone",
			5, scene.getInScopeCount());

		// And back, still without a scene change.
		config.enable(City.VARROCK);
		scene.onGameTick(PLAYER, view);
		assertEquals("ticking it back must respawn them", 5, client.registeredCount());
	}

	/**
	 * The checkbox has to be the <i>right</i> checkbox. Unticking somewhere else
	 * leaving Varrock empty would pass the test above.
	 */
	@Test
	public void untickingADifferentCityLeavesThisOneAlone()
	{
		regions.file(VARROCK_SOUTH, regions.crowd(VARROCK_SOUTH, 3220, 3355, 4));

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);
		VisibilityPasses.settle(scene, PLAYER, view);
		scene.onGameTick(PLAYER, view);
		assertEquals(4, client.registeredCount());

		config.disableOnly(City.LUMBRIDGE);
		scene.onGameTick(PLAYER, view);
		assertEquals("Lumbridge's checkbox must not empty Varrock", 4, client.registeredCount());
	}

	/**
	 * Which city an entity belongs to is decided by the tile it stands on, the same
	 * way scope membership is. The shipped misfiling puts both regions in Varrock,
	 * so this uses a file in Varrock holding an entity standing in Lumbridge to
	 * tell the two answers apart.
	 */
	@Test
	public void theCityIsDecidedByTheTileNotTheFileTheEntityIsFiledUnder()
	{
		// 12850 is Lumbridge. A tile in it is nowhere near Varrock, so filing an
		// entity standing there under a Varrock region is a fiction — which is
		// exactly what makes the two possible answers distinguishable.
		int lumbridgeRegion = 12850;
		WorldPoint inLumbridge = new WorldPoint(3225, 3210, 0);
		assertEquals(lumbridgeRegion, RenderPolicy.regionIdOf(inLumbridge.getX(), inLumbridge.getY()));

		EntityDefinition misfiled = regions.citizen(
			VARROCK_SOUTH, inLumbridge.getX(), inLumbridge.getY(), 0);
		regions.file(VARROCK_SOUTH, misfiled);

		FakeWorldView view = FakeWorldView.around(inLumbridge, VARROCK_SOUTH, lumbridgeRegion);
		scene.syncRegions(view);
		scene.onGameTick(inLumbridge, view);
		assertEquals(1, client.registeredCount());

		config.disableOnly(City.VARROCK);
		scene.onGameTick(inLumbridge, view);
		assertEquals("the file's city must not govern it", 1, client.registeredCount());

		config.disableOnly(City.LUMBRIDGE);
		scene.onGameTick(inLumbridge, view);
		assertEquals("the tile's city must", 0, client.registeredCount());
	}

	@Test
	public void thinningTheCrowdRemovesSomeOfItAndTurningTheDialBackRestoresIt()
	{
		int crowd = 12;
		regions.file(VARROCK_SOUTH, regions.crowd(VARROCK_SOUTH, 3218, 3352, crowd));

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);
		VisibilityPasses.settle(scene, PLAYER, view);
		scene.onGameTick(PLAYER, view);
		assertEquals("full density keeps everyone", crowd, client.registeredCount());

		config.setCrowdDensity(CrowdDensity.SPARSE);
		scene.onGameTick(PLAYER, view);
		int sparse = client.registeredCount();
		assertTrue("sparse must actually thin the crowd", sparse < crowd);
		assertTrue("but not empty it", sparse > 0);

		config.setCrowdDensity(CrowdDensity.NORMAL);
		scene.onGameTick(PLAYER, view);
		int normal = client.registeredCount();
		assertTrue("normal keeps at least as many as sparse", normal >= sparse);
		assertTrue("and fewer than everyone", normal <= crowd);

		config.setCrowdDensity(CrowdDensity.FULL);
		scene.onGameTick(PLAYER, view);
		assertEquals("turning it back up restores the whole crowd", crowd, client.registeredCount());
	}

	@Test
	public void aWiderCullRadiusReachesEntitiesADefaultOneDoesNot()
	{
		// Just outside the default radius, comfortably inside the widest.
		int beyond = RenderPolicy.DEFAULT_CULL_RADIUS + 2;
		regions.file(VARROCK_SOUTH, regions.citizen(VARROCK_SOUTH, PLAYER.getX(), PLAYER.getY() + beyond, 0));

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);
		scene.onGameTick(PLAYER, view);
		assertEquals("outside the default radius", 0, client.registeredCount());

		config.setCullRadius(RenderPolicy.MAX_CULL_RADIUS);
		scene.onGameTick(PLAYER, view);
		assertEquals("inside the widest the dial allows", 1, client.registeredCount());

		// And a value past the ceiling is clamped, not honoured — the entity is
		// already visible, so what this pins is that nothing throws or wraps.
		config.setCullRadius(Integer.MAX_VALUE);
		scene.onGameTick(PLAYER, view);
		assertEquals(1, client.registeredCount());
	}

	/**
	 * The border-crossing wanderers, counted and named from the shipped files.
	 *
	 * <p>The claim below — and the one in {@link EntityScene}'s own javadoc — is the
	 * reason scope membership is decided from the authored tile rather than from where
	 * a citizen currently is. It was written as "six of the 63" and stayed that way
	 * through a dataset cut that took the roster to 39 wanderers, three of which still
	 * cross a border. Counted here so the next cut cannot leave it stale.
	 *
	 * <p>The box read is {@link EntityDefinition}'s validated one, i.e. the box the
	 * walk actually uses, clamp included — the question is where a citizen can end up
	 * standing, not what the file asked for.
	 */
	@Test
	public void exactlyThreeShippedWanderersPaceAcrossARegionBorder()
	{
		RegionDataLoader loader = new RegionDataLoader(TestGson.injected());
		int wanderers = 0;
		List<String> crossing = new ArrayList<>();

		for (int regionId : ShippedRegions.ids())
		{
			RegionDefinition region = loader.loadRegion(regionId);
			assertNotNull("region " + regionId + " failed to load", region);

			for (EntityDefinition entity : region.getEntities())
			{
				EntityDefinition.WanderBox box = entity.getWanderBox();
				if (box == null)
				{
					continue;
				}

				wanderers++;
				int home = RenderPolicy.regionIdOf(box.getMinX(), box.getMinY());
				if (home != RenderPolicy.regionIdOf(box.getMaxX(), box.getMinY())
					|| home != RenderPolicy.regionIdOf(box.getMinX(), box.getMaxY())
					|| home != RenderPolicy.regionIdOf(box.getMaxX(), box.getMaxY()))
				{
					crossing.add(entity.getName());
				}
			}
		}

		assertEquals("the shipped wander boxes", 51, wanderers);
		assertEquals("wanderers pacing across a region border", 3, crossing.size());
		assertTrue("and they are the three the comments name: " + crossing,
			crossing.contains("Ava") && crossing.contains("Dark wizard")
				&& crossing.contains("Zethrus"));
	}

	/**
	 * Three of the 51 shipped wanderers have boxes that straddle a region border.
	 * Membership is pinned to the authored tile and does not follow the walk, so
	 * neither of the two failure modes is reachable: the citizen cannot be listed
	 * twice, and it cannot be left registered once its home region goes.
	 *
	 * <p>The fixture is the real one: "Dark wizard" is filed under 12853, stands
	 * in 12852 and paces a box that spans both. Which three, and that there are
	 * three, is counted from the shipped files by
	 * {@link #exactlyThreeShippedWanderersPaceAcrossARegionBorder()}.
	 */
	@Test
	public void aWandererWhoseBoxCrossesARegionBorderIsNeitherDoubledNorOrphaned()
	{
		EntityDefinition darkWizard = regions.wanderer(
			VARROCK_NORTH,
			new WorldPoint(3261, 3386, 0),
			new WorldPoint(3258, 3382, 0),
			new WorldPoint(3261, 3395, 0),
			500);
		assertEquals("filed under the northern region", VARROCK_NORTH, darkWizard.getRegionId());
		assertEquals("but standing in the southern one", VARROCK_SOUTH, darkWizard.getTileRegionId());
		regions.file(VARROCK_NORTH, darkWizard);

		WorldPoint nearby = new WorldPoint(3255, 3388, 0);
		FakeWorldView view = FakeWorldView.around(nearby, VARROCK_NORTH, VARROCK_SOUTH);
		scene.syncRegions(view);
		scene.onGameTick(nearby, view);
		assertEquals(1, scene.getInScopeCount());
		assertEquals(1, client.registeredCount());
		assertEquals(1, scene.getWalkerCount());

		CitizenWalk walk = walkOf(scene, darkWizard);

		// Walk it for long enough to cross the border and come back several times.
		boolean visitedNorth = false;
		boolean visitedSouth = false;
		for (int tick = 0; tick < 600; tick++)
		{
			scene.onGameTick(nearby, view);
			scene.onFrame(view, 0.5f);

			assertEquals("one wrapper means one object, wherever it has walked to",
				1, client.registeredCount());
			assertEquals("and one entry in the frame list", 1, scene.getWalkerCount());
			assertEquals("and one entry in scope", 1, scene.getInScopeCount());

			WorldPoint tile = walk.currentTile();
			assertTrue("a wanderer must never leave its box", darkWizard.getWanderBox()
				.contains(tile.getX(), tile.getY()));

			int region = RenderPolicy.regionIdOf(tile.getX(), tile.getY());
			visitedNorth |= region == VARROCK_NORTH;
			visitedSouth |= region == VARROCK_SOUTH;
		}

		assertTrue("the fixture has to actually cross the border to prove anything",
			visitedNorth && visitedSouth);

		// Now the region it is filed under leaves the scene while the region it
		// stands in stays loaded. That is the one case where the region keeping
		// the wrapper alive and the region making it visible come and go
		// independently, and a walk across a border does not change it: the
		// wrapper survives its grace period and is then dropped, deactivated on
		// the way out, because eviction walks the cache rather than the in-scope
		// list.
		for (int change = 0; change <= EntityScene.EVICTION_GRACE_SCOPE_CHANGES; change++)
		{
			view.setMapRegions(VARROCK_SOUTH, 10000 + change);
			scene.syncRegions(view);
			scene.onGameTick(nearby, view);
			scene.onFrame(view, 0.5f);

			assertTrue("there is one wrapper, so there can never be two objects",
				client.registeredCount() <= 1);
			assertTrue(scene.getWalkerCount() <= 1);
			assertTrue(scene.getInScopeCount() <= 1);

			if (change == 0)
			{
				assertEquals("inside the grace period it is still the same one object",
					1, client.registeredCount());
			}
		}

		assertEquals("past the grace period the wrapper is gone", 0, scene.getCachedRegionCount());
		assertEquals("an evicted wanderer must not still be in scope", 0, scene.getInScopeCount());
		assertEquals("nor still registered", 0, client.registeredCount());
		assertEquals("nor still in the per-frame list", 0, scene.getWalkerCount());

		assertEquals("and shutdown finds nothing left over", 0, scene.shutdown());
		assertEquals(0, client.registeredCount());
	}

	/**
	 * The two clocks, told apart.
	 *
	 * <p>A frame must never move the logical tile, and a game tick must never do
	 * the visual interpolation. Asserting only that "something moved" would pass
	 * with both jobs in either handler — and the version with stepping in the
	 * frame handler is a citizen sprinting at the frame rate.
	 */
	@Test
	public void movementIsSteppedPerGameTickAndInterpolatedPerFrame()
	{
		EntityDefinition walker = regions.wanderer(
			VARROCK_SOUTH,
			new WorldPoint(3225, 3355, 0),
			new WorldPoint(3220, 3350, 0),
			new WorldPoint(3230, 3360, 0),
			500);
		regions.file(VARROCK_SOUTH, walker);

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);
		scene.onGameTick(PLAYER, view);
		assertEquals(1, scene.getWalkerCount());

		// Tick until it is actually mid-step; the first destination is its own tile
		// so it stands still for the first interval.
		CitizenWalk walk = walkOf(scene, walker);
		for (int tick = 0; tick < 200 && !walk.isMoving(); tick++)
		{
			scene.onGameTick(PLAYER, view);
		}
		assertTrue("the fixture must get the citizen walking", walk.isMoving());

		WorldPoint tileBefore = walk.currentTile();
		FakeRuneLiteObject object = client.lastObject();

		scene.onFrame(view, 0f);
		int xAtStart = object.getX();
		int yAtStart = object.getY();

		// A hundred frames, no game tick.
		for (int frame = 1; frame <= 100; frame++)
		{
			scene.onFrame(view, frame / 100f);
		}

		assertEquals("frames must not step the logical tile", tileBefore, walk.currentTile());
		assertTrue("but they must move the drawn position",
			object.getX() != xAtStart || object.getY() != yAtStart);

		// Game ticks, no frames: only these may move the tile, and one of the next
		// few has to. The bound is the idle interval plus slack, so a step handler
		// that quietly did nothing could not hide inside it.
		int ticksToMove = 0;
		while (walk.currentTile().equals(tileBefore))
		{
			ticksToMove++;
			assertTrue("a game tick must be what steps the tile",
				ticksToMove <= CitizenWalk.IDLE_TICKS_BEFORE_NEW_DESTINATION + 2);
			scene.onGameTick(PLAYER, view);
		}

		// And the animation is never advanced by us — the client does that, once
		// per frame, for every registered object.
		assertEquals("advancing the animation is the client's job, not ours",
			0, object.tickCalls());
	}

	/**
	 * Interpolating a position must not churn the client's registered-object list.
	 *
	 * <p>{@code RuneLiteObject.setLocation} quietly deactivates and reactivates the
	 * object whenever the point's world view differs from the one the object
	 * already has. A hand-built {@code LocalPoint} carrying the wrong world view
	 * would therefore unregister and re-register every walking citizen on every
	 * frame — invisible in the active count, and expensive.
	 */
	@Test
	public void interpolatingAPositionDoesNotReRegisterTheObject()
	{
		EntityDefinition walker = regions.wanderer(
			VARROCK_SOUTH,
			new WorldPoint(3225, 3355, 0),
			new WorldPoint(3221, 3351, 0),
			new WorldPoint(3229, 3359, 0),
			500);
		regions.file(VARROCK_SOUTH, walker);

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);
		scene.onGameTick(PLAYER, view);

		CitizenWalk walk = walkOf(scene, walker);
		for (int tick = 0; tick < 200 && !walk.isMoving(); tick++)
		{
			scene.onGameTick(PLAYER, view);
		}
		assertTrue("the fixture must get the citizen walking", walk.isMoving());

		client.resetCounters();
		for (int frame = 0; frame < 1000; frame++)
		{
			scene.onFrame(view, (frame % 30) / 30f);
		}

		assertEquals("a thousand frames of interpolation, no re-registration",
			0, client.registerCalls());
		assertEquals(0, client.removeCalls());
		assertEquals("and the citizen is still there", 1, client.registeredCount());
	}

	@Test
	public void aStationaryCrowdNeedsNoPerFrameWork()
	{
		regions.file(VARROCK_SOUTH, regions.crowd(VARROCK_SOUTH, 3220, 3355, 6));

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);
		VisibilityPasses.settle(scene, PLAYER, view);
		scene.onGameTick(PLAYER, view);

		assertEquals(6, client.registeredCount());
		assertEquals("nothing here wanders, so the frame pass has an empty list",
			0, scene.getWalkerCount());
	}

	@Test
	public void aDeactivatedWandererStopsWalkingAndComesBackWhereItLeftOff()
	{
		EntityDefinition walker = regions.wanderer(
			VARROCK_SOUTH,
			new WorldPoint(3225, 3355, 0),
			new WorldPoint(3221, 3351, 0),
			new WorldPoint(3229, 3359, 0),
			500);
		regions.file(VARROCK_SOUTH, walker);

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);
		for (int tick = 0; tick < 40; tick++)
		{
			scene.onGameTick(PLAYER, view);
		}
		assertEquals(1, client.registeredCount());

		CitizenWalk walk = walkOf(scene, walker);
		WorldPoint whereItStopped = walk.currentTile();

		// Player walks out of range; the citizen is deactivated.
		WorldPoint away = new WorldPoint(3225, 3355 - RenderPolicy.DEFAULT_CULL_RADIUS - 5, 0);
		for (int tick = 0; tick < 40; tick++)
		{
			scene.onGameTick(away, view);
			scene.onFrame(view, 0.5f);
		}
		assertEquals(0, client.registeredCount());
		assertEquals("a citizen nobody can see does not walk", whereItStopped, walk.currentTile());
		assertEquals("and does not cost the frame pass anything either",
			0, scene.getWalkerCount());

		// Back in range: it picks up from the tile it was on, not from its
		// authored one.
		scene.onGameTick(PLAYER, view);
		assertEquals(1, client.registeredCount());
		assertEquals(1, scene.getWalkerCount());
	}

	@Test
	public void anInstancedSceneAlsoEmptiesThePerFrameList()
	{
		EntityDefinition walker = regions.wanderer(
			VARROCK_SOUTH,
			new WorldPoint(3225, 3355, 0),
			new WorldPoint(3221, 3351, 0),
			new WorldPoint(3229, 3359, 0),
			500);
		regions.file(VARROCK_SOUTH, walker);

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);
		scene.onGameTick(PLAYER, view);
		assertEquals(1, scene.getWalkerCount());

		view.setInstance(true);
		scene.onGameTick(PLAYER, view);
		assertEquals("an instance must leave nothing registered", 0, client.registeredCount());
		assertEquals("and nothing for the frame pass to interpolate inside a raid",
			0, scene.getWalkerCount());

		// A frame in an instance must therefore be a no-op.
		scene.onFrame(view, 0.5f);
		assertEquals(0, client.registeredCount());
	}

	/**
	 * The per-frame list must be empty the moment the scope changes, not merely by
	 * the time the next visibility pass has rebuilt it.
	 *
	 * <p>It holds wrapper references, and a scope change is where wrappers get
	 * evicted. Leaving stale entries in it between the scope change and the next
	 * pass is how a frame handler ends up holding the only reference to something
	 * the cache has let go of.
	 */
	@Test
	public void aSceneChangeAndAnInvalidateBothEmptyThePerFrameListImmediately()
	{
		EntityDefinition walker = regions.wanderer(
			VARROCK_SOUTH,
			new WorldPoint(3225, 3355, 0),
			new WorldPoint(3221, 3351, 0),
			new WorldPoint(3229, 3359, 0),
			500);
		regions.file(VARROCK_SOUTH, walker);

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);
		scene.onGameTick(PLAYER, view);
		assertEquals(1, scene.getWalkerCount());

		// An invalidate on its own — no visibility pass afterwards.
		scene.invalidate("LOADING");
		assertEquals("invalidate must empty it", 0, scene.getWalkerCount());

		// Back, then a scene change on its own.
		scene.syncRegions(view);
		scene.onGameTick(PLAYER, view);
		assertEquals(1, scene.getWalkerCount());

		view.setMapRegions(9999);
		scene.syncRegions(view);
		assertEquals("a scope change must empty it there and then", 0, scene.getWalkerCount());

		// And a frame landing in that window is a no-op rather than a resurrection.
		scene.onFrame(view, 0.5f);
		assertEquals(0, client.registeredCount());

		// Same again for teardown, which has to leave nothing pointing at anything.
		view.setMapRegions(VARROCK_SOUTH);
		scene.syncRegions(view);
		scene.onGameTick(PLAYER, view);
		assertEquals(1, scene.getWalkerCount());

		scene.shutdown();
		assertEquals("shutdown must empty it too", 0, scene.getWalkerCount());
		assertEquals(0, client.registeredCount());
	}

	/**
	 * The walk the scene is actually driving — not a fresh one built from the same
	 * definition, which would be a copy nothing has ever ticked.
	 */
	private static CitizenWalk walkOf(EntityScene scene, EntityDefinition definition)
	{
		LivelyEntity wrapper = scene.wrapperFor(definition);
		assertNotNull("the scene should be holding a wrapper for this definition", wrapper);
		CitizenWalk walk = wrapper.getWalk();
		assertNotNull("and that wrapper should have a walk", walk);
		return walk;
	}

	/**
	 * The visibility pass is what re-asks for an animation that missed a cold
	 * cache, for everything that stands still.
	 *
	 * <p>{@link LivelyEntity} not caching a dead controller is only half the fix:
	 * something has to ask again. A wanderer gets that for free — {@code
	 * advanceTick} re-selects its controller every game tick — but a
	 * {@code StationaryCitizen} has no other clock, and most of the dataset stands
	 * still. Without the call in {@code updateVisibility} the animation would never
	 * arrive, which is the same session-long freeze one level up.
	 */
	@Test
	public void theVisibilityPassPicksUpAnAnimationThatMissedAColdCache()
	{
		EntityDefinition citizen = regions.animatedCitizen(VARROCK_SOUTH, 3225, 3355, "Fletching");
		regions.file(VARROCK_SOUTH, citizen);
		client.setUnloadableAnimations(LivelyAnimation.Fletching.getId());

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);
		scene.onGameTick(PLAYER, view);

		LivelyEntity wrapper = scene.wrapperFor(citizen);
		assertNotNull("the scene should be holding a wrapper", wrapper);
		assertNull("this citizen has no walk, so nothing else could ever re-ask",
			wrapper.getWalk());
		assertEquals("it still spawns — standing in the right place beats not being there",
			1, client.registeredCount());
		assertNull("and nothing may be installed while the animation is missing",
			wrapper.getInstalledController());

		client.clearUnloadableAnimations();
		for (int pass = 0; pass < LivelyEntity.RETRY_BACKOFF_PASSES; pass++)
		{
			scene.onGameTick(PLAYER, view);
		}

		assertNotNull("the visibility pass has to be what asks again",
			wrapper.getInstalledController());
		assertEquals("and it must not have cost the entity its place in the scene",
			1, client.registeredCount());
	}

	@Test
	public void anEntityBeyondTheCullRadiusIsDeactivatedNotJustHidden()
	{
		regions.file(VARROCK_SOUTH, regions.citizen(VARROCK_SOUTH, 3225, 3355, 0));

		FakeWorldView view = FakeWorldView.around(PLAYER, VARROCK_SOUTH);
		scene.syncRegions(view);
		scene.updateVisibility(PLAYER, view);
		assertEquals(1, client.registeredCount());

		// Same scene, same scope — only the player has walked out of range.
		// One tile past the cull radius, measured from the entity, not from
		// where the player started.
		WorldPoint away = new WorldPoint(3225, 3355 - RenderPolicy.DEFAULT_CULL_RADIUS - 1, 0);
		scene.updateVisibility(away, view);
		assertEquals("out of range means deactivated", 0, client.registeredCount());
		assertEquals("and the wrapper is still cached, ready to come back", 1, scene.getCachedRegionCount());
	}
}
